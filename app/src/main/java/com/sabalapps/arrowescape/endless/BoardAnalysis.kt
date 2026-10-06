package com.sabalapps.arrowescape.endless

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.MoveValidator

/**
 * Read-only measurements of a board, used to score a generated puzzle's
 * difficulty and to judge whether it is interesting enough to show a player.
 *
 * Everything here goes through [MoveValidator], so these numbers describe the
 * board under the same rules the player plays by — there is no second copy of
 * the blocking logic to drift out of step.
 *
 * ## Why peeling is enough
 *
 * Removing an arrow can never *create* a blocker: an arrow is blocked only by
 * arrows that are present, so taking one off the board can only ever free
 * others. The set of escapable arrows is therefore monotone — once an arrow is
 * free it stays free for the rest of the game. Two things follow, and both are
 * load-bearing here:
 *
 *  * **Greedy order never matters.** If a board can be cleared at all, then
 *    repeatedly removing *every* currently free arrow clears it. No search, no
 *    backtracking, no node budget. [peel] is an exact solvability test in
 *    O(arrows²) — which is why production can afford to run it and why the
 *    generator never needs a DFS solver.
 *  * **Layers are a real measure of depth.** The arrows freed on the nth pass
 *    are exactly those that needed n-1 removals ahead of them, so the number of
 *    passes is the length of the longest blocker chain on the board.
 *
 * The test suite still proves solvability the hard way — an independent DFS in
 * `LevelSolver` plus a witness replayed through [MoveValidator] — so this
 * argument is checked rather than trusted.
 */
object BoardAnalysis {

    /** Arrows with a clear path out right now. */
    fun freeArrows(board: List<ArrowTile>): List<ArrowTile> =
        board.filter { MoveValidator.canEscape(it, board) }

    /**
     * The board split into removal passes: pass 0 is every arrow that is free on
     * the full board, pass 1 every arrow freed by taking all of those off, and
     * so on. Null when the board cannot be cleared — i.e. a pass came back empty
     * with arrows still standing. An empty board peels to an empty list.
     */
    fun peel(board: List<ArrowTile>): List<List<ArrowTile>>? {
        val layers = ArrayList<List<ArrowTile>>()
        var remaining = board
        while (remaining.isNotEmpty()) {
            val layer = freeArrows(remaining)
            if (layer.isEmpty()) return null
            layers += layer
            val removed = layer.mapTo(HashSet()) { it.id }
            remaining = remaining.filterNot { it.id in removed }
        }
        return layers
    }

    /**
     * A legal removal order, or null when none exists. Flattening [peel] is
     * already one: every arrow in a pass is free before that pass starts, and
     * removals only ever free more arrows, so taking them in any order within
     * the pass is legal.
     */
    fun solutionOrder(board: List<ArrowTile>): List<ArrowTile>? = peel(board)?.flatten()

    /** True when every arrow can be cleared. Exact, not a heuristic — see above. */
    fun isSolvable(board: List<ArrowTile>): Boolean = peel(board) != null

    /**
     * Replays [order] under the real rules: every removal must be legal at the
     * moment it happens, and the board must end up empty. This is what turns
     * "the generator says so" into a checked fact.
     */
    fun verifyOrder(board: List<ArrowTile>, order: List<Int>): Boolean {
        if (order.size != board.size) return false
        if (order.toSet().size != order.size) return false

        var live = board
        for (id in order) {
            val tile = live.firstOrNull { it.id == id } ?: return false
            if (!MoveValidator.canEscape(tile, live)) return false
            live = live.filterNot { it.id == id }
        }
        return live.isEmpty()
    }

    /** Longest run of same-direction arrows sitting side by side in a row or column. */
    fun longestSameDirectionRun(board: List<ArrowTile>, rows: Int, columns: Int): Int {
        val grid = HashMap<Long, Direction>(board.size * 2)
        board.forEach { grid[key(it.row, it.col)] = it.direction }

        var longest = 0
        fun walk(count: Int, cell: (Int) -> Pair<Int, Int>) {
            var run = 0
            var previous: Direction? = null
            for (step in 0 until count) {
                val (row, col) = cell(step)
                val direction = grid[key(row, col)]
                run = if (direction != null && direction == previous) run + 1 else if (direction != null) 1 else 0
                previous = direction
                if (run > longest) longest = run
            }
        }

        for (row in 0 until rows) walk(columns) { col -> row to col }
        for (col in 0 until columns) walk(rows) { row -> row to col }
        return longest
    }

    private fun key(row: Int, col: Int): Long = row.toLong() * 64L + col.toLong()
}
