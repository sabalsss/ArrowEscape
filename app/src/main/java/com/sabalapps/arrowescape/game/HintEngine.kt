package com.sabalapps.arrowescape.game

/**
 * Picks the arrow a hint should point at.
 *
 * ## What a hint is allowed to be
 *
 * One arrow that can leave right now, and nothing else. It is not the solution:
 * the board is cleared by peeling layer after layer, and showing a single free
 * arrow out of the several that are usually free gives the player the *shape* of
 * the next move without doing the reading for them. Nothing here removes an
 * arrow, reveals the removal order, or marks which other arrows are free.
 *
 * Every candidate goes through [MoveValidator], the same rules the player plays
 * by, so a hint can never point at a blocked arrow — there is no second copy of
 * the blocking logic here to drift out of step with the game's.
 *
 * ## Which free arrow
 *
 * The one that frees the most other arrows, with the lowest id breaking ties.
 *
 * Both halves matter. Picking by usefulness means the hint teaches the rule the
 * player is missing — removals are what unblock things — rather than pointing at
 * whichever arrow happens to be nearest the top-left, which on most boards is
 * one already pointing off the edge and so teaches nothing. The id tie-break is
 * what makes the choice a pure function of the board: the same position always
 * produces the same hint, on any device and any run, which is what the tests
 * pin down.
 */
object HintEngine {

    /**
     * The arrow to highlight, or null when there is nothing to suggest — an
     * empty board, or a board with no free arrow at all.
     *
     * A board with arrows but no free arrow is unreachable in play: the
     * generator and the catalogue only ship solvable boards, and removals can
     * only ever free more arrows (see `BoardAnalysis`), so a stuck position
     * cannot be arrived at. It is handled rather than asserted because a hint
     * must never be able to crash the game.
     */
    fun hint(board: List<ArrowTile>): ArrowTile? {
        val free = board.filter { MoveValidator.canEscape(it, board) }
        if (free.isEmpty()) return null
        return free.maxWithOrNull(
            compareBy<ArrowTile> { freedBy(it, board) }.thenByDescending { it.id }
        )
    }

    /** How many arrows taking [tile] off the board would unblock. */
    private fun freedBy(tile: ArrowTile, board: List<ArrowTile>): Int {
        val without = board.filterNot { it.id == tile.id }
        // Only arrows that were blocked before can become free, so the
        // comparison is one-directional: count what is free now and was not.
        return without.count { other ->
            MoveValidator.canEscape(other, without) && !MoveValidator.canEscape(other, board)
        }
    }
}
