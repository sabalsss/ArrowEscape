package com.sabalapps.arrowescape.daily

import com.sabalapps.arrowescape.endless.BoardAnalysis
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.PuzzleMetrics
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily scheme: one board per calendar day, the same board for everyone on
 * the same day, and a board that is actually worth sitting down with.
 *
 * Nothing here checks that the *generator* works — that is
 * `PuzzleGeneratorTest`'s job and the daily deliberately has no generator of its
 * own. What is checked here is the mapping from a date to a seed and a tier, and
 * that the puzzle which comes out the other end still carries every guarantee
 * the generator makes.
 */
class DailyChallengeTest {

    /** A year of consecutive dates, which is the sample most of these run over. */
    private val year: List<GameDate> =
        (0L until 365L).map { GameDate(2026, 1, 1).plusDays(it) }

    private fun layout(level: Level): List<String> {
        val grid = Array(level.rows) { CharArray(level.columns) { '.' } }
        for (arrow in level.arrows) {
            grid[arrow.row][arrow.col] = when (arrow.direction) {
                Direction.UP -> '^'
                Direction.DOWN -> 'v'
                Direction.LEFT -> '<'
                Direction.RIGHT -> '>'
            }
        }
        return grid.map { String(it) }
    }

    // ---- the seed ------------------------------------------------------------

    @Test
    fun `the same date always derives the same seed`() {
        for (date in year) {
            assertEquals(
                "${date.iso} derived two different seeds",
                DailyChallenge.seedFor(date),
                DailyChallenge.seedFor(date)
            )
        }
    }

    @Test
    fun `a date built two different ways derives the same seed`() {
        // The seed is a function of the calendar day and nothing else, so a date
        // reached by counting forward has to match the same date written down.
        val counted = GameDate(2026, 1, 1).plusDays(276)
        val written = GameDate(2026, 10, 4)
        assertEquals(written, counted)
        assertEquals(DailyChallenge.seedFor(written), DailyChallenge.seedFor(counted))
    }

    @Test
    fun `different dates derive different seeds`() {
        // Not "generally" different: across a full year every seed should be
        // distinct, and a collision here would mean two days share a puzzle.
        val seeds = year.map { DailyChallenge.seedFor(it) }
        assertEquals("two days in 2026 share a seed", seeds.size, seeds.toSet().size)
    }

    @Test
    fun `seeds stay distinct across a decade of days`() {
        val seeds = (0L until 3_650L)
            .map { DailyChallenge.seedFor(GameDate(2026, 1, 1).plusDays(it)) }
            .toSet()
        assertEquals("a decade of daily seeds collided", 3_650, seeds.size)
    }

    @Test
    fun `consecutive days do not derive neighbouring seeds`() {
        // The hash is there so that one day's board tells you nothing about the
        // next one's. Adjacent seeds would mean adjacent generator streams.
        for (day in 0L until 60L) {
            val first = DailyChallenge.seedFor(GameDate(2026, 5, 1).plusDays(day))
            val second = DailyChallenge.seedFor(GameDate(2026, 5, 1).plusDays(day + 1))
            assertTrue(
                "seeds for consecutive days were $first and $second",
                Math.abs(first - second) > 1_000_000L
            )
        }
    }

    @Test
    fun `the stable hash is a fixed function, not a runtime detail`() {
        // Pinned values. If a future change breaks these, every already-defined
        // daily puzzle has silently changed — which is exactly the thing
        // GENERATOR_VERSION exists to make deliberate.
        assertEquals(-3_750_763_034_362_895_579L, DailyChallenge.stableHash(""))
        assertEquals(
            "the same text must hash the same twice",
            DailyChallenge.stableHash("daily2026-10-04v1"),
            DailyChallenge.stableHash("daily2026-10-04v1")
        )
        assertTrue(
            "a one character change must change the hash",
            DailyChallenge.stableHash("daily2026-10-04v1") !=
                DailyChallenge.stableHash("daily2026-10-05v1")
        )
    }

    @Test
    fun `the scheme version is part of the seed`() {
        // The version cannot be varied at runtime, so what is checked is that it
        // is in the hashed text at all: the seed has to be the hash of a string
        // that includes it.
        val date = GameDate(2026, 10, 4)
        val expected = com.sabalapps.arrowescape.endless.SeededRandom.derive(
            DailyChallenge.stableHash("daily${date.iso}v${DailyChallenge.GENERATOR_VERSION}")
        )
        assertEquals(expected, DailyChallenge.seedFor(date))
    }

    // ---- the tier ------------------------------------------------------------

    @Test
    fun `every daily is Medium or Hard`() {
        for (date in year) {
            val tier = DailyChallenge.tierFor(date)
            assertTrue(
                "${date.iso} was tiered $tier",
                tier == EndlessTier.MEDIUM || tier == EndlessTier.HARD
            )
        }
    }

    @Test
    fun `no daily is ever Expert, Easy or Beginner`() {
        // Spelled out as its own test because this is the rule a first-time
        // player's experience depends on: a brand new install opening the Daily
        // Challenge must not be handed a 36-arrow Expert board, and must not be
        // handed a ten-second one either.
        val tiers = (0L until 2_000L)
            .map { DailyChallenge.tierFor(GameDate(2026, 1, 1).plusDays(it)) }
            .toSet()
        assertEquals(setOf(EndlessTier.MEDIUM, EndlessTier.HARD), tiers)
    }

    @Test
    fun `the tier is deterministic from the date`() {
        for (date in year) {
            assertEquals(DailyChallenge.tierFor(date), DailyChallenge.tierFor(date))
        }
    }

    @Test
    fun `both tiers come up regularly`() {
        // A split that in practice only ever produced one tier would pass the
        // test above and still be wrong.
        val tiers = year.map { DailyChallenge.tierFor(it) }
        val hard = tiers.count { it == EndlessTier.HARD }
        assertTrue("only $hard of 365 days were Hard", hard in 80..250)
    }

    // ---- the puzzle ----------------------------------------------------------

    @Test
    fun `today's puzzle is the same board every time it is generated`() {
        for (date in year.take(40)) {
            val first = DailyChallenge.generate(date)
            val second = DailyChallenge.generate(date)
            assertEquals("${date.iso} generated two layouts", layout(first.level), layout(second.level))
            assertEquals("${date.iso} generated two witnesses", first.solution, second.solution)
            assertEquals(first.seed, second.seed)
            assertEquals(first.tier, second.tier)
        }
    }

    @Test
    fun `restoring a save generates the same board as playing it fresh`() {
        for (date in year.take(20)) {
            assertEquals(
                layout(DailyChallenge.generate(date).level),
                layout(DailyChallenge.generateForSave(date).level)
            )
        }
    }

    @Test
    fun `every day of a year is solvable and its witness is valid`() {
        for (date in year) {
            val puzzle = DailyChallenge.generate(date)
            // The witness replayed through the real rules, which is what turns
            // "the generator says so" into a checked fact.
            assertTrue(
                "${date.iso}: the witness is not a legal removal order",
                BoardAnalysis.verifyOrder(puzzle.level.arrows, puzzle.solution)
            )
            assertTrue(
                "${date.iso}: the board cannot be peeled clear",
                BoardAnalysis.isSolvable(puzzle.level.arrows)
            )
            assertEquals(
                "${date.iso}: the witness does not cover the board",
                puzzle.level.arrows.size,
                puzzle.solution.size
            )
        }
    }

    @Test
    fun `every day of a year lands on its tier and inside the column cap`() {
        for (date in year) {
            val puzzle = DailyChallenge.generate(date)
            assertTrue("${date.iso} fell back off-tier: ${puzzle.debugSummary()}", puzzle.onTier)
            assertTrue(
                "${date.iso} built ${puzzle.level.columns} columns",
                puzzle.level.columns <= com.sabalapps.arrowescape.endless.BoardSize.MAX_COLUMNS
            )
        }
    }

    @Test
    fun `every day of a year is its picture, on its tier, and measurable`() {
        for (date in year) {
            val puzzle = DailyChallenge.generate(date)
            val variant = DailyChallenge.shapeFor(date)
            assertEquals(
                "${date.iso}: the board is not the day's picture",
                variant.mask,
                com.sabalapps.arrowescape.shape.ShapeMask.of(puzzle.level)
            )
            assertNotNull("${date.iso} carries no shape identity", puzzle.shape)
            assertEquals(variant.template.id, puzzle.shape!!.templateId)
            assertEquals(DailyChallenge.tierFor(date), puzzle.tier)
            requireNotNull(PuzzleMetrics.measure(puzzle.level)) { "${date.iso} produced an unmeasurable board" }
        }
    }

    @Test
    fun `no two days in a year are the same board`() {
        val layouts = year.map { layout(DailyChallenge.generate(it).level) }
        assertEquals("two days in 2026 share a layout", layouts.size, layouts.toSet().size)
    }

    @Test
    fun `the board is named for the player, not for the generator`() {
        assertEquals("Daily Challenge", DailyChallenge.generate(GameDate(2026, 10, 4)).level.name)
        assertNotNull(DailyChallenge.NAME)
    }
}
