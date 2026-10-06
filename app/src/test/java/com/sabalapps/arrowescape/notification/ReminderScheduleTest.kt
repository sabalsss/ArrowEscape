package com.sabalapps.arrowescape.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The reminder's clock: when it next fires for a given local time and zone, including the
 * days the wall clock jumps.
 */
class ReminderScheduleTest {

    private val newYork = TimeZone.getTimeZone("America/New_York")
    private val sydney = TimeZone.getTimeZone("Australia/Sydney")
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun fields(ms: Long, zone: TimeZone): List<Int> = Calendar.getInstance(zone).run {
        timeInMillis = ms
        listOf(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH), get(Calendar.HOUR_OF_DAY), get(Calendar.MINUTE))
    }

    @Test
    fun `the default is one place and a sensible evening hour`() {
        assertEquals(18, ReminderSchedule.HOUR)
        assertEquals(0, ReminderSchedule.MINUTE)
    }

    @Test
    fun `before the time it is today`() {
        val now = at(utc, 2026, 10, 6, 9, 30)
        assertEquals(listOf(2026, 10, 6, 18, 0), fields(ReminderSchedule.nextTriggerAtMs(now, utc), utc))
    }

    @Test
    fun `after the time it is tomorrow`() {
        val now = at(utc, 2026, 10, 6, 19, 5)
        assertEquals(listOf(2026, 10, 7, 18, 0), fields(ReminderSchedule.nextTriggerAtMs(now, utc), utc))
    }

    @Test
    fun `exactly at the time it is tomorrow, never an immediate second reminder`() {
        val now = at(utc, 2026, 10, 6, 18, 0)
        assertEquals(listOf(2026, 10, 7, 18, 0), fields(ReminderSchedule.nextTriggerAtMs(now, utc), utc))
    }

    @Test
    fun `month and year ends roll over`() {
        assertEquals(
            listOf(2027, 1, 1, 18, 0),
            fields(ReminderSchedule.nextTriggerAtMs(at(utc, 2026, 12, 31, 20), utc), utc)
        )
    }

    @Test
    fun `the delay is always positive and never more than a day and an hour`() {
        for (zone in listOf(utc, newYork, sydney)) {
            var now = at(zone, 2026, 1, 1, 0)
            repeat(24 * 7 * 60 / 30) {
                val delay = ReminderSchedule.delayUntilNextMs(now, zone)
                assertTrue("$zone $now", delay in 1..(25L * 60 * 60 * 1000))
                now += 30L * 60 * 1000
            }
        }
    }

    @Test
    fun `the reminder is at six pm on the wall clock across a daylight saving change`() {
        // New York springs forward on 2026-03-08: the day is 23 hours long.
        val now = at(newYork, 2026, 3, 7, 19)
        val next = ReminderSchedule.nextTriggerAtMs(now, newYork)
        assertEquals(listOf(2026, 3, 8, 18, 0), fields(next, newYork))
        assertEquals("22 real hours, not 24", 22L * 60 * 60 * 1000, next - now)

        // And back: on 2026-11-01 the 1am hour happens twice, so 19:00 to 18:00 is a full
        // 24 real hours for 23 on the wall — where "+24h" would have said 19:00.
        val fall = at(newYork, 2026, 10, 31, 19)
        val fallNext = ReminderSchedule.nextTriggerAtMs(fall, newYork)
        assertEquals(listOf(2026, 11, 1, 18, 0), fields(fallNext, newYork))
        assertEquals(24L * 60 * 60 * 1000, fallNext - fall)
    }

    @Test
    fun `a change of time zone puts the next reminder back on local six pm`() {
        val now = at(newYork, 2026, 10, 6, 12)
        val inNewYork = ReminderSchedule.nextTriggerAtMs(now, newYork)
        val inSydney = ReminderSchedule.nextTriggerAtMs(now, sydney)
        assertNotEquals(inNewYork, inSydney)
        assertEquals(18, fields(inNewYork, newYork)[3])
        assertEquals(18, fields(inSydney, sydney)[3])
    }

    @Test
    fun `minutes past the reminder time are measured on the local clock`() {
        assertEquals(0, ReminderSchedule.minutesPastReminderTime(at(newYork, 2026, 10, 6, 18, 0), newYork))
        assertEquals(30, ReminderSchedule.minutesPastReminderTime(at(newYork, 2026, 10, 6, 18, 30), newYork))
        assertEquals(-60, ReminderSchedule.minutesPastReminderTime(at(newYork, 2026, 10, 6, 17, 0), newYork))
        // The same instant is a different local time elsewhere.
        val instant = at(newYork, 2026, 10, 6, 18, 0)
        assertNotEquals(0, ReminderSchedule.minutesPastReminderTime(instant, sydney))
    }
}
