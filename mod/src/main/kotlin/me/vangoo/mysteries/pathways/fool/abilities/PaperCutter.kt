package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.PaperThrowDamage
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import net.minecraft.network.chat.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Fool, Seq. 8 (Clown): **Paper Cutter**.
 *
 * A free, passive throw: hold plain paper, right-click, and it flies as a dagger.
 * [me.vangoo.mysteries.pathways.fool.PaperThrowTrigger] catches the right-click and finds
 * whichever [PaperThrower] the caster currently owns — this one below Seq. 7, [PaperWeaponry]
 * (which shares [IDENTITY]) at and above it.
 */
class PaperCutter : PermanentPassiveAbility(IDENTITY), PaperThrower {

    override val displayName: Component = Component.literal("Паперовий різак")

    private val lastThrowTick = ConcurrentHashMap<UUID, Long>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Тримаючи звичайний папір, кидайте його (ПКМ) — паперовий кинджал завдає " +
            "${PaperThrowDamage.damageFor(sequence)} шкоди.\n" +
            "▪ Безкоштовно, відкат 0.5 с",
    )

    override fun throwPaper(context: AbilityContext): Boolean = PaperThrows.throwOnce(context, lastThrowTick)

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("fool_paper_cutter")
    }
}
