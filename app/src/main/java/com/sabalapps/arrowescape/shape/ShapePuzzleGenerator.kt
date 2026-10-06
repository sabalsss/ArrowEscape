package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.endless.BoardAnalysis
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.SeededRandom
import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.MoveValidator

/**
 * How hard a shape puzzle is to *solve*, as opposed to how big a picture it is. The two are
 * separate on purpose: a Fish is the same clean Fish at every tier, and what changes is how
 * its arrows point — how many can leave at the start, how deep the chains of "this is in the
 * way of that" run, how evenly the four directions are mixed.
 *
 * @param cells which templates the tier may draw: those with this many arrows. The windows
 *   overlap, so a template appears in several tiers.
 * @param free the share of arrows that may be tappable on the opening move.
 * @param minFree never fewer opening choices than this, however small the share.
 * @param minDepth at least this many removal passes — the length of the longest blocker chain.
 * @param minDirections at least this many of the four glyphs in use.
 * @param maxDominant no single glyph above this share of the board.
 * @param maxRun no more than this many of one glyph side by side in a row or column.
 * @param maxSingleStreak no more than this many removal passes in a row with exactly one
 *   arrow free — a stretch with nothing to choose is a chore, not a puzzle.
 * @param steering the construction knobs, a range each; every attempt samples one value from
 *   each. Correctness never depends on them — they only decide which valid board comes out.
 */
class ShapeTierProfile(
    val cells: IntRange,
    val free: ClosedFloatingPointRange<Double>,
    val minFree: Int,
    val minDepth: Int,
    val minDirections: Int,
    val maxDominant: Double,
    val maxRun: Int,
    val maxSingleStreak: Int,
    val steering: Steering
) {
    /** Ranges the construction knobs are drawn from. */
    class Steering(
        val blockBonus: ClosedFloatingPointRange<Double>,
        val roomBonus: ClosedFloatingPointRange<Double>,
        val freeFactor: ClosedFloatingPointRange<Double>
    )

    companion object {
        private val BEGINNER = ShapeTierProfile(
            cells = 14..20, free = 0.34..0.62, minFree = 4, minDepth = 3, minDirections = 3,
            maxDominant = 0.55, maxRun = 3, maxSingleStreak = 2,
            steering = Steering(1.0..7.0, 0.5..6.0, 0.55..1.0)
        )
        private val EASY = ShapeTierProfile(
            cells = 16..24, free = 0.27..0.48, minFree = 4, minDepth = 4, minDirections = 3,
            maxDominant = 0.50, maxRun = 3, maxSingleStreak = 2,
            steering = Steering(3.0..11.0, 0.5..8.0, 0.30..0.85)
        )
        private val MEDIUM = ShapeTierProfile(
            cells = 18..26, free = 0.20..0.38, minFree = 4, minDepth = 5, minDirections = 4,
            maxDominant = 0.45, maxRun = 3, maxSingleStreak = 2,
            steering = Steering(6.0..16.0, 0.5..9.0, 0.10..0.55)
        )
        private val HARD = ShapeTierProfile(
            cells = 23..30, free = 0.15..0.30, minFree = 4, minDepth = 6, minDirections = 4,
            maxDominant = 0.42, maxRun = 3, maxSingleStreak = 2,
            steering = Steering(10.0..22.0, 0.3..9.0, 0.05..0.35)
        )
        private val EXPERT = ShapeTierProfile(
            cells = 25..32, free = 0.12..0.26, minFree = 4, minDepth = 7, minDirections = 4,
            maxDominant = 0.40, maxRun = 3, maxSingleStreak = 2,
            steering = Steering(14.0..25.0, 0.3..9.0, 0.02..0.25)
        )

        fun of(tier: EndlessTier): ShapeTierProfile = when (tier) {
            EndlessTier.BEGINNER -> BEGINNER
            EndlessTier.EASY -> EASY
            EndlessTier.MEDIUM -> MEDIUM
            EndlessTier.HARD -> HARD
            EndlessTier.EXPERT -> EXPERT
        }
    }
}

/** What a shape puzzle measures, as the profile reads it. */
class ShapeMetrics(
    val arrows: Int,
    val free: Int,
    val depth: Int,
    val layerSizes: List<Int>,
    val distinctDirections: Int,
    val dominantShare: Double,
    val longestRun: Int
) {
    val freeRatio: Double get() = if (arrows == 0) 0.0 else free.toDouble() / arrows

    /** The longest stretch of passes with exactly one arrow free. */
    val singleStreak: Int
        get() {
            var best = 0
            var run = 0
            for (size in layerSizes) {
                run = if (size == 1) run + 1 else 0
                best = maxOf(best, run)
            }
            return best
        }
}

/** A finished shape puzzle with its proof. */
class GeneratedShapeBoard(
    val level: Level,
    /** Arrow ids in an order that empties the board, every step legal when taken. */
    val solution: List<Int>,
    val metrics: ShapeMetrics,
    /** Constructions tried, the accepted one included. */
    val attempts: Int,
    /** False when the attempt cap was hit and the closest board was shipped. */
    val onProfile: Boolean
)

/**
 * Turns a picture into a puzzle: **the shape decides where the arrows are, the generator
 * only decides which way they point.**
 *
 * ## The same idea as the Endless generator, pointed at a mask
 *
 * The board is built backwards. Arrows are laid on the mask's cells one at a time, each given a
 * direction whose escape path is clear *at that moment* — judged by the real [MoveValidator],
 * there is no second copy of the rule here. Taking them off in the reverse of the order they were
 * laid down replays exactly those states, so a legal clearing order exists by construction: the
 * witness is free, and nothing is ever generated and then tested for solvability. The finished
 * board is nonetheless replayed through [BoardAnalysis.verifyOrder] before it is returned, which
 * turns "by construction" into a checked fact on every call.
 *
 * The occupied cells never move: `ShapeMask.of(result.level) == mask`, always.
 *
 * ## Steering
 *
 * Placement is weighted, not random: it rewards blocking arrows that are still free (fewer
 * openers), pointing down a line that still holds unplaced cells (a dependency to come), keeping
 * all four glyphs in play and avoiding stripes — and it places a cell that is running out of
 * directions before its neighbours close the last one. A placement that would strand another cell
 * is refused, so an attempt only fails by running out of moves. The tier's [ShapeTierProfile]
 * then decides whether the finished board is hard enough, and easy enough, to ship.
 *
 * Generation is a pure function of `(mask, seed, profile)`: the same inputs give the same board
 * down to the last arrow, on every device, which is what lets Daily and Endless persist a puzzle
 * as a seed.
 */
object ShapePuzzleGenerator {

    /** Constructions tried before the closest one ships. Each is a full build. */
    const val MAX_ATTEMPTS = 96

    /** Generated boards carry the id Endless always has, which no catalogue lookup matches. */
    const val GENERATED_LEVEL_ID = 0

    fun generate(mask: ShapeMask, seed: Long, profile: ShapeTierProfile, name: String): GeneratedShapeBoard {
        require(mask.cellCount > 0) { "a puzzle needs at least one arrow" }
        require(mask.columns <= MysteryShapes.MAX_COLUMNS && mask.rows <= MysteryShapes.MAX_ROWS) {
            "a ${mask.rows}x${mask.columns} picture does not fit the board limits"
        }

        var best: Built? = null
        var bestScore = Int.MAX_VALUE
        for (attempt in 0 until MAX_ATTEMPTS) {
            val rng = SeededRandom(SeededRandom.derive(seed, mask.cellCount.toLong(), attempt.toLong()))
            val built = construct(mask, rng, profile, name) ?: continue
            val misses = misses(built.metrics, profile)
            if (misses == 0) return built.toBoard(attempt + 1, onProfile = true)
            if (misses < bestScore) {
                best = built
                bestScore = misses
            }
        }
        // Unreachable in practice for the shipped catalogue (the tests prove it), but a player is
        // never shown an exception: ship the closest board, or — if every construction dead-ended —
        // the plain one that cannot fail.
        val fallback = best ?: fallback(mask, name)
        return fallback.toBoard(MAX_ATTEMPTS, onProfile = false)
    }

    // ---- the profile ------------------------------------------------------------

    /** How many of the profile's rules [m] breaks. Zero ships. */
    internal fun misses(m: ShapeMetrics, p: ShapeTierProfile): Int {
        var misses = 0
        val minFree = maxOf(p.minFree, kotlin.math.ceil(p.free.start * m.arrows).toInt())
        val maxFree = kotlin.math.floor(p.free.endInclusive * m.arrows).toInt()
        if (m.free < minFree || m.free > maxFree) misses++
        if (m.depth < p.minDepth) misses++
        if (m.distinctDirections < p.minDirections) misses++
        if (m.dominantShare > p.maxDominant) misses++
        if (m.longestRun > p.maxRun) misses++
        if (m.singleStreak > p.maxSingleStreak) misses++
        return misses
    }

    // ---- one construction ---------------------------------------------------------

    private class Built(
        val level: Level,
        val solution: List<Int>,
        val metrics: ShapeMetrics
    ) {
        fun toBoard(attempts: Int, onProfile: Boolean) =
            GeneratedShapeBoard(level, solution, metrics, attempts, onProfile)
    }

    private class Knobs(val blockBonus: Double, val roomBonus: Double, val freeFactor: Double, val urgency: Double)

    private data class Spot(val cell: MaskCell, val direction: Direction)

    private fun construct(mask: ShapeMask, rng: SeededRandom, profile: ShapeTierProfile, name: String): Built? {
        val st = profile.steering
        val knobs = Knobs(
            blockBonus = lerp(st.blockBonus, rng.nextDouble()),
            roomBonus = lerp(st.roomBonus, rng.nextDouble()),
            freeFactor = lerp(st.freeFactor, rng.nextDouble()),
            urgency = rng.nextDouble() * 3.0
        )

        val remaining = LinkedHashSet<MaskCell>(mask.cells)
        val placed = ArrayList<ArrowTile>(mask.cellCount)
        val directionAt = HashMap<MaskCell, Direction>()
        val counts = IntArray(Direction.entries.size)

        while (remaining.isNotEmpty()) {
            val legal = HashMap<MaskCell, List<Direction>>(remaining.size * 2)
            for (cell in remaining) {
                val options = Direction.entries.filter { isClear(cell, it, placed) }
                if (options.isEmpty()) return null
                legal[cell] = options
            }

            val gain = blockGain(placed, mask.rows, mask.columns)
            val busiest = counts.max()
            val spots = ArrayList<Spot>()
            val weights = ArrayList<Double>()
            for (cell in remaining) {
                val options = legal.getValue(cell)
                for (direction in options) {
                    val room = unplacedAhead(cell, direction, remaining, mask.rows, mask.columns)
                    var weight = 1.0
                    weight += knobs.blockBonus * gain[cell.row][cell.col]
                    weight += knobs.roomBonus * minOf(room, 4)
                    if (room == 0) weight *= knobs.freeFactor
                    weight += BALANCE_BONUS * (busiest - counts[direction.ordinal])
                    weight -= RUN_PENALTY * maxOf(0, runThrough(cell, direction, directionAt) - 2)
                    weight += knobs.urgency * (Direction.entries.size - options.size)
                    val floored = weight.coerceAtLeast(MIN_WEIGHT)
                    spots += Spot(cell, direction)
                    weights += floored * floored
                }
            }

            // Draw until a placement that leaves every other cell in its line a way out.
            var chosen: Spot? = null
            while (spots.isNotEmpty() && chosen == null) {
                val pick = draw(rng, weights)
                val spot = spots[pick]
                val after = placed + ArrowTile(placed.size, spot.cell.row, spot.cell.col, spot.direction)
                val strands = remaining.any { other ->
                    other != spot.cell &&
                        (other.row == spot.cell.row || other.col == spot.cell.col) &&
                        Direction.entries.none { isClear(other, it, after) }
                }
                if (strands) {
                    spots.removeAt(pick)
                    weights.removeAt(pick)
                } else {
                    chosen = spot
                }
            }
            val spot = chosen ?: return null

            placed += ArrowTile(placed.size, spot.cell.row, spot.cell.col, spot.direction)
            directionAt[spot.cell] = spot.direction
            counts[spot.direction.ordinal]++
            remaining -= spot.cell
        }

        return finish(mask, placed, name)
    }

    /** Reading-order ids, the proof, the measurements. Null if the proof fails. */
    private fun finish(mask: ShapeMask, placed: List<ArrowTile>, name: String): Built? {
        val ordered = placed.sortedWith(compareBy({ it.row }, { it.col }))
        val arrows = ordered.mapIndexed { id, tile -> ArrowTile(id, tile.row, tile.col, tile.direction) }
        val level = Level(name, mask.rows, mask.columns, arrows, GENERATED_LEVEL_ID)
        val idByCell = arrows.associate { (it.row to it.col) to it.id }
        val witness = placed.asReversed().map { idByCell.getValue(it.row to it.col) }
        if (!BoardAnalysis.verifyOrder(arrows, witness)) return null
        return Built(level, witness, measure(arrows, mask))
    }

    private fun measure(arrows: List<ArrowTile>, mask: ShapeMask): ShapeMetrics {
        val layers = requireNotNull(BoardAnalysis.peel(arrows)) { "a verified board must peel" }
        val histogram = Direction.entries.map { d -> arrows.count { it.direction == d } }
        return ShapeMetrics(
            arrows = arrows.size,
            free = layers.first().size,
            depth = layers.size,
            layerSizes = layers.map { it.size },
            distinctDirections = histogram.count { it > 0 },
            dominantShare = histogram.max().toDouble() / arrows.size,
            longestRun = BoardAnalysis.longestSameDirectionRun(arrows, mask.rows, mask.columns)
        )
    }

    /**
     * Every arrow pointing up, laid out top row first: each is free once everything above it in its
     * column is gone, so clearing top-down is legal. Ugly, and never shipped for a catalogue
     * picture — it is the guarantee that generation cannot fail.
     */
    private fun fallback(mask: ShapeMask, name: String): Built {
        val placed = mask.cells.mapIndexed { i, c -> ArrowTile(i, c.row, c.col, Direction.UP) }
        // Placement order = bottom row first, so the reversed witness clears the top row first.
        return requireNotNull(finish(mask, placed.sortedByDescending { it.row }, name)) {
            "the all-up fallback must verify"
        }
    }

    // ---- placement helpers --------------------------------------------------------

    /** A clear path for [cell] pointing [direction] among [placed], judged by the real rules. */
    private fun isClear(cell: MaskCell, direction: Direction, placed: List<ArrowTile>): Boolean =
        MoveValidator.canEscape(ArrowTile(NO_ID, cell.row, cell.col, direction), placed)

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
    private fun unplacedAhead(cell: MaskCell, direction: Direction, remaining: Set<MaskCell>, rows: Int, cols: Int): Int {
        var count = 0
        var row = cell.row + direction.dRow
        var col = cell.col + direction.dCol
        while (row in 0 until rows && col in 0 until cols) {
            if (MaskCell(row, col) in remaining) count++
            row += direction.dRow
            col += direction.dCol
        }
        return count
    }

    /** The run of [direction] this placement would sit in, along either axis. */
    private fun runThrough(cell: MaskCell, direction: Direction, at: Map<MaskCell, Direction>): Int {
        fun reach(dRow: Int, dCol: Int): Int {
            var steps = 0
            var next = MaskCell(cell.row + dRow, cell.col + dCol)
            while (at[next] == direction) {
                steps++
                next = MaskCell(next.row + dRow, next.col + dCol)
            }
            return steps
        }
        return maxOf(1 + reach(0, -1) + reach(0, 1), 1 + reach(-1, 0) + reach(1, 0))
    }

    private fun draw(rng: SeededRandom, weights: List<Double>): Int {
        var left = rng.nextDouble() * weights.sum()
        for (i in weights.indices) {
            left -= weights[i]
            if (left <= 0.0) return i
        }
        return weights.lastIndex
    }

    private fun lerp(range: ClosedFloatingPointRange<Double>, t: Double): Double =
        range.start + (range.endInclusive - range.start) * t

    private const val NO_ID = -1
    private const val BALANCE_BONUS = 1.0
    private const val RUN_PENALTY = 8.0
    private const val MIN_WEIGHT = 0.05
}
