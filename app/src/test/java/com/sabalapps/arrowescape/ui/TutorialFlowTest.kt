package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.tutorial.InMemoryTutorialFlagStore
import com.sabalapps.arrowescape.tutorial.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first-run lesson, driven through the ViewModel: when it appears, when it
 * does not, what it does to the board it runs over, and what Replay Tutorial
 * costs a player who is twenty levels in.
 *
 * As elsewhere, a relaunch is a fresh ViewModel over the same stores — and here
 * the flag store is shared too, because "has this player been taught" is exactly
 * the thing that has to survive the process dying.
 */
class TutorialFlowTest {

    private val mainStore: ProgressStore = InMemoryProgressStore()
    private val dailyStore: ProgressStore = InMemoryProgressStore()
    private val flags = InMemoryTutorialFlagStore()
    private val clock = FixedDateProvider(GameDate(2026, 10, 4))
    private var nextSeed = 4_000L

    private fun relaunch(): GameViewModel = GameViewModel(
        progress = ProgressRepository(mainStore),
        endless = EndlessRepository(mainStore, newSeed = { nextSeed++ }),
        daily = DailyRepository(dailyStore, clock),
        tutorialFlags = flags
    )

    private fun GameViewModel.removeOne() {
        val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
        onArrowTapped(free.id)
        escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
    }

    private fun GameViewModel.tapBlocked() {
        val target = state.value.arrows.first { !MoveValidator.canEscape(it, state.value.arrows) }
        onArrowTapped(target.id)
        blocked.value?.let { onBlockedFeedbackFinished(it.nonce) }
    }

    private fun GameViewModel.clearBoard() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            removeOne()
            check(guard++ < 500) { "clearing the board did not terminate" }
        }
    }

    // ---- when it appears -----------------------------------------------------

    @Test
    fun `the tutorial runs on the first ever level one`() {
        val viewModel = relaunch()
        viewModel.continueGame()

        assertEquals(LevelProgression.first.id, viewModel.state.value.level.id)
        assertTrue("a fresh install should be taught", viewModel.tutorial.value.isActive)
        assertEquals(TutorialStep.TAP_FREE, viewModel.tutorial.value.step)
    }

    @Test
    fun `the opening step emphasises a free arrow and says what to do`() {
        val viewModel = relaunch()
        viewModel.continueGame()

        val step = viewModel.tutorial.value.step
        assertTrue(step.highlightsFreeArrow)
        assertEquals("Tap an arrow with a clear path", step.message)
    }

    @Test
    fun `a clean tap moves on to the encouragement`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.removeOne()

        assertEquals(TutorialStep.CHAIN, viewModel.tutorial.value.step)
        assertEquals("Nice! It escaped \u2728", viewModel.tutorial.value.step.message)
    }

    @Test
    fun `a blocked tap early on explains blocking`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.tapBlocked()

        assertEquals(TutorialStep.BLOCKED, viewModel.tutorial.value.step)
        assertEquals("Another arrow is blocking its path", viewModel.tutorial.value.step.message)
    }

    @Test
    fun `a blocked tap during the tutorial still names the blocker on the board`() {
        // The caption explains the rule; the board still has to point at the
        // specific arrow that caused it, which is pre-existing behaviour the
        // tutorial must not have switched off.
        val viewModel = relaunch()
        viewModel.continueGame()
        // Tapped without the usual "animation finished" callback, so the
        // feedback is still on screen to inspect.
        val target = viewModel.state.value.arrows
            .first { !MoveValidator.canEscape(it, viewModel.state.value.arrows) }
        viewModel.onArrowTapped(target.id)

        val feedback = requireNotNull(viewModel.blocked.value)
        assertEquals(target.id, feedback.tileId)
        assertTrue("the blocker should be identified", feedback.blockerId != null)
        assertEquals(TutorialStep.BLOCKED, viewModel.tutorial.value.step)
    }

    @Test
    fun `the tutorial does not run on any level but the first`() {
        // Unlock a few levels the honest way, then open one of them.
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()
        viewModel.continueAfterWin()

        assertEquals(2, viewModel.state.value.level.id)
        assertFalse(viewModel.tutorial.value.isActive)
    }

    @Test
    fun `the tutorial does not run in endless mode or the daily challenge`() {
        val viewModel = relaunch()
        viewModel.startEndless()
        assertFalse("endless is not where the rule gets taught", viewModel.tutorial.value.isActive)

        viewModel.startDaily()
        assertFalse("the daily is not where the rule gets taught", viewModel.tutorial.value.isActive)
    }

    // ---- it only shows itself once ------------------------------------------

    @Test
    fun `the first clean escape persists the flag, and the lesson then finishes`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        assertFalse(flags.isTutorialCompleted())

        viewModel.removeOne() // -> CHAIN: taught
        assertTrue("the lesson worked, so it should already be recorded", flags.isTutorialCompleted())
        assertTrue(viewModel.tutorial.value.isActive)

        viewModel.removeOne() // -> FINISHED
        assertFalse(viewModel.tutorial.value.isActive)
        assertTrue(flags.isTutorialCompleted())
    }

    @Test
    fun `clearing level one finishes the tutorial even if it was not read through`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()

        assertFalse(viewModel.tutorial.value.isActive)
        assertTrue(flags.isTutorialCompleted())
    }

    @Test
    fun `running out of lives on level one still counts as taught`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        var guard = 0
        while (viewModel.state.value.status == GameStatus.PLAYING) {
            viewModel.tapBlocked()
            check(guard++ < 50) { "losing the board did not terminate" }
        }
        assertEquals(GameStatus.LOST, viewModel.state.value.status)
        assertTrue(flags.isTutorialCompleted())
    }

    @Test
    fun `the tutorial never shows itself again after it has been finished`() {
        val first = relaunch()
        first.continueGame()
        first.clearBoard()

        // Come back to level one deliberately, several times, including after a
        // relaunch. It must stay quiet.
        val reopened = relaunch()
        reopened.startLevel(1)
        assertFalse(reopened.tutorial.value.isActive)

        reopened.continueGame()
        assertFalse(reopened.tutorial.value.isActive)

        relaunch().startLevel(1)
        assertFalse(relaunch().also { it.startLevel(1) }.tutorial.value.isActive)
    }

    @Test
    fun `a tutorial abandoned before the first escape comes back, because the player was not taught`() {
        val first = relaunch()
        first.continueGame()
        first.tapBlocked() // saw the explanation of blocking, never cleared an arrow, then quit
        assertTrue(first.tutorial.value.isActive)
        assertFalse(flags.isTutorialCompleted())

        val reopened = relaunch()
        reopened.continueGame()
        assertTrue("an unfinished lesson should still be offered", reopened.tutorial.value.isActive)
        assertEquals(TutorialStep.TAP_FREE, reopened.tutorial.value.step)
    }

    @Test
    fun `a tutorial abandoned after the first escape stays taught`() {
        val first = relaunch()
        first.continueGame()
        first.removeOne() // the guided tap worked, then quit
        assertTrue(flags.isTutorialCompleted())

        val reopened = relaunch()
        reopened.continueGame()
        assertFalse("a lesson that already worked is not shown again", reopened.tutorial.value.isActive)
    }

    @Test
    fun `restarting before the lesson has worked starts it over`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.tapBlocked()
        assertEquals(TutorialStep.BLOCKED, viewModel.tutorial.value.step)

        viewModel.restart()
        assertEquals(TutorialStep.TAP_FREE, viewModel.tutorial.value.step)
    }

    @Test
    fun `restarting after the lesson has worked does not bring it back`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.removeOne()
        assertEquals(TutorialStep.CHAIN, viewModel.tutorial.value.step)

        viewModel.restart()
        assertFalse(viewModel.tutorial.value.isActive)
    }

    @Test
    fun `restarting a level after the lesson is over does not bring it back`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()
        viewModel.continueAfterWin()
        viewModel.restart()

        assertFalse(viewModel.tutorial.value.isActive)
    }

    // ---- replay from settings ------------------------------------------------

    @Test
    fun `replay tutorial opens the first board with the lesson running`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()
        assertTrue(flags.isTutorialCompleted())

        viewModel.startTutorial()

        assertEquals(GameMode.Tutorial, viewModel.mode.value)
        assertEquals(LevelProgression.first.id, viewModel.state.value.level.id)
        assertTrue(viewModel.tutorial.value.isActive)
        assertEquals(TutorialStep.TAP_FREE, viewModel.tutorial.value.step)
    }

    @Test
    fun `replay tutorial works from a fresh install too`() {
        val viewModel = relaunch()
        viewModel.startTutorial()
        assertEquals(GameMode.Tutorial, viewModel.mode.value)
        assertTrue(viewModel.tutorial.value.isActive)
    }

    @Test
    fun `replaying the tutorial does not reopen it automatically next time`() {
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()
        viewModel.startTutorial()
        viewModel.clearBoard()

        assertTrue("the flag should still say taught", flags.isTutorialCompleted())
        val reopened = relaunch()
        reopened.startLevel(1)
        assertFalse(reopened.tutorial.value.isActive)
    }

    @Test
    fun `the tutorial replays its own board rather than advancing`() {
        val viewModel = relaunch()
        viewModel.startTutorial()
        viewModel.clearBoard()

        viewModel.replayCurrent()
        assertEquals(GameMode.Tutorial, viewModel.mode.value)
        assertEquals(LevelProgression.first.id, viewModel.state.value.level.id)
        assertEquals(viewModel.state.value.level.arrows.size, viewModel.state.value.arrows.size)

        // There is no "next" to advance to, so the primary action does nothing.
        viewModel.continueAfterWin()
        assertEquals(GameMode.Tutorial, viewModel.mode.value)
    }

    // ---- it must not touch Campaign Level 1 ---------------------------------

    @Test
    fun `replaying the tutorial does not move campaign selection or completion`() {
        // A player several levels in, which is the case the separate mode exists
        // for: being shown the lesson again must not drag them back to Level 1.
        val viewModel = relaunch()
        repeat(4) {
            viewModel.continueGame()
            viewModel.clearBoard()
            viewModel.continueAfterWin()
        }
        val progressBefore = viewModel.playerProgress.value
        assertEquals(setOf(1, 2, 3, 4), progressBefore.completedLevels)
        assertEquals(5, progressBefore.currentLevel)

        viewModel.startTutorial()
        viewModel.clearBoard()

        assertEquals(
            "the tutorial moved campaign progress",
            progressBefore,
            viewModel.playerProgress.value
        )
        assertEquals(5, viewModel.playerProgress.value.currentLevel)
    }

    @Test
    fun `replaying the tutorial does not write a campaign save`() {
        val viewModel = relaunch()
        repeat(2) {
            viewModel.continueGame()
            viewModel.clearBoard()
            viewModel.continueAfterWin()
        }
        // Leave level three half-done, so there is a real save to protect.
        viewModel.continueGame()
        viewModel.removeOne()
        val saved = requireNotNull(viewModel.savedGame.value)

        viewModel.startTutorial()
        viewModel.removeOne()
        viewModel.removeOne()

        assertEquals(
            "the tutorial overwrote the campaign save",
            saved,
            viewModel.savedGame.value
        )

        // And the real board still comes back.
        val reopened = relaunch()
        reopened.continueGame()
        assertEquals(saved.levelId, reopened.state.value.level.id)
        assertEquals(saved.remainingArrowIds, reopened.state.value.arrows.map { it.id }.toSet())
    }

    @Test
    fun `the tutorial leaves level one's layout exactly as it was`() {
        val before = Levels.byId(1)
        val viewModel = relaunch()
        viewModel.startTutorial()
        viewModel.clearBoard()
        viewModel.restart()
        viewModel.clearBoard()

        assertEquals("the catalogue was modified", before, Levels.byId(1))
        assertEquals(
            "the board on screen is not level one",
            Levels.byId(1)?.arrows?.size,
            viewModel.state.value.level.arrows.size
        )
    }

    @Test
    fun `the tutorial leaves endless and daily progress untouched`() {
        val viewModel = relaunch()
        viewModel.startEndless()
        viewModel.clearBoard()
        viewModel.startDaily()
        viewModel.clearBoard()
        val endlessBefore = viewModel.endlessProgress.value
        val dailyBefore = viewModel.dailyProgress.value

        viewModel.startTutorial()
        viewModel.clearBoard()

        assertEquals(endlessBefore, viewModel.endlessProgress.value)
        assertEquals(dailyBefore, viewModel.dailyProgress.value)
    }

    @Test
    fun `a fresh install that only ever replays the tutorial has no campaign progress`() {
        val viewModel = relaunch()
        repeat(3) {
            viewModel.startTutorial()
            viewModel.clearBoard()
        }
        assertEquals(PlayerProgress(), viewModel.playerProgress.value)
        assertNull(viewModel.savedGame.value)
    }

    @Test
    fun `the first run of the tutorial does count as playing level one`() {
        // The counterpart to the tests above: the lesson over a *first* Level 1
        // is a real level being played, so clearing it has to unlock Level 2.
        val viewModel = relaunch()
        viewModel.continueGame()
        assertTrue(viewModel.tutorial.value.isActive)
        viewModel.clearBoard()

        assertEquals(GameMode.Campaign, viewModel.mode.value)
        assertTrue(viewModel.playerProgress.value.isCompleted(1))
        assertTrue(viewModel.playerProgress.value.isUnlocked(2))
    }
}
