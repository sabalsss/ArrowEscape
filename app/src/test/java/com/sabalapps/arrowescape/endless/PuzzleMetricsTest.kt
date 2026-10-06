package com.sabalapps.arrowescape.endless

import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The difficulty score is what decides which tier a generated board is allowed
 * to appear in, so the thing worth testing is that it responds to the right
 * inputs: a board that is harder to read in some specific way has to score
 * higher than the same board without that property.
 */
class PuzzleMetricsTest {

    private fun measure(vararg layout: String): PuzzleMetrics =
        requireNotNull(PuzzleMetrics.measure(level(*layout))) {
            "fixture is unsolvable: ${layout.toList()}"
        }

    private fun level(vararg layout: String): Level =
        Levels.fromLayout(name = "fixture", layout = layout.toList())

    @Test
    fun `the weights sum to one so the score is a real 0 to 100`() {
        assertEquals(1.0, PuzzleMetrics.Weights.sum, 1e-9)
    }

    @Test
    fun `an unsolvable board cannot be measured`() {
        // Two arrows pointing at each other. The rest of the numbers would be
        // meaningless, so `measure` refuses rather than reporting them.
        assertNull(PuzzleMetrics.measure(level("><..")))
    }

    @Test
    fun `basic counts describe the board`() {
        val metrics = measure("^>..", "....", "..v.")
        assertEquals(3, metrics.rows)
        assertEquals(4, metrics.columns)
        assertEquals(3, metrics.arrowCount)
        assertEquals(12, metrics.cellCount)
        assertEquals(0.25, metrics.density, 1e-9)
        assertEquals(3, metrics.distinctDirections)
    }

    @Test
    fun `free arrows and chain depth are read off the peel`() {
        // One column of four arrows all pointing up: one free, four passes.
        val metrics = measure("^...", "^...", "^...", "^...")
        assertEquals(1, metrics.freeAtStart)
        assertEquals(4, metrics.peelDepth)
        assertEquals(0.25, metrics.freeRatio, 1e-9)
    }

    @Test
    fun `a board where everything is free has depth one`() {
        val metrics = measure("^^^^")
        assertEquals(4, metrics.freeAtStart)
        assertEquals(1, metrics.peelDepth)
        assertEquals(1.0, metrics.freeRatio, 1e-9)
        assertEquals(0.0, metrics.depthComponent, 1e-9)
        assertEquals("nothing is blocked, so nothing is withheld", 0.0, metrics.blockedComponent, 1e-9)
    }

    @Test
    fun `dominant share and entropy track how mixed the glyphs are`() {
        val single = measure("^^^^")
        assertEquals(1.0, single.dominantDirectionShare, 1e-9)
        assertEquals("one glyph is no mix at all", 0.0, single.directionEntropy, 1e-9)
        assertEquals(1, single.distinctDirections)

        val mixed = measure("^v<>")
        assertEquals(0.25, mixed.dominantDirectionShare, 1e-9)
        assertEquals("an even four-way split is a full mix", 1.0, mixed.directionEntropy, 1e-9)
        assertEquals(4, mixed.distinctDirections)
    }

    @Test
    fun `band coverage finds a dead row or column`() {
        val full = measure("^...", "^...", "^...", "^...")
        assertEquals("every row holds an arrow", 1.0, full.rowCoverage, 1e-9)
        assertEquals("only one column is used", 0.25, full.columnCoverage, 1e-9)
    }

    @Test
    fun `quadrant coverage finds an empty corner`() {
        val oneCorner = measure("^^..", "^^..", "....", "....")
        assertEquals(1, oneCorner.occupiedQuadrants)

        val spread = measure("^..^", "....", "....", "v..v")
        assertEquals(4, spread.occupiedQuadrants)
    }

    @Test
    fun `a longer blocker chain scores higher than the same arrows unchained`() {
        // Same arrow count, same board, same glyph. The only difference is that
        // one version has the arrows in each other's way.
        val chained = measure("^...", "^...", "^...", "^...")
        val loose = measure("^^^^", "....", "....", "....")

        assertEquals(chained.arrowCount, loose.arrowCount)
        assertTrue(
            "chained ${chained.difficultyScore} should beat loose ${loose.difficultyScore}",
            chained.difficultyScore > loose.difficultyScore
        )
    }

    @Test
    fun `a mixed board scores higher than a single-glyph board of the same shape`() {
        val mixed = measure("^v<>")
        val uniform = measure("^^^^")
        assertTrue(
            "mixed ${mixed.difficultyScore} should beat uniform ${uniform.difficultyScore}",
            mixed.difficultyScore > uniform.difficultyScore
        )
    }

    @Test
    fun `more arrows on the same board score higher`() {
        val few = measure("^...", "....", "....", "....")
        val many = measure("^..^", "....", "....", "v..v")
        assertTrue(
            "many ${many.difficultyScore} should beat few ${few.difficultyScore}",
            many.difficultyScore > few.difficultyScore
        )
    }

    @Test
    fun `the score stays inside zero to one hundred for every tier`() {
        for (tier in EndlessTier.entries) {
            for (seed in 1L..40L) {
                val score = PuzzleGenerator.generate(seed, tier).metrics.difficultyScore
                assertTrue("tier=$tier seed=$seed scored $score", score in 0.0..100.0)
            }
        }
    }

    @Test
    fun `tiers are ordered by the score their boards actually land on`() {
        // The point of the five tiers: a board drawn from a higher tier should
        // measurably be more work than one from a lower tier. Compared on
        // averages, because the bands are allowed to overlap at the edges.
        val averages = EndlessTier.entries.map { tier ->
            tier to (1L..60L).map { PuzzleGenerator.generate(it, tier).metrics.difficultyScore }.average()
        }
        for ((lower, higher) in averages.zipWithNext()) {
            assertTrue(
                "${lower.first} averaged ${lower.second} but ${higher.first} averaged ${higher.second}",
                higher.second > lower.second
            )
        }
    }

    @Test
    fun `every campaign level can still be measured`() {
        // The metrics are written for generated boards, but they are plain board
        // measurements — nothing in them should choke on a curated level.
        for (level in Levels.ALL) {
            assertNotNull("Level ${level.id} could not be measured", PuzzleMetrics.measure(level))
        }
    }

    // ---- the depth component ------------------------------------------------

    @Test
    fun `depth is a saturating curve that never actually saturates`() {
        // The whole point of the change: every extra layer is worth something,
        // for as deep as a board can get. The old ratio-against-a-ceiling form
        // stopped paying out at depth 9, which is below the depth 11 that Expert
        // boards actually reach.
        val components = (1..24).map { depth -> depth to depthComponentFor(depth) }

        for ((shallower, deeper) in components.zipWithNext()) {
            assertTrue(
                "depth ${shallower.first} scored ${shallower.second} and " +
                    "depth ${deeper.first} scored ${deeper.second}",
                deeper.second > shallower.second
            )
        }
        assertEquals("depth 1 is nothing withheld", 0.0, components.first().second, 1e-9)
        assertTrue("the component must stay inside 0..1", components.all { it.second < 1.0 })
    }

    @Test
    fun `the depths Expert boards reach are told apart`() {
        // The specific regression. Depth 9, 10 and 11 used to be one value.
        val nine = depthComponentFor(9)
        val ten = depthComponentFor(10)
        val eleven = depthComponentFor(11)

        assertTrue("depth 9 and 10 collapsed to $nine", ten > nine)
        assertTrue("depth 10 and 11 collapsed to $ten", eleven > ten)
        // And the differences are big enough to show up in a score out of 100,
        // not just in the last bits of a double.
        val perLayer = (eleven - nine) / 2 * PuzzleMetrics.Weights.DEPTH * 100
        assertTrue(
            "a layer past 9 is worth only %.3f score points".format(perLayer),
            perLayer > 0.4
        )
    }

    @Test
    fun `a deeper board scores higher than a shallower one all the way up`() {
        // Expressed on real boards rather than on the component: a column of n
        // arrows pointing up is a chain exactly n deep.
        var previous = Double.NEGATIVE_INFINITY
        for (height in 2..12) {
            val column = List(height) { "^....." }
            val metrics = measure(*column.toTypedArray())
            assertEquals("the fixture should be $height deep", height, metrics.peelDepth)
            assertTrue(
                "a $height-deep board scored ${metrics.difficultyScore}, " +
                    "no more than the one below it",
                metrics.difficultyScore > previous
            )
            previous = metrics.difficultyScore
        }
    }

    @Test
    fun `depth has diminishing returns`() {
        // A tenth layer genuinely is a smaller addition than a third one, and
        // the curve says so. This is what stops the component being a straight
        // line that would have to be clamped somewhere.
        val early = depthComponentFor(4) - depthComponentFor(3)
        val late = depthComponentFor(11) - depthComponentFor(10)
        assertTrue("early gain $early should beat late gain $late", early > late)
        assertTrue("a late layer should still be worth something", late > 0.0)
    }

    @Test
    fun `the score still stays inside zero to one hundred at extreme depth`() {
        // A board far deeper than the generator builds, to show the component
        // has no ceiling it could overshoot.
        val tall = measure(*List(60) { "^." }.toTypedArray())
        assertTrue("a 60-deep board scored ${tall.difficultyScore}", tall.difficultyScore in 0.0..100.0)
    }

    /** The depth component in isolation, by measuring a chain that deep. */
    private fun depthComponentFor(depth: Int): Double =
        measure(*List(depth) { "^....." }.toTypedArray()).depthComponent

    @Test
    fun `the debug summary mentions the board without crashing`() {
        val summary = measure("^...", "^...").debugSummary()
        assertTrue("summary was '$summary'", summary.contains("arrows=2"))
        assertTrue("summary was '$summary'", summary.contains("score="))
    }
}
