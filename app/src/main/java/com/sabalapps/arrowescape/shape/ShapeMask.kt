package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.Level

/** One cell of a [ShapeMask], in the level's own row / column coordinates. */
data class MaskCell(val row: Int, val col: Int)

/** The tightest block of rows and columns that holds every occupied cell (both ends inclusive). */
data class MaskBounds(val minRow: Int, val maxRow: Int, val minCol: Int, val maxCol: Int) {
    val rows: Int get() = maxRow - minRow + 1
    val columns: Int get() = maxCol - minCol + 1
}

/**
 * Which cells of a grid hold an arrow — the *picture* of a puzzle, with every
 * direction thrown away.
 *
 * This is the one representation the whole shape system shares. A Campaign level
 * is a mask (the silhouette it was authored to), a Daily or Endless puzzle is a
 * mask (a [MysteryShapeTemplate] laid on a grid), and the glowing outline that
 * confirms a solved board is traced from a mask: **the outline is generated from the
 * puzzle's own occupied cells**, never from an unrelated icon.
 *
 * Pure Kotlin, immutable, no Android and no Compose, so everything built on it —
 * the contour tracer, the generator, the authoring checks — runs on a plain JVM.
 * Coordinates are the level's own: row 0 is the top row.
 */
class ShapeMask private constructor(
    val rows: Int,
    val columns: Int,
    private val occupied: BooleanArray
) {
    /** How many cells are occupied — for a puzzle, how many arrows it has. */
    val cellCount: Int = occupied.count { it }

    /** Occupied cells in reading order (top row first, left to right). */
    val cells: List<MaskCell> by lazy {
        buildList {
            for (row in 0 until rows) for (col in 0 until columns) {
                if (occupied[row * columns + col]) add(MaskCell(row, col))
            }
        }
    }

    /** True for an occupied cell; false for an empty cell or anything off the grid. */
    operator fun get(row: Int, col: Int): Boolean =
        row in 0 until rows && col in 0 until columns && occupied[row * columns + col]

    /** The tight bounding box of the occupied cells, or null for an empty mask. */
    val bounds: MaskBounds? by lazy {
        if (cellCount == 0) {
            null
        } else {
            MaskBounds(
                minRow = cells.minOf { it.row },
                maxRow = cells.maxOf { it.row },
                minCol = cells.minOf { it.col },
                maxCol = cells.maxOf { it.col }
            )
        }
    }

    /** The same picture cropped to [bounds], so no empty margin row or column is left. */
    fun trimmed(): ShapeMask {
        val box = bounds ?: return this
        return fromCells(
            rows = box.rows,
            columns = box.columns,
            cells = cells.map { MaskCell(it.row - box.minRow, it.col - box.minCol) }
        )
    }

    /** The picture flipped left to right. */
    fun mirrored(): ShapeMask =
        fromCells(rows, columns, cells.map { MaskCell(it.row, columns - 1 - it.col) })

    /** The picture turned clockwise by [quarterTurns] quarter turns (any integer). */
    fun rotated(quarterTurns: Int): ShapeMask {
        var result = this
        repeat(quarterTurns.mod(4)) {
            result = fromCells(
                rows = result.columns,
                columns = result.rows,
                cells = result.cells.map { MaskCell(it.col, result.rows - 1 - it.row) }
            )
        }
        return result
    }

    /**
     * Groups of occupied cells that touch along an edge. 1 for a single connected
     * picture; more for a deliberately multi-part one (a Snowman's head and body
     * touch, a Balloon's string might not).
     */
    val components: Int by lazy {
        val seen = BooleanArray(rows * columns)
        var groups = 0
        for (cell in cells) {
            if (seen[cell.row * columns + cell.col]) continue
            groups++
            val stack = ArrayDeque<MaskCell>()
            stack.addLast(cell)
            seen[cell.row * columns + cell.col] = true
            while (stack.isNotEmpty()) {
                val here = stack.removeLast()
                for ((dr, dc) in NEIGHBOURS) {
                    val r = here.row + dr
                    val c = here.col + dc
                    if (this[r, c] && !seen[r * columns + c]) {
                        seen[r * columns + c] = true
                        stack.addLast(MaskCell(r, c))
                    }
                }
            }
        }
        groups
    }

    /** Which 4-connected piece the cell belongs to (0, 1, … in reading order of first cell), or -1 if empty. */
    internal fun pieceOf(row: Int, col: Int): Int =
        if (this[row, col]) pieceLabels[row * columns + col] else -1

    private val pieceLabels: IntArray by lazy {
        val labels = IntArray(rows * columns) { -1 }
        var next = 0
        for (cell in cells) {
            if (labels[cell.row * columns + cell.col] >= 0) continue
            val stack = ArrayDeque<MaskCell>()
            stack.addLast(cell)
            labels[cell.row * columns + cell.col] = next
            while (stack.isNotEmpty()) {
                val here = stack.removeLast()
                for ((dr, dc) in NEIGHBOURS) {
                    val r = here.row + dr
                    val c = here.col + dc
                    if (this[r, c] && labels[r * columns + c] < 0) {
                        labels[r * columns + c] = next
                        stack.addLast(MaskCell(r, c))
                    }
                }
            }
            next++
        }
        labels
    }

    /** The picture drawn as text: [on] for an occupied cell, [off] for an empty one. For audits and test output. */
    fun ascii(on: Char = '#', off: Char = '.'): String =
        (0 until rows).joinToString("\n") { row ->
            (0 until columns).joinToString("") { col -> if (this[row, col]) on.toString() else off.toString() }
        }

    override fun equals(other: Any?): Boolean =
        other is ShapeMask && other.rows == rows && other.columns == columns &&
            other.occupied.contentEquals(occupied)

    override fun hashCode(): Int = 31 * (31 * rows + columns) + occupied.contentHashCode()

    override fun toString(): String = "ShapeMask(${rows}x$columns, $cellCount cells)\n${ascii()}"

    companion object {
        private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        /**
         * Parses a picture written as text: `#` is an occupied cell, anything else
         * (`.` or a space) is empty. Rows may differ in length; the grid is as wide as
         * the longest, and a short row is empty to its right.
         */
        fun parse(rows: List<String>): ShapeMask {
            val width = rows.maxOfOrNull { it.length } ?: 0
            val cells = buildList {
                rows.forEachIndexed { row, line ->
                    line.forEachIndexed { col, char -> if (char == '#') add(MaskCell(row, col)) }
                }
            }
            return fromCells(rows.size, width, cells)
        }

        /** The occupied cells of [arrows] on a [rows] × [columns] grid. */
        fun of(arrows: List<ArrowTile>, rows: Int, columns: Int): ShapeMask =
            fromCells(rows, columns, arrows.map { MaskCell(it.row, it.col) })

        /** The picture a level's *full* arrow list draws — what it is, not what is left of it. */
        fun of(level: Level): ShapeMask = of(level.arrows, level.rows, level.columns)

        fun fromCells(rows: Int, columns: Int, cells: Collection<MaskCell>): ShapeMask {
            require(rows >= 0 && columns >= 0) { "grid must not be negative: ${rows}x$columns" }
            val bits = BooleanArray(rows * columns)
            for (cell in cells) {
                require(cell.row in 0 until rows && cell.col in 0 until columns) {
                    "cell $cell is off a ${rows}x$columns grid"
                }
                bits[cell.row * columns + cell.col] = true
            }
            return ShapeMask(rows, columns, bits)
        }
    }
}
