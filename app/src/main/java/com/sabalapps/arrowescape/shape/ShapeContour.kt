package com.sabalapps.arrowescape.shape

import kotlin.math.abs

/**
 * A corner of the cell grid. `x` counts columns and `y` counts rows, so the corner
 * at the top-left of cell (row 2, column 3) is `GridPoint(x = 3, y = 2)` and its
 * bottom-right is `GridPoint(4, 3)`. Integer, so a traced outline is exact.
 */
data class GridPoint(val x: Int, val y: Int)

/** A point of a smoothed outline, in the same grid units as [GridPoint]. */
data class OutlinePoint(val x: Float, val y: Float)

/**
 * One closed outline: the boundary of a picture, or of a hole in it.
 *
 * [points] are its corners in order, with every point that merely sits on a straight
 * run removed — a 4-wide flat edge is two points, not five. An outer boundary runs
 * clockwise on screen and a hole counter-clockwise, which is what lets a single
 * even-odd fill (or a non-zero one) paint the picture with its holes left open.
 */
class ShapeLoop(val points: List<GridPoint>, val isHole: Boolean) {

    /** Twice the signed area (shoelace), positive for an outer boundary, negative for a hole. */
    val signedArea2: Int by lazy {
        var sum = 0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        sum
    }

    /** The area the loop encloses, in cells. */
    val area: Int get() = abs(signedArea2) / 2

    /** The length of the loop, in cell edges. */
    val perimeter: Int by lazy {
        var sum = 0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += abs(a.x - b.x) + abs(a.y - b.y)
        }
        sum
    }

    /**
     * The same loop with its corners rounded off, as a closed polyline.
     *
     * Every edge is first cut into unit steps, so a long straight run stays straight
     * and each *corner* is what gets rounded — then Chaikin's corner cutting is
     * applied [iterations] times. The result is the recognisable silhouette of the
     * picture with the staircase taken out of its diagonals (a Heart's lobes, a
     * Fish's tail curve), which is what the polished reveal for Daily and Endless is
     * drawn from. It is a presentation of the traced outline, not a replacement for
     * it: the confirmation outline on the board stays on the cell edges.
     */
    fun smoothed(iterations: Int = 2): List<OutlinePoint> {
        var ring = ArrayList<OutlinePoint>()
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val steps = abs(b.x - a.x) + abs(b.y - a.y)
            val dx = (b.x - a.x).toFloat() / steps
            val dy = (b.y - a.y).toFloat() / steps
            for (s in 0 until steps) ring += OutlinePoint(a.x + dx * s, a.y + dy * s)
        }
        repeat(iterations) {
            val next = ArrayList<OutlinePoint>(ring.size * 2)
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                next += OutlinePoint(0.75f * a.x + 0.25f * b.x, 0.75f * a.y + 0.25f * b.y)
                next += OutlinePoint(0.25f * a.x + 0.75f * b.x, 0.25f * a.y + 0.75f * b.y)
            }
            ring = next
        }
        return ring
    }

}

/**
 * Every outline of a picture: the outer boundary of each separate part, plus one loop
 * per enclosed hole (an Owl's eye, a Crown's jewel, a Rocket's porthole).
 */
class ShapeContour(val loops: List<ShapeLoop>) {

    /** Outer boundaries, one per separate part. */
    val outer: List<ShapeLoop> get() = loops.filterNot { it.isHole }

    /** Enclosed holes. */
    val holes: List<ShapeLoop> get() = loops.filter { it.isHole }

    /** The total length of every loop, in cell edges. */
    val perimeter: Int get() = loops.sumOf { it.perimeter }

    /** The area inside the outer boundaries less the holes, in cells — equals the mask's cell count. */
    val area: Int get() = loops.sumOf { it.signedArea2 } / 2

    val isEmpty: Boolean get() = loops.isEmpty()
}

/**
 * Traces the outline of a [ShapeMask] from its cell edges.
 *
 * Every occupied cell contributes one directed edge for each side that faces an empty cell (or the
 * outside), directed so that the cell is on the right-hand side as you walk it — clockwise around a
 * part, counter-clockwise around a hole. Each edge is then paired with the edge that continues it,
 * and the loops are the cycles of that pairing. No bitmaps, no image processing, no geometry
 * library: the cost is proportional to the perimeter.
 *
 * ## The one real decision: corners where two cells only touch diagonally
 *
 * At such a corner two edges leave, and either pairing is a valid set of closed loops — they differ in
 * what they call connected. The rule is chosen by *what is actually joined*:
 *
 *  - the two filled cells belong to **different pieces** (a Bird's wing tip beside its body): they stay
 *    two separate outlines, so each piece gets its own loop;
 *  - they belong to **the same piece** (a Fish's body wrapped around its eye, with an outer notch
 *    diagonally beyond it): they are joined, which keeps the empty cell on the other diagonal sealed
 *    off — so the eye is a hole, not a slit into the outside.
 */
object GridContourTracer {

    private data class Edge(val from: GridPoint, val to: GridPoint) {
        val dx: Int get() = to.x - from.x
        val dy: Int get() = to.y - from.y
    }

    fun trace(mask: ShapeMask): ShapeContour {
        if (mask.cellCount == 0) return ShapeContour(emptyList())

        val edges = ArrayList<Edge>()
        val outgoing = HashMap<GridPoint, MutableList<Edge>>()
        fun add(from: GridPoint, to: GridPoint) {
            val e = Edge(from, to)
            edges += e
            outgoing.getOrPut(from) { ArrayList(2) }.add(e)
        }
        for (cell in mask.cells) {
            val r = cell.row
            val c = cell.col
            if (!mask[r - 1, c]) add(GridPoint(c, r), GridPoint(c + 1, r))
            if (!mask[r, c + 1]) add(GridPoint(c + 1, r), GridPoint(c + 1, r + 1))
            if (!mask[r + 1, c]) add(GridPoint(c + 1, r + 1), GridPoint(c, r + 1))
            if (!mask[r, c - 1]) add(GridPoint(c, r + 1), GridPoint(c, r))
        }

        // Which edge continues which.
        val next = HashMap<Edge, Edge>(edges.size * 2)
        for (e in edges) {
            val options = outgoing.getValue(e.to)
            next[e] = if (options.size == 1) options[0] else pick(mask, e, options)
        }

        // The loops are the cycles; walk them in a fixed order so the output is stable.
        val used = HashSet<Edge>(edges.size * 2)
        val loops = ArrayList<ShapeLoop>()
        for (start in edges.sortedWith(compareBy({ it.from.y }, { it.from.x }, { it.to.y }, { it.to.x }))) {
            if (start in used) continue
            val cycle = ArrayList<Edge>()
            var e = start
            do {
                used += e
                cycle += e
                e = next.getValue(e)
            } while (e != start)
            loops += loopOf(cycle)
        }
        return ShapeContour(
            loops.sortedWith(
                compareBy<ShapeLoop>({ it.isHole }, { it.points.minOf { p -> p.y } }, { it.points.minOf { p -> p.x } })
            )
        )
    }

    /** At a corner with two ways on: the edge that continues [incoming]. See the class comment. */
    private fun pick(mask: ShapeMask, incoming: Edge, options: List<Edge>): Edge {
        val x = incoming.to.x
        val y = incoming.to.y
        // The filled cells diagonally across this corner (a pinch always has exactly one such pair).
        val pair = when {
            mask[y - 1, x] && mask[y, x - 1] && !mask[y - 1, x - 1] && !mask[y, x] ->
                MaskCell(y - 1, x) to MaskCell(y, x - 1)
            mask[y - 1, x - 1] && mask[y, x] && !mask[y - 1, x] && !mask[y, x - 1] ->
                MaskCell(y - 1, x - 1) to MaskCell(y, x)
            else -> null
        }
        val join = pair != null && mask.pieceOf(pair.first.row, pair.first.col) == mask.pieceOf(pair.second.row, pair.second.col)
        return options.maxBy { turnScore(incoming, it, preferLeft = join) }
    }

    /** 2 for the preferred turn, 1 for straight on, 0 for the other turn; screen coordinates (y down). */
    private fun turnScore(inE: Edge, outE: Edge, preferLeft: Boolean): Int {
        val cross = inE.dx * outE.dy - inE.dy * outE.dx // > 0 is a clockwise (right) turn on screen
        val dot = inE.dx * outE.dx + inE.dy * outE.dy
        val turn = if (preferLeft) -cross else cross
        return when {
            turn > 0 -> 2
            dot > 0 -> 1
            else -> 0
        }
    }

    /** A cycle of edges as a loop: its corners only, hole or not by the sign of its area. */
    private fun loopOf(cycle: List<Edge>): ShapeLoop {
        val corners = ArrayList<GridPoint>()
        for (i in cycle.indices) {
            val previous = cycle[(i - 1 + cycle.size) % cycle.size]
            val here = cycle[i]
            if (previous.dx != here.dx || previous.dy != here.dy) corners += here.from
        }
        val loop = ShapeLoop(corners, isHole = false)
        return if (loop.signedArea2 < 0) ShapeLoop(corners, isHole = true) else loop
    }
}
