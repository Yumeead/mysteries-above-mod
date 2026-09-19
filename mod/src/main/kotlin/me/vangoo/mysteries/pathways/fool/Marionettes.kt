package me.vangoo.mysteries.pathways.fool

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.entity.ModEntities
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload
import me.vangoo.mysteries.net.payload.MarionetteStatePayload
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who is on whose threads: the whole of Fool Seq. 5 that is not an ability.
 *
 * ## Two kinds of puppet, and only one of them was ever killed
 *
 * The plugin converted **every** target the same way — 10 000 damage, then a Citizens NPC in its
 * place — because Citizens was the only body Spigot let it steer. Here a mob target is never
 * killed: it stays the mob it already was, minus the will to pick its own enemies ([seize]). Only
 * a player, whose body cannot be borrowed, leaves a [MarionetteEntity] behind ([convert]).
 *
 * So a puppet is a plain [LivingEntity] and the few places the two kinds differ branch on the
 * type. That branch buys real pathfinding for mobs, which an `Avatar` cannot have at all.
 *
 * ## The strand clock is the one thing on the wall clock
 *
 * Ten minutes out of control and the puppet dies — the plugin's rule, kept. Like `TheftRegistry`
 * and unlike everything else in the mod, it is measured in real time rather than ticks: a
 * countdown that pauses while the owner is offline would make "out of control" cost nothing.
 *
 * ## Death is where the Characteristic comes from
 *
 * Any death of a puppet made from a Beyonder condenses their Characteristic — stranded, killed by
 * a stranger, killed by its own master, or released. Thirty seconds of the power gathering itself
 * out of the corpse, then a core on the ground. That is why [MarionetteEntity.Capture] holds the
 * pathway and Sequence taken **at conversion**: by the time the puppet falls, the living original
 * is already one Sequence weaker (the Law of Convergence is permanent), and asking them what they
 * are would answer wrong.
 */
object Marionettes {

    /** Puppets by owner, in the order they were taken — the order the HUD row shows. */
    private val byOwner = ConcurrentHashMap<UUID, MutableList<LivingEntity>>()

    /** Which one the owner is commanding right now. */
    private val chosen = ConcurrentHashMap<UUID, UUID>()

    /** Puppet → when it dies of being out of control, in wall-clock millis. */
    private val stranded = ConcurrentHashMap<UUID, Long>()

    /** A seized mob's standing order. [MarionetteEntity] keeps its own — it steers itself every tick. */
    private val mobOrders = ConcurrentHashMap<UUID, MarionetteEntity.Order>()

    private val possessions = ConcurrentHashMap<UUID, Possession>()

    /**
     * A Marionettist inside one of their puppets.
     *
     * @property anchor where the body was left standing — `setCamera` teleports it to the puppet,
     *   so it has to be put back by hand on the very same tick
     * @property yaw where the driver is looking; it arrives on the mod's own channel, because a
     *   client whose camera is elsewhere stops sending its rotation
     */
    private class Possession(
        val puppet: LivingEntity,
        val anchor: Vec3,
        var yaw: Float,
        var pitch: Float,
        /** The victim's powers, lent for as long as the driver stays inside. */
        val borrowed: List<AbilityIdentity>,
    )

    // ── Registry ────────────────────────────────────────────────────────────────

    fun of(ownerId: UUID): List<LivingEntity> =
        byOwner[ownerId]?.filter { it.isAlive } ?: emptyList()

    fun selected(ownerId: UUID): LivingEntity? {
        val puppets = of(ownerId)
        if (puppets.isEmpty()) return null
        val wanted = chosen[ownerId]
        return puppets.firstOrNull { it.uuid == wanted } ?: puppets.first().also { chosen[ownerId] = it.uuid }
    }

    /** Steps the selection [delta] places along the row, wrapping — the mouse wheel's whole job. */
    fun cycle(ownerId: UUID, delta: Int) {
        val puppets = of(ownerId)
        if (puppets.isEmpty()) return
        val at = puppets.indexOfFirst { it.uuid == chosen[ownerId] }.coerceAtLeast(0)
        val next = Math.floorMod(at + delta, puppets.size)
        chosen[ownerId] = puppets[next].uuid
    }

    fun select(ownerId: UUID, puppetId: UUID) {
        if (of(ownerId).any { it.uuid == puppetId }) chosen[ownerId] = puppetId
    }

    fun isPuppet(entity: LivingEntity): Boolean = byOwner.values.any { list -> list.any { it === entity } }

    fun ownerOf(puppet: LivingEntity): UUID? =
        byOwner.entries.firstOrNull { (_, list) -> list.any { it === puppet } }?.key

    /** How many threads this Marionettist can hold at once: 1, 2 or 3 by mastery — the plugin's tiers. */
    fun limitFor(beyonder: Beyonder): Int = when {
        beyonder.mastery.value >= 100.0 -> 3
        beyonder.mastery.value >= 50.0 -> 2
        else -> 1
    }

    /** How far a thread reaches before it starts to fray: 100 blocks, 200 past half mastery. */
    fun controlRange(beyonder: Beyonder): Double = if (beyonder.mastery.value >= 50.0) 200.0 else 100.0

    fun orderOf(puppet: LivingEntity): MarionetteEntity.Order = when (puppet) {
        is MarionetteEntity -> puppet.order
        else -> mobOrders[puppet.uuid] ?: MarionetteEntity.Order.Stand
    }

    // ── Taking a body ───────────────────────────────────────────────────────────

    /**
     * A mob goes on the threads without dying: it loses the right to choose its own enemies and
     * keeps everything else, its own pathfinding included.
     *
     * ponytail: only the target selector is cleared, so the mob's melee goals still do the hitting
     * for us. Clearing every goal would leave a puppet that can be ordered to attack and then
     * stands there.
     */
    fun seize(owner: ServerPlayer, mob: Mob) {
        mob.targetSelector.removeAllGoals { true }
        mob.setTarget(null)
        mob.setPersistenceRequired()
        register(owner.uuid, mob)
    }

    /**
     * A player goes on the threads the only way a player can: their body dies and a puppet wearing
     * their face gets up in its place.
     *
     * Their things travel **into the puppet** and are cleared before the killing blow — the
     * plugin's own measured fix against duplicating the inventory, kept as it was.
     */
    fun convert(owner: ServerPlayer, victim: ServerPlayer): MarionetteEntity {
        val level = victim.level()
        val beyonder = AbilityServices.beyonders.of(victim.uuid)
        val capture = MarionetteEntity.Capture(
            victimId = victim.uuid,
            name = victim.gameProfile.name,
            pathway = beyonder?.pathway?.name,
            sequence = beyonder?.sequence?.level,
            inventory = victim.inventory.nonEquipmentItems.filterNot { it.isEmpty }.map { it.copy() },
        )

        val puppet = MarionetteEntity(ModEntities.MARIONETTE, level)
        puppet.snapTo(victim.x, victim.y, victim.z, victim.yRot, victim.xRot)
        puppet.setProfile(ResolvableProfile.createResolved(victim.gameProfile))
        puppet.inheritHealth(victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH), victim.health.toDouble())
        puppet.customName = Component.literal(capture.name)
        puppet.isCustomNameVisible = true
        puppet.capture = capture
        EquipmentSlot.entries.forEach { slot -> puppet.setItemSlot(slot, victim.getItemBySlot(slot).copy()) }

        victim.inventory.clearContent()
        level.addFreshEntity(puppet)
        register(owner.uuid, puppet)

        conversionEffects(level, puppet.position())
        victim.kill(level)

        // The Law of Convergence, and it is permanent: the Characteristic has left the original
        // and now lives in the puppet. It comes back into the world only when the puppet falls.
        if (beyonder != null && beyonder.sequence.canDemote()) {
            beyonder.demote()
            AbilityServices.messaging.tell(
                victim.uuid,
                Component.literal("Вашу Характеристику вирвано в маріонетку — Послідовність ослабла до ${beyonder.sequence.level}"),
            )
            AbilityServices.beyonders.of(victim.uuid)?.let { BeyonderStatus.pushRoster(victim) }
        }
        return puppet
    }

    private fun register(ownerId: UUID, puppet: LivingEntity) {
        byOwner.computeIfAbsent(ownerId) { mutableListOf() } += puppet
        chosen[ownerId] = puppet.uuid
        mobOrders.remove(puppet.uuid)
        if (puppet is MarionetteEntity) puppet.ownerId = ownerId
    }

    // ── Orders ──────────────────────────────────────────────────────────────────

    fun command(puppet: LivingEntity, order: MarionetteEntity.Order) {
        when (puppet) {
            is MarionetteEntity -> puppet.order = order
            else -> {
                mobOrders[puppet.uuid] = order
                applyToMob(puppet, order)
            }
        }
    }

    private fun applyToMob(puppet: LivingEntity, order: MarionetteEntity.Order) {
        val mob = puppet as? Mob ?: return
        when (order) {
            is MarionetteEntity.Order.Stand -> {
                mob.navigation.stop()
                mob.target = null
            }
            is MarionetteEntity.Order.GoTo -> {
                mob.target = null
                mob.navigation.moveTo(order.target.x, order.target.y, order.target.z, MOB_SPEED)
            }
            is MarionetteEntity.Order.Follow -> {
                val owner = ownerOf(mob)?.let { (mob.level() as? ServerLevel)?.getPlayerInAnyDimension(it) }
                if (owner != null && owner.level() === mob.level() && mob.distanceToSqr(owner) > 9.0) {
                    mob.navigation.moveTo(owner, MOB_SPEED)
                }
            }
            is MarionetteEntity.Order.Attack -> {
                val victim = (mob.level() as? ServerLevel)?.getEntityInAnyDimension(order.victimId) as? LivingEntity
                mob.target = victim
            }
        }
    }

    // ── Possession ──────────────────────────────────────────────────────────────

    fun isPossessing(ownerId: UUID): Boolean = possessions.containsKey(ownerId)

    fun possessedBy(ownerId: UUID): LivingEntity? = possessions[ownerId]?.puppet

    /**
     * The Marionettist steps into a puppet — and their body stays exactly where it stood.
     *
     * `setCamera` is what makes this cheap: with the camera elsewhere the vanilla client stops
     * applying input to its own body and stops sending its position (`LocalPlayer.applyInput` and
     * `sendPosition` are both behind `isControlledCamera`), while it keeps sending the keys
     * themselves. So the body is frozen by vanilla, not by us, and the keys arrive as
     * `ServerPlayer.lastClientInput` with no mixin anywhere.
     *
     * The one thing to undo is `setCamera`'s own teleport: it moves the player to the camera
     * entity, which is the single line standing between this and a body left behind.
     */
    fun possess(owner: ServerPlayer, puppet: LivingEntity) {
        val anchor = owner.position()
        owner.setCamera(puppet)
        owner.teleportTo(anchor.x, anchor.y, anchor.z)
        possessions[owner.uuid] = Possession(puppet, anchor, owner.yRot, owner.xRot, lend(owner, puppet))
        swapEffects(owner.level(), anchor, puppet.position())
        BeyonderStatus.pushRoster(owner)
    }

    /**
     * The victim's powers, for as long as the driver is wearing their body.
     *
     * This is what the Law of Convergence is **for**: the Characteristic did not evaporate when it
     * left its owner, it moved into the puppet, and whoever is inside the puppet is holding it.
     * The seam already exists — `Beyonder.grantOffPathway` is what Error's theft casts through —
     * so nothing new is needed to make somebody else's Sequence castable.
     *
     * A power the driver already has of their own is not lent and, crucially, not taken back on
     * the way out: only what this possession actually added is recorded.
     */
    private fun lend(owner: ServerPlayer, puppet: LivingEntity): List<AbilityIdentity> {
        val capture = (puppet as? MarionetteEntity)?.capture ?: return emptyList()
        val pathway = capture.pathway?.let(ModPathways::byName) ?: return emptyList()
        val sequence = capture.sequence ?: return emptyList()
        val beyonder = AbilityServices.beyonders.of(owner.uuid) ?: return emptyList()

        val lent = Sequence(sequence).getAllSequencesUpToCurrent()
            .flatMap(pathway::abilitiesFor)
            .filter { beyonder.grantOffPathway(it) }
            .map { it.identity }

        if (lent.isNotEmpty()) {
            AbilityServices.messaging.tell(
                owner,
                Component.literal("Крізь це тіло вам відкрились сили ${capture.name}: ${lent.size}"),
            )
        }
        return lent
    }

    /** Back into one's own body: the puppet stops where it stands, the threads stay tied. */
    fun eject(ownerId: UUID, reason: Component? = null) {
        val possession = possessions.remove(ownerId) ?: return
        (possession.puppet as? MarionetteEntity)?.driverInput = null

        // The borrowed powers go back with the body. Only what this possession lent is taken —
        // a power the driver owns in their own right was never on the list.
        val beyonder = AbilityServices.beyonders.of(ownerId)
        possession.borrowed.forEach { beyonder?.remove(it) }

        val owner = player(ownerId)
        if (owner != null) {
            owner.setCamera(owner)
            owner.teleportTo(possession.anchor.x, possession.anchor.y, possession.anchor.z)
            swapEffects(owner.level(), possession.anchor, possession.puppet.position())
            reason?.let { AbilityServices.messaging.actionBar(owner, it) }
            BeyonderStatus.pushRoster(owner)
        }
    }

    /** The driver's head: their client's camera is on the puppet, so its rotation comes to us. */
    fun look(ownerId: UUID, yaw: Float, pitch: Float) {
        val possession = possessions[ownerId] ?: return
        possession.yaw = yaw
        possession.pitch = pitch
    }

    /**
     * The driver swung: whatever the puppet is looking at takes the blow.
     *
     * The blow has to come from here rather than from the vanilla attack the client also sends —
     * that one is measured from the **body**, standing a hundred blocks away, and the server drops
     * it on its reach check. The ray is cast from the puppet's own eyes, which is where the driver
     * is looking from anyway.
     */
    fun strike(ownerId: UUID) {
        val puppet = possessions[ownerId]?.puppet ?: return
        val level = puppet.level() as? ServerLevel ?: return
        val hit = ProjectileUtil.getHitResultOnViewVector(
            puppet,
            { it !== puppet && it is LivingEntity && it.isAlive },
            MELEE_REACH,
        )
        val victim = (hit as? EntityHitResult)?.entity ?: return
        puppet.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        puppet.doHurtTarget(level, victim)
    }

    // ── The Marionettist's hands ────────────────────────────────────────────────

    /**
     * One order off the wire.
     *
     * The aiming happens here rather than on the client: while the Marionettist stands in the
     * world their look and their sneak are server-side truth already, so the four verbs come out
     * of one ray — a creature under the crosshair is *attack*, a block is *go there*, empty air is
     * *follow*, and sneaking is *stand still*, whatever the crosshair says.
     */
    fun handle(owner: ServerPlayer, payload: MarionetteOrderPayload) {
        when (payload.action) {
            MarionetteOrderPayload.Action.CYCLE -> {
                cycle(owner.uuid, payload.delta)
                pushState(owner)
            }
            MarionetteOrderPayload.Action.LOOK -> look(owner.uuid, payload.yaw, payload.pitch)
            MarionetteOrderPayload.Action.STRIKE -> strike(owner.uuid)
            MarionetteOrderPayload.Action.CUT -> {
                val puppet = selected(owner.uuid) ?: return
                AbilityServices.messaging.actionBar(
                    owner,
                    Component.literal("✂ Нитку обрізано: ${nameOf(puppet)}"),
                )
                release(puppet)
                pushState(owner)
            }
            MarionetteOrderPayload.Action.ORDER -> order(owner)
        }
    }

    private fun order(owner: ServerPlayer) {
        // Pointing at one of your own puppets picks it. The wheel walks the row; this reaches
        // straight for the one you can see, and it costs a single raycast — the free half of the
        // "choose it in the world" answer, sitting on top of the HUD row rather than replacing it.
        lookingAtOwn(owner)?.let { seen ->
            select(owner.uuid, seen.uuid)
            AbilityServices.messaging.actionBar(owner, Component.literal("▸ ${nameOf(seen)}"))
            pushState(owner)
            return
        }

        val puppet = selected(owner.uuid) ?: return
        val beyonder = AbilityServices.beyonders.of(owner.uuid) ?: return
        if (owner.distanceToSqr(puppet) > controlRange(beyonder) * controlRange(beyonder)) {
            AbilityServices.messaging.actionBar(owner, Component.literal("Нитка не дістає — ${nameOf(puppet)} задалеко"))
            return
        }

        val order = when {
            owner.isShiftKeyDown -> MarionetteEntity.Order.Stand
            else -> aim(owner)
        }
        command(puppet, order)
        AbilityServices.messaging.actionBar(owner, Component.literal("${nameOf(puppet)} — ${caption(order)}"))
        owner.level().playSound(
            null, owner.x, owner.y, owner.z, SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 0.5f, 1.6f,
        )
        pushState(owner)
    }

    /** One of this Marionettist's own puppets under the crosshair, or `null`. */
    private fun lookingAtOwn(owner: ServerPlayer): LivingEntity? {
        val mine = of(owner.uuid)
        if (mine.isEmpty()) return null
        val hit = ProjectileUtil.getHitResultOnViewVector(
            owner,
            { candidate -> mine.any { it === candidate } },
            ORDER_REACH,
        )
        return (hit as? EntityHitResult)?.entity as? LivingEntity
    }

    /** What the Marionettist is pointing at, turned into a verb. */
    private fun aim(owner: ServerPlayer): MarionetteEntity.Order {
        val creature = ProjectileUtil.getHitResultOnViewVector(
            owner,
            { it !== owner && it is LivingEntity && it.isAlive && !isPuppet(it) },
            ORDER_REACH,
        )
        (creature as? EntityHitResult)?.entity?.let { return MarionetteEntity.Order.Attack(it.uuid) }

        val block = owner.pick(ORDER_REACH, 0f, false)
        if (block.type == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return MarionetteEntity.Order.GoTo(block.location)
        }
        return MarionetteEntity.Order.Follow
    }

    private fun caption(order: MarionetteEntity.Order): String = when (order) {
        is MarionetteEntity.Order.Stand -> "стій"
        is MarionetteEntity.Order.GoTo -> "іди туди"
        is MarionetteEntity.Order.Follow -> "за мною"
        is MarionetteEntity.Order.Attack -> "бий"
    }

    // ── What the client sees ────────────────────────────────────────────────────

    fun pushState(owner: ServerPlayer) {
        ModChannels.MARIONETTE_STATE.sendTo(owner, snapshot(owner))
    }

    private fun snapshot(owner: ServerPlayer): MarionetteStatePayload {
        val puppets = of(owner.uuid)
        if (puppets.isEmpty()) return MarionetteStatePayload.EMPTY

        val beyonder = AbilityServices.beyonders.of(owner.uuid)
        val now = System.currentTimeMillis()
        return MarionetteStatePayload(
            entries = puppets.map { puppet ->
                MarionetteStatePayload.Entry(
                    id = puppet.uuid,
                    name = nameOf(puppet),
                    health = puppet.health,
                    maxHealth = puppet.maxHealth,
                    order = when (orderOf(puppet)) {
                        is MarionetteEntity.Order.Stand -> MarionetteStatePayload.Order.STAND
                        is MarionetteEntity.Order.GoTo -> MarionetteStatePayload.Order.GOING
                        is MarionetteEntity.Order.Follow -> MarionetteStatePayload.Order.FOLLOWING
                        is MarionetteEntity.Order.Attack -> MarionetteStatePayload.Order.ATTACKING
                    },
                    strandSeconds = stranded[puppet.uuid]?.let { ((it - now) / 1000L).coerceAtLeast(0L).toInt() } ?: 0,
                    distance = if (owner.level() === puppet.level()) owner.distanceTo(puppet).toInt() else -1,
                )
            },
            selected = selected(owner.uuid)?.uuid,
            possessing = possessions[owner.uuid]?.puppet?.uuid,
            limit = beyonder?.let(::limitFor) ?: 0,
        )
    }

    // ── The clock ───────────────────────────────────────────────────────────────

    fun install() {
        ModChannels.MARIONETTE_ORDER.receiveOnServer { payload, context -> handle(context.player(), payload) }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            drive()
            if (server.tickCount % TICKS_PER_SECOND == 0) sweep()
        }

        // A puppet falling is the only way a Characteristic ever comes back into the world.
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> onDeath(entity) }

        // The driver cannot see their own body being hit — their eyes are a hundred blocks away —
        // so they are told the only way that works from inside someone else: they hear it. A
        // ready Fabric hook, i.e. step one of the search order; no mixin is involved.
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, _, taken, _ ->
            if (entity !is ServerPlayer || taken <= 0f || !isPossessing(entity.uuid)) return@register
            entity.connection.send(
                ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.HEARTBEAT),
                    SoundSource.MASTER,
                    entity.x, entity.y, entity.z,
                    1.4f, 0.7f,
                    entity.level().random.nextLong(),
                ),
            )
            AbilityServices.messaging.actionBar(entity, Component.literal("❤ Ваше тіло б'ють!"))
        }

        // The Marionettist left the game: nobody is holding the threads, so every puppet starts
        // the same ten-minute clock a puppet out of range does. The rule is one.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val id = handler.player.uuid
            eject(id)
            of(id).forEach { strand(it) }
        }
    }

    /** Every tick, because this is somebody walking: one frame of lag reads as the puppet stuttering. */
    private fun drive() {
        possessions.forEach { (ownerId, possession) ->
            val owner = player(ownerId) ?: return@forEach
            val puppet = possession.puppet
            if (!puppet.isAlive) {
                eject(ownerId, Component.literal("Маріонетку розбито — вас викинуло у власне тіло"))
                return@forEach
            }
            // The body must not drift: `setCamera` froze the client's own movement, but a shove or
            // a piston still moves the server-side entity, and the anchor is the promise that the
            // Marionettist is where they were left.
            if (owner.position().distanceToSqr(possession.anchor) > DRIFT * DRIFT) {
                owner.teleportTo(possession.anchor.x, possession.anchor.y, possession.anchor.z)
            }
            puppet.yRot = possession.yaw
            puppet.yHeadRot = possession.yaw
            puppet.yBodyRot = possession.yaw
            puppet.xRot = possession.pitch
            when (puppet) {
                is MarionetteEntity -> puppet.driverInput = owner.lastClientInput
                is Mob -> {
                    puppet.navigation.stop()
                    puppet.zza = if (owner.lastClientInput.forward()) 1f else 0f
                    if (owner.lastClientInput.jump() && puppet.onGround()) puppet.jumpFromGround()
                }
                else -> Unit
            }
        }
    }

    /** Once a second: who has drifted out of reach, and whose ten minutes have run out. */
    private fun sweep() {
        byOwner.forEach { (ownerId, puppets) ->
            puppets.removeIf { !it.isAlive }
            val owner = player(ownerId)
            val beyonder = AbilityServices.beyonders.of(ownerId)
            val range = beyonder?.let(::controlRange) ?: 0.0

            puppets.forEach { puppet ->
                val inReach = owner != null &&
                    owner.level() === puppet.level() &&
                    owner.distanceToSqr(puppet) <= range * range
                when {
                    inReach -> recover(puppet, owner)
                    else -> strand(puppet)
                }
            }

            val deadline = System.currentTimeMillis()
            puppets.filter { (stranded[it.uuid] ?: Long.MAX_VALUE) <= deadline }
                .forEach { it.kill(it.level() as ServerLevel) }

            // The row of threads is redrawn on the same beat the numbers on it change: health,
            // distance and the strand countdown all move on their own, so there is nothing here
            // for a dirty flag to save.
            owner?.let(::pushState)
        }
        byOwner.values.removeIf { it.isEmpty() }
    }

    private fun strand(puppet: LivingEntity) {
        if (stranded.containsKey(puppet.uuid)) return
        stranded[puppet.uuid] = System.currentTimeMillis() + STRAND_MILLIS
        ownerOf(puppet)?.let { ownerId ->
            AbilityServices.messaging.tell(
                ownerId,
                Component.literal("Нитка натягнулась до краю: ${nameOf(puppet)} поза зоною контролю. Десять хвилин — і вона обірветься."),
            )
        }
    }

    private fun recover(puppet: LivingEntity, owner: ServerPlayer?) {
        if (stranded.remove(puppet.uuid) == null) return
        owner?.let {
            AbilityServices.messaging.tell(it, Component.literal("Нитка знову тримає: ${nameOf(puppet)}"))
        }
    }

    // ── Death and condensation ──────────────────────────────────────────────────

    /** Cutting the thread. The puppet does not walk away free — a body with no threads falls. */
    fun release(puppet: LivingEntity) {
        val level = puppet.level() as? ServerLevel ?: return
        puppet.kill(level)
    }

    private fun onDeath(puppet: LivingEntity) {
        // The body died while its owner was away in a puppet. Nothing brings them back into it —
        // that is the whole price of possession — but the camera has to come home, or the player
        // would respawn still looking out of somebody else's eyes.
        if (puppet is ServerPlayer && isPossessing(puppet.uuid)) {
            eject(puppet.uuid, Component.literal("Ваше тіло загинуло, поки вас у ньому не було"))
        }

        val ownerId = ownerOf(puppet) ?: return
        val level = puppet.level() as? ServerLevel ?: return
        val where = puppet.position()

        byOwner[ownerId]?.removeIf { it === puppet }
        stranded.remove(puppet.uuid)
        mobOrders.remove(puppet.uuid)
        if (possessions[ownerId]?.puppet === puppet) {
            eject(ownerId, Component.literal("Маріонетку розбито — вас викинуло у власне тіло"))
        }

        val capture = (puppet as? MarionetteEntity)?.capture
        AbilityServices.messaging.tell(
            ownerId,
            Component.literal("Маріонетку ${capture?.name ?: nameOf(puppet)} знищено"),
        )
        capture?.inventory?.forEach { stack ->
            net.minecraft.world.entity.item.ItemEntity(level, where.x, where.y + 0.5, where.z, stack).also(level::addFreshEntity)
        }

        val pathway = capture?.pathway ?: return
        val sequence = capture.sequence ?: return
        condense(level, where, pathway, sequence, capture.name)
    }

    /**
     * Thirty seconds of a power gathering itself out of a corpse, then a core on the ground.
     *
     * The wait is the point: a Characteristic is the scarcest thing in the game, and anyone who
     * saw the puppet fall has half a minute to reach the spot and take it. The plugin condensed
     * instantly, into an invulnerable drop — the invulnerability is kept, the instant is not.
     */
    private fun condense(level: ServerLevel, where: Vec3, pathway: String, sequence: Int, name: String) {
        val scheduling = AbilityServices.scheduling
        var ticks = 0
        lateinit var handle: me.vangoo.mysteries.schedule.TaskHandle
        handle = scheduling.every(CONDENSE_STEP_TICKS) {
            ticks += CONDENSE_STEP_TICKS
            val progress = ticks.toFloat() / CONDENSE_TICKS
            level.sendParticles(
                ParticleTypes.SOUL, where.x, where.y + 0.6, where.z,
                CONDENSE_PARTICLES, (1.6 * (1.0 - progress)), 0.4, (1.6 * (1.0 - progress)), 0.01,
            )
            level.playSound(
                null, where.x, where.y, where.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.AMBIENT, 0.7f, 0.5f + progress,
            )
            if (ticks < CONDENSE_TICKS) return@every

            handle.cancel()
            level.sendParticles(ParticleTypes.END_ROD, where.x, where.y + 0.6, where.z, 30, 0.1, 0.3, 0.1, 0.02)
            level.playSound(
                null, where.x, where.y, where.z, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1.2f, 0.6f,
            )
            val core = net.minecraft.world.entity.item.ItemEntity(
                level, where.x, where.y + 0.6, where.z, ModItems.characteristicOf(pathway, sequence),
            )
            // The scarcest thing in the game does not burn away in a lava pool it happened to fall in.
            core.setUnlimitedLifetime()
            core.isInvulnerable = true
            level.addFreshEntity(core)
            level.players().forEach { nearby ->
                if (nearby.position().distanceToSqr(where) < CONDENSE_HERALD_RANGE) {
                    AbilityServices.messaging.actionBar(nearby, Component.literal("З $name згустилась Характеристика"))
                }
            }
        }
    }

    private fun conversionEffects(level: ServerLevel, where: Vec3) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, where.x, where.y + 1.0, where.z, 60, 0.4, 0.8, 0.4, 0.02)
        level.sendParticles(ParticleTypes.SQUID_INK, where.x, where.y + 1.0, where.z, 40, 0.5, 0.7, 0.5, 0.0)
        level.playSound(null, where.x, where.y, where.z, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.2f, 0.5f)
    }

    private fun swapEffects(level: ServerLevel, from: Vec3, to: Vec3) {
        listOf(from, to).forEach { at ->
            level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 40, 0.4, 0.8, 0.4, 0.3)
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.7f)
        }
    }

    fun nameOf(puppet: LivingEntity): String =
        (puppet as? MarionetteEntity)?.capture?.name ?: puppet.name.string

    /** Server shutdown or a fresh world: puppets do not outlive the server that held them. */
    fun reset() {
        byOwner.clear()
        chosen.clear()
        stranded.clear()
        mobOrders.clear()
        possessions.clear()
    }

    private fun player(id: UUID): ServerPlayer? = AbilityServices.playerOf(id)

    /** Ten minutes, on the wall clock. See the class comment. */
    private const val STRAND_MILLIS = 10L * 60L * 1000L

    private const val CONDENSE_TICKS = 30 * TICKS_PER_SECOND
    private const val CONDENSE_STEP_TICKS = 5
    private const val CONDENSE_PARTICLES = 12
    private const val CONDENSE_HERALD_RANGE = 32.0 * 32.0

    private const val MOB_SPEED = 1.1
    private const val MELEE_REACH = 3.5
    private const val DRIFT = 1.5

    /** How far an order can be pointed. Further than a hand reaches, and rightly: it is a gesture. */
    private const val ORDER_REACH = 64.0
}
