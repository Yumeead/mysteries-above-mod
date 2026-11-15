package me.vangoo.mysteries.entity

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import me.vangoo.mysteries.creature.Creatures
import me.vangoo.mysteries.creature.Mind
import me.vangoo.mysteries.creature.Repertoire
import me.vangoo.mysteries.creature.release
import net.minecraft.core.component.DataComponents
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.enchantment.EnchantmentHelper
import kotlin.math.sqrt
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import java.util.Optional
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
 * ## A puppet is saved with its chunk; a stand-in is not
 *
 * A puppet carries the Characteristic torn out of its victim, so it must outlive a chunk unload
 * and a restart exactly as a mob does: its owner, capture, face and order are written with the
 * chunk ([addAdditionalSaveData]), and `Marionettes` re-adopts it the moment it loads. The plugin
 * needed a `MarionetteRestorer` for this; vanilla already saves entities.
 *
 * A vessel (possession, Language of the Dead) has no [capture] and is never saved
 * ([shouldBeSaved]): it stands in for a living player and means nothing without them.
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

        /** Keeping close to the Marionettist and striking back at whoever strikes them. */
        data object Guard : Order

        /** Keeping close to the Marionettist and striking whoever they strike. */
        data object Assist : Order

        /** Walking to [pos] and pressing what is there: a lever, a button, a door. */
        data class Use(val pos: BlockPos) : Order
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
    ) {
        companion object {
            val CODEC: Codec<Capture> = RecordCodecBuilder.create { row ->
                row.group(
                    UUIDUtil.CODEC.fieldOf("victim").forGetter(Capture::victimId),
                    Codec.STRING.fieldOf("name").forGetter(Capture::name),
                    Codec.STRING.optionalFieldOf("pathway").forGetter { Optional.ofNullable(it.pathway) },
                    Codec.INT.optionalFieldOf("sequence").forGetter { Optional.ofNullable(it.sequence) },
                    ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inventory").forGetter(Capture::inventory),
                ).apply(row) { victim, name, pathway, sequence, inventory ->
                    Capture(victim, name, pathway.orElse(null), sequence.orElse(null), inventory)
                }
            }
        }
    }

    var capture: Capture? = null

    /** Who pulls the threads. Set right after construction, before the puppet is added to a level. */
    var ownerId: UUID? = null

    var order: Order = Order.Stand

    /**
     * What a [Order.Guard], [Order.Assist] or [Order.Use] has the body do this moment: one of the
     * four plain verbs, decided by `Marionettes` a few times a second for puppets and seized mobs
     * alike. Not saved: it is worked out again from the order.
     */
    var step: Order? = null

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

    override fun shouldBeSaved(): Boolean = capture != null && ownerId != null && super.shouldBeSaved()

    override fun addAdditionalSaveData(output: ValueOutput) {
        super.addAdditionalSaveData(output)
        capture?.let { output.store("capture", Capture.CODEC, it) }
        output.storeNullable("owner", UUIDUtil.CODEC, ownerId)
        output.store("profile", ResolvableProfile.CODEC, profile)
        when (val current = order) {
            is Order.Stand -> output.putString("order", "stand")
            is Order.Follow -> output.putString("order", "follow")
            is Order.GoTo -> {
                output.putString("order", "go_to")
                output.store("order_target", Vec3.CODEC, current.target)
            }
            is Order.Attack -> {
                output.putString("order", "attack")
                output.store("order_victim", UUIDUtil.CODEC, current.victimId)
            }
            is Order.Guard -> output.putString("order", "guard")
            is Order.Assist -> output.putString("order", "assist")
            is Order.Use -> {
                output.putString("order", "use")
                output.store("order_block", BlockPos.CODEC, current.pos)
            }
        }
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        super.readAdditionalSaveData(input)
        capture = input.read("capture", Capture.CODEC).orElse(null)
        ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null)
        input.read("profile", ResolvableProfile.CODEC).ifPresent(::setProfile)
        order = when (input.getStringOr("order", "stand")) {
            "follow" -> Order.Follow
            "go_to" -> input.read("order_target", Vec3.CODEC).map<Order> { Order.GoTo(it) }.orElse(Order.Stand)
            "attack" -> input.read("order_victim", UUIDUtil.CODEC).map<Order> { Order.Attack(it) }.orElse(Order.Stand)
            "guard" -> Order.Guard
            "assist" -> Order.Assist
            "use" -> input.read("order_block", BlockPos.CODEC).map<Order> { Order.Use(it) }.orElse(Order.Stand)
            else -> Order.Stand
        }
    }

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
     * One tick of will: the driver's, or the standing order's.
     *
     * Movement is written into the same `zza`/`xxa` fields vanilla's own `aiStep` reads, so
     * gravity, water, slabs and knockback all keep working without a line here.
     */
    private fun steer() {
        if (driverId != null) return
        aiming = false
        when (val current = order) {
            is Order.Stand -> halt()
            is Order.GoTo -> walkTo(current.target) { order = Order.Stand }
            is Order.Follow -> follow()
            is Order.Attack -> assault(current.victimId) { order = Order.Stand }
            // The longer errands: `Marionettes` says what they mean right now.
            is Order.Guard, is Order.Assist, is Order.Use -> when (val now = step) {
                is Order.GoTo -> walkTo(now.target) { step = Order.Stand }
                is Order.Follow -> follow()
                is Order.Attack -> assault(now.victimId) { step = null }
                else -> halt()
            }
        }
        // The bow comes down the moment the fight that drew it is over.
        if (!aiming && isUsingItem) {
            drawn = 0
            stopUsingItem()
        }
    }

    /** Stands where it is; in water it treads it rather than sinking. */
    private fun halt() {
        zza = 0f
        xxa = 0f
        isSprinting = false
        setJumping(isInWater || isInLava)
    }

    /** At the Marionettist's heels, on foot: it runs to catch up and jumps where they jumped. */
    private fun follow() {
        val owner = ownerId?.let { (level() as? ServerLevel)?.getPlayerInAnyDimension(it) }
        if (owner == null || owner.level() !== level()) {
            halt()
            return
        }
        val distance = distanceTo(owner)
        if (distance <= FOLLOW_GAP) halt() else walkTo(owner.position(), sprint = distance > SPRINT_GAP) {}
    }

    // ── Fighting ────────────────────────────────────────────────────────────────

    /**
     * One tick of a fight, in the order a person fights: a technique of the Beyonder this was, if
     * one is ready and reaches; the bow or crossbow, if the target stands off and there are arrows;
     * otherwise the hardest-hitting blade in its pockets, up close.
     */
    private fun assault(victimId: UUID, onGone: () -> Unit) {
        val level = level() as? ServerLevel ?: return
        val victim = level.getEntityInAnyDimension(victimId) as? LivingEntity
        if (victim == null || !victim.isAlive || victim.level() !== level) {
            channel = null
            onGone()
            halt()
            return
        }
        lookAt(victim.position())
        if (technique(level, victim)) {
            halt()
            return
        }
        val distance = distanceTo(victim)
        val bow = if (distance > CLOSE && distance < SHOT_RANGE && hasLineOfSight(victim)) pocketOf { isRanged(it) } else null
        if (bow != null && pocketOf(::isArrow) != null) {
            halt()
            draw(level, victim, bow)
            return
        }
        wield(bestBlade())
        if (distance > REACH) {
            walkTo(victim.position(), sprint = distance > SPRINT_GAP) {}
            return
        }
        halt()
        if (attackCooldown > 0) {
            attackCooldown--
            return
        }
        attackCooldown = ATTACK_PERIOD
        swing(InteractionHand.MAIN_HAND)
        doHurtTarget(level, victim)
    }

    /**
     * A blow with what is in the hand. `LivingEntity` leaves the blow itself to `Mob` and only notes
     * the target, so until this a puppet swung at its enemy and hurt nobody. These are `Mob`'s own
     * lines, without its extra knockback.
     */
    override fun doHurtTarget(level: ServerLevel, target: Entity): Boolean {
        val weapon = weaponItem
        val source = weapon.getDamageSource(this)
        var damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat())
        damage += weapon.item.getAttackDamageBonus(target, damage, source)
        val hit = target.hurtServer(level, source, damage)
        if (hit) {
            (target as? LivingEntity)?.let { weapon.hurtEnemy(it, this) }
            EnchantmentHelper.doPostAttackEffects(level, target, source)
        }
        setLastHurtMob(target)
        return hit
    }

    /** What it carries: the victim's own pockets, hotbar first. A stand-in carries nothing. */
    private val pockets: List<ItemStack> get() = capture?.inventory.orEmpty()

    private fun pocketOf(test: (ItemStack) -> Boolean): Int? = pockets.indexOfFirst(test).takeIf { it >= 0 }

    private fun isRanged(stack: ItemStack): Boolean = stack.item is BowItem || stack.item is CrossbowItem

    private fun isArrow(stack: ItemStack): Boolean = stack.typeHolder().`is`(ItemTags.ARROWS)

    /** The pocket with the blade that hits hardest, or `null`: bare fists do as well. */
    private fun bestBlade(): Int? = pockets.indices.filterNot { isRanged(pockets[it]) }
        .maxByOrNull { damageOf(pockets[it]) }
        ?.takeIf { damageOf(pockets[it]) > BARE_HANDS }

    private fun damageOf(stack: ItemStack): Double =
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            .compute(Attributes.ATTACK_DAMAGE, BARE_HANDS, EquipmentSlot.MAINHAND)

    /**
     * Takes the thing in [pocket] into the hand, or empties the hand. The hand holds a copy of the
     * pocket, as it always has for a player-made puppet: what drops on its death are the pockets.
     */
    private fun wield(pocket: Int?) {
        val stack = pocket?.let(pockets::get) ?: ItemStack.EMPTY
        if (!ItemStack.matches(mainHandItem, stack)) setItemSlot(EquipmentSlot.MAINHAND, stack.copy())
    }

    /** Set by [draw] on every tick the string is pulled; a tick without it lowers the bow. */
    private var aiming = false
    private var drawn = 0

    /** Pulls the string for as long as a player would, then looses one arrow from its pockets at [victim]. */
    private fun draw(level: ServerLevel, victim: LivingEntity, bow: Int) {
        val weapon = pockets[bow]
        val crossbow = weapon.item is CrossbowItem
        wield(bow)
        aiming = true
        if (!isUsingItem) startUsingItem(InteractionHand.MAIN_HAND)
        val pull = if (crossbow) CrossbowItem.getChargeDuration(weapon, this) else BOW_DRAW
        if (++drawn < pull) return
        drawn = 0
        stopUsingItem()

        val ammo = pocketOf(::isArrow)?.let(pockets::get) ?: return
        val arrow = ProjectileUtil.getMobArrow(this, ammo.copyWithCount(1), 1f, weapon)
        val speed = if (crossbow) CROSSBOW_SPEED else BOW_SPEED
        val dx = victim.x - x
        val dz = victim.z - z
        // At the middle of the body, lifted by what gravity takes over the flight. A skeleton's lift of
        // a fifth of the distance overshoots anything small and near: the arrows passed over a pig's back.
        val flight = sqrt(dx * dx + dz * dz) / speed
        val lift = ARROW_GRAVITY / 2 * flight * flight
        Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, victim.getY(0.5) - arrow.y + lift, dz, speed, INACCURACY)
        ammo.shrink(1)
        val sound = if (crossbow) SoundEvents.CROSSBOW_SHOOT else SoundEvents.ARROW_SHOOT
        level.playSound(null, x, y, z, sound, soundSource, 1f, 1f / (random.nextFloat() * 0.4f + 0.8f))
    }

    /**
     * The techniques of the Beyonder this was: the kit a wild Beyonder of the same pathway and
     * Sequence fights with, run by the creature engine's own rotation. Built on first use, since the
     * capture arrives after construction (at conversion, or with the chunk).
     */
    private var repertoire: Repertoire? = null
    private var repertoireOf: Capture? = null

    /** A technique being wound up; the body holds still until it lands. */
    private var channel: Mind.Channel? = null

    /** One tick of the kit; `true` while a technique is wound up and the body must stand for it. */
    private fun technique(level: ServerLevel, victim: LivingEntity): Boolean {
        val now = tickCount.toLong()
        channel?.let { wound ->
            if ((wound.releaseAt - now) % TELEGRAPH_EVERY == 0L) wound.skill.telegraph?.at(level, wound.impact)
            if (now < wound.releaseAt) return true
            channel = null
            wound.skill.release(this, wound.target, wound.impact, 1f)
            return false
        }
        val skill = repertoire(level)?.next(now, this, victim, hasLineOfSight(victim)) ?: return false
        swing(InteractionHand.MAIN_HAND)
        if (skill.windup <= 0) {
            skill.release(this, victim, victim.position(), 1f)
            return false
        }
        channel = Mind.Channel(skill, victim, victim.position(), now + skill.windup)
        level.playSound(null, x, y, z, SoundEvents.EVOKER_PREPARE_ATTACK, soundSource, 0.6f, 1.4f)
        return true
    }

    private fun repertoire(level: ServerLevel): Repertoire? {
        val capture = capture ?: return null
        if (repertoireOf !== capture) {
            repertoireOf = capture
            repertoire = Creatures.kitFor(level, capture.pathway, capture.sequence)
                ?.let { Creatures.kit(level, it) }
                ?.let { Repertoire(level, it) }
        }
        return repertoire
    }

    // ── Walking ─────────────────────────────────────────────────────────────────

    /**
     * Face the goal and walk at it. It hops whatever the step-up cannot climb, and where the goal
     * stands higher just ahead (the Marionettist jumped onto something) it jumps up after it.
     */
    private fun walkTo(goal: Vec3, sprint: Boolean = false, onArrival: () -> Unit) {
        if (position().closerThan(goal, ARRIVAL)) {
            halt()
            onArrival()
            return
        }
        lookAt(goal)
        zza = 1f
        xxa = 0f
        isSprinting = sprint
        val dx = goal.x - x
        val dz = goal.z - z
        val climb = goal.y - y > CLIMB && dx * dx + dz * dz < CLIMB_REACH * CLIMB_REACH
        if (onGround() && (horizontalCollision || climb)) jumpFromGround()
        setJumping(isInWater || isInLava)
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

        /** How close following gets before it stops pushing, and how far behind it starts to run. */
        private const val FOLLOW_GAP = 3.0
        private const val SPRINT_GAP = 6.0

        /** The goal this much higher and this near is something to jump up onto. */
        private const val CLIMB = 0.6
        private const val CLIMB_REACH = 2.5

        private const val ARRIVAL = 1.2

        /** A player's own reach, near enough. */
        private const val REACH = 3.0

        /** Ticks between blows: roughly a vanilla sword's cadence. */
        private const val ATTACK_PERIOD = 12

        /** What a bare fist adds to the base damage: a blade has to beat it to be worth drawing. */
        private const val BARE_HANDS = 1.0

        /** Nearer than this it fights hand to hand, further than this it does not shoot. */
        private const val CLOSE = 5f
        private const val SHOT_RANGE = 24f

        /** A full draw, as a player's; a skeleton's arrow speed, a crossbow bolt's, and an arrow's fall per tick. */
        private const val BOW_DRAW = 20
        private const val BOW_SPEED = 1.6f
        private const val CROSSBOW_SPEED = 3.15f
        private const val ARROW_GRAVITY = 0.05
        private const val INACCURACY = 2f

        /** How often a wound-up technique marks its spot, as a creature's does. */
        private const val TELEGRAPH_EVERY = 4L

        fun attributes(): AttributeSupplier.Builder = LivingEntity.createLivingAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.1)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.STEP_HEIGHT, 0.6)
            .add(Attributes.FOLLOW_RANGE, 32.0)
    }
}
