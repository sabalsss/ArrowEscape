package com.sabalapps.arrowescape.tutorial

/**
 * Level 1's lesson — "this arrow has a clear path and can escape" — as four states.
 *
 * The rule is small enough that reading it is harder than doing it, so there is
 * no multi-page tutorial here: the player is shown one arrow, told to tap it,
 * and told what just happened. Two sentences, both triggered by something the
 * player did. A board that is already playable is the tutorial.
 *
 * The *second* idea — removing one arrow can free another — is Level 2's lesson
 * ([DependencyLesson]), taught on a board that has a dependency to show.
 */
enum class TutorialStep {

    /** Opening state: one free arrow is emphasised and named. */
    TAP_FREE,

    /** The player tapped something blocked, so the rule gets said out loud. */
    BLOCKED,

    /** An arrow left: the lesson has worked, and says so once. */
    CHAIN,

    /** Taught. Nothing is shown and nothing is tracked. */
    FINISHED;

    /** The line under the board. Empty once there is nothing left to say. */
    val message: String
        get() = when (this) {
            TAP_FREE -> "Tap an arrow with a clear path"
            BLOCKED -> "Another arrow is blocking its path"
            CHAIN -> "Nice! It escaped \u2728"
            FINISHED -> ""
        }

    /**
     * Whether this step emphasises a free arrow on the board.
     *
     * Only the opening step does. [BLOCKED] deliberately does not: the board
     * already pulses the arrow that did the blocking, and a second highlight
     * pointing somewhere else at the same moment is two instructions competing
     * for the same glance.
     */
    val highlightsFreeArrow: Boolean get() = this == TAP_FREE

    /**
     * Whether the animated hand points at the free arrow. It stays up through
     * [BLOCKED]: a player who tapped the wrong arrow has not been taught yet and still
     * needs to be shown the right one — the board's own pulse names the blocker, the
     * hand names the way out.
     */
    val showsHand: Boolean get() = this == TAP_FREE || this == BLOCKED

    /**
     * True once an arrow has actually escaped under the lesson. This is the moment
     * the lesson is *taught*, and the moment it is recorded: a player who cleared
     * one arrow and then backed out has seen what Level 1 is for.
     */
    val isTaught: Boolean get() = this == CHAIN || this == FINISHED

    /** True while the overlay has anything to show. */
    val isActive: Boolean get() = this != FINISHED
}

/**
 * The tutorial's progress through [TutorialStep], as a pure state machine.
 *
 * It is driven by what the player did, not by a timer or a Next button, so the
 * transitions are the three things that can happen to a tap plus the end of the
 * board. Nothing in here touches the board, the level or any save — a tutorial
 * step is a caption and a glow, and Campaign Level 1 is exactly the level it
 * always was while the tutorial runs over it.
 */
data class TutorialState(val step: TutorialStep = TutorialStep.TAP_FREE) {

    val isActive: Boolean get() = step.isActive

    /** An arrow escaped. */
    fun onArrowEscaped(): TutorialState = when (step) {
        // The first clean tap earns the second sentence.
        TutorialStep.TAP_FREE, TutorialStep.BLOCKED -> copy(step = TutorialStep.CHAIN)
        // The lesson has been both stated and demonstrated; stop talking.
        TutorialStep.CHAIN -> copy(step = TutorialStep.FINISHED)
        TutorialStep.FINISHED -> this
    }

    /** A tap was refused because something was in the way. */
    fun onMoveBlocked(): TutorialState = when (step) {
        TutorialStep.TAP_FREE, TutorialStep.BLOCKED -> copy(step = TutorialStep.BLOCKED)
        // Past the explanation, a blocked tap is just a blocked tap — the board's
        // own shake and blocker pulse say it better than a caption would.
        TutorialStep.CHAIN -> copy(step = TutorialStep.FINISHED)
        TutorialStep.FINISHED -> this
    }

    /**
     * The board ended, either way. A player who cleared Level 1 or ran out of
     * lives on it has had every chance to read both sentences, so the tutorial
     * is done regardless of which step it was on.
     */
    fun onBoardFinished(): TutorialState = copy(step = TutorialStep.FINISHED)

    companion object {
        /** A tutorial about to start. */
        val Starting = TutorialState(TutorialStep.TAP_FREE)

        /** A tutorial that is not running, for every board that is not Level 1. */
        val Inactive = TutorialState(TutorialStep.FINISHED)
    }
}
