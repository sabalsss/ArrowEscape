package com.sabalapps.arrowescape.notification

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The reminder, planned with WorkManager.
 *
 * **One unique piece of work, always replaced, never added to.** Every call to
 * [scheduleNext] names the same work and replaces it, so app restarts, the Settings switch
 * and the worker's own re-planning can never leave two reminders queued.
 *
 * It is a chain of one-shot requests rather than a 24-hour periodic one on purpose: each run
 * computes the next from the *current* local clock and zone, so a player who travels or
 * changes the date is back on local time after the next run (or the next app start), where a
 * periodic request would keep its old rhythm. There is no exact-alarm permission and no
 * foreground service; the system is free to batch it, which is the point.
 */
class WorkManagerReminderScheduler(context: Context) : ReminderScheduler {

    private val appContext = context.applicationContext

    override fun scheduleNext(delayMs: Long) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }.onFailure { Log.w(TAG, "could not schedule the daily reminder", it) }
    }

    override fun cancel() {
        runCatching { WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME) }
            .onFailure { Log.w(TAG, "could not cancel the daily reminder", it) }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_mystery_reminder"
        private const val TAG = "ArrowEscapeReminder"
    }
}
