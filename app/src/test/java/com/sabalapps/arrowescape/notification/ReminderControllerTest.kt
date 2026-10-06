package com.sabalapps.arrowescape.notification

import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The switch and the schedule, kept in step. The scheduler is a fake that models WorkManager's
 * *unique* work: one slot, replaced on every schedule — which is exactly the property the
 * real one is configured for.
 */
class ReminderControllerTest {

    private class FakeScheduler : ReminderScheduler {
        /** The single planned reminder, or null. */
        var planned: Long? = null
        var scheduleCalls = 0
        var cancelCalls = 0

        override fun scheduleNext(delayMs: Long) {
            scheduleCalls++
            planned = delayMs
        }

        override fun cancel() {
            cancelCalls++
            planned = null
        }
    }

    private val store = InMemoryProgressStore()
    private val preferences = ReminderPreferenceRepository(store)
    private val scheduler = FakeScheduler()
    private var zone = TimeZone.getTimeZone("UTC")
    private var now = Calendar.getInstance(zone).apply { clear(); set(2026, 9, 6, 9, 0, 0) }.timeInMillis

    private val controller = ReminderController(preferences, scheduler, clock = { now }, zone = { zone })

    private val nineHours = 9L * 60 * 60 * 1000

    @Test
    fun `enabling schedules one reminder for the next six pm and records the choice`() {
        controller.enable()
        assertTrue(preferences.preference.value.enabled)
        assertEquals(nineHours, scheduler.planned)
        assertEquals(1, scheduler.scheduleCalls)
    }

    @Test
    fun `enabling twice leaves one reminder, not two`() {
        controller.enable()
        controller.enable()
        assertEquals("a unique slot, replaced", nineHours, scheduler.planned)
    }

    @Test
    fun `disabling cancels what was scheduled`() {
        controller.enable()
        controller.disable()
        assertFalse(preferences.preference.value.enabled)
        assertNull(scheduler.planned)
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun `re-enabling after disabling schedules again`() {
        controller.enable()
        controller.disable()
        controller.enable()
        assertEquals(nineHours, scheduler.planned)
    }

    @Test
    fun `starting the app with the reminder on re-plans it against the current clock`() {
        controller.enable()
        now += 2L * 60 * 60 * 1000 // two hours later, the app is opened again
        controller.syncOnStartup()
        assertEquals(7L * 60 * 60 * 1000, scheduler.planned)
    }

    @Test
    fun `starting the app with the reminder off makes sure nothing is left planned`() {
        scheduler.planned = 123L // a schedule restored from a backup the switch never made
        controller.syncOnStartup()
        assertNull(scheduler.planned)
    }

    @Test
    fun `restarting the app many times never creates a second reminder`() {
        controller.enable()
        repeat(20) { controller.syncOnStartup() }
        assertEquals(nineHours, scheduler.planned)
    }

    @Test
    fun `after a run tomorrow's is planned only if the player still wants it`() {
        controller.enable()
        scheduler.planned = null
        controller.planNextIfEnabled()
        assertEquals(nineHours, scheduler.planned)

        controller.disable()
        val calls = scheduler.scheduleCalls
        controller.planNextIfEnabled()
        assertEquals("a switched-off reminder is not re-planned by a run already in flight", calls, scheduler.scheduleCalls)
        assertNull(scheduler.planned)
    }

    @Test
    fun `a change of time zone is picked up the next time it is planned`() {
        controller.enable()
        val before = scheduler.planned
        zone = TimeZone.getTimeZone("Australia/Sydney")
        controller.syncOnStartup()
        assertTrue("the delay follows the new local clock", scheduler.planned != before)
        assertTrue(scheduler.planned!! in 1..(25L * 60 * 60 * 1000))
    }

    @Test
    fun `the choice survives a restart of the app`() {
        controller.enable()
        val restarted = ReminderController(ReminderPreferenceRepository(store), scheduler, { now }, { zone })
        assertTrue(restarted.preference.value.enabled)
    }
}
