package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.tutorial.DependencyStep
import com.sabalapps.arrowescape.tutorial.InMemoryTutorialFlagStore
import com.sabalapps.arrowescape.tutorial.OnboardingGuide
import com.sabalapps.arrowescape.tutorial.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two lessons, driven through the ViewModel the way the screen drives it: what the
 * board is told to show on a first play, what the guided taps do to the real game, what is
 * remembered, and where the lessons do *not* appear.
 *
 * A relaunch is a fresh ViewModel over the same stores — and the same flag store, because
 * "has this player been taught" is exactly what has to survive the process dying. The guided
 * tap is asked of the guide, and whether it was a good one is answered by the game itself:
 * the arrow leaves.
 */
class OnboardingFlowTest {

    private val mainStore: ProgressStore = InMemoryProgressStore()
    private val dailyStore: ProgressStore = InMemoryProgressStore()
    private val flags = InMemoryTutorialFlagStore()
    private val clock = FixedDateProvider(GameDate(2026, 10, 6))
    private var nextSeed = 9_000L

    private fun relaunch(): GameViewModel = GameViewModel(
        progress = ProgressRepository(mainStore),
        endless = EndlessRepository(mainStore, newSeed = { nextSeed++ }),
        daily = DailyRepository(dailyStore, clock),
        tutorialFlags = flags
    )

    private fun GameViewModel.guide(): OnboardingGuide =
        OnboardingGuide.resolve(tutorial.value, dependencyLesson.value, state.value.arrows)

    /** Taps the arrow the hand is on, and lets its flight finish. */
    private fun GameViewModel.tapGuided(): Int {
        val target = requireNotNull(guide().handTargetId) { "no hand to follow" }
        val before = state.value.arrows.size
        onArrowTapped(target)
        escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
        assertEquals("the guided arrow did not leave", before - 1, state.value.arrows.size)
        return target
    }

    private fun GameViewModel.clearBoard() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
            onArrowTapped(free.id)
            escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
            check(guard++ < 500)
        }
    }

    /** Fresh install through Level 1, landing on Level 2's first play. */
    private fun GameViewModel.toLevelTwo() {
        continueGame()
        clearBoard()
        continueAfterWin()
        assertEquals(2, state.value.level.id)
    }

    // ---- Level 1 -------------------------------------------------------------

    @Test
    fun `the first ever level one shows a hand on a real, tappable arrow`() {
        val viewModel = relaunch()
        viewModel.continueGame()

        val guide = viewModel.guide()
        assertEquals("Tap an arrow with a clear path", guide.caption)
        assertNotNull(guide.handTargetId)
        assertTrue(viewModel.isTappable(guide.handTargetId!!))
        assertEquals(guide.handTargetId, guide.spotlightId)
    }

    @Test
    fun `tapping the guided arrow escapes it, records the lesson and takes the hand away`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        assertFalse(flags.isTutorialCompleted())

        viewModel.tapGuided()

        assertTrue("the lesson worked, so it is recorded", flags.isTutorialCompleted())
        val guide = viewModel.guide()
        assertEquals("Nice! It escaped ✨", guide.caption)
        assertNull("the guide disappears", guide.handTargetId)
        assertEquals(TutorialStep.CHAIN, viewModel.tutorial.value.step)
    }

    @Test
    fun `the guide never plays a move for the player`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        val before = viewModel.state.value.arrows.size
        repeat(5) { viewModel.guide() }
        assertEquals(before, viewModel.state.value.arrows.size)
        assertEquals(GameStatus.PLAYING, viewModel.state.value.status)
    }

    @Test
    fun `a blocked tap does not take the hand away, because nothing has been taught yet`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        val blocked = viewModel.state.value.arrows.first { !MoveValidator.canEscape(it, viewModel.state.value.arrows) }
        viewModel.onArrowTapped(blocked.id)

        val guide = viewModel.guide()
        assertNotNull("the way out is still being shown", guide.handTargetId)
        assertNull("but the board is already pulsing the blocker", guide.spotlightId)
    }

    @Test
    fun `replaying level one shows no lesson`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()

        viewModel.startLevel(1)
        assertFalse(viewModel.guide().isActive)
        viewModel.restart()
        assertFalse(viewModel.guide().isActive)
        viewModel.replayCurrent()
        assertFalse(viewModel.guide().isActive)
    }

    @Test
    fun `level one's lesson survives a restart of the app`() {
        val first = relaunch()
        first.continueGame()
        first.tapGuided()

        val second = relaunch()
        second.continueGame()
        assertFalse("taught already: no hand on the next launch", second.guide().isActive)
    }

    @Test
    fun `a lesson that never worked is still offered after a restart`() {
        val first = relaunch()
        first.continueGame()
        // Opened and abandoned without a single escape.
        val second = relaunch()
        second.continueGame()
        assertNotNull(second.guide().handTargetId)
    }

    @Test
    fun `settings can replay the lesson, hand and all`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()
        assertFalse(viewModel.guide().isActive)

        viewModel.startTutorial()
        assertEquals(GameMode.Tutorial, viewModel.mode.value)
        assertNotNull("replay shows the hand again", viewModel.guide().handTargetId)
        assertFalse("and it is Level 1's lesson, not Level 2's", viewModel.dependencyLesson.value.isActive)
    }

    // ---- Level 2 -------------------------------------------------------------

    @Test
    fun `the first ever level two opens with the dependency lesson`() {
        val viewModel = relaunch()
        viewModel.toLevelTwo()

        assertEquals(DependencyStep.CLEAR_ONE, viewModel.dependencyLesson.value.step)
        assertFalse("Level 1's lesson is not running here", viewModel.tutorial.value.isActive)
        val guide = viewModel.guide()
        assertEquals("Clear one path to free another.", guide.caption)
        assertNotNull(guide.handTargetId)
        assertTrue(viewModel.isTappable(guide.handTargetId!!))
    }

    @Test
    fun `removing the guided arrow frees another, and the hand follows it`() {
        val viewModel = relaunch()
        viewModel.toLevelTwo()

        val first = viewModel.tapGuided()

        assertEquals(DependencyStep.TAP_FREED, viewModel.dependencyLesson.value.step)
        val guide = viewModel.guide()
        assertEquals("That freed another arrow. Tap it!", guide.caption)
        val next = requireNotNull(guide.handTargetId)
        assertTrue("the hand moved to a different arrow", next != first)
        assertTrue("and it is one the player can now take", viewModel.isTappable(next))
        assertEquals("the freed arrow glows", next, guide.spotlightId)
        assertFalse("not recorded until the idea has landed", flags.isDependencyLessonCompleted())
    }

    @Test
    fun `tapping the freed arrow lands the idea and records it`() {
        val viewModel = relaunch()
        viewModel.toLevelTwo()
        viewModel.tapGuided()
        viewModel.tapGuided()

        assertEquals(DependencyStep.UNDERSTOOD, viewModel.dependencyLesson.value.step)
        assertEquals("You've got it!", viewModel.guide().caption)
        assertNull(viewModel.guide().handTargetId)
        assertTrue(flags.isDependencyLessonCompleted())
        assertFalse("Level 2's lesson is not Level 1's flag", !flags.isTutorialCompleted())
    }

    @Test
    fun `the lesson then stops guiding and lets the player finish the level`() {
        val viewModel = relaunch()
        viewModel.toLevelTwo()
        viewModel.tapGuided()
        viewModel.tapGuided()

        // One more move of the player's own, and the caption goes too.
        val free = viewModel.state.value.arrows.first { MoveValidator.canEscape(it, viewModel.state.value.arrows) }
        viewModel.onArrowTapped(free.id)
        viewModel.escaping.value.forEach { viewModel.onEscapeAnimationFinished(it.flightId) }
        assertFalse(viewModel.guide().isActive)

        viewModel.clearBoard()
        assertEquals(GameStatus.WON, viewModel.state.value.status)
    }

    @Test
    fun `replaying level two shows no lesson`() {
        val viewModel = relaunch()
        viewModel.toLevelTwo()
        viewModel.tapGuided()
        viewModel.tapGuided()
        viewModel.clearBoard()

        viewModel.startLevel(2)
        assertFalse(viewModel.guide().isActive)
        viewModel.restart()
        assertFalse(viewModel.guide().isActive)
    }

    @Test
    fun `level two's lesson survives a restart of the app`() {
        val first = relaunch()
        first.toLevelTwo()
        first.tapGuided()
        first.tapGuided()

        val second = relaunch()
        second.startLevel(2)
        assertFalse(second.guide().isActive)
    }

    @Test
    fun `a level two lesson abandoned before it landed starts over next time`() {
        val first = relaunch()
        first.toLevelTwo()
        first.tapGuided() // freed something, then quit

        val second = relaunch()
        second.startLevel(2)
        assertEquals(DependencyStep.CLEAR_ONE, second.dependencyLesson.value.step)
        assertNotNull(second.guide().handTargetId)
    }

    @Test
    fun `a player who cleared level two before this lesson existed is not taught it`() {
        // Level 2 is already in their record, the flag was never written.
        val progress = ProgressRepository(mainStore)
        progress.markCompleted(1)
        progress.markCompleted(2)
        assertFalse(flags.isDependencyLessonCompleted())

        val viewModel = relaunch()
        viewModel.startLevel(2)
        assertFalse(viewModel.dependencyLesson.value.isActive)
    }

    @Test
    fun `restarting level two before the idea lands starts the lesson over, after it does not`() {
        val viewModel = relaunch()
        viewModel.toLevelTwo()
        viewModel.tapGuided()
        viewModel.restart()
        assertEquals(DependencyStep.CLEAR_ONE, viewModel.dependencyLesson.value.step)

        viewModel.tapGuided()
        viewModel.tapGuided()
        viewModel.restart()
        assertFalse(viewModel.guide().isActive)
    }

    // ---- where the lessons do not appear -------------------------------------

    @Test
    fun `no other campaign level shows a lesson`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        // Walk the whole campaign the honest way; every level but 1 and 2 must be silent.
        for (level in LevelProgression.all) {
            if (viewModel.state.value.level.id != level.id) viewModel.continueAfterWin()
            if (level.id > 2) {
                assertFalse("level ${level.id} showed a lesson", viewModel.guide().isActive)
            }
            viewModel.clearBoard()
        }
    }

    @Test
    fun `endless and the daily challenge never show a lesson`() {
        val viewModel = relaunch()
        viewModel.startEndless()
        assertFalse(viewModel.guide().isActive)
        viewModel.startDaily()
        assertFalse(viewModel.guide().isActive)
    }

    @Test
    fun `level one's lesson never appears on level two and vice versa`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        assertFalse(viewModel.dependencyLesson.value.isActive)
        viewModel.clearBoard()
        viewModel.continueAfterWin()
        assertFalse(viewModel.tutorial.value.isActive)
    }
}
