package com.sabalapps.arrowescape.notification

import com.sabalapps.arrowescape.time.GameDate
import java.util.Calendar
import java.util.TimeZone

/**
 * When the daily reminder fires, and when it may not. The single home of the reminder's
 * time of day: nothing else in the app knows what time it is.
 *
 * Pure — a clock and a zone go in, a number comes out — so every rule is a plain JVM test,
 * including the ones about midnight, time zones and daylight-saving changes.
 */
object ReminderSchedule {

    /**
     * The local time of day the reminder is aimed at: 6pm.
     *
     * Evening on purpose. The Daily Challenge is ready from midnight, and a player who has
     * played it by evening (the common case for anyone who opens the game) is not reminded
     * at all; the nudge is for the player who has not, at a time they have leisure to act on
     * it. Changing the hour is changing this line and nothing else.
     */
    const val HOUR = 18
    const val MINUTE = 0

    /**
     * How late a reminder may fire and still be sent. Power-saving can hold work back by
     * hours; a reminder that arrives at 11pm, or when the phone is switched on the next
     * morning, is no longer the one that was asked for, so it is dropped and tomorrow's is
     * on schedule.
     */
    const val MAX_LATE_MINUTES = 4 * 60

    /** The next instant, strictly after [nowMs], at which the local clock in [zone] reads the reminder's time. */
    fun nextTriggerAtMs(nowMs: Long, zone: TimeZone, hour: Int = HOUR, minute: Int = MINUTE): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = nowMs
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // "Tomorrow at 6pm" is calendar arithmetic, not "+24 hours": across a daylight-saving
        // change the two differ by an hour, and the clock on the wall is what the player reads.
        if (calendar.timeInMillis <= nowMs) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    /** How long to wait from [nowMs] for the next reminder. Always positive. */
    fun delayUntilNextMs(nowMs: Long, zone: TimeZone): Long =
        (nextTriggerAtMs(nowMs, zone) - nowMs).coerceAtLeast(1L)

    /** Minutes since the reminder's time on [nowMs]'s local day; negative before it. */
    fun minutesPastReminderTime(nowMs: Long, zone: TimeZone): Int {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = nowMs
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return minuteOfDay - (HOUR * 60 + MINUTE)
    }
}

/** Whether a reminder that has just fired should actually be posted. */
object ReminderPolicy {

    fun shouldPost(
        preference: ReminderPreference,
        dailyCompletedToday: Boolean,
        today: GameDate,
        nowMs: Long,
        zone: TimeZone
    ): Boolean {
        // Switched off: nothing to say. (The work is cancelled when the switch goes off, so
        // this is only the last line of defence against a run that was already in flight.)
        if (!preference.enabled) return false
        // The reminder is about today's mystery; if it is solved, there is nothing to remind.
        if (dailyCompletedToday) return false
        // One a day, however the work underneath was retried or re-planned.
        if (preference.lastNotifiedDate == today) return false
        // Too early or too late is not "the reminder", it is a stray run.
        val past = ReminderSchedule.minutesPastReminderTime(nowMs, zone)
        return past in -EARLY_TOLERANCE_MINUTES..ReminderSchedule.MAX_LATE_MINUTES
    }

    /** Work is allowed to wake a little early; it is never meant to. */
    private const val EARLY_TOLERANCE_MINUTES = 5
}
