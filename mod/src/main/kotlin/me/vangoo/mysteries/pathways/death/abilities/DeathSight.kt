package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.CorpseCollectorLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.pathways.death.sees
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * Death, Seq. 9 (Corpse Collector): **Death Sight**.
 *
 * "Able to directly see spiritual bodies… even WITHOUT activating their Spirit Vision." Free,
 * always on, and deliberately narrower than the [SpiritVision] toggle: it shows only what hides,
 * and only up close — a hidden body gives itself away by a wisp of soul smoke that **only the
 * Corpse Collector** receives. The target's own invisibility is never touched: undoing someone
 * else's hiding would start a tug of war with every power that hides a player each tick.
 */
class DeathSight :
    PermanentPassiveAbility(AbilityIdentity.of("death_death_sight")),
    Ticking {

    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Бачення духів")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваша природа сама бачить духовні тіла — без жодного напруження.\n" +
            "▪ Невидиме видає себе димком душі в радіусі ${CorpseCollectorLore.deathSightRange(sequence).toInt()} бл.\n" +
            "▪ Димок бачите лише ви",
    )

    override fun tick(context: AbilityContext) {
        val caster = context.caster
        for (entity in context.targeting.livingNear(CorpseCollectorLore.deathSightRange(context.sequence))) {
            if (!entity.isInvisible && !entity.hasEffect(MobEffects.INVISIBILITY)) continue
            val eye = entity.eyePosition
            caster.sees(ParticleTypes.SOUL, eye, 3, 0.15, 0.3, 0.01)
            caster.sees(ParticleTypes.SCULK_SOUL, eye.add(0.0, 0.3, 0.0), 1, 0.1)
        }
    }
}
