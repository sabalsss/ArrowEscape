package com.sabalapps.arrowescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveValidatorTest {

    private fun board(vararg tiles: ArrowTile) = tiles.toList()

    private fun tile(id: Int, row: Int, col: Int, direction: Direction) =
        ArrowTile(id, row, col, direction)

    @Test
    fun `lone arrow can always escape`() {
        val only = tile(0, 2, 2, Direction.UP)
        assertTrue(MoveValidator.canEscape(only, board(only)))
    }

    @Test
    fun `arrow is blocked by an arrow directly ahead`() {
        val mover = tile(0, 2, 1, Direction.UP)
        val blocker = tile(1, 1, 1, Direction.DOWN)
        assertFalse(MoveValidator.canEscape(mover, board(mover, blocker)))
    }

    @Test
    fun `arrow is blocked by a distant arrow in the same lane`() {
        val mover = tile(0, 4, 0, Direction.UP)
        val blocker = tile(1, 0, 0, Direction.LEFT)
        assertFalse(MoveValidator.canEscape(mover, board(mover, blocker)))
    }

    @Test
    fun `arrow ignores arrows behind it`() {
        val mover = tile(0, 2, 2, Direction.UP)
        val behind = tile(1, 4, 2, Direction.UP)
        assertTrue(MoveValidator.canEscape(mover, board(mover, behind)))
    }

    @Test
    fun `arrow ignores arrows in other rows and columns`() {
        val mover = tile(0, 2, 2, Direction.RIGHT)
        val offLane = tile(1, 3, 4, Direction.UP)
        assertTrue(MoveValidator.canEscape(mover, board(mover, offLane)))
    }

    @Test
    fun `each direction is blocked only on its own side`() {
        val centre = tile(0, 2, 2, Direction.LEFT)
        val left = tile(1, 2, 0, Direction.UP)
        val right = tile(2, 2, 4, Direction.UP)
        val above = tile(3, 0, 2, Direction.UP)
        val below = tile(4, 4, 2, Direction.UP)
        val all = board(centre, left, right, above, below)

        assertFalse(MoveValidator.canEscape(centre, all))
        assertTrue(MoveValidator.canEscape(centre.copy(direction = Direction.RIGHT), all) == false)
        assertFalse(MoveValidator.canEscape(centre.copy(direction = Direction.UP), all))
        assertFalse(MoveValidator.canEscape(centre.copy(direction = Direction.DOWN), all))

        // Remove the arrow on the left and a LEFT-facing centre arrow is free again.
        val withoutLeft = all - left
        assertTrue(MoveValidator.canEscape(centre, withoutLeft))
    }

    @Test
    fun `blockers are listed nearest first`() {
        val mover = tile(0, 0, 0, Direction.RIGHT)
        val far = tile(1, 0, 4, Direction.UP)
        val near = tile(2, 0, 1, Direction.UP)
        assertEquals(listOf(2, 1), MoveValidator.blockers(mover, board(mover, far, near)).map { it.id })
    }

    @Test
    fun `removing a blocker unblocks the arrow behind it`() {
        val mover = tile(0, 3, 1, Direction.UP)
        val blocker = tile(1, 1, 1, Direction.LEFT)
        val full = board(mover, blocker)
        assertFalse(MoveValidator.canEscape(mover, full))
        assertTrue(MoveValidator.canEscape(mover, full - blocker))
    }
}
