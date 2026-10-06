package com.sabalapps.arrowescape.daily

import com.sabalapps.arrowescape.time.GameDate

/**
 * The Daily Challenge counters. Entirely local, like every other number in this
 * app — there is no account and nothing leaves the device.
 *
 * [lastCompletedDate] is the whole of the streak's memory. Everything else is
 * derived from it when a day is recorded, which is what makes "the same day
 * twice does not count twice" a property of the data rather than a rule the
 * callers have to remember.
 */
data class DailyProgress(
    /** The last day whose puzzle was cleared, or null on a fresh install. */
    val lastCompletedDate: GameDate? = null,
    /** Days cleared back to back, counting [lastCompletedDate]. */
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCompleted: Int = 0
) {
    /** True when [date]'s puzzle is already cleared. Drives Home's card. */
    fun isCompletedOn(date: GameDate): Boolean = lastCompletedDate == date

    /**
     * This progress with [date] recorded as cleared.
     *
     * The three cases are the whole streak rule:
     *
     *  * **The same day again.** Nothing changes — not the streak, not the
     *    total. Replaying a daily is a replay; the day was already counted.
     *  * **The next calendar day.** The streak extends.
     *  * **Anything else** — a gap, a first ever daily, or a clock that moved
     *    backwards — starts a new streak at 1.
     */
    fun completing(date: GameDate): DailyProgress {
        if (lastCompletedDate == date) return this
        val streak = if (lastCompletedDate != null && date.isDayAfter(lastCompletedDate)) {
            currentStreak + 1
        } else {
            1
        }
        return copy(
            lastCompletedDate = date,
            currentStreak = streak,
            bestStreak = maxOf(bestStreak, streak),
            totalCompleted = totalCompleted + 1
        )
    }

    companion object {
        /**
         * A ceiling on the stored counters, used to sanity-check a save. Far
         * past anything reachable by playing a puzzle a day; it exists so a
         * corrupt value cannot be mistaken for progress.
         */
        const val MAX_COUNT = 1_000_000
    }
}

/**
 * A Daily Challenge the player is part-way through.
 *
 * [date] is the puzzle's identity: the seed is derived from it, so the board is
 * rebuilt rather than stored. [seed] is written alongside anyway, as a
 * cross-check — if the seed a date derives to today is not the seed that was
 * saved, the scheme version has moved under the save and the board the player
 * was looking at no longer exists, so it is dropped rather than replaced with a
 * different board under the same date.
 */
data class DailySavedGame(
    val date: GameDate,
    val seed: Long,
    val remainingArrowIds: Set<Int>,
    val lives: Int
)
