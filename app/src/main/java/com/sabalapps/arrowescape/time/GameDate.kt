package com.sabalapps.arrowescape.time

/**
 * A calendar day, with no clock, no zone and no time of day.
 *
 * ## Why not `LocalDate`
 *
 * `java.time` arrived on Android at API 26 and this app ships to 23, so
 * `LocalDate` would mean turning on core library desugaring and taking a
 * dependency for three fields and one subtraction. What the Daily Challenge
 * actually needs from a date is small and exactly specified: a stable key to
 * seed from, and whether two days are next to each other. Both are
 * [epochDay] arithmetic, which is forty lines that run the same on every API
 * level and unit test without an Android runtime.
 *
 * The conversion is the standard proleptic Gregorian days-from-civil algorithm:
 * it shifts the year so that leap days land at the end of a 400-year era, which
 * removes every special case from the arithmetic. It agrees with `LocalDate`
 * for every date this app will ever see; `GameDateTest` checks that against
 * known values and round-trips several centuries of days.
 */
data class GameDate(val year: Int, val month: Int, val day: Int) {

    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
        require(day in 1..31) { "day must be 1..31, was $day" }
    }

    /**
     * `YYYY-MM-DD`. This is the string the daily seed is hashed from and the
     * string that gets persisted, so its format is part of the save format and
     * must not change without a [com.sabalapps.arrowescape.daily.DailyChallenge.GENERATOR_VERSION]
     * bump.
     */
    val iso: String
        get() = "%04d-%02d-%02d".format(year, month, day)

    /** Days since 1970-01-01. Negative before it. */
    val epochDay: Long
        get() {
            // Shift so March is month 0: a leap day is then the last day of the
            // year rather than one stuck in the middle of it.
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yearOfEra = y - era * 400 // 0..399
            val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365L + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097L + dayOfEra - 719_468L
        }

    /** True when [other] is the calendar day immediately before this one. */
    fun isDayAfter(other: GameDate): Boolean = epochDay - other.epochDay == 1L

    /** This date shifted by [days]. Used by tests and by the debug day-skip. */
    fun plusDays(days: Long): GameDate = fromEpochDay(epochDay + days)

    /** Something to put on a card: "Saturday, 4 October". */
    fun friendly(): String = "${dayOfWeekName()}, $day ${monthName()}"

    /** 0 = Monday, as ISO numbers them. */
    val dayOfWeek: Int
        get() = ((epochDay + 3) % 7 + 7).toInt() % 7

    private fun dayOfWeekName(): String = DAY_NAMES[dayOfWeek]

    private fun monthName(): String = MONTH_NAMES[month - 1]

    override fun toString(): String = iso

    companion object {
        private val DAY_NAMES = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        )
        private val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        /** The inverse of [epochDay]. */
        fun fromEpochDay(epochDay: Long): GameDate {
            val z = epochDay + 719_468L
            val era = (if (z >= 0) z else z - 146_096L) / 146_097L
            val dayOfEra = z - era * 146_097L // 0..146096
            val yearOfEra =
                (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365 // 0..399
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153 // 0..11, 0 = March
            val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
            val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
            val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
            return GameDate(year = year.toInt(), month = month, day = day)
        }

        /**
         * Parses the [iso] form back. Null for anything that is not exactly
         * `YYYY-MM-DD` describing a real day — which is what makes a corrupt or
         * hand-edited save safe to read.
         */
        fun parse(raw: String?): GameDate? {
            if (raw == null || raw.length != 10) return null
            if (raw[4] != '-' || raw[7] != '-') return null
            val year = raw.substring(0, 4).toIntOrNull() ?: return null
            val month = raw.substring(5, 7).toIntOrNull() ?: return null
            val day = raw.substring(8, 10).toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            val date = GameDate(year, month, day)
            // 31 February parses as three fields but is not a day; the
            // round-trip is what catches it.
            return date.takeIf { fromEpochDay(it.epochDay) == it }
        }
    }
}
