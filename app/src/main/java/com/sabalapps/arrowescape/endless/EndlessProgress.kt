package com.sabalapps.arrowescape.endless

/**
 * How far the player has got in Endless Mode. Entirely local — there is no
 * account, no server and nothing here that leaves the device.
 *
 * [puzzleNumber] is the puzzle currently on offer, so a fresh install starts at
 * 1 with nothing completed. It advances on a win and stays put on a loss: a
 * failed endless puzzle is retried, not skipped.
 */
data class EndlessProgress(
    val puzzleNumber: Int = FIRST_PUZZLE,
    val totalCompleted: Int = 0,
    /** Puzzles cleared back to back without running out of lives. */
    val currentStreak: Int = 0,
    val bestStreak: Int = 0
) {
    /** The tier a *new* puzzle at this position is generated for. */
    val tier: EndlessTier get() = EndlessTier.forPuzzleNumber(puzzleNumber)

    companion object {
        const val FIRST_PUZZLE = 1

        /**
         * A ceiling on the stored puzzle number, used to sanity-check a save.
         * Far past anything reachable by playing; it exists so a corrupt value
         * cannot be mistaken for progress.
         */
        const val MAX_PUZZLE = 1_000_000
    }
}

/**
 * An endless puzzle the player is part-way through.
 *
 * [seed] and [tier] together are the whole board — generation is a pure
 * function of the pair — so a few dozen bytes restore a 36-arrow layout
 * exactly. The tier is stored rather than re-derived from [puzzleNumber] on
 * purpose: if the tier bands are ever re-cut, a puzzle already in progress still
 * rebuilds the board the player was looking at.
 */
data class EndlessSavedGame(
    val seed: Long,
    val puzzleNumber: Int,
    val tier: EndlessTier,
    val remainingArrowIds: Set<Int>,
    val lives: Int
)
