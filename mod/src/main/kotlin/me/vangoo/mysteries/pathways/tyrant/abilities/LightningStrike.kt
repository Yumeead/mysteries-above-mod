package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Lightning Strike**.
 *
 * The Songster's one lightning that needs no go-between. Two outlets, a sneaking cast switches
 * between them:
 *
 * **Strike** — a silver-white discharge into whatever is on the line of sight, or the nearest
 * enemy if nobody is in view (the bolt is called by the voice, not the eyes). It paralyses,
 * doubles against the undead and forks "electric snakes" onto nearby foes.
 *
 * **Infuse** — the next few blows in melee carry a discharge and paralysis of their own.
 */
class LightningStrike : ActiveAbility(AbilityIdentity.of("tyrant_lightning_strike")) {

    override val displayName: Component = Component.literal("Удар Блискавки")

    override val spiritualityCost: Int = 200

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Сріблясто-біла блискавка — сила шторму.\n" +
            "▪ Удар: ${damage(sequence)} шкоди по цілі на лінії зору (або по найближчому ворогові), " +
            "×2 по нежиті, параліч і розряд на $MAX_CHAINS сусідів\n" +
            "▪ Насичення: зброя ${INFUSION_TICKS / 20} с додає ${infusedDamage(sequence)} шкоди й параліч " +
            "наступним $INFUSION_HITS ударам\n" +
            "▪ Shift+каст — перемкнути режим",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val owner = context.caster.uuid

        if (context.caster.isShiftKeyDown) {
            val next = modes.getOrDefault(owner, Mode.STRIKE).next()
            modes[owner] = next
            context.caster.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f)
            return AbilityOutcome.Awaiting(Component.literal("Режим блискавки: ${next.label}"))
        }

        return if (modes.getOrDefault(owner, Mode.STRIKE) == Mode.INFUSE) {
            infuse(context)
        } else {
            strike(context)
        }
    }

    private fun strike(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val damage = damage(context.sequence)
        val primary = context.targeting.lookingAt(MAX_RANGE)
            ?: nearestOther(context)
            ?: return AbilityOutcome.failed("Поблизу немає цілі")

        smite(context, primary, damage)

        val origin = primary.position()
        context.level.getEntitiesOfClass(LivingEntity::class.java, primary.boundingBox.inflate(CHAIN_RADIUS)) {
            it !== primary && it !== caster
        }
            .sortedBy { it.position().distanceToSqr(origin) }
            .take(MAX_CHAINS)
            .forEach { chained ->
                chain(context, origin, chained.position().add(0.0, 1.0, 0.0))
                smite(context, chained, (damage * CHAIN_DAMAGE_RATIO).toInt())
            }

        val at = caster.position()
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.2f)
        return AbilityOutcome.done("Блискавка вдарила в ${primary.name.string}")
    }

    private fun infuse(context: CastContext): AbilityOutcome {
        val owner = context.caster.uuid
        val bonus = infusedDamage(context.sequence)
        val charges = java.util.concurrent.atomic.AtomicInteger(INFUSION_HITS)

        context.events.on<IncomingDamage>(durationTicks = INFUSION_TICKS) { damage ->
            val attacker = damage.attacker ?: return@on
            if (attacker.uuid != owner || damage.directCause !== attacker) return@on
            if (charges.getAndDecrement() <= 0) return@on

            smite(context, damage.victim, bonus)
            context.messaging.actionBar(owner, Component.literal("⚡ Заряди: ${charges.get()}"))
        }

        context.effects.bodyAura(context.caster, INFUSION_TICKS, counterFlow = ParticleTypes.ELECTRIC_SPARK)
        val at = context.caster.position()
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 0.9f, 1.5f)
        return AbilityOutcome.done("Зброя насичена блискавкою")
    }

    private fun nearestOther(context: CastContext): LivingEntity? {
        val caster = context.caster
        return context.level.getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(MAX_RANGE)) {
            it !== caster
        }.minByOrNull { it.position().distanceToSqr(caster.position()) }
    }

    private fun chain(context: CastContext, from: net.minecraft.world.phys.Vec3, to: net.minecraft.world.phys.Vec3) {
        val steps = (from.distanceTo(to) / 0.4).toInt().coerceIn(1, 40)
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0)
        }
    }

    private fun damage(sequence: Sequence): Int = scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)
    private fun infusedDamage(sequence: Sequence): Int = scale(BASE_INFUSED_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private enum class Mode(val label: String) {
        STRIKE("Удар"), INFUSE("Насичення");

        fun next(): Mode = entries[(ordinal + 1) % entries.size]
    }

    internal companion object {
        const val BASE_DAMAGE = 9
        const val BASE_INFUSED_DAMAGE = 4
        const val PURIFICATION_MULTIPLIER = 2.0
        const val CHAIN_DAMAGE_RATIO = 0.5
        const val MAX_CHAINS = 3
        const val CHAIN_RADIUS = 5.0
        const val MAX_RANGE = 25.0
        const val PARALYSIS_TICKS = 60
        const val INFUSION_TICKS = 600
        const val INFUSION_HITS = 5
        const val COOLDOWN_SECONDS = 30

        private val modes = ConcurrentHashMap<UUID, Mode>()

        /**
         * A discharge into one target: damage (doubled against the undead via
         * [EntityTypeTags.SENSITIVE_TO_SMITE]), paralysis, and a real lightning bolt for show.
         * Shared with [LightningBoltArrow], since a bolt does not care what carried it.
         */
        fun smite(context: CastContext, victim: LivingEntity, damage: Int) {
            val undead = victim.type.builtInRegistryHolder().`is`(EntityTypeTags.SENSITIVE_TO_SMITE)
            val final = if (undead) (damage * PURIFICATION_MULTIPLIER).toInt() else damage

            victim.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), final.toFloat())
            victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, PARALYSIS_TICKS, 2))
            victim.addEffect(MobEffectInstance(MobEffects.WEAKNESS, PARALYSIS_TICKS, 0))

            val level = victim.level() as? ServerLevel ?: return
            val at = victim.position()
            val bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED) ?: return
            bolt.setVisualOnly(true)
            bolt.snapTo(at.x, at.y, at.z)
            level.addFreshEntity(bolt)
            level.sendParticles(ModParticles.mote(RgbColor.of(200, 220, 255)), at.x, at.y + 1.0, at.z, 16, 0.4, 0.6, 0.4, 0.02)
        }
    }
}
