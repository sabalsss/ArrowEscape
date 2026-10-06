package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.ui.discovery.RevealSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the screen shows of itself while a result is up: the gameplay controls leave,
 * the world stays. Pure policy over the same inputs `GameScreen` reads.
 */
class ResultPresentationTest {

    @Test
    fun `a loss shows its result at once, a win only after the celebration`() {
        assertTrue(isResultShowing(GameStatus.LOST, celebrationFinished = false))
        assertTrue(isResultShowing(GameStatus.LOST, celebrationFinished = true))
        assertFalse(isResultShowing(GameStatus.WON, celebrationFinished = false))
        assertTrue(isResultShowing(GameStatus.WON, celebrationFinished = true))
    }

    @Test
    fun `nothing is shown while the board is still being played`() {
        assertFalse(isResultShowing(GameStatus.PLAYING, celebrationFinished = false))
        assertFalse(isResultShowing(GameStatus.PLAYING, celebrationFinished = true))
    }

    @Test
    fun `gameplay chrome is suppressed exactly while a result is showing`() {
        // Campaign discovery, Daily completion and Game Over are all "a result is
        // showing", so one rule covers every one of them.
        for (status in GameStatus.entries) {
            for (finished in listOf(false, true)) {
                val showing = isResultShowing(status, finished)
                assertEquals(
                    "$status, celebrationFinished=$finished",
                    !showing,
                    gameplayChromeVisible(showing)
                )
            }
        }
    }

    @Test
    fun `the board keeps its controls while it celebrates and brings them back after a replay`() {
        // The celebration plays over the board, so the chrome is still there for it...
        assertTrue(gameplayChromeVisible(isResultShowing(GameStatus.WON, celebrationFinished = false)))
        // ...goes with the result...
        assertFalse(gameplayChromeVisible(isResultShowing(GameStatus.WON, celebrationFinished = true)))
        assertFalse(gameplayChromeVisible(isResultShowing(GameStatus.LOST, celebrationFinished = false)))
        // ...and is back the moment a new board is playing.
        assertTrue(gameplayChromeVisible(isResultShowing(GameStatus.PLAYING, celebrationFinished = true)))
    }

    @Test
    fun `the chrome leaves over the span the scrim arrives in`() {
        assertEquals(RevealSchedule.Standard.scrimMs, resultEnterMs(CompletionKind.Discovery, reducedMotion = false))
        assertEquals(RevealSchedule.Reduced.scrimMs, resultEnterMs(CompletionKind.Discovery, reducedMotion = true))
        assertEquals(300, resultEnterMs(CompletionKind.Plain, reducedMotion = false))
        assertEquals(120, resultEnterMs(CompletionKind.Plain, reducedMotion = true))
    }
}
