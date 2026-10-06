package com.sabalapps.arrowescape.notification

import android.content.Context
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.progress.SharedPrefsProgressStore
import com.sabalapps.arrowescape.time.GameDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The player's say over the daily reminder.
 *
 *  - [enabled] — the Daily Reminder switch. **Off by default**: the reminder is something the
 *    player turns on, at the end of the second level or in Settings, never something the game
 *    starts doing to them.
 *  - [introShown] — the "Want a daily mystery?" offer has been put in front of the player. It is
 *    asked once; either answer ends it, and Settings is the way back.
 *  - [lastNotifiedDate] — the day a reminder was last posted. One reminder a day, whatever
 *    re-schedules or retries happen underneath.
 *  - [permissionAsked] — the system's notification permission dialog has been shown at least
 *    once. With Android's own rationale flag this is how the Settings switch tells "ask again"
 *    from "the system will not ask again — send the player to system settings".
 */
data class ReminderPreference(
    val enabled: Boolean = false,
    val introShown: Boolean = false,
    val lastNotifiedDate: GameDate? = null,
    val permissionAsked: Boolean = false
)

/** The reminder preference, persisted like the other records: one short delimited string, range-checked on the way in. */
class ReminderPreferenceRepository(private val store: ProgressStore) {

    private val _preference = MutableStateFlow(read())
    val preference: StateFlow<ReminderPreference> = _preference.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (_preference.value.enabled == enabled) return
        write(_preference.value.copy(enabled = enabled))
    }

    fun markIntroShown() {
        if (_preference.value.introShown) return
        write(_preference.value.copy(introShown = true))
    }

    fun markPermissionAsked() {
        if (_preference.value.permissionAsked) return
        write(_preference.value.copy(permissionAsked = true))
    }

    fun recordNotified(date: GameDate) {
        write(_preference.value.copy(lastNotifiedDate = date))
    }

    private fun write(next: ReminderPreference) {
        _preference.value = next
        store.putString(
            KEY,
            listOf(
                VERSION.toString(),
                if (next.enabled) "1" else "0",
                if (next.introShown) "1" else "0",
                next.lastNotifiedDate?.iso ?: "",
                if (next.permissionAsked) "1" else "0"
            ).joinToString(FIELD)
        )
    }

    private fun read(): ReminderPreference {
        val raw = store.getString(KEY) ?: return ReminderPreference()
        val parsed = parse(raw)
        if (parsed == null) store.putString(KEY, null)
        return parsed ?: ReminderPreference()
    }

    private fun parse(raw: String): ReminderPreference? {
        val parts = raw.split(FIELD)
        if (parts.size != 5) return null
        if (parts[0].toIntOrNull() != VERSION) return null
        val enabled = flag(parts[1]) ?: return null
        val intro = flag(parts[2]) ?: return null
        val last = if (parts[3].isEmpty()) null else GameDate.parse(parts[3]) ?: return null
        val asked = flag(parts[4]) ?: return null
        return ReminderPreference(
            enabled = enabled,
            introShown = intro,
            lastNotifiedDate = last,
            permissionAsked = asked
        )
    }

    private fun flag(raw: String): Boolean? = when (raw) {
        "1" -> true
        "0" -> false
        else -> null
    }

    companion object {
        private const val VERSION = 1
        private const val FIELD = "|"
        private const val KEY = "daily_reminder"

        @Volatile
        private var instance: ReminderPreferenceRepository? = null

        fun get(context: Context): ReminderPreferenceRepository =
            instance ?: synchronized(this) {
                instance ?: ReminderPreferenceRepository(
                    SharedPrefsProgressStore(context, SharedPrefsProgressStore.RETENTION_NAME)
                ).also { instance = it }
            }
    }
}
