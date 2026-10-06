package com.sabalapps.arrowescape.notification

import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPreferenceRepositoryTest {

    private val store = InMemoryProgressStore()

    @Test
    fun `the reminder is off by default and nothing has been asked`() {
        val preference = ReminderPreferenceRepository(store).preference.value
        assertFalse(preference.enabled)
        assertFalse(preference.introShown)
        assertFalse(preference.permissionAsked)
        assertNull(preference.lastNotifiedDate)
    }

    @Test
    fun `every field survives a restart`() {
        ReminderPreferenceRepository(store).apply {
            setEnabled(true)
            markIntroShown()
            markPermissionAsked()
            recordNotified(GameDate(2026, 10, 6))
        }
        val reloaded = ReminderPreferenceRepository(store).preference.value
        assertTrue(reloaded.enabled && reloaded.introShown && reloaded.permissionAsked)
        assertEquals(GameDate(2026, 10, 6), reloaded.lastNotifiedDate)
    }

    @Test
    fun `turning it off and on again is remembered each time`() {
        val repo = ReminderPreferenceRepository(store)
        repo.setEnabled(true)
        repo.setEnabled(false)
        assertFalse(ReminderPreferenceRepository(store).preference.value.enabled)
        repo.setEnabled(true)
        assertTrue(ReminderPreferenceRepository(store).preference.value.enabled)
    }

    @Test
    fun `a corrupt record reads as the defaults and is cleared`() {
        for (bad in listOf("", "x", "2|1|1||0", "1|2|0||0", "1|1|0|not-a-date|0", "1|1|0|", "1|1|0||9")) {
            val broken = InMemoryProgressStore(mapOf("daily_reminder" to bad))
            assertEquals("for '$bad'", ReminderPreference(), ReminderPreferenceRepository(broken).preference.value)
            assertNull("for '$bad'", broken.getString("daily_reminder"))
        }
    }
}
