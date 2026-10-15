package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.CorpseCollectorLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffects

/**
 * Death, Seq. 9 (Corpse Collector): **Resistance to Cadaveric Auras**.
 *
 * "Resistance to the Cold, Decay and Corrosiveness of cadaveric auras."
 *
 * ## The blow, not the clock
 *
 * The plugin shortened Wither and Poison every second and locked the freeze counter by a tick.
 * The decay half is really a question about damage, so it is answered at the moment of damage:
 * the withering and the poison simply bite [CorpseCollectorLore.decayAcceleration] times weaker,
 * frost does not bite at all. Only the frost overlay and slowdown stay a tick's work — vanilla has
 * no event for freezing, and `canFreeze` cannot be overridden on a player without a mixin.
 */
class CadavericResilience :
    PermanentPassiveAbility(AbilityIdentity.of("death_cadaveric_resilience")),
    Ticking {

    override val tickPeriodTicks: Int = 5

    override val displayName: Component = Component.literal("Стійкість до трупних аур")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Тіло трупозбирача холодне й напівмертве, тож трупні аури беруть його погано.\n" +
            "▪ Мороз не намерзає й не ранить\n" +
            "▪ Тління й отрута шкодять у ${"%.1f".format(CorpseCollectorLore.decayAcceleration(sequence))} раза слабше",
    )

    override fun onActivate(context: AbilityContext) {
        val casterId = context.caster.uuid
        val beyonders = context.beyonders
        context.events.on<IncomingDamage> { damage ->
            val victim = damage.victim
            if (victim.uuid != casterId) return@on
            val source = damage.source
            when {
                source.`is`(DamageTypes.FREEZE) -> damage.cancel()
                source.`is`(DamageTypes.WITHER) || isPoison(damage) -> {
                    val sequence = beyonders.of(casterId)?.sequence ?: return@on
                    damage.scale(1.0 / CorpseCollectorLore.decayAcceleration(sequence))
                }
            }
        }
    }

    override fun tick(context: AbilityContext) {
        val player = context.caster
        if (player.ticksFrozen <= 0) return
        player.ticksFrozen = 0
        context.level.sendParticles(ParticleTypes.SNOWFLAKE, player.x, player.y + 1.0, player.z, 4, 0.3, 0.5, 0.3, 0.0)
    }

    /** Poison has no damage type of its own: it is plain magic with nobody behind it. */
    private fun isPoison(damage: IncomingDamage): Boolean =
        damage.source.`is`(DamageTypes.MAGIC) && damage.attacker == null && damage.victim.hasEffect(MobEffects.POISON)
}
