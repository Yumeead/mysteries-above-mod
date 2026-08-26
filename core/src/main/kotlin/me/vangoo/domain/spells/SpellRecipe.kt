package me.vangoo.domain.spells

import kotlin.math.max

/**
 * Рецепт згенерованого заклинання — ЛИШЕ дані + правила балансу.
 *
 * Жодного Minecraft: ні частинок, ні типів ефектів, ні позицій. Саме це серіалізується
 * ([SpellCodec]), валідовується і тестується без сервера. Хореографія ефектів (партикли,
 * урон, телепорт) живе окремо, у шарі поведінки.
 */
data class SpellRecipe(
    val name: String,
    val shape: Shape,
    val damage: Double,
    val radius: Double,
    val heal: Double,
    val durationTicks: Int,
    /** null, якщо [shape] != [Shape.BUFF] */
    val buff: Buff?,
    val buffAmplifier: Int,
    val spiritualityCost: Int,
    val cooldownSeconds: Int,
) {
    enum class Shape { PROJECTILE, AOE, SELF, BUFF, TELEPORT }

    /** Доменне представлення баффів — НЕ тип ефекту Minecraft. Маппінг живе в раннері. */
    enum class Buff { SPEED, STRENGTH, RESISTANCE, REGENERATION, FIRE_RESISTANCE }

    init {
        require(name.isNotBlank()) { "Spell name cannot be blank" }
        require(spiritualityCost >= 0 && cooldownSeconds >= 0) { "Cost/cooldown cannot be negative" }
        require(shape != Shape.BUFF || buff != null) { "BUFF spell requires a buff" }
    }

    val dealsDamage: Boolean get() = damage > 0

    val heals: Boolean get() = heal > 0

    companion object {
        /**
         * Будує рецепт із вибору гравця в конструкторі заклинань.
         * Це колишній `Spellcasting.calculateStats` — тепер чиста доменна функція,
         * яку можна покрити юніт-тестами без сервера.
         */
        fun fromBlueprint(b: SpellBlueprint): SpellRecipe {
            var cooldown = max(1, 10 - b.cooldownLvl)
            var damage = 0.0
            var radius = 0.0
            var heal = 0.0
            var durationTicks = 0
            var amplifier = 0
            var buff: Buff? = null
            var cost: Int

            when (b.shape) {
                Shape.PROJECTILE -> {
                    damage = 5 + b.powerLvl * 2.5
                    cost = 25 + b.powerLvl * 5
                }

                Shape.AOE -> {
                    damage = 4 + b.powerLvl * 2.0
                    radius = 3 + b.areaLvl * 1.5
                    cost = 40 + b.powerLvl * 5 + b.areaLvl * 5
                    cooldown += 5
                }

                Shape.TELEPORT -> {
                    radius = (10 + b.areaLvl * 5).toDouble()
                    cost = 30 + b.areaLvl * 3
                }

                Shape.SELF -> {
                    heal = 4 + b.healLvl * 2.0
                    cost = 40 + b.healLvl * 8
                    cooldown += 10
                }

                Shape.BUFF -> {
                    durationTicks = 100 + b.areaLvl * 40
                    amplifier = b.healLvl
                    buff = b.buff
                    cost = 50 + b.areaLvl * 5 + b.healLvl * 20
                    cooldown += 30
                }
            }

            cost = max(5, cost - b.costReductionLvl * 5)
            return SpellRecipe(
                b.name, b.shape, damage, radius, heal,
                durationTicks, buff, amplifier, cost, cooldown,
            )
        }
    }
}
