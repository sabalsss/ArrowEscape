package com.sabalapps.arrowescape.daily

import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.shape.MysteryShapes
import com.sabalapps.arrowescape.shape.ShapeMask
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The day's mystery shape: deterministic, fairly dealt, never repeating back to back. */
class DailyShapeTest {

    private val start = GameDate(2026, 1, 1)
    private fun days(n: Int) = (0 until n).map { start.plusDays(it.toLong()) }

    private fun layout(date: GameDate) =
        DailyChallenge.generate(date).level.arrows.map { Triple(it.row, it.col, it.direction) }

    @Test
    fun `the scheme is the shape generator`() {
        assertEquals(2, DailyChallenge.GENERATOR_VERSION)
    }

    @Test
    fun `a date always means the same picture, mask and arrows`() {
        for (date in days(60)) {
            val a = DailyChallenge.shapeFor(date)
            val b = DailyChallenge.shapeFor(date)
            assertEquals(a.template.id, b.template.id)
            assertEquals(a.mirrored, b.mirrored)
            assertEquals(a.mask, b.mask)
            assertEquals(layout(date), layout(date))
            assertEquals(a.mask, ShapeMask.of(DailyChallenge.generate(date).level))
        }
    }

    @Test
    fun `the picture is chosen from the daily pool`() {
        val pool = MysteryShapes.dailyPool.map { it.id }.toSet()
        for (date in days(400)) assertTrue(DailyChallenge.shapeFor(date).template.id in pool)
    }

    @Test
    fun `every cycle of days visits each picture exactly once`() {
        val size = MysteryShapes.dailyPool.size
        // Align to a cycle boundary: epoch day divisible by the pool size.
        val firstBoundary = (0L..size.toLong()).first { GameDate.fromEpochDay(start.epochDay + it).epochDay % size == 0L }
        val first = GameDate.fromEpochDay(start.epochDay + firstBoundary)
        for (cycle in 0 until 3) {
            val ids = (0 until size).map {
                DailyChallenge.shapeFor(GameDate.fromEpochDay(first.epochDay + cycle * size + it)).template.id
            }
            assertEquals("cycle $cycle repeats a picture: $ids", size, ids.toSet().size)
        }
    }

    @Test
    fun `no picture is ever served two days running, across years and cycle seams`() {
        var previous = DailyChallenge.shapeFor(start).template.id
        for (date in days(1500).drop(1)) {
            val id = DailyChallenge.shapeFor(date).template.id
            assertTrue("${date.iso} repeats $id", id != previous)
            previous = id
        }
    }

    @Test
    fun `a year shows real variety`() {
        val ids = days(365).map { DailyChallenge.shapeFor(it).template.id }
        assertEquals(MysteryShapes.dailyPool.size, ids.toSet().size)
        val boards = days(365).map { layout(it) }
        assertEquals("two days share a board", 365, boards.toSet().size)
    }

    @Test
    fun `the tier follows the picture`() {
        for (date in days(200)) {
            val cells = DailyChallenge.shapeFor(date).template.cellCount
            val expected = if (cells >= MysteryShapes.DAILY_HARD_FROM_CELLS) EndlessTier.HARD else EndlessTier.MEDIUM
            assertEquals(expected, DailyChallenge.tierFor(date))
            assertEquals(expected, DailyChallenge.generate(date).tier)
        }
        val tiers = days(365).map { DailyChallenge.tierFor(it) }.toSet()
        assertEquals(setOf(EndlessTier.MEDIUM, EndlessTier.HARD), tiers)
    }

    @Test
    fun `a picture that may flip is flipped on some days and not on others`() {
        val flips = days(800).map { DailyChallenge.shapeFor(it) }.filter { it.template.allowMirror }
        assertTrue(flips.any { it.mirrored })
        assertTrue(flips.any { !it.mirrored })
        // ...and one that may not, never is.
        assertFalse(days(800).map { DailyChallenge.shapeFor(it) }.filter { !it.template.allowMirror }.any { it.mirrored })
    }

    @Test
    fun `a day's board does not carry the picture's name`() {
        for (date in days(30)) {
            val puzzle = DailyChallenge.generate(date)
            assertEquals("Daily Challenge", puzzle.level.name)
            assertFalse(puzzle.level.name.contains(puzzle.shape!!.name, ignoreCase = true))
        }
    }
}
