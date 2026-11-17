package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.pathways.justiciar.clang
import me.vangoo.mysteries.pathways.justiciar.lash
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Justiciar, Seq. 7 (Interrogator): **Psychic Lashing**.
 *
 * As the novel has it: the Interrogator coats a cold weapon in bolts of illusory lightning, and for a while it
 * strikes the Spirit Body as well as the flesh. Lightning crawls over the weapon in hand for [CHARGE_TICKS]
 * (seen by everyone); every melee blow in that time adds [SPIRIT_DAMAGE] and an arc that jumps from the blade
 * onto the one struck.
 *
 * The plugin threw a bolt that hopped from target to target. That was a ranged power standing in for this
 * one; the lore's power is a weapon that is suddenly worth fearing up close.
 */
class PsychicLashing : ActiveAbility(AbilityIdentity.of("justiciar_psychic_lashing")) {

    override val displayName: Component = Component.literal("Психічне Шмагання")

    override val spiritualityCost: Int = 75

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Зброя в руці вкривається ілюзорними блискавками на ${CHARGE_TICKS / 20} с.\n" +
            "▪ Кожен удар нею б'є й по Духовному Тілу: ще $SPIRIT_DAMAGE шкоди\n" +
            "▪ Потрібна зброя в головній руці",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        if (caster.mainHandItem.isEmpty) return AbilityOutcome.failed("Візьміть у руку зброю")

        val level = context.level
        val color = context.effects.pathwayColor
        lash(level, caster, caster, WorldEffectPayload.Lash.Shape.COAT, color)
        level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4f, 1.8f)

        context.events.on<IncomingDamage>(CHARGE_TICKS) { damage ->
            // A blow of the hand that holds the weapon: not an arrow, not this power's own damage.
            if (damage.attacker !== caster || damage.directCause !== caster || !damage.source.`is`(DamageTypes.PLAYER_ATTACK)) return@on
            val target = damage.victim
            damage.amount += SPIRIT_DAMAGE
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 10, 2))
            lash(level, caster, target, WorldEffectPayload.Lash.Shape.BOLT, color)
            clang(level, target.position(), 1.6f)
        }
        return AbilityOutcome.Done()
    }

    private companion object {
        val CHARGE_TICKS = WorldEffectPayload.Lash.Shape.COAT.life
        const val SPIRIT_DAMAGE = 5.0f
        const val COOLDOWN_SECONDS = 20
    }
}
