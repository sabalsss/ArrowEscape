package com.sabalapps.arrowescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {

    private fun stateOf(vararg tiles: ArrowTile, lives: Int = GameState.STARTING_LIVES) =
        GameState(
            level = Level("test", rows = 5, columns = 5, arrows = tiles.toList()),
            arrows = tiles.toList(),
            lives = lives
        )

    @Test
    fun `tapping a free arrow removes it and keeps lives`() {
        val free = ArrowTile(0, 4, 0, Direction.DOWN)
        val other = ArrowTile(1, 0, 3, Direction.UP)
        val (next, result) = stateOf(free, other).onArrowTapped(0)

        assertTrue(result is TapResult.Escaped)
        assertEquals(listOf(1), next.arrows.map { it.id })
        assertEquals(3, next.lives)
        assertEquals(GameStatus.PLAYING, next.status)
    }

    @Test
    fun `tapping a blocked arrow costs a life and keeps the arrow`() {
        val blocked = ArrowTile(0, 4, 0, Direction.UP)
        val blocker = ArrowTile(1, 1, 0, Direction.LEFT)
        val (next, result) = stateOf(blocked, blocker).onArrowTapped(0)

        assertTrue(result is TapResult.Blocked)
        assertEquals(2, next.arrows.size)
        assertEquals(2, next.lives)
        assertEquals(GameStatus.PLAYING, next.status)
    }

    @Test
    fun `removing the last arrow wins the level`() {
        val (next, result) = stateOf(ArrowTile(0, 2, 2, Direction.UP)).onArrowTapped(0)
        assertTrue(result is TapResult.Escaped)
        assertTrue(next.arrows.isEmpty())
        assertEquals(GameStatus.WON, next.status)
    }

    @Test
    fun `three blocked taps lose the game`() {
        val blocked = ArrowTile(0, 4, 0, Direction.UP)
        val blocker = ArrowTile(1, 1, 0, Direction.LEFT)
        var state = stateOf(blocked, blocker)
        repeat(3) { state = state.onArrowTapped(0).first }

        assertEquals(0, state.lives)
        assertEquals(GameStatus.LOST, state.status)
    }

    @Test
    fun `taps are ignored once the game is over`() {
        val blocked = ArrowTile(0, 4, 0, Direction.UP)
        val blocker = ArrowTile(1, 1, 0, Direction.LEFT)
        var state = stateOf(blocked, blocker, lives = 1)
        state = state.onArrowTapped(0).first
        assertEquals(GameStatus.LOST, state.status)

        val (after, result) = state.onArrowTapped(1)
        assertEquals(TapResult.Ignored, result)
        assertEquals(state, after)
    }

    @Test
    fun `tapping an unknown id is ignored`() {
        val state = stateOf(ArrowTile(0, 0, 0, Direction.UP))
        assertEquals(TapResult.Ignored, state.onArrowTapped(99).second)
    }

    /**
     * The default board is whichever level the catalogue starts with, so this
     * checks the shape of a new game rather than one level's dimensions.
     */
    @Test
    fun `new game starts with the first level and three lives`() {
        val state = GameState.newGame()
        val first = LevelProgression.first
        assertEquals(GameState.STARTING_LIVES, state.lives)
        assertEquals(first.rows, state.rows)
        assertEquals(first.columns, state.columns)
        assertEquals(first.arrows, state.arrows)
        assertTrue(state.arrows.isNotEmpty())
        assertEquals(GameStatus.PLAYING, state.status)
    }

    @Test
    fun `arrowAt finds occupied cells only`() {
        val tile = ArrowTile(0, 1, 2, Direction.LEFT)
        val state = stateOf(tile)
        assertEquals(tile, state.arrowAt(1, 2))
        assertNull(state.arrowAt(2, 1))
    }
}
