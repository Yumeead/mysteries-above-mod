package me.vangoo.domain.creatures

/**
 * A town-dweller's day: where a living person should be at a given hour.
 *
 * The time is Minecraft's time of day — `0` is sunrise (6:00), `6000` noon, `12000` sunset,
 * `18000` midnight. The hours are close to a vanilla villager's, because a city that wakes, works
 * and sleeps on the same clock as its villagers reads as one world rather than two.
 */
object DailyRoutine {

    enum class Activity {
        /** Indoors at home, awake: the morning before work and the evening before bed. */
        HOME,

        /** At the workplace — a workstation, a market stall, a post. */
        WORK,

        /** Out among people: the square, the bell, a neighbour's door. */
        GATHER,

        /** In bed. */
        SLEEP,

        /** A guard on their rounds. */
        PATROL,
    }

    fun citizen(timeOfDay: Long): Activity = when (Math.floorMod(timeOfDay, DAY)) {
        in 0L until WORK_STARTS -> Activity.HOME
        in WORK_STARTS until GATHER_STARTS -> Activity.WORK
        in GATHER_STARTS until HOME_STARTS -> Activity.GATHER
        in HOME_STARTS until SLEEP_STARTS -> Activity.HOME
        else -> Activity.SLEEP
    }

    /**
     * A guard patrols through the day; at night only the night watch stays on its feet, the rest
     * sleep. A city with no one awake after dark is a city anyone can walk into.
     */
    fun guard(timeOfDay: Long, nightWatch: Boolean): Activity = when (citizen(timeOfDay)) {
        Activity.SLEEP -> if (nightWatch) Activity.PATROL else Activity.SLEEP
        Activity.HOME -> if (nightWatch) Activity.HOME else Activity.PATROL
        else -> Activity.PATROL
    }

    const val DAY = 24_000L
    const val WORK_STARTS = 1_000L
    const val GATHER_STARTS = 9_000L
    const val HOME_STARTS = 11_000L
    const val SLEEP_STARTS = 12_500L
}
