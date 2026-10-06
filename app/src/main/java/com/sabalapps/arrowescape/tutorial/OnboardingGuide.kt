package com.sabalapps.arrowescape.tutorial

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.HintEngine

/**
 * What the board should show right now to teach, as plain data: the caption under the
 * board, the arrow the animated hand taps, and the arrow that glows.
 *
 * Both lessons ([TutorialState] on Level 1, [DependencyLesson] on Level 2) reduce to this,
 * which is what lets the screen stay ignorant of which lesson is running. Only one lesson
 * can run on a board, so the first active one wins.
 *
 * Pure: no Android, no Compose. Every arrow named here comes from [HintEngine] or from the
 * lesson's own record of what was freed — never from a second copy of the blocking rule.
 */
data class OnboardingGuide(
    /** The caption, or null when nothing is being taught. */
    val caption: String?,
    /** The arrow the hand taps, or null for no hand. */
    val handTargetId: Int?,
    /** The arrow that glows. Not always the hand's: Level 1's second step has a hand and no glow. */
    val spotlightId: Int?
) {
    val isActive: Boolean get() = caption != null || handTargetId != null

    companion object {
        val None = OnboardingGuide(caption = null, handTargetId = null, spotlightId = null)

        fun resolve(
            tutorial: TutorialState,
            dependency: DependencyLesson,
            board: List<ArrowTile>
        ): OnboardingGuide = when {
            tutorial.isActive -> {
                val step = tutorial.step
                // Level 1 points at the arrow the Hint button would pick, so the lesson and
                // the hint can never disagree about what a good move looks like.
                val target = if (step.showsHand) HintEngine.hint(board)?.id else null
                OnboardingGuide(
                    caption = step.message,
                    handTargetId = target,
                    // BLOCKED has a hand but no glow: the board is already pulsing the
                    // arrow that did the blocking, and two glows is two instructions.
                    spotlightId = target.takeIf { step.highlightsFreeArrow }
                )
            }

            dependency.isActive -> {
                val step = dependency.step
                val target = if (step.showsHand) dependency.targetId(board) else null
                OnboardingGuide(
                    caption = step.message,
                    handTargetId = target,
                    spotlightId = target
                )
            }

            else -> None
        }
    }
}
