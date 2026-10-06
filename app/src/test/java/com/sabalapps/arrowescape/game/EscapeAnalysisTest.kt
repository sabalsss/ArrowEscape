package com.sabalapps.arrowescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a removal *changed*, which is the only thing the board's "these opened
 * up" reaction is allowed to say. The arrows that were already free are not part
 * of it — that is the difference between acknowledging a move and handing the
 * player a solver.
 */
class EscapeAnalysisTest {

    private fun board(vararg layout: String): List<ArrowTile> =
        Levels.fromLayout("test", layout.toList()).arrows

    private fun without(board: List<ArrowTile>, id: Int): List<ArrowTile> =
        board.filterNot { it.id == id }

    @Test
    fun `a removal reports the arrow it unblocked`() {
        // id0 is free at the top of column 1; id1 is stuck behind it.
        val before = board(
            ".^.",
            ".^."
        )

        assertEquals(setOf(1), EscapeAnalysis.newlyFreed(before, without(before, 0)))
    }

    @Test
    fun `an arrow that was already free is not reported as newly freed`() {
        // id1 has had a clear path out of column 2 the whole time; only id2,
        // which was behind id0, actually changed.
        val before = board(
            ".^^",
            ".^."
        )
        assertTrue(MoveValidator.canEscape(before.first { it.id == 1 }, before))

        assertEquals(setOf(2), EscapeAnalysis.newlyFreed(before, without(before, 0)))
    }

    @Test
    fun `one removal can free arrows in two directions at once`() {
        // id1 blocks id0 along the row and id2 up the column.
        val before = board(
            ">^.",
            ".^."
        )

        assertEquals(setOf(0, 2), EscapeAnalysis.newlyFreed(before, without(before, 1)))
    }

    @Test
    fun `a removal that changes nothing reports nothing`() {
        val before = board(".^^")

        assertEquals(emptySet<Int>(), EscapeAnalysis.newlyFreed(before, without(before, 0)))
    }

    @Test
    fun `clearing the last arrow frees nothing`() {
        val before = board(".^.")

        assertEquals(emptySet<Int>(), EscapeAnalysis.newlyFreed(before, without(before, 0)))
    }

    @Test
    fun `the removed arrow is never reported as newly freed`() {
        val before = board(
            ">^.",
            ".^."
        )

        val freed = EscapeAnalysis.newlyFreed(before, without(before, 1))

        assertFalse(1 in freed)
    }

    @Test
    fun `freeIds agrees with MoveValidator`() {
        val board = board(
            ">^.",
            ".^."
        )

        assertEquals(
            board.filter { MoveValidator.canEscape(it, board) }.map { it.id }.toSet(),
            EscapeAnalysis.freeIds(board)
        )
    }

    /**
     * The invariant across the whole catalogue, cleared the honest way: every id
     * reported was blocked before the move and can leave after it, and nothing
     * that was already free sneaks in.
     */
    @Test
    fun `every campaign level reports only arrows that genuinely changed`() {
        for (level in Levels.ALL) {
            var board = level.arrows
            var guard = 0
            while (board.isNotEmpty()) {
                check(guard++ < 500) { "level ${level.id} did not clear" }
                val free = board.first { MoveValidator.canEscape(it, board) }
                val after = board.filterNot { it.id == free.id }

                for (id in EscapeAnalysis.newlyFreed(board, after)) {
                    val wasBlocked = board.first { it.id == id }
                    assertFalse(
                        "level ${level.id}: arrow $id was already free",
                        MoveValidator.canEscape(wasBlocked, board)
                    )
                    assertTrue(
                        "level ${level.id}: arrow $id cannot actually escape",
                        MoveValidator.canEscape(after.first { it.id == id }, after)
                    )
                }
                board = after
            }
        }
    }
}
