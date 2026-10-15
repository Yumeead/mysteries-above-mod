package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.CorpseCollectorLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.death.DeathTraces
import me.vangoo.mysteries.pathways.death.isUndead
import me.vangoo.mysteries.pathways.death.sees
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.HitResult
import java.util.UUID

/**
 * Death, Seq. 9 (Corpse Collector): **Undead Knowledge**.
 *
 * "An abundant wealth of information on the characteristics and weaknesses of many Undead… which
 * makes a Corpse Collector an expert on autopsy." Two sides of one knowledge, chosen by what is in
 * the sights: living undead — the weak spot is marked; nothing undead — the place is read for
 * deaths.
 *
 * ## The autopsy reads the cause, not only the fact
 *
 * The plugin asked CoreProtect "who died here" and printed a description of its own. The mod's
 * [DeathTraces] keeps vanilla's own death line — the same words the chat printed at the moment of
 * death — so the Corpse Collector reads *how* each one died: fell, burned, was shot by a skeleton.
 * The graves themselves are marked for the caster alone.
 */
class UndeadKnowledge : ActiveAbility(AbilityIdentity.of("death_undead_knowledge")) {

    override val displayName: Component = Component.literal("Знання нежиті")

    override val spiritualityCost: Int = CorpseCollectorLore.KNOWLEDGE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = CorpseCollectorLore.knowledgeCooldownSeconds(sequence)

    /** One weak spot per Corpse Collector: a new mark takes the old one's place. */
    private val marks = HashMap<UUID, Subscription>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Дивитесь на нежить — знаходите її слабке місце. Дивитесь на землю — читаєте, хто тут помер і від чого.\n" +
            "▪ Слабке місце: ваша шкода ×${"%.2f".format(CorpseCollectorLore.markDamageMultiplier(sequence))} " +
            "на ${CorpseCollectorLore.markDurationSeconds(sequence)} с, дальність ${CorpseCollectorLore.markRange(sequence).toInt()} бл.\n" +
            "▪ Розтин: смерті в радіусі ${CorpseCollectorLore.autopsyRadius(sequence).toInt()} бл. " +
            "за ${CorpseCollectorLore.autopsyWindowSeconds(sequence) / 60} хв, до ${CorpseCollectorLore.autopsyMaxRecords(sequence)} записів",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val range = CorpseCollectorLore.markRange(context.sequence)
        val aimed = context.targeting.lookingAt(range)
        return if (aimed != null && aimed.isUndead()) mark(context, aimed) else autopsy(context, range)
    }

    private fun mark(context: CastContext, target: LivingEntity): AbilityOutcome {
        val casterId = context.caster.uuid
        val ticks = CorpseCollectorLore.markDurationSeconds(context.sequence) * TICKS_PER_SECOND
        val multiplier = CorpseCollectorLore.markDamageMultiplier(context.sequence)

        marks.remove(casterId)?.cancel()
        // An arrow counts as much as a blade: the attacker of a projectile is its shooter.
        marks[casterId] = context.events.on<IncomingDamage>(ticks) { damage ->
            if (damage.victim !== target || damage.attacker?.uuid != casterId) return@on
            damage.scale(multiplier)
            val level = target.level() as net.minecraft.server.level.ServerLevel
            level.sendParticles(ParticleTypes.SOUL, target.x, target.y + 1.0, target.z, 5, 0.2, 0.2, 0.2, 0.01)
        }
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, false))

        val level = context.level
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.x, target.eyeY, target.z, 10, 0.25, 0.3, 0.25, 0.02)
        level.playSound(null, target.x, target.y, target.z, SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.PLAYERS, 0.7f, 0.6f)

        val ailments = target.activeEffects.joinToString { it.effect.value().displayName.string }.ifEmpty { "нічого" }
        context.messaging.tell(
            context.caster,
            Component.literal(
                "☠ ${target.name.string}: ${"%.0f".format(target.health)} ❤ — слабке місце знайдено " +
                    "(шкода ×${"%.2f".format(multiplier)})\n  Стан: $ailments",
            ),
        )
        return AbilityOutcome.Done()
    }

    private fun autopsy(context: CastContext, range: Double): AbilityOutcome {
        val hit = context.caster.pick(range, 1.0f, false)
        val scene = if (hit.type == HitResult.Type.BLOCK) hit.location else context.origin
        val sequence = context.sequence
        val deaths = DeathTraces.near(
            context.level.dimension(),
            scene,
            CorpseCollectorLore.autopsyRadius(sequence),
            CorpseCollectorLore.autopsyWindowSeconds(sequence) * TICKS_PER_SECOND,
        ).take(CorpseCollectorLore.autopsyMaxRecords(sequence))
        if (deaths.isEmpty()) return AbilityOutcome.failed("Тут ніхто не вмирав — розтинати нічого")

        val caster = context.caster
        val lines = deaths.joinToString("\n") { trace ->
            val minutes = DeathTraces.ticksAgo(trace) / (60 * TICKS_PER_SECOND)
            "  • ${trace.message.string} (${if (minutes == 0L) "щойно" else "$minutes хв тому"})"
        }
        context.messaging.tell(caster, Component.literal("☠ Розтин — смертей поблизу: ${deaths.size}\n$lines"))

        // Each grave smoulders for the caster alone for a few seconds, then goes cold.
        var pulses = 0
        lateinit var handle: me.vangoo.mysteries.schedule.TaskHandle
        handle = context.scheduling.every(GRAVE_PULSE_TICKS, delayTicks = 0) {
            if (++pulses > GRAVE_PULSES || caster.isRemoved) return@every handle.cancel()
            deaths.forEach { trace ->
                caster.sees(ParticleTypes.SOUL, trace.pos.add(0.0, 0.3, 0.0), 3, 0.3, 0.05, 0.01)
                caster.sees(ParticleTypes.SCULK_SOUL, trace.pos.add(0.0, 1.0, 0.0), 1, 0.1, 0.3)
            }
        }
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.SOUL_SAND_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f)
        return AbilityOutcome.Done()
    }

    private companion object {
        const val GRAVE_PULSE_TICKS = 10
        const val GRAVE_PULSES = 12
    }
}
