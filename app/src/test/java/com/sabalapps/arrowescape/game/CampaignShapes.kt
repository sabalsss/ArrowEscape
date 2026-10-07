package com.sabalapps.arrowescape.game

/**
 * The silhouette each Campaign level is built to — the occupancy blueprint.
 *
 * **Test and development only.** A blueprint says *which cells hold an arrow*
 * (`#`) and which are empty (`.`); it says nothing about direction. The arrow
 * directions live in [Levels.ALL], designed so the occupied cells are a real,
 * solvable Arrow Escape puzzle. `CampaignShapesTest` pins every level's occupied
 * cells to its blueprint, so a later edit to a layout cannot quietly redraw the
 * picture the level is hiding.
 *
 * These are deliberately coarse icons — at most 6 columns by 9 rows, the board
 * limits — and are meant to be "recognisable once the grid disappears", not
 * pixel-perfect artwork. Where a subject was hard to draw at that size the
 * choice is noted beside it.
 *
 * Empty cells matter: a shape occupies only what its silhouette needs, and no blueprint
 * carries a margin row or column of its own (the Cloud that once had one is Level 5 now and
 * is drawn on exactly its three rows).
 */
data class CampaignShape(
    val levelId: Int,
    /** The discovery's visible name; `CampaignShapesTest` checks it against the catalogue. */
    val discovery: String,
    /** `#` occupied, `.` empty. Every row is the same width. */
    val rows: List<String>,
    /** One line for the shape review table. */
    val description: String
) {
    val height: Int get() = rows.size
    val width: Int get() = rows.first().length

    /** Occupied cells as (row, column), in reading order. */
    val cells: List<Pair<Int, Int>> = buildList {
        rows.forEachIndexed { row, line ->
            line.forEachIndexed { col, char -> if (char == '#') add(row to col) }
        }
    }

    val size: Int get() = cells.size
}

object CampaignShapes {

    val ALL: List<CampaignShape> = listOf(
        // ---- Sky Garden ----------------------------------------------------
        shape(
            1, "Heart", "two lobes over a tapering body down to a single point",
            ".#.#.",
            "#####",
            ".###.",
            "..#.."
        ),
        shape(
            2, "Star", "five-point star: a tip over wide arms, a body and two legs",
            "..#..",
            "#####",
            ".###.",
            ".#.#."
        ),
        shape(
            3, "Kite", "a diamond head, widest across the middle, tapering to a point with a tail of two trailing away",
            "..#..",
            ".###.",
            "#####",
            "..#..",
            ".#...",
            "#...."
        ),
        shape(
            4, "Bird", "a gull: wings swept up to the corners, a body and a short tail",
            "#....#",
            "##..##",
            ".####.",
            "..##.."
        ),
        shape(
            5, "Cloud", "a flat-bottomed cloud with two humps on top and a shoulder to the right",
            ".#.#..",
            "######",
            ".####."
        ),
        shape(
            6, "Flower", "a ringed daisy head with an open centre on a straight stem",
            ".###.",
            "##.##",
            ".###.",
            "..#..",
            "..#.."
        ),

        // ---- Forest --------------------------------------------------------
        shape(
            7, "Leaf", "diagonal leaf, pointed at the top right, with a stem at the bottom left",
            "...##",
            "..###",
            ".###.",
            "###..",
            "#...."
        ),
        shape(
            8, "Mushroom", "domed cap over a narrow two-wide stem",
            ".####.",
            "######",
            "..##..",
            "..##.."
        ),
        shape(
            9, "Tree", "layered pine: pointed top, widening tiers, a two-high trunk",
            "..#..",
            ".###.",
            ".###.",
            "#####",
            "..#..",
            "..#.."
        ),
        shape(
            10, "Butterfly", "two antennae over a pair of broad wings either side of a centre body",
            ".#.#.",
            "##.##",
            "#####",
            "##.##"
        ),
        shape(
            11, "Fox", "pointed ears, wide cheeks, a tapering muzzle",
            "#...#",
            "##.##",
            "#####",
            ".###.",
            "..#.."
        ),
        shape(
            12, "Owl", "ear tufts, a face with two eye holes and a beak, short feet",
            "#...#",
            "#####",
            "#.#.#",
            ".###.",
            ".#.#."
        ),

        // ---- Sunset Canyon -------------------------------------------------
        shape(
            13, "Sun", "solid disc with eight rays",
            "#.#.#",
            ".###.",
            "#####",
            ".###.",
            "#.#.#"
        ),
        shape(
            14, "Cactus", "saguaro: a tall trunk with two raised arms, a U-bend each side",
            "..#..",
            "#.#.#",
            "#.#.#",
            "#.#.#",
            "#####",
            "..#..",
            "..#.."
        ),
        shape(
            15, "Mountain", "a tall peak beside a small one over a solid base",
            "..#...",
            ".###.#",
            "######",
            "######"
        ),
        shape(
            16, "Canyon Arch", "rounded top over a doorway, one pillar thicker than the other",
            ".####.",
            "######",
            "##..##",
            "#...##",
            "#...##"
        ),
        shape(
            17, "Eagle", "soaring: wings swept up to the tips, a body, a fanned tail",
            "#....#",
            "##..##",
            "######",
            ".####.",
            "..##..",
            ".####."
        ),
        shape(
            18, "Treasure Chest", "domed lid, a gap, then a deep base",
            ".####.",
            "######",
            "......",
            "######",
            "######"
        ),

        // ---- Crystal Night -------------------------------------------------
        shape(
            19, "Gem", "cut gem: flat top, wide girdle, tapering to a point",
            ".####.",
            "######",
            "######",
            ".####.",
            "..##.."
        ),
        shape(
            20, "Crescent Moon", "thick crescent opening to the right, tips top and bottom",
            "..####",
            ".###..",
            "###...",
            "###...",
            "###...",
            ".###..",
            "..####"
        ),
        shape(
            21, "Crystal", "a pointed shard with a small spike beside it, on a rock base",
            "...#..",
            "..###.",
            "#.###.",
            "##.###",
            "######",
            ".#####"
        ),
        shape(
            22, "Snowflake", "six arms: two long vertical arms and four diagonal tips",
            "..##..",
            "..##..",
            "#.##.#",
            ".####.",
            ".####.",
            "#.##.#",
            "..##..",
            "..##.."
        ),
        shape(
            23, "Magic Star", "four-point sparkle: long vertical spikes, concave sides, a broad centre",
            "..##..",
            "..##..",
            ".####.",
            "######",
            "######",
            ".####.",
            "..##..",
            "..##.."
        ),
        shape(
            24, "Crown", "three points, the middle one taller, over a solid band with a rounded base",
            "..##..",
            "#.##.#",
            "######",
            "######",
            "######",
            ".####."
        ),

        // ---- Cosmic --------------------------------------------------------
        shape(
            25, "Comet", "round head at the lower left, a tail streaming to the upper right",
            ".....#",
            "....##",
            "...###",
            "..####",
            ".####.",
            "#####.",
            "#####.",
            ".####."
        ),
        shape(
            26, "Rocket", "pointed nose, a body with a porthole, wide fins, a flame at the base",
            "..##..",
            ".####.",
            ".####.",
            ".#..#.",
            ".####.",
            "######",
            "#.##.#",
            "..##.."
        ),
        shape(
            27, "Planet", "a round body with a wide ring across its middle",
            "..##..",
            ".####.",
            ".####.",
            "######",
            "######",
            ".####.",
            ".####.",
            "..##.."
        ),
        shape(
            28, "UFO", "small dome over a wide saucer, with three lights underneath",
            "..##..",
            ".####.",
            ".####.",
            "######",
            "######",
            ".####.",
            "#.##.#",
            "..##.."
        ),
        shape(
            29, "Satellite", "central body with an antenna mast and two tall solar panels",
            "..##..",
            "##..##",
            "##..##",
            "##..##",
            "######",
            "######",
            "##..##",
            "##..##"
        ),
        shape(
            30, "Galaxy", "spiral: two arms curling out of a bright central bar",
            ".####.",
            "##..##",
            "###...",
            "####..",
            "######",
            "...###",
            "...###",
            "##..##",
            ".####."
        ),
    )

    fun forLevel(levelId: Int): CampaignShape? = ALL.firstOrNull { it.levelId == levelId }

    private fun shape(
        levelId: Int,
        discovery: String,
        description: String,
        vararg rows: String
    ) = CampaignShape(levelId, discovery, rows.toList(), description)
}
