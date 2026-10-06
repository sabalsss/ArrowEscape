package com.sabalapps.arrowescape.notification

import java.util.TimeZone

/**
 * What schedules the next reminder. An interface so the rules around it — switching on,
 * switching off, relaunching, never doubling up — are tested without an Android runtime;
 * the real one is [WorkManagerReminderScheduler].
 */
interface ReminderScheduler {
    /**
     * Plans exactly one reminder, [delayMs] from now, replacing any that was planned before.
     * Calling it twice leaves one reminder, not two.
     */
    fun scheduleNext(delayMs: Long)

    /** Removes whatever was planned. A no-op when nothing was. */
    fun cancel()
}

/**
 * Keeps the schedule and the preference in step. The only thing that turns the reminder on
 * or off, and the only thing the worker, the settings switch and app start all go through.
 */
class ReminderController(
    private val preferences: ReminderPreferenceRepository,
    private val scheduler: ReminderScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = { TimeZone.getDefault() }
) {
    val preference get() = preferences.preference

    /** The Daily Reminder switch, on. Replaces whatever was scheduled, so turning it on twice is harmless. */
    fun enable() {
        preferences.setEnabled(true)
        planNext()
    }

    /** The Daily Reminder switch, off. Nothing is left scheduled. */
    fun disable() {
        preferences.setEnabled(false)
        scheduler.cancel()
    }

    /**
     * App start. Re-plans the reminder against the *current* clock and zone — which is what
     * lets a player who has travelled, or moved their clock, get the reminder at the right
     * local time again — or makes sure nothing is left planned if it is off (a restored
     * backup can carry a schedule the new device never made).
     */
    fun syncOnStartup() {
        if (preferences.preference.value.enabled) planNext() else scheduler.cancel()
    }

    /** The reminder fired (or was skipped): line up tomorrow's, if the player still wants one. */
    fun planNextIfEnabled() {
        if (preferences.preference.value.enabled) planNext()
    }

    private fun planNext() {
        scheduler.scheduleNext(ReminderSchedule.delayUntilNextMs(clock(), zone()))
    }
}
