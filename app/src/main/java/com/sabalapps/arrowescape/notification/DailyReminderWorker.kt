package com.sabalapps.arrowescape.notification

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sabalapps.arrowescape.daily.DailyRepository
import java.util.TimeZone

/**
 * Runs once a day, decides whether today's reminder is still wanted, posts it if so, and
 * always lines up tomorrow's.
 *
 * Tomorrow's is planned in a `finally`, so a failure while posting cannot end the chain:
 * the worst case is one missed reminder, not a reminder that silently never comes back.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val services = ReminderServices.get(applicationContext)
        try {
            val daily = DailyRepository.get(applicationContext)
            val today = daily.today()
            val shouldPost = ReminderPolicy.shouldPost(
                preference = services.preferences.preference.value,
                dailyCompletedToday = daily.isTodayCompleted(),
                today = today,
                nowMs = System.currentTimeMillis(),
                zone = TimeZone.getDefault()
            )
            // The mystery is the point of the notification, so what it asks about is checked
            // against the Daily Challenge itself rather than against a copy of its state.
            if (shouldPost && ReminderNotifier.post(applicationContext)) {
                services.preferences.recordNotified(today)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "daily reminder run failed", t)
        } finally {
            services.controller.planNextIfEnabled()
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "ArrowEscapeReminder"
    }
}
