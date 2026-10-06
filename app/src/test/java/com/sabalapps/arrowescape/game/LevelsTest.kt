package com.sabalapps.arrowescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun `layout parsing maps glyphs to directions and positions`() {
        val level = Levels.fromLayout("t", listOf("^.", "<>"))
        assertEquals(2, level.rows)
        assertEquals(2, level.columns)
        assertEquals(3, level.arrows.size)
        assertEquals(ArrowTile(0, 0, 0, Direction.UP), level.arrows[0])
        assertEquals(ArrowTile(1, 1, 0, Direction.LEFT), level.arrows[1])
        assertEquals(ArrowTile(2, 1, 1, Direction.RIGHT), level.arrows[2])
    }

    @Test
    fun `arrow ids are unique`() {
        val ids = Levels.FIRST.arrows.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `arrows stay inside the grid`() {
        val level = Levels.FIRST
        level.arrows.forEach {
            assertTrue(it.row in 0 until level.rows)
            assertTrue(it.col in 0 until level.columns)
        }
    }

    @Test
    fun `no two arrows share a cell`() {
        val cells = Levels.FIRST.arrows.map { it.row to it.col }
        assertEquals(cells.size, cells.toSet().size)
    }

    /**
     * Depth-first search over removal orders: level 1 must be clearable without
     * ever tapping a blocked arrow.
     */
    @Test
    fun `the first level is solvable`() {
        val dead = HashSet<Set<Int>>()

        fun solve(board: List<ArrowTile>): Boolean {
            if (board.isEmpty()) return true
            val key = board.map { it.id }.toSet()
            if (!dead.add(key)) return false
            for (tile in board) {
                if (MoveValidator.canEscape(tile, board) && solve(board - tile)) return true
            }
            return false
        }

        assertTrue(solve(Levels.FIRST.arrows))
    }

    @Test
    fun `the first level needs planning - only a few arrows start free`() {
        val arrows = Levels.FIRST.arrows
        val free = arrows.count { MoveValidator.canEscape(it, arrows) }
        assertTrue("expected a few free arrows but found $free", free in 1..5)
    }
}
