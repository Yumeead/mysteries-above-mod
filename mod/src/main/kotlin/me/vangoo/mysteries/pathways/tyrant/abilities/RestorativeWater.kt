package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Tyrant, Посл. 7 (Мореплавець): **Цілюща Вода**.
 *
 * Мореплавець огортає себе чи союзника, на якого дивиться, живлющою водою — слабка
 * регенерація. Навмисно слабша за зілля, зате миттєва й безкоштовна для матеріалів
 * підтримка в бою.
 */
class RestorativeWater : ActiveAbility(AbilityIdentity.of("tyrant_restorative_water")) {

    override val displayName: Component = Component.literal("Цілюща Вода")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Живлюща вода огортає вас або союзника, на якого ви дивитесь: слабка регенерація " +
            "на ${durationTicks(sequence) / TICKS_PER_SECOND} с. Поступається зіллям, зате миттєва.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        // Ціль — союзник на лінії зору, інакше сам кастер: промах не має коштувати касту.
        val target: LivingEntity = context.targeting.playerLookingAt(MAX_RANGE) ?: caster
        val ticks = durationTicks(context.sequence)

        target.addEffect(MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, true, true))

        // Покрив тримається рівно стільки, скільки діє вода, і ходить із ціллю: колір
        // шляху й планувальник має сам сервіс. Бризки — зустрічний рух до пасм.
        context.effects.bodyAura(target, ticks, counterFlow = ParticleTypes.SPLASH)
        splash(context, target)

        if (target !== caster) {
            context.messaging.actionBar(
                target.uuid,
                Component.literal("Цілюща вода огортає вас — ${caster.name.string}"),
            )
            return AbilityOutcome.done("Ви огорнули ${target.name.string} цілющою водою")
        }
        return AbilityOutcome.done("Цілюща вода огортає вас")
    }

    private fun durationTicks(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    /**
     * Сплеск у мить касту: без нього покрив «проявляється», замість початись.
     *
     * Лишається тут, а не в `context.effects`, і це та сама межа: два ванільні виклики
     * без тривалості й без повтору. У словник фігур потрапляє те, що триває або що
     * попросила друга здібність, а не все, що видно.
     */
    private fun splash(context: CastContext, target: LivingEntity) {
        val eye = target.eyePosition
        context.level.sendParticles(ParticleTypes.SPLASH, eye.x, eye.y, eye.z, 20, 0.4, 0.4, 0.4, 0.05)
        context.level.playSound(
            null, eye.x, eye.y, eye.z,
            SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
    }

    private companion object {
        /** ~4 с регенерації I на Посл. 7; росте з Послідовністю. */
        const val BASE_DURATION_TICKS = 80
        const val MAX_RANGE = 20.0
        const val COOLDOWN_SECONDS = 20
    }
}
