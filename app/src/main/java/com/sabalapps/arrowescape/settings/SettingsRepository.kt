package com.sabalapps.arrowescape.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.sabalapps.arrowescape.tutorial.TutorialFlagStore

/**
 * Stores [GameSettings] in SharedPreferences. Deliberately plain: no DataStore,
 * no DI container, nothing to sync anywhere.
 *
 * It also holds the two bits onboarding persists — see [TutorialFlagStore]: Level 1's
 * lesson and Level 2's. They are kept out of [GameSettings] because [GameSettings] is
 * the set of things the settings screen renders as controls, and these are not: the
 * screen offers "Replay Tutorial", an action, and the flags are what first play sets.
 */
class SettingsRepository private constructor(context: Context) : TutorialFlagStore {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) = update { it.copy(soundEnabled = enabled) }

    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun setTheme(theme: ThemeOption) = update { it.copy(theme = theme) }

    private fun update(transform: (GameSettings) -> GameSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_SOUND, next.soundEnabled)
            .putBoolean(KEY_HAPTICS, next.hapticsEnabled)
            .putString(KEY_THEME, next.theme.key)
            .apply()
        _settings.value = next
    }

    // ---- the tutorial flag ---------------------------------------------------

    override fun isTutorialCompleted(): Boolean = prefs.getBoolean(KEY_TUTORIAL_DONE, false)

    override fun setTutorialCompleted(completed: Boolean) {
        // commit(), not apply(): a player who taps Replay Tutorial and is then
        // killed by Android must not come back to a tutorial that is still
        // marked as seen.
        prefs.edit().putBoolean(KEY_TUTORIAL_DONE, completed).commit()
    }

    override fun isDependencyLessonCompleted(): Boolean =
        prefs.getBoolean(KEY_DEPENDENCY_LESSON_DONE, false)

    override fun setDependencyLessonCompleted(completed: Boolean) {
        // commit() for the same reason as above: a lesson that worked must stay recorded
        // even if the process is killed a frame later.
        prefs.edit().putBoolean(KEY_DEPENDENCY_LESSON_DONE, completed).commit()
    }

    private fun read() = GameSettings(
        soundEnabled = prefs.getBoolean(KEY_SOUND, true),
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
        theme = ThemeOption.fromKey(prefs.getString(KEY_THEME, null))
    )

    companion object {
        private const val PREFS_NAME = "arrow_escape_settings"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_THEME = "theme"
        /** Level 1's lesson. The key predates Level 2's lesson and keeps its name, so an existing save still counts. */
        private const val KEY_TUTORIAL_DONE = "tutorial_completed"
        private const val KEY_DEPENDENCY_LESSON_DONE = "onboarding_level2_completed"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
