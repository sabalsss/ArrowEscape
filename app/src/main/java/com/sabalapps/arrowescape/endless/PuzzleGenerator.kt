package com.sabalapps.arrowescape.endless

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.shape.ShapeIdentity

/**
 * A finished, playable puzzle plus everything needed to reason about it.
 *
 * [solution] is the witness: arrow ids in an order that empties the board, every
 * step legal at the moment it is taken. It is the reverse of the order the
 * arrows were laid down in, which is what makes it valid rather than hopeful —
 * see [PuzzleGenerator].
 */
data class GeneratedPuzzle(
    val level: Level,
    val seed: Long,
    val tier: EndlessTier,
    val solution: List<Int>,
    val metrics: PuzzleMetrics,
    /** How many construction attempts the quality filter turned down, plus one. */
    val attempts: Int,
    /** False when the bounded search ran out and the closest attempt was shipped. */
    val onTier: Boolean,
    /**
     * The hidden picture this board was built to, or null for a board with none (the original
     * abstract generator). Carries the picture's *name*, so it is held by the ViewModel and
     * published only once the board is won — never handed to a screen while it is in play.
     */
    val shape: ShapeIdentity? = null
) {
    /** For `Log.d` in debug builds. Never surfaced in a release UI. */
    fun debugSummary(): String =
        "seed=$seed tier=${tier.name} attempts=$attempts onTier=$onTier :: ${metrics.debugSummary()}"
}

/**
 * Builds endless puzzles that are solvable by construction.
 *
 * ## Why construction, not search
 *
 * The board is built backwards. Starting from an empty grid, each arrow is laid
 * on a cell whose escape path — the straight line from that cell to the edge in
 * the direction it points — is clear *at that moment*. Taking the arrows off in
 * the reverse of the order they went down therefore replays exactly those board
 * states, so every removal is legal and the board empties. The witness is free;
 * nothing is ever generated and then tested for solvability.
 *
 * That leaves only one real problem: a board built this way is *valid* but not
 * automatically *interesting*. So the construction is steered rather than
 * random — see [chooseWeight] — and the result is then held to the tier's
 * [QualityRules]. A rejection costs one more attempt, never correctness, and the
 * attempt count is hard-capped at [MAX_ATTEMPTS] so generation cannot run long.
 * When the cap is hit, the closest attempt is shipped and flagged
 * [GeneratedPuzzle.onTier]`= false`.
 *
 * Generation is a pure function of `(seed, tier)`: the same pair always yields
 * the same board, which is what lets Endless Mode persist a puzzle as a single
 * `Long` and what makes a generator bug reproducible from a log line.
 */
object PuzzleGenerator {

    /**
     * Attempts before the best-so-far is shipped. Each attempt is a full
     * construction, so this is also the worst-case cost of one call.
     */
    const val MAX_ATTEMPTS = 24

    /** The board for `(seed, tier)`. Always solvable, always within the column cap. */
    fun generate(seed: Long, tier: EndlessTier, name: String = "Endless · ${tier.label}"): GeneratedPuzzle {
        var best: Attempt? = null
        var bestDistance = Double.MAX_VALUE

        for (attempt in 0 until MAX_ATTEMPTS) {
            val built = construct(
                rng = SeededRandom(SeededRandom.derive(seed, tier.ordinal.toLong(), attempt.toLong())),
                tier = tier,
                name = name
            ) ?: continue

            if (built.rules.accepts(built.metrics)) {
                return built.toPuzzle(seed, tier, attempts = attempt + 1, onTier = true)
            }

            // Keep whichever near-miss sits closest to the middle of the tier's
            // score band, so the fallback is the most on-tier board seen and not
            // merely the last one tried.
            val mid = (tier.baseRules.score.start + tier.baseRules.score.endInclusive) / 2
            val distance = kotlin.math.abs(built.metrics.difficultyScore - mid)
            if (distance < bestDistance) {
                best = built
                bestDistance = distance
            }
        }

        // Unreachable in practice — the first placement on an empty board is
        // always legal, so every attempt yields a board — but the fallback is
        // written out rather than asserted, because a player must never be shown
        // an exception. `onTier = false` is what the debug log reports.
        val fallback = best ?: construct(
            rng = SeededRandom(SeededRandom.derive(seed, tier.ordinal.toLong(), -1L)),
            tier = tier,
            name = name
        ) ?: error("generator produced no board for seed=$seed tier=$tier")

        return fallback.toPuzzle(seed, tier, attempts = MAX_ATTEMPTS, onTier = false)
    }

    // ---- one attempt --------------------------------------------------------

    /** A completed construction, with the bar it was built to aim at. */
    private class Attempt(
        val level: Level,
        val solution: List<Int>,
        val metrics: PuzzleMetrics,
        val rules: QualityRules
    ) {
        fun toPuzzle(seed: Long, tier: EndlessTier, attempts: Int, onTier: Boolean) =
            GeneratedPuzzle(
                level = level,
                seed = seed,
                tier = tier,
                solution = solution,
                metrics = metrics,
                attempts = attempts,
                onTier = onTier
            )
    }

    /**
     * Lays arrows down one at a time until the target count is reached or the
     * board runs out of legal placements. Returns null only if the board came
     * out unmeasurable, which would mean the construction invariant was broken.
     */
    private fun construct(rng: SeededRandom, tier: EndlessTier, name: String): Attempt? {
        val size = rng.pick(tier.sizes)
        val target = rng.nextInt(tier.arrows).coerceAtMost(size.cells)
        val board = Grid(size)
        val rules = tier.rulesFor(target)

        while (board.count < target) {
            val choice = board.chooseNext(rng, rules.maxSameDirectionRun) ?: break
            board.place(choice)
        }

        val level = board.toLevel(name)
        val metrics = PuzzleMetrics.measure(level) ?: return null
        return Attempt(
            level = level,
            solution = board.solutionWitness(),
            metrics = metrics,
            rules = rules
        )
    }

    // ---- the board under construction ---------------------------------------

    /** A candidate placement: cell plus the direction to point it. */
    private data class Spot(val row: Int, val col: Int, val direction: Direction)

    /**
     * Mutable scratch board. Holds the occupancy grid, the placement order and
     * which placed arrows are still free, so every candidate can be scored in a
     * few array lookups rather than by re-deriving the board.
     */
    private class Grid(val size: BoardSize) {
        private val cells = arrayOfNulls<Direction>(size.cells)
        private val order = ArrayList<Spot>(size.cells)

        /** Parallel to [order]: whether that arrow still has a clear path out. */
        private val stillFree = ArrayList<Boolean>(size.cells)

        private val directionCount = IntArray(Direction.entries.size)
        private val rowCount = IntArray(size.rows)
        private val colCount = IntArray(size.columns)

        val count: Int get() = order.size

        fun place(spot: Spot) {
            cells[index(spot.row, spot.col)] = spot.direction

            // Anything already down whose escape path runs over this cell has
            // just been blocked — permanently, since arrows are only ever added
            // during construction.
            for (i in order.indices) {
                if (stillFree[i] && covers(order[i], spot.row, spot.col)) stillFree[i] = false
            }

            order += spot
            // Legal by selection: its path was clear, so it is free right now.
            stillFree += true
            directionCount[spot.direction.ordinal]++
            rowCount[spot.row]++
            colCount[spot.col]++
        }

        /**
         * Picks the next placement, weighted by [chooseWeight], or null when no
         * legal placement is left. Candidates are enumerated in a fixed order so
         * the weighted draw is reproducible from the seed alone.
         */
        fun chooseNext(rng: SeededRandom, maxRun: Int): Spot? {
            val blockGain = blockGainPerCell()
            val busiestDirection = directionCount.max()

            val spots = ArrayList<Spot>(size.cells)
            val weights = ArrayList<Double>(size.cells)
            var total = 0.0

            for (row in 0 until size.rows) {
                for (col in 0 until size.columns) {
                    if (cells[index(row, col)] != null) continue
                    for (direction in Direction.entries) {
                        if (!pathIsClear(row, col, direction)) continue
                        val weight = chooseWeight(
                            row = row,
                            col = col,
                            direction = direction,
                            blocksFreed = blockGain[index(row, col)],
                            busiestDirection = busiestDirection,
                            maxRun = maxRun
                        )
                        spots += Spot(row, col, direction)
                        weights += weight
                        total += weight
                    }
                }
            }

            if (spots.isEmpty()) return null

            var draw = rng.nextDouble() * total
            for (i in spots.indices) {
                draw -= weights[i]
                if (draw <= 0.0) return spots[i]
            }
            return spots.last() // only reachable through floating-point drift
        }

        /**
         * How attractive a placement is. Correctness does not depend on any of
         * this — every candidate offered here is already legal — so these are
         * purely the things that make a board worth looking at:
         *
         *  * **Blocking what is free** is worth the most. It is the only lever on
         *    the opening free-arrow count, which is the single biggest difference
         *    between a puzzle and a tapping exercise.
         *  * **Room ahead** is worth something: an arrow with empty cells in
         *    front of it can still be blocked by a later placement, while one
         *    pointing straight off the edge is free for the rest of the game.
         *  * **The thin glyph** is nudged, to keep all four directions in play.
         *  * **Empty rows and columns** are nudged, so the board fills out
         *    instead of clumping into one corner.
         *  * **Runs** are pushed down hard once they would pass the tier's cap,
         *    which is what stops solid stripes of one glyph forming.
         */
        private fun chooseWeight(
            row: Int,
            col: Int,
            direction: Direction,
            blocksFreed: Int,
            busiestDirection: Int,
            maxRun: Int
        ): Double {
            var weight = 1.0
            weight += BLOCK_BONUS * blocksFreed
            weight += ROOM_BONUS * distanceToEdge(row, col, direction)
            weight += BALANCE_BONUS * (busiestDirection - directionCount[direction.ordinal])
            if (rowCount[row] == 0) weight += SPREAD_BONUS
            if (colCount[col] == 0) weight += SPREAD_BONUS
            weight -= RUN_PENALTY * maxOf(0, resultingRun(row, col, direction) - maxRun)
            // Squared so a clearly better placement is clearly more likely, while
            // every legal placement keeps a non-zero chance of being drawn.
            val floored = weight.coerceAtLeast(MIN_WEIGHT)
            return floored * floored
        }

        /** For each empty cell, how many currently-free arrows a placement there would block. */
        private fun blockGainPerCell(): IntArray {
            val gain = IntArray(size.cells)
            for (i in order.indices) {
                if (!stillFree[i]) continue
                val spot = order[i]
                var row = spot.row + spot.direction.dRow
                var col = spot.col + spot.direction.dCol
                while (row in 0 until size.rows && col in 0 until size.columns) {
                    // The whole path of a free arrow is empty by definition, so
                    // every cell on it is a candidate that would block it.
                    gain[index(row, col)]++
                    row += spot.direction.dRow
                    col += spot.direction.dCol
                }
            }
            return gain
        }

        /** The run of [direction] this placement would sit in, along either axis. */
        private fun resultingRun(row: Int, col: Int, direction: Direction): Int {
            fun reach(dRow: Int, dCol: Int): Int {
                var steps = 0
                var r = row + dRow
                var c = col + dCol
                while (r in 0 until size.rows && c in 0 until size.columns &&
                    cells[index(r, c)] == direction
                ) {
                    steps++
                    r += dRow
                    c += dCol
                }
                return steps
            }
            val horizontal = 1 + reach(0, -1) + reach(0, 1)
            val vertical = 1 + reach(-1, 0) + reach(1, 0)
            return maxOf(horizontal, vertical)
        }

        /** True when nothing stands between this cell and the edge in [direction]. */
        private fun pathIsClear(row: Int, col: Int, direction: Direction): Boolean {
            var r = row + direction.dRow
            var c = col + direction.dCol
            while (r in 0 until size.rows && c in 0 until size.columns) {
                if (cells[index(r, c)] != null) return false
                r += direction.dRow
                c += direction.dCol
            }
            return true
        }

        /** Whether [spot]'s escape path runs over the given cell. */
        private fun covers(spot: Spot, row: Int, col: Int): Boolean = when (spot.direction) {
            Direction.UP -> col == spot.col && row < spot.row
            Direction.DOWN -> col == spot.col && row > spot.row
            Direction.LEFT -> row == spot.row && col < spot.col
            Direction.RIGHT -> row == spot.row && col > spot.col
        }

        private fun distanceToEdge(row: Int, col: Int, direction: Direction): Int =
            when (direction) {
                Direction.UP -> row
                Direction.DOWN -> size.rows - 1 - row
                Direction.LEFT -> col
                Direction.RIGHT -> size.columns - 1 - col
            }

        private fun index(row: Int, col: Int): Int = row * size.columns + col

        /**
         * The finished board. Ids are handed out in reading order — top-left
         * first — exactly as the campaign's layout parser does, so an id means
         * the same thing in a save file, a debug log and a hand-written level.
         */
        fun toLevel(name: String): Level = Level(
            name = name,
            rows = size.rows,
            columns = size.columns,
            arrows = readingOrder().mapIndexed { id, spot ->
                ArrowTile(id = id, row = spot.row, col = spot.col, direction = spot.direction)
            },
            id = ENDLESS_LEVEL_ID
        )

        /**
         * Arrow ids in a legal removal order: the reverse of the order they were
         * placed in. At the moment an arrow went down its path was clear, and the
         * board at that moment is exactly the board left once everything placed
         * after it has been taken off again.
         */
        fun solutionWitness(): List<Int> {
            val idByCell = HashMap<Int, Int>(order.size * 2)
            readingOrder().forEachIndexed { id, spot -> idByCell[index(spot.row, spot.col)] = id }
            return order.asReversed().map { idByCell.getValue(index(it.row, it.col)) }
        }

        private fun readingOrder(): List<Spot> = order.sortedWith(
            compareBy({ it.row }, { it.col })
        )
    }

    /**
     * Generated boards are not in the catalogue, so they carry an id no
     * catalogue lookup will ever match. That is what keeps a campaign save from
     * being able to restore an endless board, and vice versa.
     */
    const val ENDLESS_LEVEL_ID = 0

    /**
     * Construction weights, tuned by generating a few thousand boards per tier
     * and watching the quality filter's rejection rate. The pair that matters is
     * [BLOCK_BONUS] and [ROOM_BONUS]: between them they decide how much of the
     * board is tappable on the opening move, which is the rule that used to turn
     * down three Expert boards in four before [ROOM_BONUS] was raised.
     */
    private const val BLOCK_BONUS = 7.0
    private const val ROOM_BONUS = 6.0
    private const val BALANCE_BONUS = 1.0
    private const val SPREAD_BONUS = 0.5
    private const val RUN_PENALTY = 8.0
    private const val MIN_WEIGHT = 0.05
}
