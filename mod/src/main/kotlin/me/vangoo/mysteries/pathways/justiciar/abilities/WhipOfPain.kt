package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Justiciar, Seq. 7 (Interrogator): **Whip of Pain**.
 *
 * A whip of crackling force strikes the target under the crosshair and forces the truth out: the
 * caster reads their Sequence, held weapon and health straight off the pain.
 */
class WhipOfPain : ActiveAbility(AbilityIdentity.of("justiciar_whip_of_pain")) {

    override val displayName: Component = Component.literal("Хлист Болю")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Матеріалізує електричний батіг у ціль під прицілом.\n" +
            "▪ Шкода $DAMAGE, Повільність і Сліпота\n" +
            "▪ Дальність $RANGE бл.\n" +
            "▪ Змушує ціль розкрити секрети: Послідовність, зброю, здоров'я",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає цілі під прицілом")

        drawWhip(context.level, context.eye, target.eyePosition)
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.5f,
        )

        target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), DAMAGE)
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 60, 4))
        target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 40, 0))

        interrogate(context, target)
        return AbilityOutcome.done("Хлист Болю розколов волю цілі")
    }

    private fun interrogate(context: CastContext, target: LivingEntity) {
        val caster = context.caster
        context.messaging.tell(caster, Component.literal("▬▬▬▬ ПРИМУСОВИЙ ДОПИТ ▬▬▬▬"))
        context.messaging.tell(
            caster,
            Component.literal("Ціль: ${target.name.string} | HP: ${"%.1f".format(target.health)}"),
        )

        val defender = context.beyonders.of(target.uuid)
        context.messaging.tell(
            caster,
            Component.literal(
                if (defender != null) "Статус: Потойбічний (Посл. ${defender.sequenceLevel})" else "Статус: Звичайна людина",
            ),
        )

        if (target is Player) {
            context.messaging.tell(caster, Component.literal("Зброя: ${target.mainHandItem.displayName.string}"))
        }
        context.messaging.tell(caster, Component.literal("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"))
    }

    /** A sagging arc of sparks — the "whip" shape, rather than a straight beam. */
    private fun drawWhip(level: net.minecraft.server.level.ServerLevel, from: Vec3, to: Vec3) {
        val span = to.subtract(from)
        val length = span.length()
        if (length < 0.1) return

        val points = max(12, (length * 6).toInt())
        for (i in 0..points) {
            val t = i.toDouble() / points
            val sag = sin(t * PI) * SAG_DEPTH
            val point = from.add(span.scale(t)).subtract(0.0, sag, 0.0)
            level.sendParticles(WHIP_DUST, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 20, 0.3, 0.3, 0.3, 0.1)
    }

    private companion object {
        const val RANGE = 10.0
        const val DAMAGE = 7.0f
        const val SAG_DEPTH = 0.45
        const val COOLDOWN_SECONDS = 12
        val WHIP_DUST = DustParticleOptions(0xFFF078, 0.8f)
    }
}
