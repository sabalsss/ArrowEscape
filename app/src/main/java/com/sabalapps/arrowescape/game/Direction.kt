package com.sabalapps.arrowescape.game

/**
 * The four directions an arrow can point. [dRow] / [dCol] are the step taken
 * when walking the board in that direction (row 0 is the top row).
 */
enum class Direction(val dRow: Int, val dCol: Int, val glyph: String) {
    UP(-1, 0, "↑"),
    DOWN(1, 0, "↓"),
    LEFT(0, -1, "←"),
    RIGHT(0, 1, "→")
}
