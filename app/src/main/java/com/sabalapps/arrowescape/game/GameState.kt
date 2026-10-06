package com.sabalapps.arrowescape.game

/** How the last tap was resolved, so the UI knows which animation to play. */
sealed interface TapResult {
    /** The arrow flew off the board. */
    data class Escaped(val tile: ArrowTile) : TapResult

    /** The arrow was blocked and a life was spent. */
    data class Blocked(val tile: ArrowTile) : TapResult

    /** Tap ignored (game already over, or unknown arrow). */
    data object Ignored : TapResult
}

enum class GameStatus { PLAYING, WON, LOST }

/**
 * Immutable snapshot of a level in progress. All rule changes go through
 * [onArrowTapped], which returns the next state plus what just happened.
 */
data class GameState(
    val level: Level,
    val arrows: List<ArrowTile>,
    val lives: Int,
    val status: GameStatus = GameStatus.PLAYING
) {
    val rows: Int get() = level.rows
    val columns: Int get() = level.columns

    fun arrowAt(row: Int, col: Int): ArrowTile? =
        arrows.firstOrNull { it.row == row && it.col == col }

    fun canEscape(tile: ArrowTile): Boolean = MoveValidator.canEscape(tile, arrows)

    fun onArrowTapped(id: Int): Pair<GameState, TapResult> {
        if (status != GameStatus.PLAYING) return this to TapResult.Ignored
        val tile = arrows.firstOrNull { it.id == id } ?: return this to TapResult.Ignored

        if (!canEscape(tile)) {
            val remainingLives = (lives - 1).coerceAtLeast(0)
            val next = copy(
                lives = remainingLives,
                status = if (remainingLives == 0) GameStatus.LOST else GameStatus.PLAYING
            )
            return next to TapResult.Blocked(tile)
        }

        val remaining = arrows.filterNot { it.id == tile.id }
        val next = copy(
            arrows = remaining,
            status = if (remaining.isEmpty()) GameStatus.WON else GameStatus.PLAYING
        )
        return next to TapResult.Escaped(tile)
    }

    companion object {
        const val STARTING_LIVES = 3

        fun newGame(level: Level = Levels.FIRST, lives: Int = STARTING_LIVES) =
            GameState(level = level, arrows = level.arrows, lives = lives)
    }
}
