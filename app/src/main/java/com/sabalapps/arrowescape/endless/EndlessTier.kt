package com.sabalapps.arrowescape.endless

/**
 * A board shape. Columns are capped at six everywhere, exactly as in the
 * campaign: on a 320dp-wide phone a seventh column pushes a cell under the 48dp
 * minimum tap target. Difficulty goes into rows, arrow count and chain depth.
 */
data class BoardSize(val rows: Int, val columns: Int) {
    init {
        require(columns in 1..MAX_COLUMNS) { "columns must be 1..$MAX_COLUMNS, was $columns" }
        require(rows in 1..MAX_ROWS) { "rows must be 1..$MAX_ROWS, was $rows" }
    }

    val cells: Int get() = rows * columns

    override fun toString(): String = "${rows}x$columns"

    companion object {
        /** Widest board that still leaves 48dp tap targets on a 320dp phone. */
        const val MAX_COLUMNS = 6

        /** Tallest board that still fits above the hint line on a short phone. */
        const val MAX_ROWS = 9
    }
}

/**
 * The bar a generated board has to clear before a player is allowed to see it.
 *
 * These are all "is this interesting?" rules, never "is this solvable?" — every
 * board the generator builds is solvable by construction, so nothing here can
 * reject a puzzle for being broken. A rejection just costs one more attempt.
 */
data class QualityRules(
    /** Arrow count the board must land in. Boards that stall early fail here. */
    val arrows: IntRange,
    /** Most of the board may not be tappable on the opening move. */
    val maxFreeRatio: Double,
    /** Beginner boards may use two glyphs; everything else needs at least three. */
    val minDistinctDirections: Int,
    /** No single glyph may own more than this share of the board. */
    val maxDominantShare: Double,
    /** Caps solid stripes of one glyph along a row or column. */
    val maxSameDirectionRun: Int,
    val density: ClosedFloatingPointRange<Double>,
    /** At least this many removal passes, so there is a chain to work out. */
    val minPeelDepth: Int,
    /** Share of rows and columns that must hold an arrow — no dead bands. */
    val minBandCoverage: Double,
    /** Where the difficulty score has to land for the board to be on-tier. */
    val score: ClosedFloatingPointRange<Double>
) {
    /** Why a board was turned down, for the stress test's rejection report. */
    enum class Rejection {
        ARROW_COUNT, TOO_MANY_FREE, TOO_FEW_DIRECTIONS, ONE_DIRECTION_DOMINATES,
        DIRECTION_STRIPE, DENSITY, TOO_SHALLOW, DEAD_BAND, EMPTY_QUADRANT, OFF_TIER
    }

    /** The first rule [metrics] breaks, or null when the board is good to ship. */
    fun reject(metrics: PuzzleMetrics): Rejection? = when {
        metrics.arrowCount !in arrows -> Rejection.ARROW_COUNT
        metrics.freeRatio > maxFreeRatio -> Rejection.TOO_MANY_FREE
        metrics.distinctDirections < minDistinctDirections -> Rejection.TOO_FEW_DIRECTIONS
        metrics.dominantDirectionShare > maxDominantShare -> Rejection.ONE_DIRECTION_DOMINATES
        metrics.longestSameDirectionRun > maxSameDirectionRun -> Rejection.DIRECTION_STRIPE
        metrics.density !in density -> Rejection.DENSITY
        metrics.peelDepth < minPeelDepth -> Rejection.TOO_SHALLOW
        metrics.rowCoverage < minBandCoverage ||
            metrics.columnCoverage < minBandCoverage -> Rejection.DEAD_BAND
        metrics.occupiedQuadrants < 4 -> Rejection.EMPTY_QUADRANT
        metrics.difficultyScore !in score -> Rejection.OFF_TIER
        else -> null
    }

    fun accepts(metrics: PuzzleMetrics): Boolean = reject(metrics) == null
}

/**
 * The five endless difficulty tiers.
 *
 * Board sizes follow the campaign's convention of `rows x columns` with columns
 * capped at six, and the arrow bands overlap the campaign's own difficulty bands
 * so an endless board never feels out of place next to a curated one.
 */
enum class EndlessTier(
    val label: String,
    val sizes: List<BoardSize>,
    val arrows: IntRange,
    private val rules: (IntRange) -> QualityRules
) {
    BEGINNER(
        label = "Beginner",
        sizes = listOf(BoardSize(4, 4), BoardSize(5, 4)),
        arrows = 6..9,
        rules = { count ->
            QualityRules(
                arrows = count,
                maxFreeRatio = 0.50,
                minDistinctDirections = 2,
                maxDominantShare = 0.70,
                maxSameDirectionRun = 3,
                density = 0.25..0.70,
                minPeelDepth = 2,
                minBandCoverage = 0.60,
                score = 20.0..47.0
            )
        }
    ),
    EASY(
        label = "Easy",
        sizes = listOf(BoardSize(5, 5), BoardSize(6, 5)),
        arrows = 10..14,
        rules = { count ->
            QualityRules(
                arrows = count,
                maxFreeRatio = 0.40,
                minDistinctDirections = 3,
                maxDominantShare = 0.60,
                maxSameDirectionRun = 3,
                density = 0.30..0.70,
                minPeelDepth = 3,
                minBandCoverage = 0.70,
                score = 30.0..57.0
            )
        }
    ),
    MEDIUM(
        label = "Medium",
        sizes = listOf(BoardSize(6, 6), BoardSize(7, 6)),
        arrows = 15..22,
        rules = { count ->
            QualityRules(
                arrows = count,
                maxFreeRatio = 0.32,
                minDistinctDirections = 3,
                maxDominantShare = 0.55,
                maxSameDirectionRun = 3,
                density = 0.33..0.70,
                minPeelDepth = 4,
                minBandCoverage = 0.75,
                score = 42.0..70.0
            )
        }
    ),
    HARD(
        label = "Hard",
        sizes = listOf(BoardSize(7, 6), BoardSize(8, 6)),
        arrows = 22..29,
        rules = { count ->
            QualityRules(
                arrows = count,
                maxFreeRatio = 0.28,
                minDistinctDirections = 4,
                maxDominantShare = 0.50,
                maxSameDirectionRun = 3,
                density = 0.40..0.75,
                minPeelDepth = 5,
                minBandCoverage = 0.80,
                score = 55.0..82.0
            )
        }
    ),
    EXPERT(
        label = "Expert",
        sizes = listOf(BoardSize(8, 6), BoardSize(9, 6)),
        arrows = 28..36,
        rules = { count ->
            QualityRules(
                arrows = count,
                maxFreeRatio = 0.28,
                minDistinctDirections = 4,
                maxDominantShare = 0.45,
                maxSameDirectionRun = 3,
                density = 0.50..0.80,
                minPeelDepth = 5,
                minBandCoverage = 0.85,
                score = 65.0..92.0
            )
        }
    );

    /**
     * The quality bar for a board aiming at [target] arrows. The arrow rule is
     * built from the target rather than the whole tier band, with a little slack
     * below it, so a board that stalls three arrows short of what was asked for
     * is still shippable while one that stalls at half the target is not.
     */
    fun rulesFor(target: Int): QualityRules {
        val floor = maxOf(arrows.first, target - ARROW_SLACK)
        return rules(floor..arrows.last)
    }

    /** The bar used when nothing about a specific attempt is known yet. */
    val baseRules: QualityRules get() = rules(arrows)

    companion object {
        /** How far under its arrow target a board may land and still be shippable. */
        const val ARROW_SLACK = 3

        /**
         * The tier for the nth endless puzzle (1-based). Puzzles 1-5 are
         * Beginner, 6-15 Easy, 16-30 Medium, 31-50 Hard, 51 and up Expert.
         */
        fun forPuzzleNumber(puzzleNumber: Int): EndlessTier = when {
            puzzleNumber <= 5 -> BEGINNER
            puzzleNumber <= 15 -> EASY
            puzzleNumber <= 30 -> MEDIUM
            puzzleNumber <= 50 -> HARD
            else -> EXPERT
        }

        /** Lookup by persisted name, falling back to the tier the number implies. */
        fun fromKey(key: String?, puzzleNumber: Int): EndlessTier =
            entries.firstOrNull { it.name == key } ?: forPuzzleNumber(puzzleNumber)
    }
}
