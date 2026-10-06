package com.sabalapps.arrowescape.game

/**
 * Which arrows became able to leave *because of* the arrow that just left.
 *
 * Pure Kotlin, like the rest of the `game` package, and built entirely on
 * [MoveValidator] — there is deliberately no second opinion anywhere in this
 * app about whether an arrow can escape.
 *
 * This exists for presentation only. The board uses it to acknowledge what the
 * player's last move *changed*, which is a different thing from marking which
 * arrows are free: the set is the difference between two boards, it is produced
 * once per successful removal, and it says nothing about the arrows that were
 * already free before the tap. The explicit move suggestion is still
 * [HintEngine] and nothing else, and none of this reaches accessibility
 * semantics — see the invariant that an arrow is never announced as escapable.
 *
 * Cost is O(n²) per removal on a board of at most 36 arrows, computed once on a
 * tap rather than per frame.
 */
object EscapeAnalysis {

    /** The ids of every arrow on [board] whose path out is clear right now. */
    fun freeIds(board: List<ArrowTile>): Set<Int> {
        val free = LinkedHashSet<Int>()
        for (tile in board) {
            if (MoveValidator.canEscape(tile, board)) free += tile.id
        }
        return free
    }

    /**
     * `freeIds(after) - freeIds(before)`, fused into one pass over [after].
     *
     * The arrow that was removed cannot appear in the result because it is not
     * on [after] at all, and an arrow that was already free stays out of it
     * because its id is in the `before` set — which is the whole point. An
     * unsuccessful tap changes no arrow's path, so callers never ask.
     */
    fun newlyFreed(before: List<ArrowTile>, after: List<ArrowTile>): Set<Int> {
        if (after.isEmpty()) return emptySet()
        val freeBefore = freeIds(before)
        val freed = LinkedHashSet<Int>()
        for (tile in after) {
            if (tile.id !in freeBefore && MoveValidator.canEscape(tile, after)) freed += tile.id
        }
        return freed
    }
}
