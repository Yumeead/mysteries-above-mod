package me.vangoo.domain.rituals

/** The catalogue of seven rituals. The gate: Seq. 9 — 3 rituals, 8 — 5, 7 — all 7. */
object RitualCatalog {

    val ALL: List<RitualRecipe> = listOf(
        RitualRecipe(
            RitualType.LUCK_PRAYER, "Молитва удачі",
            "Прихильність долі на кілька хвилин; зірваний ритуал накличе невдачу",
            9, 3, mapOf("GOLD_INGOT" to 1), false,
        ),
        RitualRecipe(
            RitualType.SANCTIFICATION, "Освячення предмета",
            "Трохи відновлює міцність предмета в головній руці",
            9, 3, mapOf("IRON_INGOT" to 15), false,
        ),
        RitualRecipe(
            RitualType.SACRIFICE, "Жертвоприношення",
            "Спаліть предмет із головної руки — відновіть духовність",
            9, 3, emptyMap(), true,
        ),
        RitualRecipe(
            RitualType.BESTOWMENT, "Ритуал одкровення",
            "Жертва — інгредієнт ЧУЖОГО шляху наступної послідовності; " +
                "у відповідь сутності підкажуть, де шукати ваші",
            8, 3, emptyMap(), true,
        ),
        RitualRecipe(
            RitualType.MEDIUMSHIP, "Спіритизм",
            "Духи розкажуть про минулі події поблизу",
            8, 3, mapOf("BONE" to 1), false,
        ),
        RitualRecipe(
            RitualType.MIRROR_DIVINATION, "Дзеркальне ворожіння",
            "Покаже, хто діяв у цьому місці останнім часом",
            7, 5, mapOf("AMETHYST_SHARD" to 1), false,
        ),
        RitualRecipe(
            RitualType.SPIRIT_WALL, "Стіна духовності",
            "Захисна зона навколо вівтаря на пів хвилини",
            7, 5, mapOf("LAPIS_LAZULI" to 1), false,
        ),
    )

    fun availableFor(sequenceLevel: Int): List<RitualRecipe> = ALL.filter { it.availableAt(sequenceLevel) }

    fun of(type: RitualType): RitualRecipe =
        ALL.first { it.type == type }
}
