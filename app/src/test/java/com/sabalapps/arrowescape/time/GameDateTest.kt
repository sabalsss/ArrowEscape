package com.sabalapps.arrowescape.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [GameDate] carries its own calendar arithmetic because `java.time` is not
 * available at the minimum SDK, so the arithmetic is the thing worth testing —
 * and it is testable against ground truth, because the *test* JVM has
 * `java.time` even though the shipped app does not. Every claim below is checked
 * against `LocalDate` rather than against hand-written expectations.
 */
class GameDateTest {

    private fun GameDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)

    @Test
    fun `epoch day matches java time for a long stretch of days`() {
        // Two centuries of consecutive days, which covers every leap rule there
        // is: the 4-year rule, the 100-year skip and the 400-year exception.
        var date = LocalDate.of(1900, 1, 1)
        val end = LocalDate.of(2100, 1, 1)
        while (date.isBefore(end)) {
            val mine = GameDate(date.year, date.monthValue, date.dayOfMonth)
            assertEquals("epochDay for $date", date.toEpochDay(), mine.epochDay)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `fromEpochDay is the exact inverse of epochDay`() {
        for (day in -40_000L..40_000L step 7L) {
            val date = GameDate.fromEpochDay(day)
            assertEquals("round trip of day $day", day, date.epochDay)
            assertEquals(
                "fromEpochDay($day) should match java.time",
                LocalDate.ofEpochDay(day),
                date.toLocalDate()
            )
        }
    }

    @Test
    fun `the leap day and the day either side of it survive the round trip`() {
        // 2024 is a leap year, 2100 is not, 2000 is. All three are the cases the
        // shifted-year arithmetic exists to get right.
        for (year in listOf(2000, 2024, 2100)) {
            val lastOfFebruary = LocalDate.of(year, 3, 1).minusDays(1)
            val mine = GameDate.fromEpochDay(lastOfFebruary.toEpochDay())
            assertEquals(lastOfFebruary.dayOfMonth, mine.day)
            assertEquals(2, mine.month)
        }
    }

    @Test
    fun `iso is the stable key the daily seed is hashed from`() {
        assertEquals("2026-10-04", GameDate(2026, 10, 4).iso)
        assertEquals("single digits are padded", "2026-01-02", GameDate(2026, 1, 2).iso)
    }

    @Test
    fun `parse accepts its own iso form and nothing else`() {
        for (day in 0L..3_000L step 37L) {
            val date = GameDate.fromEpochDay(day)
            assertEquals("round trip of ${date.iso}", date, GameDate.parse(date.iso))
        }
    }

    @Test
    fun `parse refuses anything that is not a real day`() {
        // Everything a corrupt or hand-edited preference could contain.
        for (raw in listOf(
            null, "", "2026", "2026-10", "2026-10-4", "20261004", "2026/10/04",
            "abcd-10-04", "2026-ab-04", "2026-10-ab", "2026-13-01", "2026-00-01",
            "2026-10-00", "2026-10-32", "2026-02-30", "2025-02-29", "2026-10-04 "
        )) {
            assertNull("'$raw' should not parse", GameDate.parse(raw))
        }
        // 2024 is a leap year, so this one is a real day and must survive.
        assertNotNull(GameDate.parse("2024-02-29"))
    }

    @Test
    fun `isDayAfter is true only for the immediately following day`() {
        val today = GameDate(2026, 3, 1)
        assertTrue(today.isDayAfter(GameDate(2026, 2, 28)))
        assertFalse("a two day gap is not consecutive", today.isDayAfter(GameDate(2026, 2, 27)))
        assertFalse("the same day is not after itself", today.isDayAfter(today))
        assertFalse("the day after is not before", today.isDayAfter(GameDate(2026, 3, 2)))
    }

    @Test
    fun `isDayAfter works across every month and year boundary`() {
        for (day in 0L..5_000L step 1L) {
            val earlier = GameDate.fromEpochDay(day)
            val later = GameDate.fromEpochDay(day + 1)
            assertTrue("${later.iso} should follow ${earlier.iso}", later.isDayAfter(earlier))
        }
    }

    @Test
    fun `plusDays agrees with java time`() {
        val start = GameDate(2026, 12, 30)
        for (offset in -400L..400L step 13L) {
            assertEquals(
                "offset $offset",
                LocalDate.of(2026, 12, 30).plusDays(offset),
                start.plusDays(offset).toLocalDate()
            )
        }
    }

    @Test
    fun `day of week matches java time`() {
        for (day in 0L..1_000L) {
            val mine = GameDate.fromEpochDay(day)
            // java.time numbers Monday as 1; GameDate numbers it as 0.
            assertEquals(
                "day of week for ${mine.iso}",
                LocalDate.ofEpochDay(day).dayOfWeek.value - 1,
                mine.dayOfWeek
            )
        }
    }

    @Test
    fun `the friendly form names the day and the month`() {
        // 2026-10-04 is a Sunday.
        assertEquals("Sunday, 4 October", GameDate(2026, 10, 4).friendly())
    }

    @Test
    fun `an impossible month or day is refused at construction`() {
        for (bad in listOf({ GameDate(2026, 0, 1) }, { GameDate(2026, 13, 1) },
            { GameDate(2026, 1, 0) }, { GameDate(2026, 1, 32) })) {
            val threw = runCatching(bad).isFailure
            assertTrue("an out-of-range field should be refused", threw)
        }
    }

    @Test
    fun `a fixed provider is the clock a test can move`() {
        val clock = FixedDateProvider(GameDate(2026, 10, 4))
        assertEquals(GameDate(2026, 10, 4), clock.today())
        clock.advance()
        assertEquals(GameDate(2026, 10, 5), clock.today())
        clock.advance(days = 10)
        assertEquals(GameDate(2026, 10, 15), clock.today())
    }

    @Test
    fun `the system provider returns a plausible day`() {
        // Not asserted against a literal date — that would be a test that fails
        // tomorrow. What is worth checking is that the Calendar month offset is
        // right, which a nonsense month would catch.
        val today = SystemDateProvider().today()
        assertTrue("month was ${today.month}", today.month in 1..12)
        assertTrue("day was ${today.day}", today.day in 1..31)
        assertTrue("year was ${today.year}", today.year in 2000..2200)
        assertEquals("it should round-trip its own epoch day", today, GameDate.fromEpochDay(today.epochDay))
    }
}
