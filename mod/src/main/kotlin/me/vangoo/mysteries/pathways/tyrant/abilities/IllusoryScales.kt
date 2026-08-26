package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Tyrant, Посл. 9 (Матрос): **Ілюзорна луска**.
 *
 * Прихована під шкірою луска робить Матроса слизьким і твердим на дотик, мов риба.
 * Механічно — опір шкоді, але лише поки тіло у воді, де луска «оживає». На суші ефекту немає.
 */
class IllusoryScales : PermanentPassiveAbility(AbilityIdentity.of("tyrant_illusory_scales")) {

    /** Двічі на секунду: гравець встигає вистрибнути з води й отримати шкоду вже без луски. */
    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Ілюзорна луска")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Прихована під шкірою луска робить вас слизьким і твердим, мов риба. " +
            "У воді вона дає опір шкоді.",
    )

    override fun tick(context: AbilityContext) {
        val player = context.caster
        if (player.isInWater) {
            player.addEffect(
                MobEffectInstance(MobEffects.RESISTANCE, EFFECT_TICKS, AMPLIFIER, true, false, true),
            )
            shimmer(context, player)
        } else {
            removeOwnScales(player)
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        removeOwnScales(context.caster)
    }

    /**
     * Знімає **свій** опір, не чужий.
     *
     * Плагін кликав `removePotionEffect(RESISTANCE)` без розбору, і вихід із води гасив
     * ще й випите зілля опору. Луска накладає рівно [EFFECT_TICKS] і рівно [AMPLIFIER],
     * тож усе, що триває довше або сильніше, — не її, і чіпати його нема підстав.
     */
    private fun removeOwnScales(player: ServerPlayer) {
        val active = player.getEffect(MobEffects.RESISTANCE) ?: return
        if (active.amplifier != AMPLIFIER) return
        if (active.isInfiniteDuration || active.duration > EFFECT_TICKS) return
        player.removeEffect(MobEffects.RESISTANCE)
    }

    private fun shimmer(context: AbilityContext, player: ServerPlayer) {
        context.level.sendParticles(
            ParticleTypes.BUBBLE,
            player.x, player.y + player.bbHeight / 2.0, player.z,
            2, 0.3, 0.4, 0.3, 0.0,
        )
    }

    private companion object {
        /** Трохи довше за період такту — щоб опір не мерехтів між перевірками. */
        const val EFFECT_TICKS = 30

        /** Опір I: на Посл. 9 луска ще тонка. */
        const val AMPLIFIER = 0
    }
}
