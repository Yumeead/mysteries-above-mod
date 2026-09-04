package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.mysteries.ability.AbilityContext

/**
 * The shared contract of the "hold plain paper, right-click to throw" family.
 *
 * [PaperCutter] (Seq. 8, passive) and [PaperWeaponry] (Seq. 7, its active evolution) implement
 * this and share one [me.vangoo.domain.valueobjects.AbilityIdentity] — a Beyonder has exactly
 * one of them at a time. [me.vangoo.mysteries.pathways.fool.PaperThrowTrigger] finds whichever
 * one a Beyonder currently owns and throws through it.
 */
interface PaperThrower {

    /**
     * Throws one paper dagger toward whatever the caster is looking at. Consumes one paper from
     * the main hand. Costs nothing, has its own short cooldown.
     *
     * @return `true` if a throw actually happened (so the caller can swallow the vanilla
     * right-click instead of, say, opening the book the paper would otherwise place pages into).
     */
    fun throwPaper(context: AbilityContext): Boolean
}
