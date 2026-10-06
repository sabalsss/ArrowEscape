package com.sabalapps.arrowescape.game

/**
 * A solver for the level catalogue. **Test and development only** — it lives in
 * the test source set, so it cannot be reached from the app at all, let alone
 * used to play a level for the player.
 *
 * It searches removal orders depth-first, remembering states it has already
 * proved hopeless. It returns the order it found rather than a bare boolean, so
 * a caller can replay the witness against [MoveValidator] and check the claim
 * instead of trusting this file.
 *
 * The search is bounded. An unsolvable level added in future fails the tests
 * with [Outcome.Unsolvable] or [Outcome.Exhausted]; neither ever hangs a build.
 */
object LevelSolver {

    sealed interface Outcome {
        /** [order] empties the board, every step legal at the moment it is taken. */
        data class Solved(val order: List<ArrowTile>) : Outcome

        /** Proved: no removal order clears this board. */
        data object Unsolvable : Outcome

        /** Hit the node budget before deciding. Treated as a failure. */
        data object Exhausted : Outcome
    }

    const val DEFAULT_NODE_BUDGET = 200_000

    fun solve(level: Level, nodeBudget: Int = DEFAULT_NODE_BUDGET): Outcome =
        solve(level.arrows, nodeBudget)

    fun solve(board: List<ArrowTile>, nodeBudget: Int = DEFAULT_NODE_BUDGET): Outcome {
        val hopeless = HashSet<Set<Int>>()
        val order = ArrayList<ArrowTile>(board.size)
        var nodes = 0
        var exhausted = false

        fun search(remaining: List<ArrowTile>): Boolean {
            if (remaining.isEmpty()) return true
            if (nodes++ >= nodeBudget) {
                exhausted = true
                return false
            }
            val key = remaining.mapTo(HashSet()) { it.id }
            if (key in hopeless) return false

            for (tile in remaining) {
                if (!MoveValidator.canEscape(tile, remaining)) continue
                order += tile
                if (search(remaining.filterNot { it.id == tile.id })) return true
                order.removeAt(order.lastIndex)
                if (exhausted) return false
            }
            hopeless += key
            return false
        }

        return when {
            search(board) -> Outcome.Solved(order.toList())
            exhausted -> Outcome.Exhausted
            else -> Outcome.Unsolvable
        }
    }

    /**
     * Replays [order] against the real rules and returns true only if every
     * removal was legal when it happened and the board ends up empty. This is
     * what turns "the solver said so" into a checked fact.
     */
    fun verify(level: Level, order: List<ArrowTile>): Boolean {
        if (order.size != level.arrows.size) return false
        if (order.map { it.id }.toSet() != level.arrows.map { it.id }.toSet()) return false

        var board = level.arrows
        for (tile in order) {
            val live = board.firstOrNull { it.id == tile.id } ?: return false
            if (!MoveValidator.canEscape(live, board)) return false
            board = board.filterNot { it.id == live.id }
        }
        return board.isEmpty()
    }

    /** Arrows that could be tapped right now on a full board. */
    fun initiallyValidMoves(level: Level): Int =
        level.arrows.count { MoveValidator.canEscape(it, level.arrows) }
}
