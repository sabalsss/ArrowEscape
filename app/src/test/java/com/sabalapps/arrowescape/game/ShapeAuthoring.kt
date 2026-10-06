package com.sabalapps.arrowescape.game

import com.sabalapps.arrowescape.endless.BoardAnalysis
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.random.Random

/**
 * Development-only aid for authoring Campaign boards whose occupied cells are a
 * fixed silhouette (see [CampaignShapes]). **It lives in the test source set, so
 * it cannot be reached from the app, and it is not a second rules engine:** every
 * legality decision goes through the real [MoveValidator], and every board it
 * returns has been replayed through [BoardAnalysis.verifyOrder] before the caller
 * sees it. The catalogue tests re-prove the result independently on every build.
 *
 * ## The same idea as the Endless generator, pointed at a mask
 *
 * The board is built backwards. Arrows are laid on the silhouette's cells one at
 * a time; each is given a direction whose escape path is clear *at that moment*
 * (checked with [MoveValidator]). Taking them off in the reverse of the order
 * they were laid down replays exactly those states, so a legal removal order
 * exists by construction — nothing is generated and then tested for solvability.
 *
 * What differs from `PuzzleGenerator` is that the *cells* are not chosen freely:
 * the silhouette decides them, so the only freedom is the order and the
 * directions. Both are steered, not random:
 *
 *  * **Blocking what is free** is rewarded — it is the lever on how many arrows
 *    can leave on the opening move.
 *  * **Pointing down a line that still holds unplaced cells** is rewarded. Those
 *    cells are placed later, so they will sit in front of the arrow: that is a
 *    dependency. An arrow pointing down an empty line is free for the entire game,
 *    which is how the opening free-arrow count is controlled.
 *  * **Direction balance and runs** keep all four glyphs in play and stop stripes.
 *  * **Urgency** places a cell that is running out of legal directions before the
 *    arrows around it close its last one — and a placement that would strand
 *    another cell is refused, so an attempt can only fail by running out of moves.
 *
 * Correctness never depends on any of that steering; it only decides which of the
 * many valid boards for a silhouette this attempt produces. A [Candidate] is then
 * measured, and the caller picks the one that fits the level's place in the curve.
 */
object ShapeAuthoring {

    // ---- steering -------------------------------------------------------------

    /** Per-attempt steering. Any combination still yields a valid board. */
    data class Knobs(
        val blockBonus: Double,
        val roomBonus: Double,
        val freeFactor: Double,
        val balanceBonus: Double,
        val runPenalty: Double,
        val urgencyBonus: Double
    ) {
        companion object {
            fun sample(rng: Random) = Knobs(
                blockBonus = 1.0 + rng.nextDouble() * 24.0,
                roomBonus = 0.3 + rng.nextDouble() * 9.7,
                freeFactor = 0.02 + rng.nextDouble() * 0.98,
                balanceBonus = rng.nextDouble() * 2.0,
                runPenalty = 4.0 + rng.nextDouble() * 6.0,
                urgencyBonus = rng.nextDouble() * 3.0
            )
        }
    }

    /** A finished board for a silhouette, with its proof and measurements. */
    class Candidate(
        val shape: CampaignShape,
        val seed: Long,
        val knobs: Knobs,
        /** The board as `^ v < >` / `.` rows, ready to paste into [Levels]. */
        val layout: List<String>,
        val level: Level,
        val metrics: Metrics
    )

    // ---- construction -----------------------------------------------------------

    private data class Cell(val row: Int, val col: Int)

    /**
     * One construction attempt, or null when it ran out of legal moves (a cell
     * with no clear direction left). The returned board is verified: its
     * placement order, reversed, is replayed through the real rules.
     */
    fun construct(shape: CampaignShape, seed: Long, knobs: Knobs): Pair<List<String>, Level>? {
        val rng = Random(seed)
        val remaining = LinkedHashSet<Cell>(shape.cells.map { Cell(it.first, it.second) })
        val placed = ArrayList<ArrowTile>(shape.size)
        val directionAt = HashMap<Cell, Direction>()
        val directionCount = IntArray(Direction.entries.size)

        while (remaining.isNotEmpty()) {
            // What each unplaced cell could still be, under the real rules.
            val legal = HashMap<Cell, List<Direction>>(remaining.size * 2)
            for (cell in remaining) {
                val options = Direction.entries.filter { isClear(cell, it, placed) }
                if (options.isEmpty()) return null
                legal[cell] = options
            }

            val gain = blockGain(placed, shape.height, shape.width)
            val busiest = directionCount.max()

            val spots = ArrayList<Pair<Cell, Direction>>()
            val weights = ArrayList<Double>()
            for (cell in remaining) {
                val options = legal.getValue(cell)
                for (direction in options) {
                    val room = unplacedAhead(cell, direction, remaining, shape.height, shape.width)
                    var weight = 1.0
                    weight += knobs.blockBonus * gain[cell.row][cell.col]
                    weight += knobs.roomBonus * minOf(room, 4)
                    if (room == 0) weight *= knobs.freeFactor
                    weight += knobs.balanceBonus * (busiest - directionCount[direction.ordinal])
                    weight -= knobs.runPenalty * maxOf(0, runThrough(cell, direction, directionAt) - 2)
                    weight += knobs.urgencyBonus * (Direction.entries.size - options.size)
                    val floored = weight.coerceAtLeast(0.05)
                    spots += cell to direction
                    weights += floored * floored
                }
            }

            // Draw until a placement that leaves every other cell a way out.
            var chosen: Pair<Cell, Direction>? = null
            while (spots.isNotEmpty() && chosen == null) {
                val pick = draw(rng, weights)
                val (cell, direction) = spots[pick]
                val tile = ArrowTile(placed.size, cell.row, cell.col, direction)
                val after = placed + tile
                val strands = remaining.any { other ->
                    other != cell &&
                        (other.row == cell.row || other.col == cell.col) &&
                        Direction.entries.none { isClear(other, it, after) }
                }
                if (strands) {
                    spots.removeAt(pick)
                    weights.removeAt(pick)
                } else {
                    chosen = spots[pick]
                }
            }
            val (cell, direction) = chosen ?: return null

            placed += ArrowTile(placed.size, cell.row, cell.col, direction)
            directionAt[cell] = direction
            directionCount[direction.ordinal]++
            remaining -= cell
        }

        val layout = layoutOf(shape, placed)
        val level = Levels.fromLayout("Level ${shape.levelId}", layout, shape.levelId)

        // The proof: the reverse of the placement order must clear the real board.
        val idByCell = level.arrows.associate { (it.row to it.col) to it.id }
        val witness = placed.asReversed().map { idByCell.getValue(it.row to it.col) }
        check(BoardAnalysis.verifyOrder(level.arrows, witness)) {
            "construction for level ${shape.levelId} seed $seed produced an unclearable board"
        }
        return layout to level
    }

    /** A clear path for [cell] pointing [direction], judged by the real rules. */
    private fun isClear(cell: Cell, direction: Direction, placed: List<ArrowTile>): Boolean {
        val tile = ArrowTile(-1, cell.row, cell.col, direction)
        return MoveValidator.canEscape(tile, placed + tile)
    }

    /** For every cell, how many currently free arrows a placement there would block. */
    private fun blockGain(placed: List<ArrowTile>, rows: Int, cols: Int): Array<IntArray> {
        val gain = Array(rows) { IntArray(cols) }
        for (tile in placed) {
            if (!MoveValidator.canEscape(tile, placed)) continue
            var row = tile.row + tile.direction.dRow
            var col = tile.col + tile.direction.dCol
            while (row in 0 until rows && col in 0 until cols) {
                gain[row][col]++
                row += tile.direction.dRow
                col += tile.direction.dCol
            }
        }
        return gain
    }

    /** Unplaced cells on the line [cell] would point down: its future blockers. */
    private fun unplacedAhead(
        cell: Cell,
        direction: Direction,
        remaining: Set<Cell>,
        rows: Int,
        cols: Int
    ): Int {
        var count = 0
        var row = cell.row + direction.dRow
        var col = cell.col + direction.dCol
        while (row in 0 until rows && col in 0 until cols) {
            if (Cell(row, col) in remaining) count++
            row += direction.dRow
            col += direction.dCol
        }
        return count
    }

    /** The run of [direction] this placement would sit in, along either axis. */
    private fun runThrough(cell: Cell, direction: Direction, at: Map<Cell, Direction>): Int {
        fun reach(dRow: Int, dCol: Int): Int {
            var steps = 0
            var next = Cell(cell.row + dRow, cell.col + dCol)
            while (at[next] == direction) {
                steps++
                next = Cell(next.row + dRow, next.col + dCol)
            }
            return steps
        }
        return maxOf(1 + reach(0, -1) + reach(0, 1), 1 + reach(-1, 0) + reach(1, 0))
    }

    private fun draw(rng: Random, weights: List<Double>): Int {
        var draw = rng.nextDouble() * weights.sum()
        for (i in weights.indices) {
            draw -= weights[i]
            if (draw <= 0.0) return i
        }
        return weights.lastIndex
    }

    private fun layoutOf(shape: CampaignShape, placed: List<ArrowTile>): List<String> {
        val glyph = mapOf(
            Direction.UP to '^', Direction.DOWN to 'v', Direction.LEFT to '<', Direction.RIGHT to '>'
        )
        val grid = Array(shape.height) { CharArray(shape.width) { '.' } }
        placed.forEach { grid[it.row][it.col] = glyph.getValue(it.direction) }
        return grid.map { String(it) }
    }

    // ---- measurement ------------------------------------------------------------

    /** Everything the authoring tool and the shape report read off a board. */
    data class Metrics(
        val arrows: Int,
        val free: Int,
        /** Peel passes: the longest blocker chain on the board. */
        val depth: Int,
        val layerSizes: List<Int>,
        val effort: Double,
        val directionCounts: Map<Direction, Int>,
        val dominantShare: Double,
        val longestRun: Int,
        /** Opening arrows that block nobody, ever — mechanically irrelevant. */
        val irrelevantOpeners: Int,
        /** Most arrows a single opening arrow frees by leaving. */
        val bestUnlock: Int,
        /** Opening arrows whose removal frees at least one other arrow. */
        val usefulOpeners: Int,
        /** Quadrants of the board holding at least one opening arrow. */
        val openerQuadrants: Int
    )

    fun measure(level: Level, withEffort: Boolean = true): Metrics {
        val board = level.arrows
        val layers = requireNotNull(BoardAnalysis.peel(board)) { "${level.name} is not solvable" }
        val free = layers.first()

        val counts = Direction.entries.associateWith { d -> board.count { it.direction == d } }
        val blockers = board.associate { tile -> tile.id to MoveValidator.blockers(tile, board).map { it.id } }
        val blocksSomeone = blockers.values.flatten().toSet()

        val unlocks = free.associate { tile -> tile.id to freedBy(tile, board) }
        val midRow = (level.rows - 1) / 2.0
        val midCol = (level.columns - 1) / 2.0
        val quadrants = free.map { (if (it.row <= midRow) 0 else 2) + (if (it.col <= midCol) 0 else 1) }.toSet()

        return Metrics(
            arrows = board.size,
            free = free.size,
            depth = layers.size,
            layerSizes = layers.map { it.size },
            effort = if (withEffort) scanningEffort(level) else Double.NaN,
            directionCounts = counts,
            dominantShare = counts.values.max().toDouble() / board.size,
            longestRun = BoardAnalysis.longestSameDirectionRun(board, level.rows, level.columns),
            irrelevantOpeners = free.count { it.id !in blocksSomeone },
            bestUnlock = unlocks.values.maxOrNull() ?: 0,
            usefulOpeners = unlocks.values.count { it > 0 },
            openerQuadrants = quadrants.size
        )
    }

    /** How many arrows taking [tile] off the board would unblock. */
    private fun freedBy(tile: ArrowTile, board: List<ArrowTile>): Int {
        val without = board.filterNot { it.id == tile.id }
        return without.count { other ->
            MoveValidator.canEscape(other, without) && !MoveValidator.canEscape(other, board)
        }
    }

    /**
     * The scanning-effort measure `LevelDifficultyReportTest` uses, reproduced
     * exactly (same seed, same sample count, same draw order) so a board can be
     * ranked against the curve before it is written into the catalogue. The test
     * stays the authority; this only lets the tool aim at it.
     */
    fun scanningEffort(level: Level, samples: Int = 120): Double {
        val random = Random(seed = level.id.toLong() * 31 + 7)
        var total = 0.0
        repeat(samples) {
            var board = level.arrows
            var run = 0.0
            while (board.isNotEmpty()) {
                val free = board.filter { MoveValidator.canEscape(it, board) }
                if (free.isEmpty()) return Double.NaN
                run += board.size.toDouble() / free.size
                val taken = free[random.nextInt(free.size)]
                board = board.filterNot { it.id == taken.id }
            }
            total += run
        }
        return total / samples
    }

    // ---- judging ----------------------------------------------------------------

    /** The longest run of consecutive single-arrow passes anywhere in a peel. */
    fun singleStreak(layerSizes: List<Int>): Int {
        var best = 0
        var run = 0
        for (size in layerSizes) {
            run = if (size == 1) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    /**
     * The share of moves, over fixed-seed random clears, at which exactly one arrow
     * is free — a move with nothing to choose between. High means the clear is
     * mostly a forced sequence however wide the opening was.
     */
    fun forcedShare(level: Level, samples: Int = 120): Double {
        val random = Random(seed = level.id.toLong() * 131 + 3)
        var forced = 0
        var steps = 0
        repeat(samples) {
            var board = level.arrows
            while (board.isNotEmpty()) {
                val free = board.filter { MoveValidator.canEscape(it, board) }
                if (free.isEmpty()) return Double.NaN
                if (free.size == 1) forced++
                steps++
                val taken = free[random.nextInt(free.size)]
                board = board.filterNot { it.id == taken.id }
            }
        }
        return forced.toDouble() / steps
    }

    /**
     * The pacing bar, applied on top of [passesBar] when a level is *re-authored*
     * for how it plays rather than for how it is built: no stretch of the clear is
     * a single-file shuffle. Not applied to the levels shipped before it existed —
     * several of them would fail it, and that is a finding, not a defect to chase.
     *
     * [forcedShare] is checked only when supplied (it costs a sampled clear).
     */
    fun passesPacing(m: Metrics, forcedShare: Double? = null): Boolean {
        if (m.arrows < 12) return true
        if (singleStreak(m.layerSizes) > 2) return false
        if (m.layerSizes.takeLastWhile { it == 1 }.size > 2) return false
        if (forcedShare != null && forcedShare > MAX_FORCED_SHARE) return false
        return true
    }

    /** Most moves of a re-authored clear that may have exactly one free arrow. */
    const val MAX_FORCED_SHARE = 0.24

    /**
     * What "a puzzle, not a tapping exercise" means for a board of [arrows]
     * arrows, written down so the tool applies it the same way everywhere. These
     * are authoring bars, stricter in places than the catalogue tests — the tests
     * only guard against the unacceptable; this chooses among the acceptable.
     */
    fun passesBar(m: Metrics, levelId: Int): Boolean {
        val n = m.arrows
        val minFree = if (n <= 10) 2 else maxOf(3, ceil(0.15 * n).toInt())
        val ratio = when {
            // Level 1 teaches the rule, so it opens with at most three free arrows.
            levelId == 1 -> 0.40
            levelId == 2 -> 0.50
            levelId <= 6 -> 0.40
            levelId <= 12 -> 0.36
            levelId <= 18 -> 0.33
            levelId <= 24 -> 0.30
            else -> 0.27
        }
        if (m.free !in minFree..floor(ratio * n).toInt()) return false
        if (m.directionCounts.values.count { it > 0 } < (if (n >= 10) 4 else 3)) return false
        if (m.dominantShare > (if (n >= 10) 0.42 else 0.5)) return false
        if (m.longestRun > (if (n >= 10) 3 else 2)) return false
        val minDepth = when {
            n <= 10 -> 3
            n <= 16 -> 4
            n <= 24 -> 5
            n <= 30 -> 6
            else -> 7
        }
        if (m.depth < minDepth) return false
        if (m.irrelevantOpeners > maxOf(1, m.free / 2)) return false
        if (n >= 12 && m.openerQuadrants < 2) return false
        if (n >= 22 && m.openerQuadrants < 3) return false
        if (m.usefulOpeners < 1) return false
        // A clear that ends in a long single-file shuffle has stopped being a puzzle.
        if (n >= 16 && m.layerSizes.takeLastWhile { it == 1 }.size > (if (n >= 26) 3 else 2)) return false
        return true
    }

    /**
     * How good a passing board is, lower being better: a soft preference for a
     * peeling-apart feel — several useful openers spread over the picture, a hub
     * that frees more than one arrow, chains that run deep, and few arrows that
     * are mechanically irrelevant.
     */
    fun flaws(m: Metrics): Double {
        val n = m.arrows
        var flaws = 0.0
        flaws += m.irrelevantOpeners * 1.0
        flaws += maxOf(0, 2 - m.bestUnlock) * 1.0
        // A big board should have at least one hub: an arrow whose leaving frees three.
        if (n >= 20) flaws += maxOf(0, 3 - m.bestUnlock) * 1.5
        flaws += maxOf(0, 2 - m.usefulOpeners) * 1.0
        flaws += (m.dominantShare - 0.30).coerceAtLeast(0.0) * 8.0
        flaws += maxOf(0, m.longestRun - 2) * 0.7
        flaws += (n / 6.0 - m.depth).coerceAtLeast(0.0) * 0.4
        flaws -= m.openerQuadrants * 0.3
        return flaws
    }

    // ---- searching --------------------------------------------------------------

    /**
     * Up to [tries] constructions for [shape], measured, keeping those that clear
     * the authoring bar. Seeds are `baseSeed + i`, so a candidate is reproducible
     * from `(shape, seed)` alone. Runs in parallel; each attempt is independent.
     */
    fun search(shape: CampaignShape, tries: Int, baseSeed: Long = 1L): List<Candidate> =
        (0 until tries).toList().parallelStream().map { i ->
            val seed = baseSeed + i
            val knobs = Knobs.sample(Random(seed * 7919))
            val built = construct(shape, seed, knobs) ?: return@map null
            val (layout, level) = built
            val light = measure(level, withEffort = false)
            if (!passesBar(light, shape.levelId)) return@map null
            Candidate(shape, seed, knobs, layout, level, measure(level))
        }.toList().filterNotNull()

    /** Geometric interpolation of the target effort between anchor levels. */
    fun targetEffort(levelId: Int): Double {
        val anchors = listOf(1 to 14.0, 6 to 34.0, 12 to 52.0, 18 to 80.0, 24 to 108.0, 30 to 150.0)
        val upper = anchors.first { it.first >= levelId }
        val lower = anchors.last { it.first <= levelId }
        if (upper.first == lower.first) return upper.second
        val t = (levelId - lower.first).toDouble() / (upper.first - lower.first)
        return Math.exp(ln(lower.second) + t * (ln(upper.second) - ln(lower.second)))
    }

    /**
     * Picks one candidate per level so that the chain climbs the way the
     * difficulty-report test demands (never easier, never a spike) while staying
     * as close to [targetEffort] as the candidates allow and as free of flaws as
     * possible. Dynamic programming over the levels; null if no chain exists.
     */
    fun chooseChain(
        perLevel: Map<Int, List<Candidate>>,
        minGrowth: Double = 1.03,
        maxGrowth: Double = 1.30
    ): List<Candidate>? {
        val levels = perLevel.keys.sorted()
        fun cost(c: Candidate): Double =
            abs(ln(c.metrics.effort / targetEffort(c.shape.levelId))) * 6.0 + flaws(c.metrics) * 0.15

        var best: List<Pair<Candidate, Double>> = perLevel.getValue(levels.first())
            .map { it to cost(it) }
        val trail = ArrayList<Map<Candidate, Candidate?>>()
        trail += best.associate { it.first to null }

        for (level in levels.drop(1)) {
            val next = ArrayList<Pair<Candidate, Double>>()
            val back = HashMap<Candidate, Candidate?>()
            for (candidate in perLevel.getValue(level)) {
                var winner: Candidate? = null
                var winnerCost = Double.MAX_VALUE
                for ((previous, previousCost) in best) {
                    val growth = candidate.metrics.effort / previous.metrics.effort
                    val ceiling = if (level <= 2) 2.0 else maxGrowth
                    if (growth < minGrowth || growth > ceiling) continue
                    if (previousCost < winnerCost) {
                        winner = previous
                        winnerCost = previousCost
                    }
                }
                if (winner != null) {
                    next += candidate to (winnerCost + cost(candidate))
                    back[candidate] = winner
                }
            }
            if (next.isEmpty()) return null
            best = next
            trail += back
        }

        var node: Candidate = best.minBy { it.second }.first
        val chain = ArrayList<Candidate>()
        for (index in trail.indices.reversed()) {
            chain += node
            node = trail[index][node] ?: break
        }
        return chain.reversed()
    }

    /**
     * The whole authoring pass: search every blueprint, then pick the chain. How
     * the shipped Campaign was made, as one call — `authorAll(20_000)` — from any
     * scratch test or script that has this file and the game sources on its
     * classpath. Null when some silhouette produced no board or no chain fits.
     */
    fun authorAll(tries: Int): List<Candidate>? {
        val perLevel = CampaignShapes.ALL.associate { it.levelId to search(it, tries) }
        if (perLevel.values.any { it.isEmpty() }) return null
        return chooseChain(perLevel)
    }

    /** `^ v < >` rows joined for pasting into source. */
    fun asSource(layout: List<String>): String = layout.joinToString(", ") { "\"$it\"" }
}
