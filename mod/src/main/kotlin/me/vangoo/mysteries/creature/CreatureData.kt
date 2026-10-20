package me.vangoo.mysteries.creature

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.mysteries.modId
import net.minecraft.core.Registry
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.phys.Vec3
import java.util.Optional

/**
 * The creature engine's three datapack registries.
 *
 * MythicMobs kept creatures in its own YAML dialect that only it could read; here they are
 * ordinary datapack entries — `data/<namespace>/mysteries_above/creature/<id>.json` — so a server
 * owner adds a creature the way they add a biome, and a broken file fails the world's load
 * loudly instead of spawning a zombie with no name.
 *
 * | registry | what an entry is | plugin counterpart |
 * |---|---|---|
 * | [CREATURE] | a body, its stats, its temperament | `Mobs/<pathway>.yml` + `creatures.yml` |
 * | [KIT] | how a pathway fights at one Sequence | the `MA_<Pathway>_S<seq>` templates |
 * | [SKILL] | one technique: what it does to whom | `Skills/<pathway>.yml` metaskills |
 *
 * References between them are plain keys, resolved when used: the mod's own data is pinned by a
 * unit test (`CreatureDataTest`), and a datapack that names a missing skill loses that skill, not
 * the world.
 */
object CreatureRegistries {
    val CREATURE: ResourceKey<Registry<CreatureSpec>> = ResourceKey.createRegistryKey(modId("creature"))
    val KIT: ResourceKey<Registry<CreatureKit>> = ResourceKey.createRegistryKey(modId("creature_kit"))
    val SKILL: ResourceKey<Registry<CreatureSkill>> = ResourceKey.createRegistryKey(modId("creature_skill"))
}

/** A lower-case enum in the data; an unknown word is a load error naming the choices. */
inline fun <reified E : Enum<E>> enumCodec(): Codec<E> = Codec.STRING.comapFlatMap(
    { word ->
        enumValues<E>().firstOrNull { it.name.equals(word, ignoreCase = true) }
            ?.let { DataResult.success(it) }
            ?: DataResult.error { "«$word» — очікувалось одне з ${enumValues<E>().joinToString { it.name.lowercase() }}" }
    },
    { it.name.lowercase() },
)

/**
 * A burst of particles around a point: the creature's aura, a technique's flash, a telegraph.
 * The fields read exactly like a MythicMobs `effect:particles` line, so the plugin's numbers moved over as they were.
 */
class Puff(
    val particle: ParticleOptions,
    val count: Int,
    val spread: Double,
    val rise: Double,
    val y: Double,
    val speed: Double,
) {
    fun at(level: ServerLevel, where: Vec3) {
        level.sendParticles(particle, where.x, where.y + y, where.z, count, spread, rise, spread, speed)
    }

    companion object {
        val CODEC: Codec<Puff> = RecordCodecBuilder.create { row ->
            row.group(
                ParticleTypes.CODEC.fieldOf("particle").forGetter(Puff::particle),
                Codec.INT.optionalFieldOf("count", 8).forGetter(Puff::count),
                Codec.DOUBLE.optionalFieldOf("spread", 0.3).forGetter(Puff::spread),
                Codec.DOUBLE.optionalFieldOf("rise", 0.5).forGetter(Puff::rise),
                Codec.DOUBLE.optionalFieldOf("y", 1.0).forGetter(Puff::y),
                Codec.DOUBLE.optionalFieldOf("speed", 0.02).forGetter(Puff::speed),
            ).apply(row) { particle, count, spread, rise, y, speed -> Puff(particle, count, spread, rise, y, speed) }
        }
    }
}

// ── The creature ───────────────────────────────────────────────────────────────

/**
 * What a creature **is**. How it thinks is [MindSpec]; how it fights at its Sequence is its
 * [CreatureKit].
 *
 * @property pathway the pathway whose characteristic the creature carries — the Law of Convergence
 * leans spawns towards it, and a wild Beyonder's kit is derived from it
 * @property apex Sequence 5 and above: shown only to a player who has come near Sequence 5 himself
 */
class CreatureSpec(
    val body: Body,
    val stats: Stats,
    val mind: MindSpec,
    val kit: ResourceKey<CreatureKit>?,
    val pathway: String?,
    val sequence: Int?,
    val apex: Boolean,
    val boss: BossSpec?,
    val spawn: SpawnSpec?,
    val persona: PersonaSpec?,
    val speech: Speech,
) {
    companion object {
        val CODEC: Codec<CreatureSpec> = RecordCodecBuilder.create { row ->
            row.group(
                Body.CODEC.fieldOf("body").forGetter(CreatureSpec::body),
                Stats.CODEC.fieldOf("stats").forGetter(CreatureSpec::stats),
                MindSpec.CODEC.optionalFieldOf("mind", MindSpec.DEFAULT).forGetter(CreatureSpec::mind),
                ResourceKey.codec(CreatureRegistries.KIT).optionalFieldOf("kit").forGetter { Optional.ofNullable(it.kit) },
                Codec.STRING.optionalFieldOf("pathway").forGetter { Optional.ofNullable(it.pathway) },
                Codec.intRange(0, 9).optionalFieldOf("sequence").forGetter { Optional.ofNullable(it.sequence) },
                Codec.BOOL.optionalFieldOf("apex", false).forGetter(CreatureSpec::apex),
                BossSpec.CODEC.optionalFieldOf("boss").forGetter { Optional.ofNullable(it.boss) },
                SpawnSpec.CODEC.optionalFieldOf("spawn").forGetter { Optional.ofNullable(it.spawn) },
                PersonaSpec.CODEC.optionalFieldOf("persona").forGetter { Optional.ofNullable(it.persona) },
                Speech.CODEC.optionalFieldOf("speech", Speech.SILENT).forGetter(CreatureSpec::speech),
            ).apply(row) { body, stats, mind, kit, pathway, sequence, apex, boss, spawn, persona, speech ->
                CreatureSpec(
                    body, stats, mind, kit.orElse(null), pathway.orElse(null), sequence.orElse(null), apex,
                    boss.orElse(null), spawn.orElse(null), persona.orElse(null), speech,
                )
            }
        }
    }
}

/**
 * The body: a vanilla entity type the mind is laid over, or the mod's own human.
 *
 * A vanilla body keeps its own way of moving and striking — the vex flies through walls, the
 * guardian fires its beam, the slime hops — which is exactly what MythicMobs kept, and why
 * thirty different bases did not need thirty movement systems.
 */
class Body(
    val entity: EntityType<*>,
    val name: Component?,
    val scale: Float,
    val equipment: Map<EquipmentSlot, Item>,
    val sunProof: Boolean,
    val aura: Puff?,
) {
    companion object {
        val CODEC: Codec<Body> = RecordCodecBuilder.create { row ->
            row.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Body::entity),
                ComponentSerialization.CODEC.optionalFieldOf("name").forGetter { Optional.ofNullable(it.name) },
                ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("scale", 1f).forGetter(Body::scale),
                Codec.unboundedMap(EquipmentSlot.CODEC, BuiltInRegistries.ITEM.byNameCodec())
                    .optionalFieldOf("equipment", emptyMap()).forGetter(Body::equipment),
                Codec.BOOL.optionalFieldOf("sun_proof", false).forGetter(Body::sunProof),
                Puff.CODEC.optionalFieldOf("aura").forGetter { Optional.ofNullable(it.aura) },
            ).apply(row) { entity, name, scale, equipment, sunProof, aura ->
                Body(entity, name.orElse(null), scale, equipment, sunProof, aura.orElse(null))
            }
        }
    }
}

/**
 * Raw numbers, laid onto the body's attributes. [speed] is the attribute value itself (a zombie
 * walks at 0.23), as in MythicMobs' `MovementSpeed`.
 */
class Stats(
    val health: Float,
    val damage: Float,
    val speed: Double?,
    val armor: Double,
    val followRange: Double,
    val knockbackResistance: Double,
) {
    companion object {
        val CODEC: Codec<Stats> = RecordCodecBuilder.create { row ->
            row.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("health").forGetter(Stats::health),
                ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("damage", 2f).forGetter(Stats::damage),
                Codec.DOUBLE.optionalFieldOf("speed").forGetter { Optional.ofNullable(it.speed) },
                Codec.DOUBLE.optionalFieldOf("armor", 0.0).forGetter(Stats::armor),
                Codec.DOUBLE.optionalFieldOf("follow_range", 32.0).forGetter(Stats::followRange),
                Codec.DOUBLE.optionalFieldOf("knockback_resistance", 0.0).forGetter(Stats::knockbackResistance),
            ).apply(row) { health, damage, speed, armor, follow, knockback ->
                Stats(health, damage, speed.orElse(null), armor, follow, knockback)
            }
        }
    }
}

// ── The mind ───────────────────────────────────────────────────────────────────

/** Whom a creature starts a fight with. Everyone defends themselves except [WILLLESS]. */
enum class Temperament {
    /** Hunts any player in reach — Beyonders first: a characteristic calls to another. */
    HOSTILE,

    /** Hunts only Beyonders it is sure to beat; ordinary people and its equals it leaves alone. */
    PREDATORY,

    /** Never starts a fight, always finishes one. */
    NEUTRAL,

    /** Never fights at all: hurt or frightened, it runs. */
    TIMID,

    /** Has no will of its own — a Spirit World being waiting for someone to give it orders. */
    WILLLESS,
}

/** What life a creature lives when nobody is fighting it. */
enum class Role {
    /** Roams, hunts, despawns like any wild thing. */
    BEAST,

    /** Lives by the clock: home, work, the square, bed. Flees danger and calls the guard. */
    CITIZEN,

    /** Walks rounds around its post; fights monsters and whoever the citizens point at. */
    GUARD,

    /** A wild Beyonder: hides their aura, picks their fights, leaves a characteristic behind. */
    BEYONDER,
}

/** Who moves the body in a fight: its own vanilla goals, or the mod's chase-and-strike. */
enum class Combat {
    /** Vanilla's attack goal if the body has one (a goat or a chicken does not), the mod's otherwise. */
    AUTO,
    NATIVE,
    OWN,
}

/**
 * @property fleeBelow the share of health under which the creature breaks off to survive; `0` — never
 * @property fearless ignores the pressure of a higher Sequence — bosses, the Spirit World's beings
 */
class MindSpec(
    val temperament: Temperament,
    val role: Role,
    val fleeBelow: Float,
    val fearless: Boolean,
    val combat: Combat,
) {
    companion object {
        val DEFAULT = MindSpec(Temperament.HOSTILE, Role.BEAST, 0f, false, Combat.AUTO)

        val CODEC: Codec<MindSpec> = RecordCodecBuilder.create { row ->
            row.group(
                enumCodec<Temperament>().optionalFieldOf("temperament", DEFAULT.temperament).forGetter(MindSpec::temperament),
                enumCodec<Role>().optionalFieldOf("role", DEFAULT.role).forGetter(MindSpec::role),
                Codec.floatRange(0f, 1f).optionalFieldOf("flee_below", DEFAULT.fleeBelow).forGetter(MindSpec::fleeBelow),
                Codec.BOOL.optionalFieldOf("fearless", DEFAULT.fearless).forGetter(MindSpec::fearless),
                enumCodec<Combat>().optionalFieldOf("combat", DEFAULT.combat).forGetter(MindSpec::combat),
            ).apply(row) { temperament, role, flee, fearless, combat -> MindSpec(temperament, role, flee, fearless, combat) }
        }
    }
}

/**
 * Who a human is, rolled once at spawn and kept on the entity: a city of a hundred people from
 * one file, each with their own name, face and — for a wild Beyonder — their own pathway.
 *
 * @property pathways the pathways a wild Beyonder may walk; empty — an ordinary person
 */
class PersonaSpec(
    val names: List<String>,
    val pathways: List<String>,
    val sequenceMin: Int,
    val sequenceMax: Int,
) {
    companion object {
        val CODEC: Codec<PersonaSpec> = RecordCodecBuilder.create { row ->
            row.group(
                Codec.STRING.listOf().optionalFieldOf("names", emptyList()).forGetter(PersonaSpec::names),
                Codec.STRING.listOf().optionalFieldOf("pathways", emptyList()).forGetter(PersonaSpec::pathways),
                Codec.intRange(0, 9).optionalFieldOf("weakest", 9).forGetter(PersonaSpec::sequenceMin),
                Codec.intRange(0, 9).optionalFieldOf("strongest", 9).forGetter(PersonaSpec::sequenceMax),
            ).apply(row) { names, pathways, min, max -> PersonaSpec(names, pathways, min, max) }
        }
    }
}

/** Lines a creature says over its head. Empty lists — it says nothing at that moment. */
class Speech(
    val greet: List<String>,
    val work: List<String>,
    val night: List<String>,
    val panic: List<String>,
    val fight: List<String>,
    val flee: List<String>,
) {
    companion object {
        val SILENT = Speech(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        val CODEC: Codec<Speech> = RecordCodecBuilder.create { row ->
            fun lines(key: String, get: (Speech) -> List<String>) =
                Codec.STRING.listOf().optionalFieldOf(key, emptyList()).forGetter(get)
            row.group(
                lines("greet", Speech::greet),
                lines("work", Speech::work),
                lines("night", Speech::night),
                lines("panic", Speech::panic),
                lines("fight", Speech::fight),
                lines("flee", Speech::flee),
            ).apply(row) { greet, work, night, panic, fight, flee -> Speech(greet, work, night, panic, fight, flee) }
        }
    }
}

// ── Bosses ─────────────────────────────────────────────────────────────────────

/**
 * A creature that is a fight in several acts rather than a bag of health.
 *
 * @property leash how far from where it rose the boss may be drawn; past it the fight is abandoned —
 * it walks home, heals and starts over from the first act
 * @property enrageAfter ticks of fighting after which it hits half again as hard; `0` — never
 */
class BossSpec(
    val bar: BossEvent.BossBarColor,
    val leash: Double,
    val enrageAfter: Int,
    val phases: List<Phase>,
) {
    companion object {
        val CODEC: Codec<BossSpec> = RecordCodecBuilder.create { row ->
            row.group(
                BossEvent.BossBarColor.CODEC.optionalFieldOf("bar", BossEvent.BossBarColor.PURPLE).forGetter(BossSpec::bar),
                Codec.DOUBLE.optionalFieldOf("leash", 32.0).forGetter(BossSpec::leash),
                Codec.INT.optionalFieldOf("enrage_after", 0).forGetter(BossSpec::enrageAfter),
                Phase.CODEC.listOf().optionalFieldOf("phases", emptyList()).forGetter(BossSpec::phases),
            ).apply(row) { bar, leash, enrage, phases -> BossSpec(bar, leash, enrage, phases) }
        }.validate { boss ->
            // The first act is the creature's own; every later one must begin lower than the one before.
            val thresholds = listOf(1f) + boss.phases.map(Phase::below)
            if (thresholds.zipWithNext().all { (a, b) -> b < a }) {
                DataResult.success(boss)
            } else {
                DataResult.error { "Пороги актів боса мусять спадати від 1: $thresholds" }
            }
        }
    }
}

/**
 * One act. It begins when health first falls to [below].
 *
 * @property kit the act's techniques; `null` — keep the creature's own
 * @property enter techniques released at once as the act begins — a roar, a shockwave, a summoning
 * @property pause ticks the boss stands untouchable while the act turns: the telegraph that a new
 * act is coming, and the breath a party needs to see it
 */
class Phase(
    val below: Float,
    val kit: ResourceKey<CreatureKit>?,
    val enter: List<ResourceKey<CreatureSkill>>,
    val pause: Int,
) {
    companion object {
        val CODEC: Codec<Phase> = RecordCodecBuilder.create { row ->
            row.group(
                Codec.floatRange(0f, 1f).fieldOf("below").forGetter(Phase::below),
                ResourceKey.codec(CreatureRegistries.KIT).optionalFieldOf("kit").forGetter { Optional.ofNullable(it.kit) },
                ResourceKey.codec(CreatureRegistries.SKILL).listOf().optionalFieldOf("enter", emptyList()).forGetter(Phase::enter),
                Codec.INT.optionalFieldOf("pause", 40).forGetter(Phase::pause),
            ).apply(row) { below, kit, enter, pause -> Phase(below, kit.orElse(null), enter, pause) }
        }
    }
}

/**
 * Where the creature turns up by itself. [chance] is its weight in its biomes' ambient roll,
 * [structures] its weight in the "a chest was opened" lottery — the plugin's `creatures.yml` numbers.
 */
class SpawnSpec(
    val biomes: List<Identifier>,
    val chance: Double,
    val structures: Double,
) {
    companion object {
        val CODEC: Codec<SpawnSpec> = RecordCodecBuilder.create { row ->
            row.group(
                Identifier.CODEC.listOf().optionalFieldOf("biomes", emptyList()).forGetter(SpawnSpec::biomes),
                Codec.DOUBLE.optionalFieldOf("chance", 0.0).forGetter(SpawnSpec::chance),
                Codec.DOUBLE.optionalFieldOf("structures", 0.0).forGetter(SpawnSpec::structures),
            ).apply(row) { biomes, chance, structures -> SpawnSpec(biomes, chance, structures) }
        }
    }
}

// ── The kit ────────────────────────────────────────────────────────────────────

/**
 * How a pathway fights at one Sequence — shared by its creatures and its wild Beyonders alike, the
 * way a MythicMobs template was.
 *
 * @property gcd ticks between any two techniques from [skills]
 * @property range how near the target must be for a technique to be worth casting
 * @property stance `null` — a melee fighter; otherwise it holds the band and casts from it
 * @property haste extra walking speed, a fifth per step — the plugin's `SPEED` potion on a timer
 * @property stalker unseen while nobody stands within four blocks: the Visionary's ambush
 */
class CreatureKit(
    val gcd: Int,
    val range: Double,
    val stance: Stance?,
    val skills: List<KitSkill>,
    val reactions: List<Reaction>,
    val haste: Int,
    val stalker: Boolean,
) {
    companion object {
        val CODEC: Codec<CreatureKit> = RecordCodecBuilder.create { row ->
            row.group(
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("gcd", 120).forGetter(CreatureKit::gcd),
                Codec.DOUBLE.optionalFieldOf("range", 12.0).forGetter(CreatureKit::range),
                Stance.CODEC.optionalFieldOf("stance").forGetter { Optional.ofNullable(it.stance) },
                KitSkill.CODEC.listOf().optionalFieldOf("skills", emptyList()).forGetter(CreatureKit::skills),
                Reaction.CODEC.listOf().optionalFieldOf("reactions", emptyList()).forGetter(CreatureKit::reactions),
                Codec.intRange(0, 4).optionalFieldOf("haste", 0).forGetter(CreatureKit::haste),
                Codec.BOOL.optionalFieldOf("stalker", false).forGetter(CreatureKit::stalker),
            ).apply(row) { gcd, range, stance, skills, reactions, haste, stalker ->
                CreatureKit(gcd, range, stance.orElse(null), skills, reactions, haste, stalker)
            }
        }
    }
}

/** A ranged fighter keeps its target between [min] and [max] blocks, and charges only when provoked. */
class Stance(val min: Double, val max: Double) {
    companion object {
        val CODEC: Codec<Stance> = RecordCodecBuilder.create { row ->
            row.group(
                Codec.DOUBLE.optionalFieldOf("min", 5.0).forGetter(Stance::min),
                Codec.DOUBLE.optionalFieldOf("max", 11.0).forGetter(Stance::max),
            ).apply(row) { min, max -> Stance(min, max) }
        }
    }
}

/** A technique in the rotation; [cooldown] in ticks, on top of the kit's GCD. */
class KitSkill(val skill: ResourceKey<CreatureSkill>, val cooldown: Int) {
    companion object {
        val CODEC: Codec<KitSkill> = RecordCodecBuilder.create { row ->
            row.group(
                ResourceKey.codec(CreatureRegistries.SKILL).fieldOf("skill").forGetter(KitSkill::skill),
                Codec.INT.fieldOf("cooldown").forGetter(KitSkill::cooldown),
            ).apply(row) { skill, cooldown -> KitSkill(skill, cooldown) }
        }
    }
}

/**
 * A technique outside the rotation, fired by a moment instead of the GCD.
 *
 * | when | the moment | aimed at |
 * |---|---|---|
 * | `hurt` | taking a blow, at or below [below] health | whoever struck |
 * | `near` | an enemy within [distance] blocks | that enemy |
 * | `pulse` | every [every] ticks while fighting | the technique's own area |
 */
class Reaction(
    val trigger: Trigger,
    val skill: ResourceKey<CreatureSkill>,
    val cooldown: Int,
    val below: Float,
    val distance: Double,
    val every: Int,
) {
    enum class Trigger { HURT, NEAR, PULSE }

    companion object {
        val CODEC: Codec<Reaction> = RecordCodecBuilder.create { row ->
            row.group(
                enumCodec<Trigger>().fieldOf("when").forGetter(Reaction::trigger),
                ResourceKey.codec(CreatureRegistries.SKILL).fieldOf("skill").forGetter(Reaction::skill),
                Codec.INT.optionalFieldOf("cooldown", 0).forGetter(Reaction::cooldown),
                Codec.floatRange(0f, 1f).optionalFieldOf("below", 1f).forGetter(Reaction::below),
                Codec.DOUBLE.optionalFieldOf("distance", 3.0).forGetter(Reaction::distance),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("every", 40).forGetter(Reaction::every),
            ).apply(row) { trigger, skill, cooldown, below, distance, every -> Reaction(trigger, skill, cooldown, below, distance, every) }
        }
    }
}

// ── The technique ──────────────────────────────────────────────────────────────

/**
 * One technique: what happens to the caster ([self]) and to whom it is aimed at ([target]).
 *
 * @property windup ticks the caster stands still, marking the spot, before the technique lands.
 * A target that has left the spot by then is missed — the telegraph is a promise, not decoration.
 * @property beyondersOnly the plugin's `isbeyonder` condition: aimed at an ordinary person, the
 * technique is simply not chosen (the plugin spent its cooldown on nothing)
 * @property area `0` — the target alone; otherwise every enemy within this radius
 * @property aroundSelf the area is centred on the caster, not on the target
 * @property animation what a body with a model plays as the technique begins — the plugin's
 * `state{s=…}` line; a body without one, or without that animation, simply does not
 */
class CreatureSkill(
    val windup: Int,
    val telegraph: Puff?,
    val beyondersOnly: Boolean,
    val range: Double?,
    val area: Double,
    val aroundSelf: Boolean,
    val self: List<Mechanic>,
    val target: List<Mechanic>,
    val animation: String?,
) {
    companion object {
        val CODEC: Codec<CreatureSkill> = RecordCodecBuilder.create { row ->
            row.group(
                Codec.INT.optionalFieldOf("windup", 0).forGetter(CreatureSkill::windup),
                Puff.CODEC.optionalFieldOf("telegraph").forGetter { Optional.ofNullable(it.telegraph) },
                Codec.BOOL.optionalFieldOf("beyonders_only", false).forGetter(CreatureSkill::beyondersOnly),
                Codec.DOUBLE.optionalFieldOf("range").forGetter { Optional.ofNullable(it.range) },
                Codec.DOUBLE.optionalFieldOf("area", 0.0).forGetter(CreatureSkill::area),
                Codec.BOOL.optionalFieldOf("around_self", false).forGetter(CreatureSkill::aroundSelf),
                Mechanic.CODEC.listOf().optionalFieldOf("self", emptyList()).forGetter(CreatureSkill::self),
                Mechanic.CODEC.listOf().optionalFieldOf("target", emptyList()).forGetter(CreatureSkill::target),
                Codec.STRING.optionalFieldOf("animation").forGetter { Optional.ofNullable(it.animation) },
            ).apply(row) { windup, telegraph, beyonders, range, area, aroundSelf, self, target, animation ->
                CreatureSkill(windup, telegraph.orElse(null), beyonders, range.orElse(null), area, aroundSelf, self, target, animation.orElse(null))
            }
        }
    }
}
