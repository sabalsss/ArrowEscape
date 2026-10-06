package com.sabalapps.arrowescape.endless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tier table itself: the progression bands, the board-shape constraints the
 * UI depends on, and the quality rules' behaviour as a filter.
 */
class EndlessTierTest {

    @Test
    fun `the progression bands are the documented ones`() {
        val expected = mapOf(
            1 to EndlessTier.BEGINNER, 5 to EndlessTier.BEGINNER,
            6 to EndlessTier.EASY, 15 to EndlessTier.EASY,
            16 to EndlessTier.MEDIUM, 30 to EndlessTier.MEDIUM,
            31 to EndlessTier.HARD, 50 to EndlessTier.HARD,
            51 to EndlessTier.EXPERT, 500 to EndlessTier.EXPERT
        )
        for ((number, tier) in expected) {
            assertEquals("puzzle #$number", tier, EndlessTier.forPuzzleNumber(number))
        }
    }

    @Test
    fun `difficulty never goes backwards as the player progresses`() {
        var previous = EndlessTier.forPuzzleNumber(1).ordinal
        for (number in 1..200) {
            val ordinal = EndlessTier.forPuzzleNumber(number).ordinal
            assertTrue("puzzle #$number stepped back a tier", ordinal >= previous)
            previous = ordinal
        }
    }

    @Test
    fun `expert is the ceiling and it is reached`() {
        assertEquals(EndlessTier.EXPERT, EndlessTier.forPuzzleNumber(Int.MAX_VALUE))
        assertEquals(EndlessTier.EXPERT, EndlessTier.entries.last())
    }

    @Test
    fun `a puzzle number below the first still gets a tier`() {
        // Nothing should be able to hand this an out-of-range number, but a
        // generator that threw here would take the whole mode down.
        assertEquals(EndlessTier.BEGINNER, EndlessTier.forPuzzleNumber(0))
        assertEquals(EndlessTier.BEGINNER, EndlessTier.forPuzzleNumber(-10))
    }

    @Test
    fun `no tier is allowed more than six columns`() {
        // The hard UI constraint: a seventh column puts a cell under the 48dp
        // tap target on a 320dp-wide phone.
        for (tier in EndlessTier.entries) {
            for (size in tier.sizes) {
                assertTrue(
                    "$tier offers ${size.rows}x${size.columns}",
                    size.columns <= BoardSize.MAX_COLUMNS
                )
                assertTrue("$tier offers $size, taller than the cap", size.rows <= BoardSize.MAX_ROWS)
            }
        }
    }

    @Test
    fun `a board size outside the caps cannot be constructed`() {
        for (bad in listOf(1 to 7, 10 to 6, 0 to 4, 4 to 0)) {
            val failed = runCatching { BoardSize(bad.first, bad.second) }.isFailure
            assertTrue("${bad.first}x${bad.second} should have been rejected", failed)
        }
    }

    @Test
    fun `every tier asks for fewer arrows than its smallest board has cells`() {
        // Otherwise the construction could never reach the target and every
        // board would ship as an under-filled fallback.
        for (tier in EndlessTier.entries) {
            val smallest = tier.sizes.minOf { it.cells }
            assertTrue(
                "$tier wants up to ${tier.arrows.last} arrows but its smallest board has $smallest cells",
                tier.arrows.last <= smallest
            )
        }
    }

    @Test
    fun `tier arrow bands step upwards`() {
        for ((lower, higher) in EndlessTier.entries.zipWithNext()) {
            assertTrue(
                "${lower.name} ${lower.arrows} does not step up to ${higher.name} ${higher.arrows}",
                higher.arrows.first >= lower.arrows.first && higher.arrows.last > lower.arrows.last
            )
        }
    }

    @Test
    fun `tier score bands step upwards`() {
        for ((lower, higher) in EndlessTier.entries.zipWithNext()) {
            assertTrue(
                "${lower.name} scores ${lower.baseRules.score}, ${higher.name} ${higher.baseRules.score}",
                higher.baseRules.score.start > lower.baseRules.score.start &&
                    higher.baseRules.score.endInclusive > lower.baseRules.score.endInclusive
            )
        }
    }

    @Test
    fun `every tier has a label and at least one board shape`() {
        for (tier in EndlessTier.entries) {
            assertTrue("$tier has no label", tier.label.isNotBlank())
            assertTrue("$tier offers no board shapes", tier.sizes.isNotEmpty())
        }
    }

    @Test
    fun `rulesFor allows a little slack under the target but not a lot`() {
        val tier = EndlessTier.EXPERT
        val rules = tier.rulesFor(target = 34)
        assertEquals(34 - EndlessTier.ARROW_SLACK, rules.arrows.first)
        assertEquals(tier.arrows.last, rules.arrows.last)

        // The floor never drops below the tier's own band, however low the
        // target: a 6-arrow Expert board is not shippable.
        assertEquals(tier.arrows.first, tier.rulesFor(target = 1).arrows.first)
    }

    @Test
    fun `the quality filter names the first rule a board breaks`() {
        val rules = EndlessTier.MEDIUM.baseRules
        val tooFew = metrics(arrowCount = 2)
        assertEquals(QualityRules.Rejection.ARROW_COUNT, rules.reject(tooFew))
        assertFalse(rules.accepts(tooFew))
    }

    @Test
    fun `the filter catches each boring-board symptom in turn`() {
        val rules = EndlessTier.MEDIUM.baseRules
        // Each case starts from a board that passes, then breaks exactly one
        // rule, so the mapping from symptom to rejection is pinned down.
        val passing = metrics()
        assertNull("the baseline fixture should pass the bar", rules.reject(passing))

        assertEquals(
            QualityRules.Rejection.TOO_MANY_FREE,
            rules.reject(passing.copy(freeAtStart = passing.arrowCount))
        )
        assertEquals(
            QualityRules.Rejection.TOO_FEW_DIRECTIONS,
            rules.reject(passing.copy(distinctDirections = 2))
        )
        assertEquals(
            QualityRules.Rejection.ONE_DIRECTION_DOMINATES,
            rules.reject(passing.copy(dominantDirectionShare = 0.95))
        )
        assertEquals(
            QualityRules.Rejection.DIRECTION_STRIPE,
            rules.reject(passing.copy(longestSameDirectionRun = 6))
        )
        assertEquals(
            QualityRules.Rejection.TOO_SHALLOW,
            rules.reject(passing.copy(peelDepth = 1))
        )
        assertEquals(
            QualityRules.Rejection.DEAD_BAND,
            rules.reject(passing.copy(rowCoverage = 0.2))
        )
        assertEquals(
            QualityRules.Rejection.EMPTY_QUADRANT,
            rules.reject(passing.copy(occupiedQuadrants = 2))
        )
    }

    @Test
    fun `fromKey round-trips a stored tier and falls back on anything else`() {
        for (tier in EndlessTier.entries) {
            assertEquals(tier, EndlessTier.fromKey(tier.name, puzzleNumber = 1))
        }
        // A key from a future version, a renamed tier, or nothing at all: the
        // puzzle number decides instead of the mode breaking.
        assertEquals(EndlessTier.MEDIUM, EndlessTier.fromKey(null, puzzleNumber = 20))
        assertEquals(EndlessTier.MEDIUM, EndlessTier.fromKey("NIGHTMARE", puzzleNumber = 20))
        assertEquals(EndlessTier.MEDIUM, EndlessTier.fromKey("", puzzleNumber = 20))
        assertEquals(EndlessTier.MEDIUM, EndlessTier.fromKey("medium", puzzleNumber = 20))
    }

    /**
     * A board that passes [EndlessTier.MEDIUM]'s bar, so a single field can be
     * changed to isolate one rule at a time.
     */
    private fun metrics(arrowCount: Int = 18) = PuzzleMetrics(
        rows = 6,
        columns = 6,
        arrowCount = arrowCount,
        freeAtStart = 3,
        peelDepth = 5,
        distinctDirections = 4,
        dominantDirectionShare = 0.35,
        longestSameDirectionRun = 2,
        rowCoverage = 1.0,
        columnCoverage = 1.0,
        occupiedQuadrants = 4,
        directionEntropy = 0.95
    )
}
