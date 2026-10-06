package com.sabalapps.arrowescape.game

/**
 * A single arrow sitting on the board.
 *
 * [id] is stable for the lifetime of a level so the UI can animate a specific
 * arrow even while the rest of the board changes.
 */
data class ArrowTile(
    val id: Int,
    val row: Int,
    val col: Int,
    val direction: Direction
)
