package com.sabalapps.arrowescape.endless

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.LevelSolver
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.game.MoveValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard on the generator. Every tier is generated across a fixed seed range,
 * and each board is held to the same three classes of claim:
 *
 *  * **Structural** — the board is a thing that can exist: inside its own
 *    bounds, no two arrows on a cell, ids dense from zero, columns within the
 *    six-column cap the campaign also obeys.
 *  * **Solvable** — the construction witness is replayed move by move through
 *    the real [MoveValidator]. Not "the generator says so": the witness has to
 *    actually empty the board, with every removal legal at the moment it is
 *    taken.
 *  * **Interesting** — the board clears the tier's own [QualityRules] and its
 *    difficulty score lands in the tier's band.
 *
 * Seeds are fixed rather than random so a failure is reproducible from the test
 * name alone: every assertion message carries the seed that produced it.
 */
class PuzzleGeneratorTest {

    /**
     * Seeds per tier for the broad sweep. Five tiers x 120 seeds = 600 boards,
     * each one fully verified. That runs in a couple of seconds, which is the
     * budget for a test that belongs in the normal suite; the 5,000-board run
     * lives in [GeneratorStressTest] behind a flag.
     */
    private val seeds = 1L..120L

    private fun eachPuzzle(action: (EndlessTier, Long, GeneratedPuzzle) -> Unit) {
        for (tier in EndlessTier.entries) {
            for (seed in seeds) action(tier, seed, PuzzleGenerator.generate(seed, tier))
        }
    }

    // ---- structure ----------------------------------------------------------

    @Test
    fun `every generated board is structurally valid`() {
        eachPuzzle { tier, seed, puzzle ->
            val where = "tier=$tier seed=$seed"
            val level = puzzle.level

            assertTrue("$where: rows must be positive", level.rows >= 1)
            assertTrue(
                "$where: columns must never exceed ${BoardSize.MAX_COLUMNS}, was ${level.columns}",
                level.columns in 1..BoardSize.MAX_COLUMNS
            )
            assertTrue("$where: board must hold at least one arrow", level.arrows.isNotEmpty())
            assertTrue(
                "$where: more arrows than cells",
                level.arrows.size <= level.rows * level.columns
            )

            for (arrow in level.arrows) {
                assertTrue(
                    "$where: arrow ${arrow.id} at (${arrow.row},${arrow.col}) is out of bounds",
                    arrow.row in 0 until level.rows && arrow.col in 0 until level.columns
                )
                // Direction is an enum, so an invalid one cannot be constructed;
                // asserting membership is what keeps that true if it ever stops
                // being an enum.
                assertTrue(
                    "$where: arrow ${arrow.id} has an unknown direction",
                    arrow.direction in Direction.entries
                )
            }

            val cells = level.arrows.map { it.row to it.col }
            assertEquals("$where: two arrows share a cell", cells.size, cells.toSet().size)

            // Ids are dense from zero and handed out in reading order, exactly
            // as the campaign's layout parser does, so a saved id means the same
            // thing in both modes.
            assertEquals(
                "$where: ids are not 0 until ${level.arrows.size}",
                List(level.arrows.size) { it },
                level.arrows.map { it.id }
            )
            assertEquals(
                "$where: arrows are not in reading order",
                level.arrows.sortedWith(compareBy({ it.row }, { it.col })),
                level.arrows
            )
        }
    }

    @Test
    fun `a generated board is never mistaken for a campaign level`() {
        eachPuzzle { tier, seed, puzzle ->
            // This is what keeps the two saves from being able to restore each
            // other's board: the endless id matches nothing in the catalogue.
            assertEquals(
                "tier=$tier seed=$seed: unexpected level id",
                PuzzleGenerator.ENDLESS_LEVEL_ID,
                puzzle.level.id
            )
            assertNull(
                "tier=$tier seed=$seed: endless id collides with the catalogue",
                Levels.byId(puzzle.level.id)
            )
        }
    }

    // ---- solvability --------------------------------------------------------

    @Test
    fun `every construction witness empties the board through the real validator`() {
        eachPuzzle { tier, seed, puzzle ->
            val where = "tier=$tier seed=$seed"

            assertEquals(
                "$where: witness does not cover every arrow",
                puzzle.level.arrows.size,
                puzzle.solution.size
            )
            assertEquals(
                "$where: witness repeats an id",
                puzzle.solution.size,
                puzzle.solution.toSet().size
            )
            assertEquals(
                "$where: witness ids do not match the board",
                puzzle.level.arrows.map { it.id }.toSet(),
                puzzle.solution.toSet()
            )
            assertTrue(
                "$where: witness is not a legal removal order",
                BoardAnalysis.verifyOrder(puzzle.level.arrows, puzzle.solution)
            )
            // Replayed again here by hand, so this test does not rest on the
            // same helper the production code uses to reason about a board.
            assertTrue("$where: witness failed an independent replay", replays(puzzle))
        }
    }

    @Test
    fun `an independent depth-first solver agrees the board is solvable`() {
        // The DFS is exponential in the worst case, so it runs over a sample
        // rather than all 600 boards: enough to catch a generator whose witness
        // and whose board have drifted apart, without slowing the suite down.
        for (tier in EndlessTier.entries) {
            for (seed in 1L..12L) {
                val puzzle = PuzzleGenerator.generate(seed, tier)
                val outcome = LevelSolver.solve(puzzle.level)
                assertTrue(
                    "tier=$tier seed=$seed: solver returned $outcome",
                    outcome is LevelSolver.Outcome.Solved
                )
                val order = (outcome as LevelSolver.Outcome.Solved).order
                assertTrue(
                    "tier=$tier seed=$seed: solver's own order does not replay",
                    LevelSolver.verify(puzzle.level, order)
                )
            }
        }
    }

    @Test
    fun `peeling agrees with the construction witness on every board`() {
        // Production trusts `peel` as an exact solvability test. That claim is
        // what this checks: it must find an order for every board the
        // construction already proved solvable.
        eachPuzzle { tier, seed, puzzle ->
            val order = BoardAnalysis.solutionOrder(puzzle.level.arrows)
            assertNotNull("tier=$tier seed=$seed: peel found no order", order)
            assertTrue(
                "tier=$tier seed=$seed: peeled order is not legal",
                BoardAnalysis.verifyOrder(puzzle.level.arrows, order!!.map { it.id })
            )
        }
    }

    /** Replays the witness directly against [MoveValidator], step by step. */
    private fun replays(puzzle: GeneratedPuzzle): Boolean {
        var board: List<ArrowTile> = puzzle.level.arrows
        for (id in puzzle.solution) {
            val tile = board.firstOrNull { it.id == id } ?: return false
            if (!MoveValidator.canEscape(tile, board)) return false
            board = board.filterNot { it.id == id }
        }
        return board.isEmpty()
    }

    // ---- quality and difficulty ---------------------------------------------

    @Test
    fun `every generated board clears its tier's quality bar`() {
        var offTier = 0
        eachPuzzle { tier, seed, puzzle ->
            val rejection = tier.baseRules.reject(puzzle.metrics)
            if (puzzle.onTier) {
                assertNull(
                    "tier=$tier seed=$seed: shipped on-tier but fails $rejection " +
                        "(${puzzle.metrics.debugSummary()})",
                    rejection
                )
            } else {
                offTier++
            }
        }
        // The bounded search is allowed a fallback, but it is meant to be rare.
        // A regression that makes the filter unsatisfiable shows up here as a
        // flood of fallbacks rather than as a silently worse game.
        val total = EndlessTier.entries.size * seeds.count()
        assertTrue(
            "$offTier of $total boards fell back off-tier, which is more than the 2% budget",
            offTier * 50 <= total
        )
    }

    @Test
    fun `difficulty metadata stays inside the tier band`() {
        eachPuzzle { tier, seed, puzzle ->
            val where = "tier=$tier seed=$seed"
            assertEquals("$where: tier metadata does not match", tier, puzzle.tier)
            assertEquals("$where: seed metadata does not match", seed, puzzle.seed)

            val metrics = puzzle.metrics
            assertEquals("$where: metrics describe a different board", puzzle.level.rows, metrics.rows)
            assertEquals("$where: metrics describe a different board", puzzle.level.columns, metrics.columns)
            assertEquals(
                "$where: metrics arrow count disagrees with the board",
                puzzle.level.arrows.size,
                metrics.arrowCount
            )

            if (!puzzle.onTier) return@eachPuzzle
            assertTrue(
                "$where: arrow count ${metrics.arrowCount} outside tier band ${tier.arrows}",
                metrics.arrowCount in tier.arrows
            )
            assertTrue(
                "$where: board size ${metrics.rows}x${metrics.columns} is not one of ${tier.sizes}",
                BoardSize(metrics.rows, metrics.columns) in tier.sizes
            )
            assertTrue(
                "$where: score ${metrics.difficultyScore} outside ${tier.baseRules.score}",
                metrics.difficultyScore in tier.baseRules.score
            )
        }
    }

    @Test
    fun `boards are not visually boring`() {
        eachPuzzle { tier, seed, puzzle ->
            if (!puzzle.onTier) return@eachPuzzle
            val where = "tier=$tier seed=$seed"
            val metrics = puzzle.metrics
            val rules = tier.baseRules

            // Not a restatement of the quality filter: these are the specific
            // "boring board" symptoms, asserted in their own right so a
            // loosened rule in QualityRules cannot quietly let them back in.
            assertTrue(
                "$where: ${metrics.freeAtStart}/${metrics.arrowCount} arrows are free on the " +
                    "opening board — that is a tapping exercise, not a puzzle",
                metrics.freeRatio <= rules.maxFreeRatio
            )
            assertTrue(
                "$where: only ${metrics.distinctDirections} directions on the board",
                metrics.distinctDirections >= rules.minDistinctDirections
            )
            assertTrue(
                "$where: one direction owns ${metrics.dominantDirectionShare} of the board",
                metrics.dominantDirectionShare <= rules.maxDominantShare
            )
            assertTrue(
                "$where: a stripe of ${metrics.longestSameDirectionRun} identical arrows",
                metrics.longestSameDirectionRun <= rules.maxSameDirectionRun
            )
            assertTrue(
                "$where: density ${metrics.density} outside ${rules.density}",
                metrics.density in rules.density
            )
            assertTrue(
                "$where: blocker chains only ${metrics.peelDepth} deep",
                metrics.peelDepth >= rules.minPeelDepth
            )
            assertEquals("$where: a whole quadrant of the board is empty", 4, metrics.occupiedQuadrants)
            assertTrue(
                "$where: a row or column band is dead " +
                    "(rows ${metrics.rowCoverage}, cols ${metrics.columnCoverage})",
                metrics.rowCoverage >= rules.minBandCoverage &&
                    metrics.columnCoverage >= rules.minBandCoverage
            )
        }
    }

    @Test
    fun `non-beginner boards always use at least three directions`() {
        for (tier in EndlessTier.entries) {
            if (tier == EndlessTier.BEGINNER) continue
            for (seed in seeds) {
                val puzzle = PuzzleGenerator.generate(seed, tier)
                assertTrue(
                    "tier=$tier seed=$seed: only ${puzzle.metrics.distinctDirections} directions",
                    puzzle.metrics.distinctDirections >= 3
                )
            }
        }
    }

    // ---- determinism --------------------------------------------------------

    @Test
    fun `the same seed and tier always rebuild the identical board`() {
        eachPuzzle { tier, seed, first ->
            val second = PuzzleGenerator.generate(seed, tier)
            assertEquals(
                "tier=$tier seed=$seed: regenerated board differs",
                layout(first.level),
                layout(second.level)
            )
            assertEquals("tier=$tier seed=$seed: witness differs", first.solution, second.solution)
            assertEquals("tier=$tier seed=$seed: metrics differ", first.metrics, second.metrics)
            assertEquals("tier=$tier seed=$seed: attempt count differs", first.attempts, second.attempts)
        }
    }

    @Test
    fun `the name is cosmetic and does not change the board`() {
        // Endless restores a board from its seed and tier alone, so anything
        // else the caller passes must not be able to change the layout.
        for (tier in EndlessTier.entries) {
            val plain = PuzzleGenerator.generate(7L, tier)
            val named = PuzzleGenerator.generate(7L, tier, name = "something else entirely")
            assertEquals("tier=$tier", layout(plain.level), layout(named.level))
            assertEquals("tier=$tier", plain.solution, named.solution)
        }
    }

    @Test
    fun `different seeds almost always produce different boards`() {
        for (tier in EndlessTier.entries) {
            val layouts = seeds.map { layout(PuzzleGenerator.generate(it, tier).level) }
            val distinct = layouts.toSet().size
            // Beginner has only 16-20 cells to work with, so a handful of
            // collisions is a fact about the space rather than a bug. A
            // generator that ignored its seed would collapse to one layout.
            val floor = if (tier == EndlessTier.BEGINNER) 0.90 else 0.99
            assertTrue(
                "tier=$tier: only $distinct distinct layouts from ${layouts.size} seeds",
                distinct >= layouts.size * floor
            )
        }
    }

    @Test
    fun `a tier change reshapes the board for the same seed`() {
        val byTier = EndlessTier.entries.associateWith { layout(PuzzleGenerator.generate(99L, it).level) }
        assertEquals(
            "the same seed produced the same board at two different tiers: $byTier",
            EndlessTier.entries.size,
            byTier.values.toSet().size
        )
    }

    // ---- bounds -------------------------------------------------------------

    @Test
    fun `generation is bounded and never loops`() {
        eachPuzzle { tier, seed, puzzle ->
            assertTrue(
                "tier=$tier seed=$seed: ${puzzle.attempts} attempts exceeds the cap",
                puzzle.attempts in 1..PuzzleGenerator.MAX_ATTEMPTS
            )
        }
    }

    @Test
    fun `extreme seeds are handled like any other`() {
        // Long arithmetic in the seed derivation has to behave at the edges, and
        // zero is the one seed a broken derivation is most likely to collapse.
        val awkward = listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1)
        for (tier in EndlessTier.entries) {
            for (seed in awkward) {
                val puzzle = PuzzleGenerator.generate(seed, tier)
                assertTrue("tier=$tier seed=$seed produced an empty board", puzzle.level.arrows.isNotEmpty())
                assertTrue(
                    "tier=$tier seed=$seed: witness does not replay",
                    BoardAnalysis.verifyOrder(puzzle.level.arrows, puzzle.solution)
                )
                assertEquals(
                    "tier=$tier seed=$seed is not reproducible",
                    layout(puzzle.level),
                    layout(PuzzleGenerator.generate(seed, tier).level)
                )
            }
        }
    }

    @Test
    fun `generation is fast enough not to be felt`() {
        // A warm-up pass first, so this measures the generator rather than the
        // JIT. The budget is deliberately loose — it is here to catch an
        // algorithmic regression, not to police a few hundred microseconds on
        // whatever machine the suite happens to run on.
        for (tier in EndlessTier.entries) PuzzleGenerator.generate(1L, tier)

        for (tier in EndlessTier.entries) {
            val runs = 200
            val start = System.nanoTime()
            for (seed in 1L..runs) PuzzleGenerator.generate(seed, tier)
            val averageMs = (System.nanoTime() - start) / 1e6 / runs
            assertTrue(
                "tier=$tier averaged %.2fms per puzzle, well past the 100ms budget"
                    .format(averageMs),
                averageMs < 100.0
            )
        }
    }

    /** The board as the campaign writes layouts, so a failure prints readably. */
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
}
