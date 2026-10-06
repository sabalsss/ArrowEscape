package com.sabalapps.arrowescape.progress

import com.sabalapps.arrowescape.game.StarRating

/**
 * Everything the player keeps between sessions about *which* levels they have
 * reached. The board they are part-way through lives in [SavedGame].
 *
 * A fresh install is exactly the default: Level 1 unlocked, Level 1 selected,
 * nothing completed, no stars.
 */
data class PlayerProgress(
    val highestUnlockedLevel: Int = FIRST_LEVEL,
    val currentLevel: Int = FIRST_LEVEL,
    val completedLevels: Set<Int> = emptySet(),
    /**
     * The best star result per level, 1-3. A level absent here has either not
     * been cleared or was cleared before stars existed — both read as
     * [StarRating.NONE], which is why this is a sparse map rather than a value
     * on every level.
     *
     * Only ever the *best*: a worse replay does not overwrite a better run.
     */
    val bestStars: Map<Int, Int> = emptyMap()
) {
    fun isUnlocked(levelId: Int): Boolean =
        levelId in FIRST_LEVEL..highestUnlockedLevel

    fun isCompleted(levelId: Int): Boolean = levelId in completedLevels

    /** The player's best run on [levelId], or [StarRating.NONE] if there is none. */
    fun starsFor(levelId: Int): Int = bestStars[levelId] ?: StarRating.NONE

    companion object {
        const val FIRST_LEVEL = 1
    }
}

/**
 * A level the player has started but not finished. Only the facts needed to
 * rebuild the board are stored — never animation or transient UI state.
 *
 * [blockedTaps] and [hintUsed] are the run's star tally, carried through a
 * relaunch so that killing the app mid-level cannot launder a mistake into a
 * Perfect Escape. They are the whole of what the star rules read; see
 * [StarRating].
 */
data class SavedGame(
    val levelId: Int,
    val remainingArrowIds: Set<Int>,
    val lives: Int,
    val blockedTaps: Int = 0,
    val hintUsed: Boolean = false
)
