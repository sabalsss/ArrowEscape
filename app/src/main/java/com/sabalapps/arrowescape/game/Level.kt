package com.sabalapps.arrowescape.game

/**
 * A level is just a grid size plus the arrows placed on it. Levels are written
 * as a small ASCII layout so new ones are cheap to add.
 *
 * [id] is the catalogue position (1-based). It is what gets persisted, so it
 * must stay stable for a level once it has shipped.
 */
data class Level(
    val name: String,
    val rows: Int,
    val columns: Int,
    val arrows: List<ArrowTile>,
    val id: Int = 0
)

object Levels {

    /**
     * Builds a level from rows of characters. `^ v < >` place arrows, anything
     * else (space or `.`) leaves the cell empty.
     */
    fun fromLayout(name: String, layout: List<String>, id: Int = 0): Level {
        val columns = layout.maxOfOrNull { it.length } ?: 0
        var nextId = 0
        val arrows = buildList {
            layout.forEachIndexed { row, line ->
                line.forEachIndexed { col, char ->
                    val direction = when (char) {
                        '^' -> Direction.UP
                        'v' -> Direction.DOWN
                        '<' -> Direction.LEFT
                        '>' -> Direction.RIGHT
                        else -> null
                    }
                    if (direction != null) add(ArrowTile(nextId++, row, col, direction))
                }
            }
        }
        return Level(name = name, rows = layout.size, columns = columns, arrows = arrows, id = id)
    }

    /**
     * Version of the Campaign *layouts* — which arrow sits where on every level.
     * It is written into an in-progress Campaign save, and a save carrying any
     * other value is discarded, because a saved board is only a list of surviving
     * arrow ids and ids are positions in a layout: restore them onto a different
     * layout and they would silently name different arrows.
     *
     * **Bump this whenever any layout in [ALL] changes** — `CampaignRegressionTest`
     * fails when the catalogue changes and names this constant in its message. It
     * deliberately does not touch completed levels, stars or the unlock ceiling:
     * those are keyed by level id, and level ids never change.
     *
     * 1 = the original abstract arrangements (before Discovery Phase 2; a save
     *     from that era carries no version at all and is treated as 1).
     * 2 = the thirty discovery silhouettes.
     * 3 = the same silhouettes with eight boards re-authored for pacing: Levels 5, 11,
     *     17, 23 and 29 are each world's breather, and 6, 9 and 12 lose a single-file
     *     stretch. The other twenty-two layouts are byte-identical to version 2.
     * 4 = five silhouettes redrawn so they read as what they hide (the shape-discovery
     *     pass): Butterfly (10), Eagle (17), Crystal (21), Crown (24) and Planet (27) are
     *     new pictures with new directions. The other twenty-five layouts are
     *     byte-identical to version 3. Completed levels, stars and unlocks are keyed by id
     *     and untouched; only an in-progress board saved under version 3 is discarded.
     */
    const val LAYOUT_VERSION = 4

    /**
     * The full catalogue, in play order. Every level is the silhouette of its
     * Campaign discovery — the *occupied cells* draw the picture, the arrow
     * directions make it a puzzle — and `CampaignShapesTest` pins the occupancy to
     * the intended shape.
     *
     * Every layout was built by reverse construction: arrows are laid on the
     * silhouette's cells one at a time in reverse removal order, each given a
     * direction whose escape path was clear at that moment, so a valid removal
     * order provably exists. The direction choices were steered (block what is
     * free, point down lines that will fill up, keep all four glyphs in play) by
     * the development-only `ShapeAuthoring` helper, and the boards were then
     * picked to climb the difficulty curve without a cliff. `LevelCatalogueTest`
     * re-proves solvability with an independent solver on every build, which is
     * what actually guards the set.
     *
     * The comment above each level gives its arrow count, board size, opening
     * free-arrow count and the helper's seed — informational provenance (the
     * layouts below are the source of truth; a seed only reproduces under the
     * same Kotlin runtime).
     *
     * To add a level: add its blueprint to `CampaignShapes`, append a layout here
     * with the next id, and run the tests.
     *
     * Columns are capped at 6 everywhere: on a 320dp-wide phone a seventh column
     * would put cells under a 48dp tap target. Rows stop at 9.
     */
    val ALL: List<Level> = listOf(
        // ---- Sky Garden
        // 1 Cloud · 8 arrows · 4x5 · 3 free at the start · seed 13335
        level(1, ".....", ".^<.v", "^<>>v", "....."),
        // 2 Flower · 8 arrows · 5x3 · 3 free at the start · seed 1430
        level(2, ".>.", "v>v", ".^.", "<<.", ".^."),
        // 3 Kite · 10 arrows · 6x5 · 4 free at the start · seed 3200
        level(3, "..<..", ".<>v.", ".^>v.", "..^..", ".^...", "<...."),
        // 4 Bird · 10 arrows · 4x6 · 3 free at the start · seed 1358
        level(4, ">....v", ".^..<.", ".^v<<.", "..>v.."),
        // 5 Heart · 11 arrows · 4x5 · 4 free at the start · seed 16672 (re-authored for pacing, layout 3)
        level(5, ".>.^.", "^<<^<", ".>vv.", "..>.."),
        // 6 Star · 12 arrows · 5x5 · 3 free at the start · seed 23817 (re-authored for pacing, layout 3)
        level(6, "..^..", "..^..", ">v>>v", ".v>^.", ".<.<."),

        // ---- Forest
        // 7 Leaf · 12 arrows · 5x5 · 3 free at the start · seed 5218
        level(7, "...>>", "..<^^", ".v<<.", "v<^..", "v...."),
        // 8 Mushroom · 14 arrows · 4x6 · 4 free at the start · seed 4283
        level(8, ".v<>^.", "v<v>^v", "..<<..", "..>^.."),
        // 9 Tree · 14 arrows · 6x5 · 4 free at the start · seed 23993 (re-authored for pacing, layout 3)
        level(9, "..v..", ".vv>.", ".>>^.", "^<<^<", "..>..", "..<.."),
        // 10 Butterfly · 15 arrows · 4x5 · 4 free at the start · seed 10015795 (redrawn, layout 4)
        level(10, ".^.<.", "v>.^>", "v^>>v", "<<.v<"),
        // 11 Fox · 15 arrows · 5x5 · 4 free at the start · seed 29052 (re-authored for pacing, layout 3)
        level(11, "v...>", ">>.>^", "vv<v<", ".<<v.", "..^.."),
        // 12 Owl · 15 arrows · 5x5 · 3 free at the start · seed 19185 (re-authored for pacing, layout 3)
        level(12, ">...v", "^v^vv", "^.>.>", ".>^v.", ".<.<."),

        // ---- Sunset Canyon
        // 13 Sun · 17 arrows · 5x5 · 4 free at the start · seed 5419
        level(13, ">.>.>", ".>^v.", "<v<<^", ".v^v.", "^.^.<"),
        // 14 Cactus · 17 arrows · 7x5 · 4 free at the start · seed 472
        level(14, "..^..", "v.<.<", "v.^.<", "v.>.v", "<>>>v", "..>..", "..^.."),
        // 15 Mountain · 17 arrows · 4x6 · 4 free at the start · seed 9357
        level(15, "..>...", ".v<v.<", "<<^v^^", "^<^>^>"),
        // 16 Canyon Arch · 20 arrows · 5x6 · 6 free at the start · seed 17402
        level(16, ".v<v<.", "^<^<<v", "^v..v>", "<...>>", "^...v<"),
        // 17 Eagle · 22 arrows · 6x6 · 7 free at the start · seed 17003574 (redrawn, layout 4)
        level(17, ">....>", "<v..^^", "^v<v^<", ".<<>>.", "..>v..", ".>vv^."),
        // 18 Treasure Chest · 22 arrows · 5x6 · 4 free at the start · seed 16326
        level(18, ".<<>v.", "<^^v<<", "......", "^>^v>v", "^v<>>v"),

        // ---- Crystal Night
        // 19 Gem · 22 arrows · 5x6 · 5 free at the start · seed 8683
        level(19, ".>v>>.", ">^>^^v", "vv<<^>", ".<<^<.", "..v^.."),
        // 20 Crescent Moon · 23 arrows · 7x6 · 5 free at the start · seed 14071
        level(20, "..^<v<", ".^>v..", ">^^...", "^<<...", "^vv...", ".v<v..", "..>vv^"),
        // 21 Crystal · 24 arrows · 6x6 · 6 free at the start · seed 21023454 (redrawn, layout 4)
        level(21, "...^..", "..>^>.", "<.v<<.", "^^.v<<", "^^<<^v", ".>>v>v"),
        // 22 Snowflake · 24 arrows · 8x6 · 6 free at the start · seed 13551
        level(22, "..>>..", "..<^..", "<.<^.<", ".v<<^.", ".>>v^.", "^.^v.^", "..^>..", "..v<.."),
        // 23 Magic Star · 28 arrows · 8x6 · 7 free at the start · seed 10456 (re-authored for pacing, layout 3)
        level(23, "..<<..", "..^v..", ".v^v<.", "^v<>>^", "^<^v<^", ".<vv<.", "..<>..", "..>>.."),
        // 24 Crown · 28 arrows · 6x6 · 6 free at the start · seed 24000919 (redrawn, layout 4)
        level(24, "..^<..", ">.>>.>", ">^>>v^", "v<^<>^", "<^<vv<", ".v<<v."),

        // ---- Cosmic
        // 25 Comet · 28 arrows · 8x6 · 7 free at the start · seed 7347
        level(25, ".....<", "....<<", "...vv^", "..<<>^", ".>^>>.", "^^<v<.", ">^vv>.", ".^<>>."),
        // 26 Rocket · 28 arrows · 8x6 · 7 free at the start · seed 18339
        level(26, "..<>..", ".v^^v.", ".v^^>.", ".>..>.", ".<^<<.", "v<<^<<", ">.vv.v", "..v<.."),
        // 27 Planet · 32 arrows · 8x6 · 7 free at the start · seed 27002402 (redrawn, layout 4)
        level(27, "..>v..", ".^<<>.", ".^^>^.", "v<v<<v", "<^vv<>", ".v<<>.", ".<vv^.", "..>v.."),
        // 28 UFO · 32 arrows · 8x6 · 6 free at the start · seed 15509
        level(28, "..v^..", ".^<<<.", ".>v^v.", ">>v^>v", "<<v<<v", ".v<<<.", ">.>>.v", "..v^.."),
        // 29 Satellite · 34 arrows · 8x6 · 7 free at the start · seed 4270 (re-authored for pacing, layout 3)
        level(29, "..^<..", ">^..>^", "<^..<<", "<<..^<", "^^^>>v", "v<>>>v", ">>..>v", "<^..^>"),
        // 30 Galaxy · 35 arrows · 9x6 · 7 free at the start · seed 9277
        level(
            30,
            ".^<>>.", "v^..^v", "v>^...", "<^v^..", "v<v<^v", "...<^v", "...^<<", "vv..>v", ".>v>^."
        ),
    )

    private fun level(id: Int, vararg layout: String): Level =
        fromLayout(name = "Level $id", layout = layout.toList(), id = id)

    /** Catalogue lookup by [Level.id]. Null for ids that do not exist. */
    fun byId(id: Int): Level? = ALL.firstOrNull { it.id == id }

    /** Level 1 — where a new player starts, and the default fixture in tests. */
    val FIRST: Level get() = ALL.first()
}
