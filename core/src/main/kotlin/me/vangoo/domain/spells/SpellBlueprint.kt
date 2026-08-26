package me.vangoo.domain.spells

/**
 * Те, що гравець «накликав» у конструкторі заклинань: вибір типу + рівні покращень (0–5).
 * Чистий запис без Minecraft — екран лише збирає його, а доменна математика
 * ([SpellRecipe.fromBlueprint]) перетворює його на готовий рецепт.
 */
data class SpellBlueprint(
    val name: String,
    val shape: SpellRecipe.Shape,
    /** Шкода (PROJECTILE/AOE) */
    val powerLvl: Int,
    /** Радіус / Дальність / Тривалість бафа */
    val areaLvl: Int,
    /** Зцілення (SELF) / Рівень ефекту (BUFF) */
    val healLvl: Int,
    /** Зменшення кулдауну */
    val cooldownLvl: Int,
    /** Оптимізація витрат духовності */
    val costReductionLvl: Int,
    /** лише для shape == BUFF */
    val buff: SpellRecipe.Buff?,
)
