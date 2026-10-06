package com.sabalapps.arrowescape.startup

import android.content.Context
import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.notification.ReminderNotifier
import com.sabalapps.arrowescape.notification.ReminderPreferenceRepository
import com.sabalapps.arrowescape.notification.ReminderServices
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.retention.RetentionPromptRepository
import com.sabalapps.arrowescape.settings.SettingsRepository
import com.sabalapps.arrowescape.ui.world.GameWorlds
import com.sabalapps.arrowescape.ui.world.warmMenuBackdrop

/**
 * What the loading screen really waits for.
 *
 * Every step is something that would otherwise happen on the first frame and cost it time —
 * a preferences file read and parsed on the main thread, an image decoded as Home first draws
 * — moved earlier and off the main thread. The repositories are process singletons, so a step
 * that "just calls `get`" is the whole of the work: the ViewModel built afterwards finds them
 * ready.
 *
 * Deliberately *not* here: generating Endless or Daily puzzles, tracing any shape, decoding the
 * other four worlds, rendering a discovery. None of that is needed to open Home, and all of it
 * costs more than it saves.
 */
object AppStartup {

    fun steps(context: Context): List<StartupStep> {
        val app = context.applicationContext
        return listOf(
            StartupStep(StartupTask.SETTINGS, timeoutMs = 4_000) {
                SettingsRepository.get(app)
            },
            StartupStep(StartupTask.PROGRESS, timeoutMs = 4_000) {
                ProgressRepository.get(app)
                EndlessRepository.get(app)
                DailyRepository.get(app)
            },
            StartupStep(StartupTask.RETENTION, timeoutMs = 1_500) {
                RetentionPromptRepository.get(app)
                ReminderPreferenceRepository.get(app)
            },
            StartupStep(StartupTask.SCENERY, timeoutMs = 1_500) {
                // Home is dressed in the world of the level the player would resume.
                val progress = ProgressRepository.get(app)
                val levelId = progress.savedGame.value?.levelId ?: progress.progress.value.currentLevel
                warmMenuBackdrop(app, GameWorlds.forCampaignLevel(levelId))
            },
            StartupStep(StartupTask.REMINDER, timeoutMs = 2_000) {
                ReminderNotifier.ensureChannel(app)
                ReminderServices.get(app).controller.syncOnStartup()
            }
        )
    }
}
