package me.vangoo.mysteries.pathways.fool

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.SelectiveVisibility
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.entity.ModEntities
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.MarionetteStatePayload
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import me.vangoo.mysteries.net.payload.ThreadSightPayload
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import com.mojang.serialization.Codec
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.core.UUIDUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.ItemStack
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

    /**
     * A seized mob's owner, written with its chunk. Player-made puppets save their own
     * ([MarionetteEntity.ownerId]); a mob is vanilla's, so the mark rides on it as an attachment.
     * Lazy: Fabric registers attachments through a mixin, and unit tests have none.
     */
    private val PUPPET_OF: AttachmentType<UUID> by lazy {
        AttachmentRegistry.create(modId("puppet_of")) { it.persistent(UUIDUtil.CODEC) }
    }

    /**
     * When a puppet out of control dies, in wall-clock millis — on the puppet, so a restart or an
     * unloaded chunk does not wind the ten minutes back.
     */
    private val STRANDED_UNTIL: AttachmentType<Long> by lazy {
        AttachmentRegistry.create(modId("stranded_until")) { it.persistent(Codec.LONG) }
    }

    /** A seized mob's standing order. [MarionetteEntity] keeps its own — it steers itself every tick. */
    private val mobOrders = ConcurrentHashMap<UUID, MarionetteEntity.Order>()

    private val possessions = ConcurrentHashMap<UUID, Possession>()

    /**
     * A Marionettist inside one of their puppets.
     *
     * @property vessel the body left standing — a stand-in in the player's face, see [possess]
     */
    private class Possession(
        val puppet: LivingEntity,
        val vessel: MarionetteEntity,
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

    // ── What Spirit Vision sees ─────────────────────────────────────────────────

    /** A grip being taken right now: the victim, whoever weaves it, and how far it has gone. */
    private class Weave(val victim: LivingEntity, val casterId: UUID, val grip: Float)

    private val weaves = ConcurrentHashMap<UUID, Weave>()

    /** Marionette Control reports its progress here, so every Marionettist nearby sees it. */
    fun weave(victim: LivingEntity, caster: ServerPlayer, grip: Float) {
        weaves[victim.uuid] = Weave(victim, caster.uuid, grip)
    }

    fun unweave(victimId: UUID) {
        weaves.remove(victimId)
    }

    /**
     * Every body in [level] that is on someone's threads, or getting there.
     *
     * A possessing owner holds the threads **from inside** the puppet they wear — the player is
     * hidden from everyone else then, so the puppet's id is the one other clients can find. The
     * worn puppet itself is left out: its threads would run from it to itself.
     */
    fun bonds(level: ServerLevel): List<ThreadSightPayload.Bond> {
        val held = byOwner.flatMap { (ownerId, puppets) ->
            val worn = possessions[ownerId]?.puppet
            val holder = worn ?: player(ownerId)?.takeIf { it.level() === level }
            puppets.filter { it.isAlive && it.level() === level && it !== worn }
                .map { ThreadSightPayload.Bond(it.id, holder?.id ?: ThreadSightPayload.NO_HOLDER, 1f) }
        }
        val woven = weaves.values.filter { it.victim.isAlive && it.victim.level() === level }.map { weave ->
            val caster = player(weave.casterId)?.takeIf { it.level() === level }
            ThreadSightPayload.Bond(weave.victim.id, caster?.id ?: ThreadSightPayload.NO_HOLDER, weave.grip)
        }
        return held + woven
    }

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
        mob.setAttached(PUPPET_OF, owner.uuid)
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
            // Slot for slot, empties included: the first nine are the hotbar the driver sees.
            inventory = victim.inventory.nonEquipmentItems.map { it.copy() },
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

    /**
     * A puppet back from disk — a loaded chunk or a restart. Goals are not saved, so a mob gets
     * its will back on load and loses it again here; the selection and orders are left alone.
     */
    private fun adopt(puppet: LivingEntity) {
        val ownerId = (puppet as? MarionetteEntity)?.takeIf { it.capture != null }?.ownerId
            ?: puppet.getAttached(PUPPET_OF)
            ?: return
        (puppet as? Mob)?.let {
            it.targetSelector.removeAllGoals { true }
            it.target = null
            // Saved mid-possession by a crash: a possession never survives a restart, its NoAI must not either.
            it.isNoAi = false
        }
        val puppets = byOwner.computeIfAbsent(ownerId) { mutableListOf() }
        if (puppets.none { it === puppet }) puppets += puppet
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
     * The Marionettist steps into a puppet: **they** go where it stands and walk as it, and a
     * vessel in their own face stays where they were.
     *
     * ## Why the player moves, and not the camera
     *
     * The first two versions left the player home and put the camera in the puppet
     * (`setCamera`). Vanilla fought that on every front, because half the client assumes the
     * camera **is** a player: the hand, the run's FOV and bob, the hotbar, the hearts and the
     * block outline all switch off for any other camera, the server snaps the player to the
     * camera every tick anyway, and a sneak hands the camera back without asking. Each of those
     * was a patch of its own. With the player inside, every one of them is simply vanilla.
     *
     * So, for as long as the possession lasts:
     * - the player stands in the puppet's place, hidden from everyone else; the puppet follows
     *   the player every tick ([steerPossessed]) and is what others see and strike — a blow on
     *   it is passed to the player ([ALLOW_DAMAGE] below);
     * - the puppet's things and health **are** the player's: its inventory is loaded in and its
     *   hit points become theirs (a transient max-health modifier). A player's "death" here is
     *   the puppet breaking ([broken]), never the player dying;
     * - the player's own things, health and place wait in a persistent attachment ([BodyStash]),
     *   so a crash mid-possession gives them back on the next join ([restoreStashed]);
     * - the body left behind is a [MarionetteEntity] vessel with the player's own health and
     *   armour: it is seen, pushed and struck like anyone, and if it falls, the player dies.
     *
     * The client adds one thing only: the local player is drawn in the puppet's skin, first
     * person arm included (`AbstractClientPlayerSkinMixin`).
     */
    fun possess(owner: ServerPlayer, puppet: LivingEntity) {
        val level = owner.level()
        val inventory = owner.inventory

        owner.setAttached(
            BodyStash.TYPE,
            BodyStash(
                items = (0 until inventory.containerSize).map { inventory.getItem(it).copy() },
                health = owner.health,
                x = owner.x, y = owner.y, z = owner.z,
                yaw = owner.yRot, pitch = owner.xRot,
                dimension = level.dimension().identifier(),
            ),
        )

        val vessel = MarionetteEntity(ModEntities.MARIONETTE, level)
        vessel.snapTo(owner.x, owner.y, owner.z, owner.yRot, owner.xRot)
        vessel.setProfile(ResolvableProfile.createResolved(owner.gameProfile))
        vessel.inheritHealth(owner.maxHealth.toDouble(), owner.health.toDouble())
        vessel.customName = owner.name
        vessel.isCustomNameVisible = true
        vessel.vesselOf = owner.uuid
        EquipmentSlot.entries.forEach { slot -> vessel.setItemSlot(slot, owner.getItemBySlot(slot).copy()) }
        level.addFreshEntity(vessel)

        // Into the puppet's things. The main hand of a player-made puppet is a copy of one of
        // its hotbar slots, so only the armour and the off hand come from its equipment.
        (0 until inventory.containerSize).forEach { inventory.setItem(it, ItemStack.EMPTY) }
        (puppet as? MarionetteEntity)?.capture?.inventory?.forEachIndexed { slot, stack ->
            if (slot < MAIN_SLOTS) inventory.setItem(slot, stack.copy())
        }
        WORN.forEach { slot -> owner.setItemSlot(slot, puppet.getItemBySlot(slot).copy()) }
        if (puppet !is MarionetteEntity) owner.setItemSlot(EquipmentSlot.MAINHAND, puppet.mainHandItem.copy())

        owner.grantAttribute(Attributes.MAX_HEALTH, BORROWED_HEALTH, (puppet.maxHealth - owner.maxHealth).toDouble())
        owner.health = puppet.health

        (puppet as? MarionetteEntity)?.driverId = owner.uuid
        (puppet as? Mob)?.isNoAi = true
        owner.teleportTo(level, puppet.x, puppet.y, puppet.z, emptySet(), puppet.yRot, puppet.xRot, true)
        level.players().filter { it !== owner }.forEach { SelectiveVisibility.hide(owner, it) }

        possessions[owner.uuid] = Possession(puppet, vessel, lend(owner, puppet))
        swapEffects(level, vessel.position(), puppet.position())
        BeyonderStatus.pushRoster(owner)
        pushState(owner)
    }

    /** Owners whose puppet broke under them this tick, ejected on the next [steerPossessed]. */
    private val broken = ConcurrentHashMap.newKeySet<UUID>()

    /** Set while a blow on a possessed puppet is being passed to its driver. */
    private var forwarding = false

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
            AbilityServices.passives.activate(owner)
            AbilityServices.messaging.tell(
                owner,
                Component.literal("Крізь це тіло вам відкрились сили ${capture.name}: ${lent.size}"),
            )
        }
        return lent
    }

    /**
     * Back into one's own body: the puppet stays where it stands, the threads stay tied.
     *
     * The single way out, whatever caused it — a recast, a broken puppet, a fallen body, leaving
     * the game, the server stopping — so the puppet's things, the lent powers and the player's
     * own inventory are always put right here and nowhere else.
     */
    fun eject(owner: ServerPlayer, reason: Component? = null) {
        val possession = possessions.remove(owner.uuid) ?: return
        val puppet = possession.puppet
        val vessel = possession.vessel

        handBack(owner, puppet)
        puppet.health = owner.health.coerceAtMost(puppet.maxHealth)
        (puppet as? MarionetteEntity)?.driverId = null
        (puppet as? Mob)?.isNoAi = false
        owner.revokeAttribute(Attributes.MAX_HEALTH, BORROWED_HEALTH)

        // The borrowed powers go back with the body. Only what this possession lent is taken —
        // a power the driver owns in their own right was never on the list — and anything still
        // running from one of them is stopped, or it would outlive the body it came from.
        val beyonder = AbilityServices.beyonders.of(owner.uuid)
        possession.borrowed.forEach {
            AbilityServices.scheduling.stop(owner.uuid, it)
            beyonder?.remove(it)
        }
        if (possession.borrowed.isNotEmpty()) AbilityServices.passives.refresh(owner)

        val stash = owner.getAttached(BodyStash.TYPE)
        if (stash != null) {
            restore(owner, stash, vessel.health, vessel.x, vessel.y, vessel.z, vessel.yRot, vessel.xRot)
        }
        vessel.discard()

        owner.level().players().filter { it !== owner }.forEach { SelectiveVisibility.show(owner, it) }
        if (puppet !is MarionetteEntity && puppet.isAlive) SelectiveVisibility.show(puppet, owner)
        swapEffects(owner.level(), owner.position(), puppet.position())
        reason?.let { AbilityServices.messaging.actionBar(owner, it) }
        BeyonderStatus.pushRoster(owner)
        pushState(owner)
    }

    /** What the player carried as the puppet goes back into the puppet. */
    private fun handBack(owner: ServerPlayer, puppet: LivingEntity) {
        val inventory = owner.inventory
        (WORN + EquipmentSlot.MAINHAND).forEach { slot -> puppet.setItemSlot(slot, owner.getItemBySlot(slot).copy()) }
        val capture = (puppet as? MarionetteEntity)?.capture
        if (capture != null) {
            capture.inventory = (0 until MAIN_SLOTS).map { inventory.getItem(it).copy() }
            return
        }
        // A beast has hands and armour and nothing else: whatever else was picked up falls.
        val level = puppet.level() as? ServerLevel ?: return
        (0 until MAIN_SLOTS).filter { it != inventory.selectedSlot }.map(inventory::getItem).filterNot { it.isEmpty }
            .forEach { stack ->
                net.minecraft.world.entity.item.ItemEntity(level, puppet.x, puppet.y + 0.5, puppet.z, stack.copy())
                    .also(level::addFreshEntity)
            }
    }

    /** The player's own things, health and place, out of the stash — which is then cleared. */
    private fun restore(
        owner: ServerPlayer,
        stash: BodyStash,
        health: Float,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float,
    ) {
        val inventory = owner.inventory
        (0 until inventory.containerSize).forEach { slot ->
            inventory.setItem(slot, stash.items.getOrNull(slot)?.copy() ?: ItemStack.EMPTY)
        }
        val server = owner.level().server
        val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, stash.dimension)) ?: owner.level()
        owner.teleportTo(level, x, y, z, emptySet(), yaw, pitch, true)
        owner.health = health.coerceIn(1f, owner.maxHealth)
        owner.removeAttached(BodyStash.TYPE)
    }

    /**
     * A join with a stash still attached: the server went down mid-possession. The vessel did not
     * survive it (it is never saved) and the puppet came back driverless, so the player simply gets
     * their own back.
     */
    private fun restoreStashed(owner: ServerPlayer) {
        val stash = owner.getAttached(BodyStash.TYPE) ?: return
        owner.revokeAttribute(Attributes.MAX_HEALTH, BORROWED_HEALTH)
        restore(owner, stash, stash.health, stash.x, stash.y, stash.z, stash.yaw, stash.pitch)
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
                    strandSeconds = puppet.getAttached(STRANDED_UNTIL)?.let { ((it - now) / 1000L).coerceAtLeast(0L).toInt() } ?: 0,
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
        BodyStash.register()
        PUPPET_OF
        STRANDED_UNTIL

        // The registry is rebuilt from the world: whatever loads with a mark rejoins its owner's
        // row, whatever unloads leaves it until it loads again.
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ -> (entity as? LivingEntity)?.let(::adopt) }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            byOwner.values.forEach { list -> list.removeIf { it === entity } }
        }
        ModChannels.MARIONETTE_ORDER.receiveOnServer { payload, context -> handle(context.player(), payload) }

        // The ability book heads the list with the pathway the lent powers come from.
        BeyonderStatus.wornBody = { ownerId ->
            (possessions[ownerId]?.puppet as? MarionetteEntity)?.capture?.let { capture ->
                val pathway = capture.pathway ?: return@let null
                val sequence = capture.sequence ?: return@let null
                AbilityRosterPayload.Worn(pathway, Sequence(sequence))
            }
        }

        // Inside a puppet one speaks with its mouth. Chat resolves the speaker's name from the
        // real profile on the server, so the line is refused and said again under the puppet's
        // name — `Disguise`'s own move. Registered before `Disguise.install`, and Fabric's ALLOW
        // events stop at the first refusal, so a possession outranks a mask worn underneath it.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, sender, _ ->
            val puppet = possessions[sender.uuid]?.puppet ?: return@register true
            sender.level().server.playerList.broadcastSystemMessage(
                Component.translatable("chat.type.text", Component.literal(nameOf(puppet)), message.decoratedContent()),
                false,
            )
            false
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            steerPossessed()
            if (server.tickCount % TICKS_PER_SECOND == 0) sweep()
        }

        // A puppet falling is the only way a Characteristic ever comes back into the world.
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> onDeath(entity) }

        // Ready Fabric hooks for everything a possession changes about damage; no mixin.
        //
        // A blow on a possessed puppet is a blow on its driver, who stands inside it: the puppet
        // itself only follows. Passed on with the attacker's own source, so kills are credited.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            if (forwarding) return@register true
            val driver = possessions.entries.firstOrNull { it.value.puppet === entity }?.key?.let(::player)
                ?: return@register true
            forwarding = true
            try {
                driver.hurtServer(driver.level(), source, amount)
            } finally {
                forwarding = false
            }
            false
        }

        // The driver's hit points are the puppet's while inside it, so running out of them breaks
        // the puppet, not the player: they are thrown back into their own body instead of dying.
        ServerLivingEntityEvents.ALLOW_DEATH.register { entity, _, _ ->
            if (entity !is ServerPlayer || !isPossessing(entity.uuid)) return@register true
            entity.health = 1f
            broken += entity.uuid
            false
        }

        // The driver cannot see their own body being struck — so they hear it.
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, _, taken, _ ->
            val owner = (entity as? MarionetteEntity)?.vesselOf?.let(::player) ?: return@register
            if (taken <= 0f) return@register
            owner.connection.send(
                ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.HEARTBEAT),
                    SoundSource.MASTER,
                    owner.x, owner.y, owner.z,
                    1.4f, 0.7f,
                    owner.level().random.nextLong(),
                ),
            )
            AbilityServices.messaging.actionBar(owner, Component.literal("❤ Ваше тіло б'ють!"))
        }

        // A crash mid-possession: the stash is still on the player, and it goes back first thing.
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> restoreStashed(handler.player) }

        // The Marionettist left the game: nobody is holding the threads, so every puppet starts
        // the same ten-minute clock a puppet out of range does. The rule is one. The possession
        // ends first, so the player is saved with their own things on.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val id = handler.player.uuid
            eject(handler.player)
            of(id).forEach { strand(it) }
        }

        // Before the player list is saved, not after.
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            possessions.keys.toList().forEach { id -> server.playerList.getPlayer(id)?.let { eject(it) } }
        }
    }

    /**
     * Every tick: the puppet follows its driver, wears what they hold and moves as they move.
     *
     * The driver is the one walking (see [possess]); the puppet is what everyone else sees, so it
     * copies position, look, pose, sprint, swing and equipment. The player is hidden from everyone
     * else each tick, which also catches anyone who walked into range since. The driver's own
     * client keeps the puppet (it needs its face for the skin) and simply neither draws nor aims at
     * it — see [MarionetteEntity.hiddenForDriver].
     */
    private fun steerPossessed() {
        possessions.forEach { (ownerId, possession) ->
            val owner = player(ownerId) ?: return@forEach
            val puppet = possession.puppet
            if (broken.remove(ownerId)) {
                eject(owner, Component.literal("Маріонетку розбито — вас викинуло у власне тіло"))
                release(puppet)
                return@forEach
            }
            if (!puppet.isAlive) {
                eject(owner, Component.literal("Маріонетку розбито — вас викинуло у власне тіло"))
                return@forEach
            }
            if (owner.level() !== puppet.level()) {
                eject(owner, Component.literal("Нитка не тягнеться в інший світ"))
                return@forEach
            }

            puppet.snapTo(owner.x, owner.y, owner.z, owner.yRot, owner.xRot)
            puppet.yHeadRot = owner.yHeadRot
            puppet.yBodyRot = owner.yBodyRot
            puppet.pose = owner.pose
            puppet.isShiftKeyDown = owner.isShiftKeyDown
            puppet.isSprinting = owner.isSprinting
            if (owner.swinging && !puppet.swinging) puppet.swing(owner.swingingArm ?: InteractionHand.MAIN_HAND)
            EquipmentSlot.entries.forEach { slot ->
                val worn = owner.getItemBySlot(slot)
                if (!ItemStack.matches(puppet.getItemBySlot(slot), worn)) puppet.setItemSlot(slot, worn.copy())
            }
            puppet.health = owner.health.coerceIn(0.5f, puppet.maxHealth)

            owner.level().players().forEach { if (it !== owner) SelectiveVisibility.hide(owner, it) }
            // A beast has no face to lend and no flag of ours to hide behind: just take it off
            // the driver's client for the duration.
            if (puppet !is MarionetteEntity) SelectiveVisibility.hide(puppet, owner)
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
            puppets.filter { (it.getAttached(STRANDED_UNTIL) ?: Long.MAX_VALUE) <= deadline }
                .forEach { it.kill(it.level() as ServerLevel) }

            // The row of threads is redrawn on the same beat the numbers on it change: health,
            // distance and the strand countdown all move on their own, so there is nothing here
            // for a dirty flag to save.
            owner?.let(::pushState)
        }
        byOwner.values.removeIf { it.isEmpty() }
    }

    private fun strand(puppet: LivingEntity) {
        if (puppet.hasAttached(STRANDED_UNTIL)) return
        puppet.setAttached(STRANDED_UNTIL, System.currentTimeMillis() + STRAND_MILLIS)
        ownerOf(puppet)?.let { ownerId ->
            AbilityServices.messaging.tell(
                ownerId,
                Component.literal("Нитка натягнулась до краю: ${nameOf(puppet)} поза зоною контролю. Десять хвилин — і вона обірветься."),
            )
        }
    }

    private fun recover(puppet: LivingEntity, owner: ServerPlayer?) {
        if (puppet.removeAttached(STRANDED_UNTIL) == null) return
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
        // The body fell while its owner was away in a puppet. Nothing saves them — that is the
        // whole price of possession — so they are put back into it and die there, where their
        // own things drop.
        val bodyOf = (puppet as? MarionetteEntity)?.vesselOf?.let(::player)
        if (bodyOf != null && possessions[bodyOf.uuid]?.vessel === puppet) {
            eject(bodyOf, Component.literal("Ваше тіло загинуло, поки вас у ньому не було"))
            bodyOf.kill(bodyOf.level())
            return
        }
        // Anything that killed the player past `ALLOW_DEATH`: the possession ends all the same.
        if (puppet is ServerPlayer && isPossessing(puppet.uuid)) eject(puppet)

        val ownerId = ownerOf(puppet) ?: return
        val level = puppet.level() as? ServerLevel ?: return
        val where = puppet.position()

        byOwner[ownerId]?.removeIf { it === puppet }
        mobOrders.remove(puppet.uuid)
        if (possessions[ownerId]?.puppet === puppet) {
            player(ownerId)?.let { eject(it, Component.literal("Маріонетку розбито — вас викинуло у власне тіло")) }
        }

        val capture = (puppet as? MarionetteEntity)?.capture
        AbilityServices.messaging.tell(
            ownerId,
            Component.literal("Маріонетку ${capture?.name ?: nameOf(puppet)} знищено"),
        )
        // Its pockets and what it wore — the main hand is a copy of a pocket, so not that.
        val worn = if (capture != null) WORN.map(puppet::getItemBySlot) else emptyList()
        (capture?.inventory.orEmpty() + worn).filterNot { it.isEmpty }.forEach { stack ->
            net.minecraft.world.entity.item.ItemEntity(level, where.x, where.y + 0.5, where.z, stack.copy()).also(level::addFreshEntity)
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

    /** Server shutdown or a fresh world. The puppets themselves are on disk and rejoin on load. */
    fun reset() {
        byOwner.clear()
        chosen.clear()
        mobOrders.clear()
        possessions.clear()
        weaves.clear()
    }

    private fun player(id: UUID): ServerPlayer? = AbilityServices.playerOf(id)

    /** Ten minutes, on the wall clock. See the class comment. */
    private const val STRAND_MILLIS = 10L * 60L * 1000L

    private const val CONDENSE_TICKS = 30 * TICKS_PER_SECOND
    private const val CONDENSE_STEP_TICKS = 5
    private const val CONDENSE_PARTICLES = 12
    private const val CONDENSE_HERALD_RANGE = 32.0 * 32.0

    private const val MOB_SPEED = 1.1

    /** The player inventory's main part: hotbar plus the three rows above it. */
    private const val MAIN_SLOTS = 36

    /** What a body wears, as opposed to what it carries. */
    private val WORN = listOf(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND,
    )

    /** The driver's max health, stretched or shrunk to the puppet's while inside it. */
    private val BORROWED_HEALTH = modifierId("fool_marionette_possession", "max_health")

    /** How far an order can be pointed. Further than a hand reaches, and rightly: it is a gesture. */
    private const val ORDER_REACH = 64.0
}
