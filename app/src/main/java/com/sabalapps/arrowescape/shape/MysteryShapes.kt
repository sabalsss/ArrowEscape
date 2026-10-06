package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.endless.EndlessTier

/**
 * A hidden picture a Daily or Endless puzzle is built to: a coarse silhouette of an
 * everyday object, authored by hand.
 *
 * ## Why these are not the Campaign discoveries
 *
 * The Campaign owns thirty named, collectable discoveries with their own artwork.
 * Daily and Endless have a separate catalogue so that they can never spoil, duplicate or
 * quietly collect one of them: solving today's puzzle finds you *a fish*, it does not
 * add anything to an album. The reward here is the surprise and the confirmation.
 *
 * ## Hidden by construction
 *
 * [name] is read by exactly one thing: the reveal that is published *after* a board is
 * won. A board in play carries only its arrows, so there is no name to print, announce
 * or leak — see `GameViewModel.shapeReveal`.
 *
 * ## Fixed means fixed
 *
 * [id] is permanent (Daily's choice of shape and Endless's saved boards are derived
 * from it); **never reorder, rename or redraw a shipped template** without bumping
 * `MysteryShapePuzzles.GENERATOR_VERSION` and `DailyChallenge.GENERATOR_VERSION`.
 *
 * @param rows the picture, `#` = an arrow, `.` = empty, tightly cropped (no margin).
 * @param allowMirror whether the picture may be shown flipped left-to-right and still be
 *   itself — true for a Fish (it can face either way), false for anything symmetric
 *   (flipping it would be the same board) and for anything whose reading depends on
 *   handedness.
 * @param allowedRotations clockwise quarter turns the picture may be shown at. `{0}` for
 *   everything today: an upside-down House or a sideways Balloon is not that object, and
 *   a quarter turn of a tall template would also break the 6-column limit. The field
 *   exists so that a template which genuinely reads either way up can opt in.
 */
class MysteryShapeTemplate(
    val id: String,
    val name: String,
    rows: List<String>,
    val allowMirror: Boolean = false,
    val allowedRotations: Set<Int> = setOf(0)
) {
    val mask: ShapeMask = ShapeMask.parse(rows)

    /** How many arrows a puzzle built to this picture has. */
    val cellCount: Int get() = mask.cellCount

    /** Every way this template may be shown: each permitted turn, flipped or not when permitted. */
    val variants: List<ShapeVariant> by lazy {
        buildList {
            for (turns in allowedRotations.sorted()) {
                add(ShapeVariant(this@MysteryShapeTemplate, mirrored = false, quarterTurns = turns))
                if (allowMirror) add(ShapeVariant(this@MysteryShapeTemplate, mirrored = true, quarterTurns = turns))
            }
        }
    }
}

/** One orientation of a [MysteryShapeTemplate]: the picture a puzzle is actually built to. */
class ShapeVariant(
    val template: MysteryShapeTemplate,
    val mirrored: Boolean,
    val quarterTurns: Int
) {
    val mask: ShapeMask by lazy {
        val base = if (mirrored) template.mask.mirrored() else template.mask
        base.rotated(quarterTurns)
    }
}

/**
 * What a puzzle was built to, as the ViewModel keeps it until the board is won. Carries the
 * hidden [name], so nothing that is handed to the screen while a board is in play holds one.
 */
class ShapeIdentity(val templateId: String, val name: String, val mirrored: Boolean, val quarterTurns: Int)

/**
 * The Daily / Endless catalogue — 27 hand-drawn silhouettes.
 *
 * Every picture is one connected piece (4-connectivity) of 15 to 32 cells in at most 6
 * columns by 8 rows, so it is a legal board and every arrow is a full-size touch target.
 * Several have deliberate holes (a Fish's eye, a Gift's ribbon, a Camera's lens, a
 * Key's ring) which the contour keeps. Visual review sheet: `MysteryShapeSheetWriterTest`.
 *
 * Sizes are spread on purpose so that Beginner can be a small, friendly picture and Expert a
 * large one — but *every* template stays a clean recognisable object whatever the tier;
 * how hard a puzzle is comes from its arrows, not from an uglier silhouette.
 */
object MysteryShapes {

    val all: List<MysteryShapeTemplate> = listOf(
        MysteryShapeTemplate(
            "flag", "Flag",
            listOf("####.", "#####", "####.", "#....", "#....", "#...."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "music_note", "Music Note",
            listOf("...###", "...#.#", "...#..", "...#..", ".###..", "####..", ".##..."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "boot", "Boot",
            listOf(".##...", ".##...", ".##...", ".###..", "######"),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "tulip", "Tulip",
            listOf("#.#.#", "#####", "#####", ".###.", "..#..", "..#..")
        ),
        MysteryShapeTemplate(
            "house", "House",
            listOf("..#..", ".###.", "#####", "#####", "##.##")
        ),
        MysteryShapeTemplate(
            "lightning_bolt", "Lightning Bolt",
            listOf("..###", ".###.", "#####", "..##.", ".##..", "##...", "#...."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "top_hat", "Top Hat",
            listOf(".####.", ".####.", ".####.", "######")
        ),
        MysteryShapeTemplate(
            "pear", "Pear",
            listOf("..#..", "..##.", ".###.", "#####", "#####", ".###."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "apple", "Apple",
            listOf("...#.", "##.##", "#####", "#####", "#####", ".###."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "umbrella", "Umbrella",
            listOf("..##..", ".####.", "######", "#.##.#", "..#...", "..#...", ".##..."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "goblet", "Goblet",
            listOf("######", ".####.", ".####.", "..##..", ".####.")
        ),
        MysteryShapeTemplate(
            "sailboat", "Sailboat",
            listOf("..#...", "..##..", "..###.", "..####", "######", ".####."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "key", "Key",
            listOf(".###.", "##.##", "##.##", ".###.", "..#..", "..##.", "..#..", "..###"),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "balloon", "Balloon",
            listOf(".###.", "#.###", "#####", "#####", ".###.", "..#..", "..#.."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "candle", "Candle",
            listOf("..#..", ".###.", "..#..", ".###.", ".###.", ".###.", ".###.", "#####")
        ),
        MysteryShapeTemplate(
            "camera", "Camera",
            listOf(".##...", "######", "##..##", "##..##", "######"),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "bell", "Bell",
            listOf("..##..", ".####.", ".####.", ".####.", "######", "######", "..##..")
        ),
        MysteryShapeTemplate(
            "mug", "Mug",
            listOf("####..", "######", "####.#", "######", ".###.."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "lamp", "Lamp",
            listOf(".####.", "######", "######", "..##..", "..##..", ".####.")
        ),
        MysteryShapeTemplate(
            "hourglass", "Hourglass",
            listOf("######", ".####.", "..##..", "..##..", ".####.", "######")
        ),
        MysteryShapeTemplate(
            "anchor", "Anchor",
            listOf(".###.", ".#.#.", ".###.", "#####", "..#..", "#.#.#", "#####", ".###.")
        ),
        MysteryShapeTemplate(
            "fish", "Fish",
            listOf("#.###.", "####.#", "######", "######", "#.###."),
            allowMirror = true
        ),
        MysteryShapeTemplate(
            "gift", "Gift",
            listOf("##.##", ".###.", "#####", "##.##", "#####", "#####")
        ),
        MysteryShapeTemplate(
            "snowman", "Snowman",
            listOf(".###.", "#####", ".###.", ".###.", "#####", "#####", "#####", ".###.")
        ),
        MysteryShapeTemplate(
            "turtle", "Turtle",
            listOf("..##..", "#.##.#", "######", "######", "######", "#.##.#", "..##..")
        ),
        MysteryShapeTemplate(
            "ghost", "Ghost",
            listOf(".####.", "######", "#.##.#", "######", "######", "#.##.#")
        ),
        MysteryShapeTemplate(
            "shield", "Shield",
            listOf("######", "######", "##..##", "######", ".####.", ".####.", "..##..")
        )
    )

    private val byId: Map<String, MysteryShapeTemplate> = all.associateBy { it.id }

    fun byId(id: String): MysteryShapeTemplate? = byId[id]

    /** The widest and tallest any template may be — the board limits. */
    const val MAX_COLUMNS = 6
    const val MAX_ROWS = 9

    /**
     * The templates a tier draws from: the ones whose picture holds that many arrows
     * ([ShapeTierProfile.cells]). The windows overlap generously, so every tier has a
     * dozen or so pictures to choose among and a template appears in several tiers —
     * which is the point: a Fish at Beginner and a Fish at Expert are the same Fish.
     */
    fun forTier(tier: EndlessTier): List<MysteryShapeTemplate> {
        val window = ShapeTierProfile.of(tier).cells
        return all.filter { it.cellCount in window }
    }

    /**
     * The pictures a Daily may be. Smaller than Endless's easy end: the day's puzzle is a
     * proper sit-down, not a warm-up.
     */
    val dailyPool: List<MysteryShapeTemplate> = all.filter { it.cellCount >= DAILY_MIN_CELLS }

    /** A day's puzzle is at least this many arrows. */
    const val DAILY_MIN_CELLS = 18

    /** Daily boards with at least this many arrows are labelled Hard; the rest are Medium. */
    const val DAILY_HARD_FROM_CELLS = 25
}
