package me.vangoo.domain.spells

/**
 * What the player "called up" in the spell builder: the type choice + the upgrade levels (0–5).
 * A pure record with no Minecraft — the screen only assembles it, and the domain math
 * ([SpellRecipe.fromBlueprint]) turns it into a finished recipe.
 */
data class SpellBlueprint(
    val name: String,
    val shape: SpellRecipe.Shape,
    /** Damage (PROJECTILE/AOE) */
    val powerLvl: Int,
    /** Radius / Range / Buff duration */
    val areaLvl: Int,
    /** Healing (SELF) / Effect level (BUFF) */
    val healLvl: Int,
    /** Cooldown reduction */
    val cooldownLvl: Int,
    /** Spirituality cost optimisation */
    val costReductionLvl: Int,
    /** only for shape == BUFF */
    val buff: SpellRecipe.Buff?,
)
