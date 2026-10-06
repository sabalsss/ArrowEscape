package com.sabalapps.arrowescape.time

import java.util.Calendar

/**
 * Where "today" comes from.
 *
 * Every date-dependent rule in the app — which Daily Challenge is on offer,
 * whether a streak continues or restarts — reads the date through one of these
 * rather than calling the clock itself. That is the whole reason the interface
 * exists: streak logic is only testable if a test can say "it is now tomorrow",
 * and scattered clock calls cannot be told that.
 *
 * ## On device clocks
 *
 * The app is offline and there is no server to ask, so the date is whatever the
 * device says it is. A player who moves their clock forward can therefore run up
 * a daily streak they did not earn. That is a deliberate, accepted trade for
 * this build: the streak is a private number on the player's own device with
 * nothing staked on it, and the alternative — a trusted time source — means a
 * network dependency and an account, both of which Phase 5 explicitly does not
 * add. If a leaderboard ever makes the number worth cheating for, the fix is a
 * server-signed date behind this same interface, and nothing above it changes.
 */
interface DateProvider {
    fun today(): GameDate
}

/**
 * The device's local calendar day. [Calendar] rather than `java.time` because
 * the minimum SDK is 23; see [GameDate].
 */
class SystemDateProvider : DateProvider {
    override fun today(): GameDate {
        val calendar = Calendar.getInstance()
        return GameDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1, // Calendar months are 0-based
            day = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}

/** A clock a test can move. Production never builds one. */
class FixedDateProvider(var date: GameDate) : DateProvider {
    override fun today(): GameDate = date

    /** Moves the fake clock on, for "and then it was tomorrow" tests. */
    fun advance(days: Long = 1) {
        date = date.plusDays(days)
    }
}
