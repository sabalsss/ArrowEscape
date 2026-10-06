package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.ArrowTile
import kotlin.math.min

/**
 * How a board is put on the screen. A rendering choice and nothing else: the
 * rules, the board, the arrow ids and the saves are identical either way.
 *
 * - [Grid] is the traditional puzzle board: a rounded surface with a visible
 *   slot for every cell. **No mode uses it any more** — it stays as the plain,
 *   rectangular rendering of a board that has no picture in it.
 * - [Shape] is the way every mode is drawn: the level *is* a silhouette — a
 *   Campaign discovery, today's mystery shape, an Endless puzzle's picture — so
 *   the board has no container and no slots, and the arrows are simply the
 *   picture, floating on the world, centred and sized by the cells they occupy.
 *   The silhouette made of arrows is the only clue the player gets.
 *
 * Pure Kotlin, like the rest of what decides rather than draws, so the choice and
 * the maths below are testable on a plain JVM.
 */
enum class BoardPresentation { Grid, Shape }

/**
 * The presentation for [mode]: [BoardPresentation.Shape] for all of them.
 *
 * The replayed tutorial is a shape because it plays Level 1's layout, the same Cloud the first
 * run shows as a real Campaign level. Daily and Endless are shapes because their puzzles are
 * built *to* a picture (see `MysteryShapes`) — drawn on a grid with a slot for every cell, that
 * picture would be half hidden by the very rectangle it was designed to escape.
 */
@Suppress("UNUSED_PARAMETER")
fun boardPresentationFor(mode: GameMode): BoardPresentation = BoardPresentation.Shape

/**
 * How far into its closing moments a Campaign shape is, as a stage from 0 to
 * [STAGES]: 0 for four or more arrows left, then 1, 2 and 3 as the last three
 * are taken. Zero again at zero — the formation is gone and the halo settles.
 *
 * The stage only ever lifts the *halo* a little (see [CampaignShapeHalo]); it
 * never names a count, never marks an arrow and never touches a rule, so the
 * player still finds the last move themselves. A pure function of the number of
 * arrows left, so it is the same on every level and pinned by a plain JVM test.
 */
object ShapeAnticipation {
    const val STAGES = 3

    fun stage(arrowsLeft: Int): Int =
        if (arrowsLeft in 1..STAGES) STAGES + 1 - arrowsLeft else 0
}

/**
 * The smallest block of rows and columns that holds every arrow of a level, in
 * the level's own coordinates (both ends inclusive).
 *
 * Presentation only. A Campaign layout may carry margin rows or columns that are
 * not part of its picture (Level 1's Cloud has an empty row above and below), and
 * centring the whole grid would put the picture off-centre. Nothing is cropped
 * from the level: an empty row *inside* these bounds — an Owl's eye, a Chest's
 * lid gap — is part of the silhouette and stays exactly where it is.
 *
 * Always computed from the level's *full* arrow list, never from what is left
 * on the board, so the shape neither re-centres nor re-scales as arrows leave.
 */
data class OccupiedBounds(
    val minRow: Int,
    val maxRow: Int,
    val minCol: Int,
    val maxCol: Int
) {
    val rows: Int get() = maxRow - minRow + 1
    val columns: Int get() = maxCol - minCol + 1

    companion object {
        /** The bounds of [arrows], or null for a level with none. */
        fun of(arrows: List<ArrowTile>): OccupiedBounds? {
            if (arrows.isEmpty()) return null
            return OccupiedBounds(
                minRow = arrows.minOf { it.row },
                maxRow = arrows.maxOf { it.row },
                minCol = arrows.minOf { it.col },
                maxCol = arrows.maxOf { it.col }
            )
        }

        /** The whole of a [rows] × [columns] grid. */
        fun whole(rows: Int, columns: Int) = OccupiedBounds(0, rows - 1, 0, columns - 1)
    }
}

/** Sizing for a [BoardPresentation.Shape] board. */
object ShapeFit {

    /**
     * The edge of one square cell, in whatever unit the inputs share.
     *
     * The largest cell that lets [rows] × [columns] fit inside the space on offer,
     * but never bigger than [maxCell]. The two limits are what make every
     * Campaign level look intentionally sized:
     *
     * - the *fit* keeps a 9×6 board clear of the HUD and the footer and a 6-wide
     *   board inside a 320dp screen, so arrows are as large as the room allows;
     * - the *cap* keeps a 3-wide, 8-arrow board from being blown up to fill a
     *   screen built for 35, which is what would make a small shape look silly.
     *
     * Cells are square, so the silhouette keeps the proportions it was drawn with
     * instead of being stretched to the window.
     */
    fun cellSize(
        availableWidth: Float,
        availableHeight: Float,
        rows: Int,
        columns: Int,
        maxCell: Float
    ): Float {
        if (rows <= 0 || columns <= 0) return 0f
        val fit = min(availableWidth / columns, availableHeight / rows)
        return fit.coerceIn(0f, maxCell)
    }
}
