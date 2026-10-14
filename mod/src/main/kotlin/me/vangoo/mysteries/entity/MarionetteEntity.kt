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
        /** Rewritten when a possession ends: the driver may have used or picked things up. */
        var inventory: List<net.minecraft.world.item.ItemStack>,
    )

    var capture: Capture? = null

    /** Who pulls the threads. Set right after construction, before the puppet is added to a level. */
    var ownerId: UUID? = null

    var order: Order = Order.Stand

    /**
     * Set when this is not a puppet at all but the **vessel**: the body a possessing Marionettist
     * left standing, in their own face. It stands still, is pushed and struck like anyone, and
     * passes every blow on to the player it stands for — see `Marionettes.possess`.
     */
    var vesselOf: UUID? = null

    /**
     * Server side: who is inside, or `null` when the puppet moves on its own.
     *
     * While it is set the puppet only follows its driver (`Marionettes.steerPossessed`): it is
     * not simulated here ([isClientAuthoritative]) and cannot be shoved ([isPushable]) — the
     * driver stands in the very same spot, and vanilla's entity push would shove the puppet off.
     */
    var driverId: UUID? = null

    /**
     * Client side: this is the puppet the local player is inside. It is still there (its face is
     * the local player's skin for now), but it is neither drawn around the camera nor picked by
     * the crosshair — which, cast from inside its box, would otherwise always land on it.
     */
    var hiddenForDriver: Boolean = false

    /**
     * Walking speed from the attribute, as `Player` does it.
     *
     * `LivingEntity.getSpeed` returns a field only a mob's AI ever sets; for an `Avatar` it stays
     * 0, so the puppet could jump but never walk — neither under a driver nor on an order.
     */
    override fun getSpeed(): Float = getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat()

    /**
     * [driverId] is server-only, so the driver's client needs [hiddenForDriver] as well: there the
     * puppet lags a tick plus ping behind the local player it overlaps, and vanilla's client-side
     * push (which only ever moves the local player) would shove them on in the walking direction,
     * the puppet catching up and shoving again — the "inertia" slide.
     */
    override fun isPushable(): Boolean = driverId == null && !hiddenForDriver && super.isPushable()

    override fun pushEntities() {
        if (!hiddenForDriver) super.pushEntities()
    }

    override fun isClientAuthoritative(): Boolean = driverId != null

    override fun isPickable(): Boolean = !hiddenForDriver && super.isPickable()

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
        if (driverId != null) return
        when (val current = order) {
            is Order.Stand -> halt()
            is Order.GoTo -> walkTo(current.target) { order = Order.Stand }
            is Order.Follow -> follow()
            is Order.Attack -> assault(current.victimId)
        }
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
