package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.time.GameDate

/**
 * Where the board on screen came from. The rules, the board, the animations and
 * the feedback are identical in every mode — this only changes what the HUD
 * reads, which save the move is written to, and what happens after a win.
 */
sealed interface GameMode {

    /** A numbered level from the curated catalogue. */
    data object Campaign : GameMode

    /**
     * A generated board. [seed] and [tier] are all it takes to rebuild it, so
     * they are what gets saved.
     *
     * [alreadyCleared] is what keeps Replay honest: the first win on a puzzle
     * advances the endless counters, and replaying the same seed afterwards does
     * not advance them again. Retrying a puzzle that was *lost* leaves it false,
     * so the eventual win still counts.
     */
    data class Endless(
        val seed: Long,
        val puzzleNumber: Int,
        val tier: EndlessTier,
        val alreadyCleared: Boolean = false
    ) : GameMode {
        /** What the HUD shows in place of a level name. */
        val title: String get() = "Endless #$puzzleNumber"
    }

    /**
     * The day's puzzle. [date] is its whole identity — the seed is derived from
     * it — so that is what gets saved, and the same date always means the same
     * board.
     *
     * [tier] is carried so the HUD can name the difficulty without re-deriving
     * it. [alreadyCleared] means "this day was already in the books when this
     * attempt started", and is set when the board is opened or replayed rather
     * than by winning it — unlike [Endless]'s flag of the same name, it guards
     * nothing. The streak cannot be double-counted whatever this says, because
     * recording a day is idempotent per date; this is what the result card
     * reads to tell "that is today done" apart from "you have already had
     * today".
     */
    data class Daily(
        val date: GameDate,
        val seed: Long,
        val tier: EndlessTier,
        val alreadyCleared: Boolean = false
    ) : GameMode {
        /** What the HUD shows in place of a level name. */
        val title: String get() = "Daily Challenge"
    }

    /**
     * Campaign Level 1, played as a lesson rather than as a level.
     *
     * This is only ever reached through "Replay Tutorial". It exists as its own
     * mode so that re-watching the tutorial cannot touch campaign progress: a
     * player on Level 20 who wants to see the lesson again must not have their
     * selection dragged back to Level 1, and clearing the demonstration board
     * must not rewrite a completion they already had. Nothing in this mode
     * persists anything at all.
     *
     * The *first* run of the tutorial is not this mode — that genuinely is
     * Campaign Level 1, played for real, with the tutorial captions over it.
     */
    data object Tutorial : GameMode
}
