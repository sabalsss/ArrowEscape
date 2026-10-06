package com.sabalapps.arrowescape.tutorial

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.HintEngine

/**
 * Level 2's lesson — "removing one arrow can free another" — as four steps.
 *
 * ```
 * CLEAR_ONE  a hand on an arrow whose removal opens a path   "Clear one path to free another."
 *     │  the player taps it and something is freed
 * TAP_FREED  the freed arrow glows, the hand moves to it     "That freed another arrow."
 *     │  the player taps a freed arrow
 * UNDERSTOOD "You've got it!"
 *     │  the next move
 * FINISHED   nothing is shown
 * ```
 *
 * Like [TutorialState] it is driven by what the player did and by nothing else. And like
 * everything that points at an arrow in this game it asks the same two sources every
 * time — [HintEngine] for "which arrow is worth taking" and `EscapeAnalysis.newlyFreed`
 * (handed in by the ViewModel) for "what did that open" — so there is no second opinion
 * about the blocking rule in here. The lesson never makes a move, never reveals more than
 * one arrow at a time and never stops the player tapping something else.
 */
enum class DependencyStep {

    /** Opening state: the hand is on an arrow that frees something. */
    CLEAR_ONE,

    /** Something was freed: it glows and the hand moves to it. */
    TAP_FREED,

    /** The player tapped a freed arrow; the idea has landed. */
    UNDERSTOOD,

    /** Taught. Nothing is shown and nothing is tracked. */
    FINISHED;

    /** The line under the board. Empty once there is nothing left to say. */
    val message: String
        get() = when (this) {
            CLEAR_ONE -> "Clear one path to free another."
            TAP_FREED -> "That freed another arrow. Tap it!"
            UNDERSTOOD -> "You've got it!"
            FINISHED -> ""
        }

    /** Whether the animated hand (and the arrow's glow) is up. */
    val showsHand: Boolean get() = this == CLEAR_ONE || this == TAP_FREED

    /** True once the idea has landed. This is the moment it is recorded. */
    val isTaught: Boolean get() = this == UNDERSTOOD || this == FINISHED

    val isActive: Boolean get() = this != FINISHED
}

/**
 * The lesson's progress. [freed] is what the first removal opened, remembered so the hand
 * can follow it after the board has moved on.
 */
data class DependencyLesson(
    val step: DependencyStep = DependencyStep.CLEAR_ONE,
    val freed: Set<Int> = emptySet()
) {
    val isActive: Boolean get() = step.isActive

    /**
     * An arrow [escapedId] left, and [freedIds] are the arrows that left opened up (empty
     * when it opened nothing).
     */
    fun onArrowEscaped(escapedId: Int, freedIds: Set<Int>): DependencyLesson = when (step) {
        // A removal that opened nothing teaches nothing about dependencies: stay put and
        // let the hand find the next arrow worth taking.
        DependencyStep.CLEAR_ONE ->
            if (freedIds.isEmpty()) this else DependencyLesson(DependencyStep.TAP_FREED, freedIds)

        // Any arrow that the first removal freed ends it — the player has now used a path
        // that did not exist a move ago. Taking some other arrow first leaves the lesson
        // exactly where it was.
        DependencyStep.TAP_FREED ->
            if (escapedId in freed) DependencyLesson(DependencyStep.UNDERSTOOD) else this

        // Said once; the next move is the player's own.
        DependencyStep.UNDERSTOOD -> DependencyLesson(DependencyStep.FINISHED)
        DependencyStep.FINISHED -> this
    }

    /** A tap was refused. Mid-lesson it changes nothing; past the lesson it just ends it. */
    fun onMoveBlocked(): DependencyLesson =
        if (step == DependencyStep.UNDERSTOOD) DependencyLesson(DependencyStep.FINISHED) else this

    /** The board ended, either way: whatever was said, the player has had every chance to read it. */
    fun onBoardFinished(): DependencyLesson = DependencyLesson(DependencyStep.FINISHED)

    /**
     * The arrow the hand points at on [board], or null when the lesson is pointing at
     * nothing. Derived from the board each time, so it can never name an arrow that has
     * left.
     */
    fun targetId(board: List<ArrowTile>): Int? = when (step) {
        DependencyStep.CLEAR_ONE -> HintEngine.hint(board)?.id
        DependencyStep.TAP_FREED -> {
            val onBoard = board.mapTo(HashSet()) { it.id }
            freed.filter { it in onBoard }.minOrNull()
        }
        DependencyStep.UNDERSTOOD, DependencyStep.FINISHED -> null
    }

    companion object {
        /** A lesson about to start. */
        val Starting = DependencyLesson()

        /** A lesson that is not running — every board but Level 2's first play. */
        val Inactive = DependencyLesson(DependencyStep.FINISHED)
    }
}
