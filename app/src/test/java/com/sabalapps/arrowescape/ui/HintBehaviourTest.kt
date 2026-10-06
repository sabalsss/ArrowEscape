package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Hint action, as the player experiences it: what it points at, when it goes
 * away, and what it refuses to do.
 *
 * The highlight's *appearance* is a Compose concern and is not tested here. What
 * is tested is the state the board renders from — which arrow is nominated and
 * for how long it stays nominated — because that is where a hint could actually
 * be wrong.
 */
class HintBehaviourTest {

    private val mainStore: ProgressStore = InMemoryProgressStore()
    private val dailyStore: ProgressStore = InMemoryProgressStore()
    private val clock = FixedDateProvider(GameDate(2026, 10, 4))
    private var nextSeed = 3_000L

    private fun viewModel(): GameViewModel = GameViewModel(
        progress = ProgressRepository(mainStore),
        endless = EndlessRepository(mainStore, newSeed = { nextSeed++ }),
        daily = DailyRepository(dailyStore, clock)
    )

    private fun GameViewModel.hintedArrow() =
        hint.value?.let { highlight -> state.value.arrows.first { it.id == highlight.tileId } }

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

    // ---- what a hint points at -----------------------------------------------

    @Test
    fun `there is no hint until one is asked for`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        assertNull("a hint must never appear on its own", viewModel.hint.value)
    }

    @Test
    fun `a hint always nominates an arrow that can escape`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()

        val hinted = requireNotNull(viewModel.hintedArrow())
        assertTrue(
            "the hint nominated a blocked arrow",
            MoveValidator.canEscape(hinted, viewModel.state.value.arrows)
        )
    }

    @Test
    fun `a hint never nominates a blocked arrow at any point in a level`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        var guard = 0
        while (viewModel.state.value.status == GameStatus.PLAYING) {
            viewModel.requestHint()
            val hinted = requireNotNull(viewModel.hintedArrow()) {
                "no hint with ${viewModel.state.value.arrows.size} arrows left"
            }
            assertTrue(
                "hinted a blocked arrow with ${viewModel.state.value.arrows.size} arrows left",
                MoveValidator.canEscape(hinted, viewModel.state.value.arrows)
            )
            viewModel.removeOne()
            check(guard++ < 500) { "the level did not terminate" }
        }
    }

    @Test
    fun `tapping the hinted arrow is always a legal move`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        val livesBefore = viewModel.state.value.lives
        var guard = 0
        while (viewModel.state.value.status == GameStatus.PLAYING) {
            viewModel.requestHint()
            val hintedId = requireNotNull(viewModel.hint.value).tileId
            viewModel.onArrowTapped(hintedId)
            viewModel.escaping.value.forEach { viewModel.onEscapeAnimationFinished(it.flightId) }
            check(guard++ < 500) { "the level did not terminate" }
        }
        assertEquals(GameStatus.WON, viewModel.state.value.status)
        assertEquals("following hints should never cost a life", livesBefore, viewModel.state.value.lives)
    }

    @Test
    fun `the same board asked twice nominates the same arrow`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        val first = requireNotNull(viewModel.hint.value).tileId
        viewModel.requestHint()
        assertEquals(first, requireNotNull(viewModel.hint.value).tileId)
    }

    @Test
    fun `asking again restarts the highlight rather than doing nothing`() {
        // The nonce is what the glow animation keys off, so a second request has
        // to produce a new one even when it nominates the same arrow.
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        val first = requireNotNull(viewModel.hint.value)
        viewModel.requestHint()
        val second = requireNotNull(viewModel.hint.value)

        assertEquals(first.tileId, second.tileId)
        assertNotEquals("the glow should restart", first.nonce, second.nonce)
    }

    // ---- when a hint goes away -----------------------------------------------

    @Test
    fun `a hint clears when the hinted arrow is tapped`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        val hintedId = requireNotNull(viewModel.hint.value).tileId

        viewModel.onArrowTapped(hintedId)
        assertNull(viewModel.hint.value)
    }

    @Test
    fun `a hint clears when some other arrow is tapped`() {
        // The board changed, so the hint is about a position that no longer
        // exists even if the hinted arrow is still standing.
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        assertNotNull(viewModel.hint.value)

        viewModel.removeOne()
        assertNull("a board change must clear the hint", viewModel.hint.value)
    }

    @Test
    fun `a hint clears on a blocked tap too`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        assertNotNull(viewModel.hint.value)

        viewModel.tapBlocked()
        assertNull(viewModel.hint.value)
    }

    @Test
    fun `a hint clears on restart`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        viewModel.restart()
        assertNull(viewModel.hint.value)
    }

    @Test
    fun `a hint clears when the next level is opened`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        viewModel.clearBoard()
        viewModel.continueAfterWin()
        assertNull(viewModel.hint.value)
    }

    @Test
    fun `a hint clears when the mode changes`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        assertNotNull(viewModel.hint.value)

        viewModel.startEndless()
        assertNull(viewModel.hint.value)

        viewModel.requestHint()
        assertNotNull(viewModel.hint.value)
        viewModel.startDaily()
        assertNull(viewModel.hint.value)
    }

    @Test
    fun `the expiry callback only clears the hint it was issued for`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.requestHint()
        val stale = requireNotNull(viewModel.hint.value).nonce
        viewModel.requestHint()
        val current = requireNotNull(viewModel.hint.value)

        // A timer left over from the first hint fires late.
        viewModel.onHintFinished(stale)
        assertEquals("a stale timer cancelled the live hint", current, viewModel.hint.value)

        viewModel.onHintFinished(current.nonce)
        assertNull(viewModel.hint.value)
    }

    @Test
    fun `the expiry callback is harmless when there is no hint`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.onHintFinished(nonce = 99)
        assertNull(viewModel.hint.value)
    }

    // ---- finished boards -----------------------------------------------------

    @Test
    fun `a won board cannot be hinted`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        viewModel.clearBoard()
        assertEquals(GameStatus.WON, viewModel.state.value.status)

        viewModel.requestHint()
        assertNull("a cleared board has nothing to hint at", viewModel.hint.value)
        assertFalse(viewModel.canHint())
    }

    @Test
    fun `a lost board cannot be hinted`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        var guard = 0
        while (viewModel.state.value.status == GameStatus.PLAYING) {
            viewModel.tapBlocked()
            check(guard++ < 50) { "losing the board did not terminate" }
        }
        assertEquals(GameStatus.LOST, viewModel.state.value.status)

        viewModel.requestHint()
        assertNull("a lost board has nothing to hint at", viewModel.hint.value)
        assertFalse(viewModel.canHint())
    }

    @Test
    fun `the last arrow on the board can still be hinted`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        while (viewModel.state.value.arrows.size > 1) viewModel.removeOne()

        viewModel.requestHint()
        assertEquals(viewModel.state.value.arrows.single().id, viewModel.hint.value?.tileId)
    }

    @Test
    fun `hints are available throughout a level and then are not`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        var guard = 0
        while (viewModel.state.value.status == GameStatus.PLAYING) {
            assertTrue(
                "no hint available with ${viewModel.state.value.arrows.size} arrows left",
                viewModel.canHint()
            )
            viewModel.removeOne()
            check(guard++ < 500) { "the level did not terminate" }
        }
        assertFalse(viewModel.canHint())
    }

    // ---- every mode ----------------------------------------------------------

    @Test
    fun `hints work in endless mode`() {
        val viewModel = viewModel()
        viewModel.startEndless()
        viewModel.requestHint()

        val hinted = requireNotNull(viewModel.hintedArrow())
        assertTrue(MoveValidator.canEscape(hinted, viewModel.state.value.arrows))
    }

    @Test
    fun `hints work in the daily challenge`() {
        val viewModel = viewModel()
        viewModel.startDaily()
        viewModel.requestHint()

        val hinted = requireNotNull(viewModel.hintedArrow())
        assertTrue(MoveValidator.canEscape(hinted, viewModel.state.value.arrows))
    }

    @Test
    fun `hints work in the tutorial`() {
        val viewModel = viewModel()
        viewModel.startTutorial()
        viewModel.requestHint()

        val hinted = requireNotNull(viewModel.hintedArrow())
        assertTrue(MoveValidator.canEscape(hinted, viewModel.state.value.arrows))
    }

    @Test
    fun `a hint never nominates a blocked arrow across an endless board and a daily board`() {
        for (mode in listOf<(GameViewModel) -> Unit>(
            { it.startEndless() },
            { it.startDaily() }
        )) {
            val viewModel = viewModel()
            mode(viewModel)
            var guard = 0
            while (viewModel.state.value.status == GameStatus.PLAYING) {
                viewModel.requestHint()
                val hinted = requireNotNull(viewModel.hintedArrow())
                assertTrue(
                    "hinted a blocked arrow in ${viewModel.mode.value}",
                    MoveValidator.canEscape(hinted, viewModel.state.value.arrows)
                )
                viewModel.removeOne()
                check(guard++ < 500) { "the board did not terminate" }
            }
        }
    }

    /**
     * A hint still removes nothing and costs no lives. What it does cost, since
     * Phase 6E, is a star — so it writes exactly one thing to the campaign save:
     * the fact that it was asked for. That has to be on disk immediately,
     * because otherwise being killed after a hint would launder the run into a
     * Perfect Escape.
     */
    @Test
    fun `asking for a hint does not change the board or the lives`() {
        val viewModel = viewModel()
        viewModel.continueGame()
        val boardBefore = viewModel.state.value

        repeat(10) { viewModel.requestHint() }

        assertEquals("a hint changed the board", boardBefore, viewModel.state.value)

        val saved = viewModel.savedGame.value
        assertNotNull("a hint did not record itself", saved)
        assertEquals(boardBefore.level.id, saved!!.levelId)
        assertEquals(boardBefore.lives, saved.lives)
        assertEquals(
            "a hint moved an arrow",
            boardBefore.arrows.mapTo(HashSet()) { it.id },
            saved.remainingArrowIds
        )
        assertEquals("a hint cost a life", 0, saved.blockedTaps)
        assertTrue("the hint was not recorded", saved.hintUsed)
    }

    @Test
    fun `a hint reveals one arrow and not the order`() {
        // The guard against a hint quietly becoming a solution: whatever is
        // nominated, it is exactly one arrow.
        val viewModel = viewModel()
        viewModel.startEndless()
        viewModel.requestHint()
        assertNotNull(viewModel.hint.value)
        assertEquals(
            "a hint should nominate exactly one arrow",
            1,
            viewModel.state.value.arrows.count { it.id == viewModel.hint.value?.tileId }
        )
    }
}
