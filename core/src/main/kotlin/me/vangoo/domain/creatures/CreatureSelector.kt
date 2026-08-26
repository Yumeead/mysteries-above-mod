package me.vangoo.domain.creatures

/**
 * Чисте правило вибору істоти для спавну (аналог `BrewMatcher`). Детерміноване при поданому roll.
 * Кожен кандидат займає сегмент `[cumulative, cumulative+chance)` на осі [0,1); roll за сумою
 * всіх шансів означає «спавну немає».
 */
class CreatureSelector(creatures: Collection<CreatureDefinition>) {

    private val creatures: List<CreatureDefinition> = creatures.toList()

    fun pickForBiome(
        biome: String?,
        baseEntityType: String?,
        roll: Double,
        bias: ConvergenceBias? = null,
    ): CreatureDefinition? {
        val candidates = creatures.filter {
            it.spawn.naturalChance > 0.0 &&
                biome in it.spawn.naturalBiomes &&
                baseEntityType in it.spawn.naturalReplaces
        }
        val totalChance = candidates.sumOf { it.spawn.naturalChance }
        if (candidates.isEmpty() || totalChance <= 0.0) return null

        val weights = candidates.map { it.spawn.naturalChance * multiplier(it, bias) }
        val sumWeights = weights.sum()
        // Перенормовуємо так, щоб сегменти давали ту саму суму totalChance — P(спавну) незмінна.
        val scale = if (sumWeights > 0.0) totalChance / sumWeights else 0.0

        var cumulative = 0.0
        for (i in candidates.indices) {
            cumulative += weights[i] * scale
            if (roll < cumulative) return candidates[i]
        }
        return null
    }

    /**
     * Вибір істоти для ambient-спавну: серед усіх істот, чий natural-біом збігається з поточним,
     * незалежно від шляху. Закон конвергенції лишається ВАГОВИМ ухилом (свій шлях і «наступна
     * потрібна» послідовність важать більше через [multiplier]), а не жорстким фільтром.
     * Без «порогу неспавну» — рішення «спавнити» вже прийняв планувальник; цей метод лише обирає,
     * КОГО. [roll] у [0,1).
     */
    fun pickForAmbient(biome: String?, bias: ConvergenceBias?, roll: Double): CreatureDefinition? {
        if (bias == null) return null
        val candidates = creatures.filter { it.spawn.naturalChance > 0.0 && biome in it.spawn.naturalBiomes }
        return weightedPick(candidates, { it.spawn.naturalChance }, bias, roll)
    }

    /**
     * Вибір істоти для структурного спавну — тобто «скриню відкрито». Ключі лут-таблиць у виборі
     * НЕ беруть участі: працює будь-яка структура (ванільна, датапакова, модова), бо інакше
     * ванільні скрині не давали нікого, крім блукаючого духа.
     *
     * Пул — вікно з ДВОХ послідовностей навколо того, хто відкрив: його власна і наступна,
     * сильніша (`seq` і `seq - 1`). Посл. 9 бачить 9–8, Посл. 8 — 8–7, …, Посл. 6 — 6–5. Це
     * водночас замінює [ApexGate] на цьому шляху: Посл. 5 доступна лише гравцеві Посл. 6 або 5,
     * тобто рівно те саме правило, тільки виражене вікном.
     *
     * Рішення «чи спавнити взагалі» приймає не цей метод, а виклична сторона за конфігом — тут
     * лише КОГО, з ухилом Закону Конвергенції, як в ambient. [roll] у [0,1).
     */
    fun pickForStructure(playerSequence: Int, bias: ConvergenceBias?, roll: Double): CreatureDefinition? {
        val candidates = creatures.filter {
            it.spawn.structureChance > 0.0 &&
                (it.sequence == playerSequence || it.sequence == playerSequence - 1)
        }
        return weightedPick(candidates, { it.spawn.structureChance }, bias, roll)
    }

    private fun multiplier(def: CreatureDefinition, bias: ConvergenceBias?): Double {
        val biasPathway = bias?.pathway ?: return 1.0
        if (def.pathway == null || !def.pathway.equals(biasPathway, ignoreCase = true)) return 1.0
        return when (def.sequence) {
            bias.sequenceLevel - 1 -> NEXT_NEEDED_WEIGHT
            bias.sequenceLevel -> CURRENT_WEIGHT
            else -> 1.0
        }
    }

    /**
     * Ваговий вибір без «порогу неспавну»: рішення «спавнити» вже прийнято, лишилось обрати кого.
     * Спільний для ambient і структур — різняться вони лише пулом і полем-вагою.
     */
    private fun weightedPick(
        candidates: List<CreatureDefinition>,
        weightOf: (CreatureDefinition) -> Double,
        bias: ConvergenceBias?,
        roll: Double,
    ): CreatureDefinition? {
        if (candidates.isEmpty()) return null

        val weights = candidates.map { weightOf(it) * multiplier(it, bias) }
        val sumWeights = weights.sum()
        if (sumWeights <= 0.0) return null

        val target = roll * sumWeights
        var cumulative = 0.0
        for (i in candidates.indices) {
            cumulative += weights[i]
            if (target < cumulative) return candidates[i]
        }
        return candidates.last()
    }

    private companion object {
        const val NEXT_NEEDED_WEIGHT = 4.0 // sequenceLevel - 1
        const val CURRENT_WEIGHT = 2.0 // sequenceLevel
    }
}
