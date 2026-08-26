package me.vangoo.domain.spells

import java.util.Locale

/**
 * Єдине місце формату згенерованого заклинання: [SpellRecipe] ↔ рядок-ID + людський опис.
 *
 * Кодек чистий (0 Minecraft), бо рецепт оперує лише примітивами та доменними enum-ами.
 *
 * Дві відмінності від плагіна, обидві дозволені рішенням «збережень не існує — міграції немає»:
 *  1. Слот частинки (поз. 1 у старому форматі) прибрано. Він був похідним від типу, ігнорувався
 *     при читанні й тягнув у домен таблицю назв частинок Bukkit.
 *  2. Назва більше не проходить через `_`→пробіл при читанні. У плагіні кодування не робило
 *     зворотної заміни, тож будь-яке підкреслення в назві губилося при round-trip.
 */
object SpellCodec {

    private const val PREFIX = "generated:"
    private const val DELIMITER = "|"
    private const val FIELD_COUNT = 10

    fun isSerializedSpell(abilityId: String?): Boolean =
        abilityId != null && abilityId.lowercase(Locale.US).startsWith(PREFIX)

    fun encode(r: SpellRecipe): String = listOf(
        r.shape.name,
        format1(r.damage),
        format1(r.radius),
        format1(r.heal),
        r.durationTicks.toString(),
        r.buff?.name ?: "NONE",
        r.buffAmplifier.toString(),
        r.spiritualityCost.toString(),
        r.cooldownSeconds.toString(),
        sanitizeName(r.name),
    ).joinToString(DELIMITER, prefix = PREFIX)

    fun decode(serializedId: String): SpellRecipe {
        require(isSerializedSpell(serializedId)) { "Not a serialized spell: $serializedId" }
        val parts = serializedId.substring(PREFIX.length).split(DELIMITER)
        require(parts.size == FIELD_COUNT) { "Invalid spell data format: $serializedId" }

        return SpellRecipe(
            name = parts[9],
            shape = SpellRecipe.Shape.valueOf(parts[0].uppercase(Locale.US)),
            damage = parseDouble(parts[1]),
            radius = parseDouble(parts[2]),
            heal = parseDouble(parts[3]),
            durationTicks = parts[4].toInt(),
            buff = parseBuff(parts[5]),
            buffAmplifier = parts[6].toInt(),
            spiritualityCost = parts[7].toInt(),
            cooldownSeconds = parts[8].toInt(),
        )
    }

    /**
     * Людський опис рецепта (колишній `generateDescription`) — пари «підпис → значення».
     * Кольори й порядок рядків обирає шар представлення; домен їх не знає.
     * Кулдаун і вартість додає система, тут їх навмисно немає.
     */
    fun describe(r: SpellRecipe): List<Pair<String, String>> = buildList {
        add("Тип" to typeNameUkrainian(r.shape))
        if (r.dealsDamage) add("Шкода" to format1(r.damage))
        if (r.radius > 0) add("Радіус" to "${format1(r.radius)}м")
        if (r.heals) add("Зцілення" to format1(r.heal))
    }

    private fun parseBuff(raw: String): SpellRecipe.Buff? {
        val upper = raw.uppercase(Locale.US)
        if (upper == "NONE") return null
        return SpellRecipe.Buff.entries.firstOrNull { it.name == upper }
    }

    private fun typeNameUkrainian(shape: SpellRecipe.Shape): String = when (shape) {
        SpellRecipe.Shape.PROJECTILE -> "Промінь"
        SpellRecipe.Shape.AOE -> "Вибух"
        SpellRecipe.Shape.TELEPORT -> "Телепорт"
        SpellRecipe.Shape.SELF -> "Зцілення"
        SpellRecipe.Shape.BUFF -> "Посилення"
    }

    private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** Кома як десятковий роздільник трапляється в старих локалізованих записах. */
    private fun parseDouble(raw: String): Double = raw.replace(",", ".").toDouble()

    private fun sanitizeName(name: String): String = name.replace(DELIMITER, "").replace("\n", " ")
}
