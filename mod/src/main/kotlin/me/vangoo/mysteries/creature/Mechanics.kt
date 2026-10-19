package me.vangoo.mysteries.creature

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.TheftRegistry
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * One technique in flight: who released it, at whom, and where it lands.
 *
 * @property impact the spot marked when the technique began — for a wound-up technique the target
 * may no longer be standing on it
 * @property power the caster's current strength: `1` normally, more for an enraged boss
 */
class SkillCast(val caster: Mob, val target: LivingEntity?, val impact: Vec3, val power: Float) {
    val level: ServerLevel get() = caster.level() as ServerLevel
}

/**
 * One thing a technique does to one living [subject]. The vocabulary is the plugin's MythicMobs
 * mechanics that its packs actually used, plus its own custom ones (`scatter`, `blinkbehind`,
 * `drainsanity`, `sealabilities`, `stealability`, `stealitem`) — which here call the mod's real
 * services instead of a bridge.
 */
sealed interface Mechanic {

    val type: String

    fun apply(cast: SkillCast, subject: LivingEntity)

    companion object {
        private val TYPES: Map<String, MapCodec<out Mechanic>> = linkedMapOf(
            "damage" to Damage.CODEC,
            "effect" to Effect.CODEC,
            "particles" to Particles.CODEC,
            "sound" to Sound.CODEC,
            "knockback" to Knockback.CODEC,
            "heal" to Heal.CODEC,
            "ignite" to Ignite.CODEC,
            "lightning" to Lightning.CODEC,
            "projectile" to Projectile.CODEC,
            "summon" to Summon.CODEC,
            "leap" to Leap.CODEC,
            "scatter" to Scatter.CODEC,
            "blink_behind" to BlinkBehind.CODEC,
            "drain_sanity" to DrainSanity.CODEC,
            "seal_powers" to SealPowers.CODEC,
            "steal_power" to StealPower.CODEC,
            "steal_item" to StealItem.CODEC,
            "chance" to Chance.CODEC,
        )

        private val KEY: Codec<String> = Codec.STRING.validate {
            if (it in TYPES) DataResult.success(it) else DataResult.error { "Невідома механіка «$it»: ${TYPES.keys}" }
        }

        val CODEC: Codec<Mechanic> = KEY.dispatch("type", Mechanic::type) { TYPES.getValue(it) }
    }
}

class Damage(val amount: Float, val pierce: Boolean) : Mechanic {
    override val type get() = "damage"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val sources = cast.level.damageSources()
        // Piercing damage is the plugin's `ignoreArmor`: magic goes past armour, a blow does not.
        val source = if (pierce) sources.indirectMagic(cast.caster, cast.caster) else sources.mobAttack(cast.caster)
        subject.hurtServer(cast.level, source, amount * cast.power)
    }

    companion object {
        val CODEC: MapCodec<Damage> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                Codec.FLOAT.fieldOf("amount").forGetter(Damage::amount),
                Codec.BOOL.optionalFieldOf("pierce", false).forGetter(Damage::pierce),
            ).apply(row) { amount, pierce -> Damage(amount, pierce) }
        }
    }
}

class Effect(val effect: Holder<MobEffect>, val duration: Int, val amplifier: Int) : Mechanic {
    override val type get() = "effect"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        subject.addEffect(MobEffectInstance(effect, duration, amplifier, false, false, true), cast.caster)
    }

    companion object {
        val CODEC: MapCodec<Effect> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(Effect::effect),
                Codec.INT.fieldOf("duration").forGetter(Effect::duration),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Effect::amplifier),
            ).apply(row) { effect, duration, amplifier -> Effect(effect, duration, amplifier) }
        }
    }
}

class Particles(val puff: Puff) : Mechanic {
    override val type get() = "particles"

    override fun apply(cast: SkillCast, subject: LivingEntity) = puff.at(cast.level, subject.position())

    companion object {
        val CODEC: MapCodec<Particles> = Puff.CODEC.fieldOf("puff").xmap(::Particles, Particles::puff)
    }
}

class Sound(val sound: Holder<SoundEvent>, val volume: Float, val pitch: Float) : Mechanic {
    override val type get() = "sound"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        cast.level.playSound(null, subject.x, subject.y, subject.z, sound, SoundSource.HOSTILE, volume, pitch)
    }

    companion object {
        val CODEC: MapCodec<Sound> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                SoundEvent.CODEC.fieldOf("sound").forGetter(Sound::sound),
                Codec.FLOAT.optionalFieldOf("volume", 1f).forGetter(Sound::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1f).forGetter(Sound::pitch),
            ).apply(row) { sound, volume, pitch -> Sound(sound, volume, pitch) }
        }
    }
}

/** Shoves the subject away from the caster; a negative [strength] drags it in — the plugin's `pull`. */
class Knockback(val strength: Double, val up: Double) : Mechanic {
    override val type get() = "knockback"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val dir = flatDirection(cast.caster.position(), subject.position())
        val hold = 1.0 - subject.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE).coerceIn(0.0, 1.0)
        subject.push(dir.x * strength * hold, up * hold, dir.z * strength * hold)
        subject.hurtMarked = true
    }

    companion object {
        val CODEC: MapCodec<Knockback> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                Codec.DOUBLE.fieldOf("strength").forGetter(Knockback::strength),
                Codec.DOUBLE.optionalFieldOf("up", 0.2).forGetter(Knockback::up),
            ).apply(row) { strength, up -> Knockback(strength, up) }
        }
    }
}

class Heal(val amount: Float) : Mechanic {
    override val type get() = "heal"

    override fun apply(cast: SkillCast, subject: LivingEntity) = subject.heal(amount)

    companion object {
        val CODEC: MapCodec<Heal> = Codec.FLOAT.fieldOf("amount").xmap(::Heal, Heal::amount)
    }
}

class Ignite(val ticks: Int) : Mechanic {
    override val type get() = "ignite"

    override fun apply(cast: SkillCast, subject: LivingEntity) = subject.igniteForTicks(ticks)

    companion object {
        val CODEC: MapCodec<Ignite> = Codec.INT.fieldOf("ticks").xmap(::Ignite, Ignite::ticks)
    }
}

/** A real bolt for the eye and the ear, with the damage dealt by hand — a visual bolt sets no fires on a whim. */
class Lightning(val damage: Float) : Mechanic {
    override val type get() = "lightning"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val level = cast.level
        EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED)?.let { bolt ->
            bolt.snapTo(subject.x, subject.y, subject.z)
            bolt.setVisualOnly(true)
            level.addFreshEntity(bolt)
        }
        subject.hurtServer(level, level.damageSources().lightningBolt(), damage * cast.power)
    }

    companion object {
        val CODEC: MapCodec<Lightning> = Codec.FLOAT.fieldOf("damage").xmap(::Lightning, Lightning::damage)
    }
}

/**
 * A missile that flies, can be dodged and stops at walls — the plugin's MythicMobs `projectile`,
 * which was the same thing: a point marched along a line, painted with particles.
 *
 * @property speed blocks per second, the plugin's `velocity`
 */
class Projectile(
    val speed: Double,
    val range: Double,
    val radius: Double,
    val trail: Puff?,
    val hit: List<Mechanic>,
) : Mechanic {
    override val type get() = "projectile"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val level = cast.level
        val caster = cast.caster
        var pos = caster.eyePosition
        val aim = if (cast.target === subject) cast.impact.add(0.0, subject.bbHeight / 2.0, 0.0) else subject.boundingBox.center
        val step = aim.subtract(pos).normalize().scale(speed / TICKS_PER_SECOND)
        if (step.lengthSqr() < 1.0E-6) return
        var travelled = 0.0
        lateinit var flight: TaskHandle
        flight = AbilityServices.scheduler.every(1, 0) {
            val next = pos.add(step)
            val wall = level.clip(ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
            val struck = level.getEntitiesOfClass(LivingEntity::class.java, AABB(pos, next).inflate(radius)) {
                it !== caster && it.isAlive && Creatures.isFoe(caster, it)
            }.minByOrNull { it.distanceToSqr(pos) }
            when {
                struck != null -> {
                    hit.forEach { it.apply(cast, struck) }
                    flight.cancel()
                }
                wall.type != HitResult.Type.MISS || caster.isRemoved -> flight.cancel()
                else -> {
                    trail?.at(level, next)
                    pos = next
                    travelled += step.length()
                    if (travelled >= range) flight.cancel()
                }
            }
        }
    }

    companion object {
        private const val TICKS_PER_SECOND = 20.0

        val CODEC: MapCodec<Projectile> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                Codec.DOUBLE.optionalFieldOf("speed", 16.0).forGetter(Projectile::speed),
                Codec.DOUBLE.optionalFieldOf("range", 18.0).forGetter(Projectile::range),
                Codec.DOUBLE.optionalFieldOf("radius", 1.0).forGetter(Projectile::radius),
                Puff.CODEC.optionalFieldOf("trail").forGetter { java.util.Optional.ofNullable(it.trail) },
                nested().fieldOf("hit").forGetter(Projectile::hit),
            ).apply(row) { speed, range, radius, trail, hit -> Projectile(speed, range, radius, trail.orElse(null), hit) }
        }
    }
}

/** Calls kin of the caster's into the fight, already aimed at its target. */
class Summon(val creature: ResourceKey<CreatureSpec>, val count: Int, val radius: Double) : Mechanic {
    override val type get() = "summon"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val random = cast.caster.random
        repeat(count) {
            val angle = random.nextDouble() * Math.PI * 2
            val at = subject.position().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius)
            val summoned = Creatures.spawn(cast.level, creature, at, EntitySpawnReason.MOB_SUMMONED) ?: return
            cast.target?.let { summoned.target = it }
        }
    }

    companion object {
        val CODEC: MapCodec<Summon> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                ResourceKey.codec(CreatureRegistries.CREATURE).fieldOf("creature").forGetter(Summon::creature),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Summon::count),
                Codec.DOUBLE.optionalFieldOf("radius", 1.5).forGetter(Summon::radius),
            ).apply(row) { creature, count, radius -> Summon(creature, count, radius) }
        }
    }
}

/** The caster springs away from its target; a negative [strength] lunges at it. */
class Leap(val strength: Double, val up: Double) : Mechanic {
    override val type get() = "leap"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val from = cast.target?.position() ?: return
        val dir = flatDirection(from, cast.caster.position())
        cast.caster.deltaMovement = Vec3(dir.x * strength, up, dir.z * strength)
        cast.caster.hurtMarked = true
    }

    companion object {
        val CODEC: MapCodec<Leap> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                Codec.DOUBLE.fieldOf("strength").forGetter(Leap::strength),
                Codec.DOUBLE.optionalFieldOf("up", 0.3).forGetter(Leap::up),
            ).apply(row) { strength, up -> Leap(strength, up) }
        }
    }
}

/** The subject is thrown a few blocks aside onto solid ground; with no ground in reach it is only weakened. */
class Scatter(val radius: Double) : Mechanic {
    override val type get() = "scatter"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val spot = SafeSpots.near(cast.level, subject.position(), radius, subject.random)
        if (spot != null) {
            subject.teleportTo(spot.x, spot.y, spot.z)
        } else {
            subject.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false, true), cast.caster)
        }
    }

    companion object {
        val CODEC: MapCodec<Scatter> = Codec.DOUBLE.fieldOf("radius").xmap(::Scatter, Scatter::radius)
    }
}

/** The caster steps out of nowhere behind its target, facing its back. */
class BlinkBehind(val distance: Double) : Mechanic {
    override val type get() = "blink_behind"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val look = subject.lookAngle.multiply(1.0, 0.0, 1.0)
        val back = if (look.lengthSqr() < 1.0E-4) Vec3(1.0, 0.0, 0.0) else look.normalize()
        val behind = subject.position().subtract(back.scale(distance))
        val spot = SafeSpots.standable(cast.level, BlockPos.containing(behind)) ?: return
        val caster = cast.caster
        caster.teleportTo(spot.x, spot.y, spot.z)
        caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, subject.eyePosition)
    }

    companion object {
        val CODEC: MapCodec<BlinkBehind> = Codec.DOUBLE.optionalFieldOf("distance", 2.0).xmap(::BlinkBehind, BlinkBehind::distance)
    }
}

/** A Beyonder's grip on their sanity slips. Ordinary people have none to lose. */
class DrainSanity(val amount: Int) : Mechanic {
    override val type get() = "drain_sanity"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        val beyonder = AbilityServices.beyonders.of(subject.uuid) ?: return
        beyonder.increaseSanityLoss(amount)
        AbilityServices.statePump.markDirty(subject.uuid)
    }

    companion object {
        val CODEC: MapCodec<DrainSanity> = Codec.INT.fieldOf("amount").xmap(::DrainSanity, DrainSanity::amount)
    }
}

/**
 * Every power of a Beyonder falls silent for a while. The lock is the same one a broken oath puts
 * on a player — `Cooldowns.lockAll` — so the executor enforces it, not the creature.
 */
class SealPowers(val seconds: Int) : Mechanic {
    override val type get() = "seal_powers"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        if (subject !is ServerPlayer || AbilityServices.beyonders.of(subject.uuid) == null) return
        AbilityServices.cooldowns.lockAll(subject.uuid, seconds)
        AbilityServices.statePump.pushNow(subject.uuid)
        cast.level.playSound(null, subject.x, subject.y, subject.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 0.9f, 0.5f)
        AbilityServices.messaging.tell(subject, Component.literal("Ваш розум запечатано — здібності мовчать $seconds с"))
    }

    companion object {
        val CODEC: MapCodec<SealPowers> = Codec.INT.fieldOf("seconds").xmap(::SealPowers, SealPowers::seconds)
    }
}

/**
 * One active power of a Beyonder is stolen for a while — the very theft an Error player commits,
 * recorded in the same `TheftRegistry` under the creature's id. The creature has no use for the
 * power; the point is that its victim cannot call it.
 */
class StealPower(val seconds: Int) : Mechanic {
    override val type get() = "steal_power"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        if (subject !is ServerPlayer) return
        val beyonder = AbilityServices.beyonders.of(subject.uuid) ?: return
        val prey = beyonder.abilities
            .filter { it.type == AbilityType.ACTIVE && !TheftRegistry.isSuppressed(subject.uuid, it.identity) }
            .randomOrNull() ?: return
        val millis = seconds * 1000L
        TheftRegistry.steal(cast.caster.uuid, subject.uuid, prey.identity, hold = millis, suppression = millis)
        val name = (prey as? Ability)?.displayName ?: Component.literal(prey.identity.id)
        cast.level.playSound(null, subject.x, subject.y, subject.z, SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1f, 0.5f)
        AbilityServices.messaging.tell(
            subject,
            Component.literal("У вас вкрали силу «").append(name).append("» — вона мовчатиме $seconds с"),
        )
    }

    companion object {
        val CODEC: MapCodec<StealPower> = Codec.INT.fieldOf("seconds").xmap(::StealPower, StealPower::seconds)
    }
}

/**
 * A pickpocket's touch: part of a stack leaves the victim's pockets and falls at the thief's feet.
 * Nothing vanishes — getting it back means walking up to the thief, which is the pressure.
 */
class StealItem(val count: Int) : Mechanic {
    override val type get() = "steal_item"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        if (subject !is ServerPlayer) return
        val inventory = subject.inventory
        // The main pockets only: armour and the off hand stay on the body, or theft becomes undressing.
        val slot = (0 until MAIN_POCKETS).filter { !inventory.getItem(it).isEmpty }.randomOrNull() ?: return
        val loot = inventory.removeItem(slot, count)
        cast.caster.spawnAtLocation(cast.level, loot)
        cast.level.playSound(null, subject.x, subject.y, subject.z, SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.8f, 0.6f)
        AbilityServices.messaging.tell(subject, Component.literal("Щось витягло з ваших кишень: ").append(loot.hoverName))
    }

    companion object {
        private const val MAIN_POCKETS = 36

        val CODEC: MapCodec<StealItem> = Codec.INT.optionalFieldOf("count", 1).xmap(::StealItem, StealItem::count)
    }
}

/** The plugin's trailing `0.2` on a mechanic line: the inner mechanics happen only sometimes. */
class Chance(val chance: Float, val then: List<Mechanic>) : Mechanic {
    override val type get() = "chance"

    override fun apply(cast: SkillCast, subject: LivingEntity) {
        if (cast.caster.random.nextFloat() < chance) then.forEach { it.apply(cast, subject) }
    }

    companion object {
        val CODEC: MapCodec<Chance> = RecordCodecBuilder.mapCodec { row ->
            row.group(
                Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(Chance::chance),
                nested().fieldOf("then").forGetter(Chance::then),
            ).apply(row) { chance, then -> Chance(chance, then) }
        }
    }
}

/**
 * For the mechanics that hold mechanics of their own. Lazy because their codecs are built while
 * [Mechanic.CODEC] itself is still being built — its type table names them.
 */
private fun nested(): Codec<List<Mechanic>> = Codec.lazyInitialized { Mechanic.CODEC }.listOf()

/** The horizontal unit vector from [from] to [to]; straight east when they stand on one spot. */
internal fun flatDirection(from: Vec3, to: Vec3): Vec3 {
    val flat = to.subtract(from).multiply(1.0, 0.0, 1.0)
    return if (flat.lengthSqr() < 1.0E-4) Vec3(1.0, 0.0, 0.0) else flat.normalize()
}
