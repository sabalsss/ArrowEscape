package com.sabalapps.arrowescape.endless

import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.LevelSolver
import com.sabalapps.arrowescape.game.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BoardAnalysis] is load-bearing twice over: production uses `peel` as an exact
 * solvability test, and the difficulty score uses its layer count as the depth
 * of the longest blocker chain. Both claims rest on removals only ever *freeing*
 * arrows, so both are checked here against the independent depth-first solver
 * rather than taken on the argument alone.
 */
class BoardAnalysisTest {

    private fun board(vararg layout: String): Level =
        Levels.fromLayout(name = "fixture", layout = layout.toList())

    @Test
    fun `an empty board peels to nothing`() {
        val layers = BoardAnalysis.peel(emptyList())
        assertEquals(emptyList<List<Any>>(), layers)
        assertTrue(BoardAnalysis.isSolvable(emptyList()))
    }

    @Test
    fun `an arrow pointing off the edge is free`() {
        val level = board("^...")
        assertEquals(1, BoardAnalysis.freeArrows(level.arrows).size)
    }

    @Test
    fun `an arrow behind another is not free until the blocker leaves`() {
        // Both point up in the same column: the lower one is blocked by the
        // upper one, so it takes two passes to clear.
        val level = board("^...", "^...")
        val layers = requireNotNull(BoardAnalysis.peel(level.arrows))
        assertEquals("expected two passes, got ${layers.map { it.size }}", 2, layers.size)
        assertEquals(1, layers[0].size)
        assertEquals(1, layers[1].size)
    }

    @Test
    fun `a chain of blockers is exactly as deep as the chain`() {
        // Four arrows stacked in one column, all pointing up: a four-link chain.
        val level = board("^...", "^...", "^...", "^...")
        assertEquals(4, requireNotNull(BoardAnalysis.peel(level.arrows)).size)
    }

    @Test
    fun `a mutual standoff is unsolvable`() {
        // Two arrows pointing at each other block each other for ever, and no
        // removal order can start.
        val level = board("><..")
        assertNull("a standoff should not peel", BoardAnalysis.peel(level.arrows))
        assertFalse(BoardAnalysis.isSolvable(level.arrows))
        assertNull(BoardAnalysis.solutionOrder(level.arrows))
    }

    @Test
    fun `peel and the depth-first solver agree on every campaign level`() {
        // The campaign is a set of boards that are known-solvable by other
        // means, so it doubles as a fixture for the peeling argument.
        for (level in Levels.ALL) {
            val order = BoardAnalysis.solutionOrder(level.arrows)
            assertNotNull("Level ${level.id}: peel found no order", order)
            assertTrue(
                "Level ${level.id}: peeled order is not legal",
                BoardAnalysis.verifyOrder(level.arrows, order!!.map { it.id })
            )
            assertTrue(
                "Level ${level.id}: solver disagrees",
                LevelSolver.solve(level) is LevelSolver.Outcome.Solved
            )
        }
    }

    @Test
    fun `verifyOrder rejects an order that is not legal`() {
        val level = board("^...", "^...")
        val blocked = level.arrows.sortedByDescending { it.row }.map { it.id }
        // Taking the lower arrow first is illegal: the upper one is in its way.
        assertFalse(BoardAnalysis.verifyOrder(level.arrows, blocked))
    }

    @Test
    fun `verifyOrder rejects a short, a long and a repeating order`() {
        val level = board("^...", "^...")
        val valid = requireNotNull(BoardAnalysis.solutionOrder(level.arrows)).map { it.id }
        assertTrue(BoardAnalysis.verifyOrder(level.arrows, valid))

        assertFalse("a partial order should not verify", BoardAnalysis.verifyOrder(level.arrows, valid.dropLast(1)))
        assertFalse("a repeated id should not verify", BoardAnalysis.verifyOrder(level.arrows, listOf(valid[0], valid[0])))
        assertFalse("an unknown id should not verify", BoardAnalysis.verifyOrder(level.arrows, listOf(valid[0], 99)))
    }

    @Test
    fun `longestSameDirectionRun measures rows and columns`() {
        val horizontal = board(">>>.", "....")
        assertEquals(3, BoardAnalysis.longestSameDirectionRun(horizontal.arrows, 2, 4))

        val vertical = board("^...", "^...", "^...")
        assertEquals(3, BoardAnalysis.longestSameDirectionRun(vertical.arrows, 3, 4))
    }

    @Test
    fun `a run is broken by a gap and by a different direction`() {
        val gapped = board(">.>>")
        assertEquals("a gap should break the run", 2, BoardAnalysis.longestSameDirectionRun(gapped.arrows, 1, 4))

        val mixed = board(">><>")
        assertEquals("a different glyph should break the run", 2, BoardAnalysis.longestSameDirectionRun(mixed.arrows, 1, 4))
    }

    @Test
    fun `a single arrow is a run of one and an empty board a run of none`() {
        assertEquals(1, BoardAnalysis.longestSameDirectionRun(board("^...").arrows, 1, 4))
        assertEquals(0, BoardAnalysis.longestSameDirectionRun(emptyList(), 4, 4))
    }
}
