package com.sabalapps.arrowescape.notification

import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Whether a reminder that has fired should actually be posted. */
class ReminderPolicyTest {

    private val zone = TimeZone.getTimeZone("UTC")
    private val today = GameDate(2026, 10, 6)
    private val on = ReminderPreference(enabled = true)

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, 9, 6, hour, minute, 0)
    }.timeInMillis

    private fun should(
        preference: ReminderPreference = on,
        completed: Boolean = false,
        now: Long = at(18, 0)
    ) = ReminderPolicy.shouldPost(preference, completed, today, now, zone)

    @Test
    fun `an enabled reminder on time for an unsolved daily is posted`() {
        assertTrue(should())
    }

    @Test
    fun `a switched-off reminder is never posted`() {
        assertFalse(should(preference = ReminderPreference(enabled = false)))
    }

    @Test
    fun `a solved daily is not reminded about`() {
        assertFalse(should(completed = true))
    }

    @Test
    fun `one reminder a day`() {
        assertFalse(should(preference = on.copy(lastNotifiedDate = today)))
        assertTrue("yesterday's does not count", should(preference = on.copy(lastNotifiedDate = GameDate(2026, 10, 5))))
    }

    @Test
    fun `a little late is fine`() {
        assertTrue(should(now = at(18, 40)))
        assertTrue(should(now = at(21, 59)))
    }

    @Test
    fun `a reminder held back for hours is dropped rather than sent at the wrong time`() {
        assertFalse(should(now = at(22, 30)))
        assertFalse(should(now = at(23, 59)))
    }

    @Test
    fun `a stray early run is not the reminder`() {
        assertFalse(should(now = at(9, 0)))
        assertFalse(should(now = at(0, 5)))
        assertTrue("a few minutes early is tolerated", should(now = at(17, 57)))
    }
}
