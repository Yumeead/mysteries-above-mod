package me.vangoo.mysteries.entity

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * A puppet on someone's threads: a living body that used to be a person.
 *
 * ## Why an `Avatar` and not a `Mob`
 *
 * 26.2 split `Avatar` out of `Player` — a `LivingEntity` that carries a `ResolvableProfile` and
 * that `AvatarRenderer` draws with the player model, generically (`<E extends Avatar &
 * ClientAvatarEntity>`). So a player-shaped entity is a **supported shape** here rather than a
 * self-built one: the marionette inherits the skin, the name, the arms and every player animation
 * from vanilla, and the mod ships neither a model nor a texture for it.
 *
 * The plugin had none of this. Citizens was its only controllable body, which is why converting a
 * target there meant **killing it** with 10 000 damage and respawning an NPC in its place — a
 * price paid to Spigot, not a design. Here a mob target is never killed at all (it stays the mob
 * it was, minus its own will); only a player target, whose body cannot be borrowed, leaves this
 * puppet behind.
 *
 * ## No navigation, and that is a real ceiling
 *
 * `PathNavigation` binds to `Mob`, and an `Avatar` is not one — so orders here steer by hand:
 * face the goal, walk forward, hop when something is in the way. Over flat ground and stairs it
 * reads as walking; against a cliff or a maze it reads as what it is. Mob marionettes have real
 * pathfinding because they are still real mobs, so the ceiling is only ever hit by puppets made
 * from players.
 *
 * ponytail: hand-steering, swap for a real `PathFinder` if puppets need to cross terrain
 * unattended.
 *
 * ## Nothing here is saved
 *
 * The type is `noSave()`: a marionette lives as long as the server does, exactly like the
 * Beyonder status it was cut from. The plugin needed a `MarionetteRestorer` because Citizens
 * persisted its NPCs on its own and orphans would have survived a restart.
 */
class MarionetteEntity(type: EntityType<out MarionetteEntity>, level: Level) : Avatar(type, level) {

    /** What the puppet is doing while nobody is inside it. */
    sealed interface Order {

        /** Waiting where it was left. */
        data object Stand : Order

        /** Walking to a fixed spot; reverts to [Stand] on arrival. */
        data class GoTo(val target: Vec3) : Order

        /** Keeping close to the Marionettist. */
        data object Follow : Order

        /** Beating one entity until it or the puppet is down. */
        data class Attack(val victimId: UUID) : Order
    }

    /**
     * What the victim was, kept for the moment the puppet falls.
     *
     * The Characteristic condenses out of a puppet made from a **Beyonder**, so the pathway and
     * the Sequence taken at conversion have to outlive their owner's demotion — by then the real
     * player is already one Sequence weaker, and asking them what they were would answer wrong.
     *
     * @property inventory the victim's things, carried by the puppet and dropped where it falls
     */
    class Capture(
        val victimId: UUID,
        val name: String,
        val pathway: String?,
        val sequence: Int?,
        val inventory: List<net.minecraft.world.item.ItemStack>,
    )

    var capture: Capture? = null

    /** Who pulls the threads. Set right after construction, before the puppet is added to a level. */
    var ownerId: UUID? = null

    var order: Order = Order.Stand

    /**
     * The rider's input while possessed, or `null` when the puppet moves on its own.
     *
     * The whole possession layer is these two fields plus a yaw: the client keeps sending
     * `ServerboundPlayerInputPacket` even with its camera elsewhere, so the server already has
     * the keys — see `PuppetDrive`.
     */
    var driverInput: Input? = null

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_PROFILE, ResolvableProfile.createUnresolved(NOBODY))
    }

    /** The face this puppet wears — the whole of the victim's identity, name and skin both. */
    override fun getProfile(): ResolvableProfile = entityData.get(DATA_PROFILE)

    fun setProfile(profile: ResolvableProfile) {
        entityData.set(DATA_PROFILE, profile)
    }

    /** The puppet's own health pool, inherited from whoever it was made of. */
    fun inheritHealth(maxHealth: Double, health: Double) {
        getAttribute(Attributes.MAX_HEALTH)?.baseValue = maxHealth
        setHealth(health.coerceIn(1.0, maxHealth).toFloat())
    }

    override fun tick() {
        if (level().isClientSide) {
            super.tick()
            return
        }
        steer()
        super.tick()
    }

    /**
     * One tick of will — the driver's, or the standing order's.
     *
     * Movement is written into the same `zza`/`xxa` fields vanilla's own `aiStep` reads, so
     * gravity, water, slabs and knockback all keep working without a line here.
     */
    private fun steer() {
        val input = driverInput
        if (input != null) {
            drive(input)
            return
        }
        when (val current = order) {
            is Order.Stand -> halt()
            is Order.GoTo -> walkTo(current.target) { order = Order.Stand }
            is Order.Follow -> follow()
            is Order.Attack -> assault(current.victimId)
        }
    }

    private fun drive(input: Input) {
        zza = (if (input.forward()) 1f else 0f) - (if (input.backward()) 1f else 0f)
        xxa = (if (input.left()) 1f else 0f) - (if (input.right()) 1f else 0f)
        isSprinting = input.sprint() && input.forward()
        if (input.jump() && onGround()) jumpFromGround()
        yHeadRot = yRot
        yBodyRot = yRot
    }

    private fun halt() {
        zza = 0f
        xxa = 0f
    }

    private fun follow() {
        val owner = ownerId?.let { (level() as? ServerLevel)?.getPlayerInAnyDimension(it) }
        if (owner == null || owner.level() !== level()) {
            halt()
            return
        }
        if (distanceToSqr(owner) <= FOLLOW_GAP * FOLLOW_GAP) halt() else walkTo(owner.position()) {}
    }

    private fun assault(victimId: UUID) {
        val victim = (level() as? ServerLevel)?.getEntityInAnyDimension(victimId) as? LivingEntity
        if (victim == null || !victim.isAlive || victim.level() !== level()) {
            order = Order.Stand
            halt()
            return
        }
        lookAt(victim.position())
        if (distanceToSqr(victim) > REACH * REACH) {
            walkTo(victim.position()) {}
            return
        }
        halt()
        if (attackCooldown > 0) {
            attackCooldown--
            return
        }
        attackCooldown = ATTACK_PERIOD
        swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        (level() as? ServerLevel)?.let { doHurtTarget(it, victim) }
    }

    /** Face the goal and walk at it, hopping over whatever the flat step-up cannot climb. */
    private fun walkTo(goal: Vec3, onArrival: () -> Unit) {
        if (position().closerThan(goal, ARRIVAL)) {
            halt()
            onArrival()
            return
        }
        lookAt(goal)
        zza = 1f
        xxa = 0f
        if (horizontalCollision && onGround()) jumpFromGround()
    }

    private fun lookAt(goal: Vec3) {
        val dx = goal.x - x
        val dz = goal.z - z
        yRot = (Math.toDegrees(Math.atan2(dz, dx)) - 90.0).toFloat()
        yBodyRot = yRot
        yHeadRot = yRot
    }

    private var attackCooldown = 0

    companion object {

        private val DATA_PROFILE: EntityDataAccessor<ResolvableProfile> =
            SynchedEntityData.defineId(MarionetteEntity::class.java, EntityDataSerializers.RESOLVABLE_PROFILE)

        /** A profile with no name yet: replaced the moment the puppet is given a face. */
        private val NOBODY: UUID = UUID(0L, 0L)

        /** How close following gets before it stops pushing. */
        private const val FOLLOW_GAP = 3.0

        private const val ARRIVAL = 1.2

        /** A player's own reach, near enough. */
        private const val REACH = 3.0

        /** Ticks between blows — roughly a vanilla sword's cadence. */
        private const val ATTACK_PERIOD = 12

        fun attributes(): AttributeSupplier.Builder = LivingEntity.createLivingAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.1)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.STEP_HEIGHT, 0.6)
            .add(Attributes.FOLLOW_RANGE, 32.0)
    }
}
