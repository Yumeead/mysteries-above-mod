package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.Restraints
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import me.vangoo.mysteries.pathways.justiciar.bonds
import me.vangoo.mysteries.pathways.justiciar.clang
import me.vangoo.mysteries.pathways.justiciar.ratchet
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity

/**
 * Justiciar, Seq. 7 (Interrogator): **Brand of Restraint**.
 *
 * Three chains break out of the ground one after another and coil round the body (ankles, belt and arms,
 * chest), red-hot, and snap tight; shackles close on the ankles, and on a Beyonder an iron ring settles on the
 * head. The iron cools, stands while the term lasts, and falls apart at the end. For as long the victim is bound
 * ([Restraints]): the game refuses them every step, jump, item and power. Nothing in chat: the chains are the
 * notice.
 */
class BrandOfRestraint : ActiveAbility(AbilityIdentity.of("justiciar_brand_of_restraint")) {

    override val displayName: Component = Component.literal("Клеймо Обмеження")

    override val spiritualityCost: Int = 100

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Розпечені ланцюги виривають із землі й скручують ворога під прицілом (дальність $RANGE бл.).\n" +
            "▪ Ціль скута на ${DURATION_TICKS / 20} с: не ходить, не стрибає, не користується ні предметами, ні здібностями\n" +
            "▪ Потойбічному на голову лягає ще й залізний обруч",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Наведіть на ціль, щоб накласти клеймо")

        val beyonder = context.beyonders.of(target.uuid) != null
        bonds(context.level, target, context.effects.pathwayColor, DURATION_TICKS, crown = beyonder)
        // One ratchet for each chain as it snaps tight, and iron on iron for the ring on the head.
        val at = target.position()
        repeat(Edict.BOND_CHAINS) { chain ->
            context.scheduling.after(Edict.bondSnap(chain)) { ratchet(context.level, at, 0.9f + 0.1f * chain) }
        }
        if (beyonder) context.scheduling.after(Edict.CROWN_SNAP) { clang(context.level, at) }

        Restraints.hold(target, DURATION_TICKS)
        return AbilityOutcome.Done()
    }

    private companion object {
        const val RANGE = 15.0
        const val DURATION_TICKS = 6 * 20
        const val COOLDOWN_SECONDS = 25
    }
}
