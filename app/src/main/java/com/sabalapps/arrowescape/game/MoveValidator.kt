package com.sabalapps.arrowescape.game

/**
 * Pure arrow-blocking rules. No Android or Compose types here so the logic can
 * be unit tested and reused as the game grows.
 */
object MoveValidator {

    /**
     * An arrow can escape only when no other arrow sits anywhere ahead of it in
     * the direction it points. "Ahead" means the straight line of cells from the
     * arrow to the edge of the board.
     */
    fun canEscape(tile: ArrowTile, board: List<ArrowTile>): Boolean =
        blockers(tile, board).isEmpty()

    /**
     * Every arrow standing in [tile]'s escape path, nearest first. Empty when the
     * path is clear.
     */
    fun blockers(tile: ArrowTile, board: List<ArrowTile>): List<ArrowTile> =
        board.asSequence()
            .filter { it.id != tile.id && isAhead(tile, it) }
            .sortedBy { distance(tile, it) }
            .toList()

    /** True when [other] lies on the straight line [tile] would fly along. */
    private fun isAhead(tile: ArrowTile, other: ArrowTile): Boolean =
        when (tile.direction) {
            Direction.UP -> other.col == tile.col && other.row < tile.row
            Direction.DOWN -> other.col == tile.col && other.row > tile.row
            Direction.LEFT -> other.row == tile.row && other.col < tile.col
            Direction.RIGHT -> other.row == tile.row && other.col > tile.col
        }

    private fun distance(tile: ArrowTile, other: ArrowTile): Int =
        when (tile.direction) {
            Direction.UP -> tile.row - other.row
            Direction.DOWN -> other.row - tile.row
            Direction.LEFT -> tile.col - other.col
            Direction.RIGHT -> other.col - tile.col
        }
}
