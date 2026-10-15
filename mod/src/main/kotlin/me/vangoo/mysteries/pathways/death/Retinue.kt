package me.vangoo.mysteries.pathways.death

import me.vangoo.domain.valueobjects.GatekeeperLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritGuideLore
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.ProblemReporter
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The Spirit Usher's retinue: every servant one owner leads, however it came — a raised corpse, a
 * subjugated undead, a spirit that agreed to a pact. One registry, one ceiling, one order.
 *
 * ## The world keeps the retinue, not a file
 *
 * The plugin wrote `retinue.json` on every logout — a description per servant (type, name, place)
 * — and on join respawned new mobs from those descriptions, because a Bukkit entity does not
 * survive a restart in plugin memory. But vanilla **already saves mobs**: a persistent mob is
 * written with its chunk, equipment, health and name included. So a servant here is simply a mob
 * that carries [SERVANT_OF], a persistent attachment naming its owner. It stays where it was left
 * while the owner is away, and the moment its chunk loads again it rejoins the registry by itself
 * ([install]). Nothing is described, nothing is respawned, nothing is lost on a crash.
 *
 * ## No will, by event rather than by tick
 *
 * "No will or vitality": a servant never picks an enemy. The plugin reset every servant's target
 * every half second. Here a servant's target selector is simply cleared, and the one acquisition
 * left — whatever vanilla still tries — is refused at the moment it happens through the mod's own
 * [MobTargeting] event, unless it is exactly the creature the owner ordered it to kill.
 *
 * ## Hiding is a real absence
 *
 * "Into the shadows, without being discovered": hidden servants are not invisible, they are not
 * in the world at all. Each one is saved exactly as vanilla saves it — the same tag the chunk would
 * get — into a persistent attachment on the owner, and loaded back next to them. The plugin
 * rebuilt hidden servants from its own description, losing whatever it did not describe.
 *
 * ## The sun is paid for
 *
 * The dead burn at noon like any undead, and the Usher's will is what keeps the fire off them: one
 * spirituality a second for every servant the sun would set alight ([payForShade]). An Usher who
 * runs dry watches the retinue burn. The plugin simply switched burning off.
 */
object Retinue {

    sealed interface Order {
        data object Follow : Order
        data class Attack(val victimId: UUID) : Order
        data class Guard(val post: Vec3) : Order
    }

    val SERVANT_OF: AttachmentType<UUID> by lazy {
        AttachmentRegistry.create(modId("servant_of")) { it.persistent(UUIDUtil.CODEC) }
    }

    private val HIDDEN: AttachmentType<List<CompoundTag>> by lazy {
        AttachmentRegistry.create(modId("hidden_retinue")) { it.persistent(CompoundTag.CODEC.listOf()) }
    }

    private val byOwner = HashMap<UUID, MutableList<Mob>>()
    private val orders = HashMap<UUID, Order>()

    /** When an owner's dead may drag them into the shadows again — the mod's tick. */
    private val escapeReadyAt = HashMap<UUID, Long>()

    fun ownerOf(entity: Entity): UUID? = entity.getAttached(SERVANT_OF)

    fun isServant(entity: Entity): Boolean = entity.hasAttached(SERVANT_OF)

    fun isOwnedBy(owner: UUID, entity: Entity): Boolean = ownerOf(entity) == owner

    /** The servants standing in the world right now, nearest first is not promised. */
    fun of(owner: UUID): List<Mob> = byOwner[owner]?.filter { it.isAlive && !it.isRemoved }.orEmpty()

    /** Hidden servants count: going into the shadows frees no place under the ceiling. */
    fun size(owner: ServerPlayer): Int = of(owner.uuid).size + (owner.getAttached(HIDDEN)?.size ?: 0)

    fun isHidden(owner: ServerPlayer): Boolean = !owner.getAttached(HIDDEN).isNullOrEmpty()

    /**
     * The ceiling reads the owner's **current** Sequence: `Resurrection` stays on the roster after
     * the Usher becomes a Gatekeeper, and a Gatekeeper's ceiling is higher.
     */
    fun cap(sequence: Sequence): Int = GatekeeperLore.retinueCap(sequence)

    fun room(owner: ServerPlayer, sequence: Sequence): Int = (cap(sequence) - size(owner)).coerceAtLeast(0)

    fun orderOf(owner: UUID): Order = orders[owner] ?: Order.Follow

    // ── Joining ─────────────────────────────────────────────────────────────────

    fun enroll(owner: ServerPlayer, mob: Mob) {
        mob.targetSelector.removeAllGoals { true }
        mob.target = null
        mob.setPersistenceRequired()
        mob.setCanPickUpLoot(false)
        mob.setAttached(SERVANT_OF, owner.uuid)
        register(owner.uuid, mob)

        val level = mob.level() as ServerLevel
        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.x, mob.eyeY, mob.z, 12, 0.3, 0.4, 0.3, 0.02)
        // A retinue in the shadows takes a newcomer straight into them.
        if (isHidden(owner)) stash(owner, listOf(mob))
    }

    private fun register(owner: UUID, mob: Mob) {
        val list = byOwner.getOrPut(owner) { mutableListOf() }
        if (list.none { it === mob }) list += mob
    }

    // ── Leaving ─────────────────────────────────────────────────────────────────

    /** The whole retinue turns to dust, hidden ones included. */
    fun release(owner: ServerPlayer): Boolean {
        val live = of(owner.uuid)
        val hidden = owner.removeAttached(HIDDEN).orEmpty()
        live.forEach(::crumble)
        byOwner.remove(owner.uuid)
        orders.remove(owner.uuid)
        return live.isNotEmpty() || hidden.isNotEmpty()
    }

    /** One servant goes in the owner's place — the price of the Spirit Swap and of an escape. */
    fun sacrificeOne(owner: UUID): Boolean {
        val servant = of(owner).firstOrNull() ?: return false
        crumble(servant)
        return true
    }

    /** A specific servant leaves the world — into the Gatekeeper's inner underworld. */
    fun absorb(owner: UUID, mob: Mob): Boolean {
        if (!isOwnedBy(owner, mob)) return false
        byOwner[owner]?.removeIf { it === mob }
        mob.discard()
        return true
    }

    private fun crumble(mob: Mob) {
        val level = mob.level() as ServerLevel
        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.x, mob.y + 0.5, mob.z, 16, 0.3, 0.5, 0.3, 0.02)
        level.playSound(null, mob.x, mob.y, mob.z, SoundEvents.SOUL_SAND_BREAK, SoundSource.HOSTILE, 0.8f, 0.6f)
        byOwner[ownerOf(mob)]?.removeIf { it === mob }
        mob.discard()
    }

    // ── Orders ──────────────────────────────────────────────────────────────────

    fun order(owner: UUID, order: Order) {
        orders[owner] = order
        if (order !is Order.Attack) of(owner).forEach { it.target = null; it.navigation.stop() }
    }

    /** Into the shadows, or back out of them. Returns `true` when the retinue is hidden now. */
    fun toggleHidden(owner: ServerPlayer): Boolean {
        val stashed = owner.getAttached(HIDDEN)
        if (stashed.isNullOrEmpty()) {
            stash(owner, of(owner.uuid))
            return isHidden(owner)
        }
        owner.removeAttached(HIDDEN)
        val level = owner.level()
        stashed.forEachIndexed { index, tag ->
            val angle = index * GOLDEN_ANGLE
            val at = owner.position().add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0)
            val input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag)
            val entity = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD) {
                it.snapTo(at.x, at.y, at.z, owner.yRot, 0f)
                it
            } ?: return@forEachIndexed
            level.addFreshEntity(entity)
            (entity as? Mob)?.let { register(owner.uuid, it) }
            level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.5, at.z, 10, 0.3, 0.6, 0.3, 0.02)
        }
        return false
    }

    private fun stash(owner: ServerPlayer, servants: List<Mob>) {
        if (servants.isEmpty()) return
        val tags = owner.getAttached(HIDDEN).orEmpty().toMutableList()
        for (mob in servants) {
            val out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mob.registryAccess())
            if (!mob.save(out)) continue
            tags += out.buildResult()
            val level = mob.level() as ServerLevel
            level.sendParticles(ParticleTypes.SCULK_SOUL, mob.x, mob.y + 0.3, mob.z, 10, 0.3, 0.2, 0.3, 0.01)
            byOwner[owner.uuid]?.removeIf { it === mob }
            mob.discard()
        }
        owner.setAttached(HIDDEN, tags)
    }

    /**
     * The one acquisition a servant is allowed: the ordered victim. Everything else — vanilla's own
     * goals, retaliation, a passing zombie's instinct — is refused at the moment it happens.
     */
    fun onTargeting(event: MobTargeting) {
        val owner = ownerOf(event.mob) ?: return
        val order = orders[owner]
        if (order !is Order.Attack || order.victimId != event.target.uuid) event.deny()
    }

    // ── The clock ───────────────────────────────────────────────────────────────

    fun install() {
        // Attachment types register lazily — at mod init, here, never in a unit test that has no Fabric mixins.
        SERVANT_OF
        HIDDEN
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            val owner = ownerOf(entity) ?: return@register
            val mob = entity as? Mob ?: return@register
            // Goals are not saved — a servant loaded from disk has its will back until this.
            mob.targetSelector.removeAllGoals { true }
            register(owner, mob)
        }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            val owner = ownerOf(entity) ?: return@register
            byOwner[owner]?.removeIf { it === entity }
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % SECOND_TICKS == 0) payForShade()
            keepShaded()
            if (server.tickCount % STEP_TICKS == 0) step(server.tickCount % SECOND_TICKS == 0)
        }
        // "They can drag the Spirit Guide into the shadows, even if their master is knocked out":
        // a reflex, not a button — so it hangs on the blow itself, not on a poll of the health bar.
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, _, taken, _ ->
            if (entity is ServerPlayer && taken > 0f) maybeEscape(entity)
        }
    }

    fun reset() {
        shaded.clear()
        byOwner.clear()
        orders.clear()
        escapeReadyAt.clear()
    }

    // ── The sun ─────────────────────────────────────────────────────────────────

    /**
     * Servants whose shade is paid for this second. The dead still burn in daylight like any
     * undead — the Usher's will is what keeps the fire off them, and will costs spirituality:
     * one a second for every servant standing where the sun would set it alight.
     */
    private val shaded = HashSet<Mob>()

    private fun payForShade() {
        shaded.clear()
        for ((ownerId, servants) in byOwner) {
            val owner = AbilityServices.playerOf(ownerId) ?: continue
            val exposed = servants.filter(::burnsInSun)
            if (exposed.isEmpty()) continue
            val beyonder = AbilityServices.beyonders.of(ownerId) ?: continue
            if (beyonder.payUpkeep(exposed.size)) {
                shaded += exposed
                AbilityServices.statePump.markDirty(ownerId)
            } else {
                AbilityServices.messaging.actionBar(owner, Component.literal("☠ Духовності не вистачає — слуги горять на сонці"))
            }
        }
    }

    /**
     * Every tick, after the servants have ticked: vanilla has just set them alight, and the flame is
     * put out before the next update reaches any client — so a shaded servant never even flickers.
     */
    private fun keepShaded() {
        shaded.removeIf { !it.isAlive || it.isRemoved }
        shaded.forEach { if (it.isOnFire) it.clearFire() }
    }

    /**
     * Vanilla's own sun rule (`Mob.isSunBurnTick`) read without its dice: the sky burns here, the
     * spot is bright, open to the sky, dry — and the creature is one the sun burns at all.
     */
    private fun burnsInSun(mob: Mob): Boolean {
        if (!mob.isUndead() || mob.fireImmune() || mob.type in SUN_PROOF) return false
        if (!mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty) return false
        val level = mob.level()
        val burns = level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, mob.position())
        return burns && mob.lightLevelDependentMagicValue > 0.5f && !mob.isInWaterOrRain &&
            level.canSeeSky(BlockPos.containing(mob.x, mob.eyeY, mob.z))
    }

    private val SUN_PROOF = setOf(EntityTypes.HUSK, EntityTypes.PARCHED, EntityTypes.CAMEL_HUSK)

    private fun step(everySecond: Boolean) {
        byOwner.entries.removeIf { (_, list) ->
            list.removeIf { !it.isAlive || it.isRemoved }
            list.isEmpty()
        }
        for ((ownerId, servants) in byOwner) {
            // A spirit under a pact is still a vex: its limited life is renewed for as long as it serves.
            servants.forEach { (it as? Vex)?.let(Spirits::hold) }
            val owner = AbilityServices.playerOf(ownerId) ?: continue
            if (everySecond) rebel(owner, servants)
            val order = orderOf(ownerId)
            for (servant in servants.toList()) lead(owner, servant, order)
        }
    }

    private fun lead(owner: ServerPlayer, servant: Mob, order: Order) {
        when (order) {
            is Order.Follow -> walk(servant, owner.level(), owner.position(), FOLLOW_GAP)
            is Order.Guard -> walk(servant, servant.level(), order.post, GUARD_GAP)
            is Order.Attack -> {
                val victim = (servant.level() as ServerLevel).getEntity(order.victimId) as? net.minecraft.world.entity.LivingEntity
                if (victim == null || !victim.isAlive || victim.level() !== servant.level()) {
                    // The victim is gone: the whole retinue returns to heel.
                    order(owner.uuid, Order.Follow)
                } else if (servant.target !== victim) {
                    servant.target = victim
                }
            }
        }
    }

    private fun walk(servant: Mob, level: net.minecraft.world.level.Level, anchor: Vec3, gap: Double) {
        if (servant.level() !== level) return
        val distance = servant.position().distanceToSqr(anchor)
        when {
            distance > LEASH * LEASH -> servant.teleportTo(anchor.x, anchor.y, anchor.z)
            distance > gap * gap -> servant.navigation.moveTo(anchor.x, anchor.y, anchor.z, WALK_SPEED)
        }
    }

    /**
     * "When facing Higher Sequence Beyonders of the Death Pathway, their slaves are very prone to
     * rebelling, and so are they themselves": near a stronger Death Beyonder the owner's sanity
     * slips every second and each servant may break loose — and turns on its former master.
     */
    private fun rebel(owner: ServerPlayer, servants: List<Mob>) {
        val beyonders = AbilityServices.beyonders
        val own = beyonders.of(owner.uuid)?.sequence ?: return
        val rival = owner.level().players()
            .asSequence()
            .filter { it !== owner && it.distanceToSqr(owner) <= SpiritGuideLore.REBELLION_RADIUS * SpiritGuideLore.REBELLION_RADIUS }
            .mapNotNull { beyonders.of(it.uuid) }
            .filter { it.pathway.name == DEATH && it.sequence.level < own.level }
            .minByOrNull { it.sequence.level }
            ?.sequence ?: return

        beyonders.of(owner.uuid)?.increaseSanityLoss(1)
        val chance = SpiritGuideLore.rebellionChancePerSecond(own, rival)
        for (servant in servants.toList()) {
            if (owner.random.nextDouble() >= chance) continue
            servant.removeAttached(SERVANT_OF)
            byOwner[owner.uuid]?.removeIf { it === servant }
            servant.target = owner
            (servant.level() as ServerLevel).sendParticles(
                ParticleTypes.ANGRY_VILLAGER, servant.x, servant.eyeY, servant.z, 6, 0.3, 0.3, 0.3, 0.0,
            )
            AbilityServices.messaging.tell(owner, Component.literal("☠ Слуга вийшов з-під вашої волі"))
        }
    }

    private fun maybeEscape(owner: ServerPlayer) {
        if (owner.health > owner.maxHealth * ESCAPE_HEALTH) return
        val now = AbilityServices.scheduler.tick
        if (now < (escapeReadyAt[owner.uuid] ?: 0L)) return
        if (!sacrificeOne(owner.uuid)) return
        escapeReadyAt[owner.uuid] = now + ESCAPE_COOLDOWN_TICKS

        val level = owner.level()
        val from = owner.position()
        val shadow = darkSpot(level, owner.blockPosition(), owner) ?: owner.blockPosition()
        level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y + 1.0, from.z, 30, 0.4, 0.8, 0.4, 0.05)
        owner.teleportTo(shadow.x + 0.5, shadow.y.toDouble(), shadow.z + 0.5)
        owner.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, ESCAPE_INVISIBILITY_TICKS, 0, false, false, true))
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f)
        AbilityServices.messaging.tell(owner, Component.literal("☠ Нежить утягла вас у тінь"))
    }

    private fun darkSpot(level: ServerLevel, from: BlockPos, owner: ServerPlayer): BlockPos? {
        repeat(ESCAPE_TRIES) {
            val pos = from.offset(
                owner.random.nextInt(ESCAPE_REACH * 2 + 1) - ESCAPE_REACH,
                owner.random.nextInt(5) - 2,
                owner.random.nextInt(ESCAPE_REACH * 2 + 1) - ESCAPE_REACH,
            )
            val free = !level.getBlockState(pos).blocksMotion() && !level.getBlockState(pos.above()).blocksMotion()
            if (free && level.getBlockState(pos.below()).blocksMotion() && level.getMaxLocalRawBrightness(pos) < DARK) {
                return pos
            }
        }
        return null
    }

    const val DEATH: String = "Death"

    private const val STEP_TICKS = 10
    private const val SECOND_TICKS = 20
    private const val FOLLOW_GAP = 4.0
    private const val GUARD_GAP = 2.0
    private const val LEASH = 24.0
    private const val WALK_SPEED = 1.25
    private const val GOLDEN_ANGLE = 2.39996

    private const val ESCAPE_HEALTH = 0.25f
    private const val ESCAPE_COOLDOWN_TICKS = 120 * 20L
    private const val ESCAPE_INVISIBILITY_TICKS = 100
    private const val ESCAPE_TRIES = 24
    private const val ESCAPE_REACH = 8
    private const val DARK = 7
}
