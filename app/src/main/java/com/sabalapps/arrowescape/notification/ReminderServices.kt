package com.sabalapps.arrowescape.notification

import android.content.Context

/**
 * The reminder's wiring: the preference and the controller that keeps WorkManager in step
 * with it. One of these per process, shared by the settings switch, the worker and app start,
 * so they all agree about what is switched on.
 */
class ReminderServices private constructor(context: Context) {
    val preferences: ReminderPreferenceRepository = ReminderPreferenceRepository.get(context)
    val controller: ReminderController = ReminderController(
        preferences = preferences,
        scheduler = WorkManagerReminderScheduler(context)
    )

    companion object {
        @Volatile
        private var instance: ReminderServices? = null

        fun get(context: Context): ReminderServices =
            instance ?: synchronized(this) {
                instance ?: ReminderServices(context.applicationContext).also { instance = it }
            }
    }
}
