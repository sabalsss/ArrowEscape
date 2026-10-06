package com.sabalapps.arrowescape.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lesson's state machine. Small enough to enumerate exhaustively, which is
 * what is done below: every step crossed with every thing that can happen to it.
 */
class TutorialStateTest {

    @Test
    fun `a tutorial starts by asking for a clear path`() {
        assertEquals(TutorialStep.TAP_FREE, TutorialState.Starting.step)
        assertTrue(TutorialState.Starting.isActive)
        assertTrue(TutorialState.Starting.step.highlightsFreeArrow)
    }

    @Test
    fun `an inactive tutorial shows nothing`() {
        assertEquals(TutorialStep.FINISHED, TutorialState.Inactive.step)
        assertFalse(TutorialState.Inactive.isActive)
        assertFalse(TutorialState.Inactive.step.highlightsFreeArrow)
        assertEquals("", TutorialState.Inactive.step.message)
    }

    @Test
    fun `the default state is a tutorial about to start`() {
        assertEquals(TutorialState.Starting, TutorialState())
    }

    // ---- the happy path ------------------------------------------------------

    @Test
    fun `the first clean tap earns a short word of encouragement`() {
        val after = TutorialState.Starting.onArrowEscaped()
        assertEquals(TutorialStep.CHAIN, after.step)
        assertEquals("Nice! It escaped \u2728", after.step.message)
        assertTrue("the lesson is not over until the sentence has been read", after.isActive)
    }

    @Test
    fun `the hand points at the free arrow until one has escaped`() {
        assertTrue(TutorialStep.TAP_FREE.showsHand)
        // A blocked tap has not taught anything yet, so the way out is still being shown.
        assertTrue(TutorialStep.BLOCKED.showsHand)
        assertFalse(TutorialStep.CHAIN.showsHand)
        assertFalse(TutorialStep.FINISHED.showsHand)
    }

    @Test
    fun `the lesson counts as taught from the first escape on`() {
        assertFalse(TutorialStep.TAP_FREE.isTaught)
        assertFalse(TutorialStep.BLOCKED.isTaught)
        assertTrue(TutorialStep.CHAIN.isTaught)
        assertTrue(TutorialStep.FINISHED.isTaught)
    }

    @Test
    fun `the second clean tap ends the lesson`() {
        val after = TutorialState.Starting.onArrowEscaped().onArrowEscaped()
        assertEquals(TutorialStep.FINISHED, after.step)
        assertFalse(after.isActive)
    }

    // ---- the blocked branch --------------------------------------------------

    @Test
    fun `a blocked tap explains what blocking is`() {
        val after = TutorialState.Starting.onMoveBlocked()
        assertEquals(TutorialStep.BLOCKED, after.step)
        assertEquals("Another arrow is blocking its path", after.step.message)
        assertTrue(after.isActive)
    }

    @Test
    fun `repeated blocked taps keep the explanation up rather than advancing`() {
        var state = TutorialState.Starting
        repeat(5) { state = state.onMoveBlocked() }
        assertEquals(TutorialStep.BLOCKED, state.step)
    }

    @Test
    fun `getting it right after a blocked tap still earns the chain explanation`() {
        val after = TutorialState.Starting.onMoveBlocked().onArrowEscaped()
        assertEquals(TutorialStep.CHAIN, after.step)
    }

    @Test
    fun `a blocked tap after the chain explanation ends the lesson`() {
        // Past the explanation the board's own shake says it better than a
        // caption would, so the tutorial stops talking.
        val after = TutorialState.Starting.onArrowEscaped().onMoveBlocked()
        assertEquals(TutorialStep.FINISHED, after.step)
    }

    @Test
    fun `the emphasis is only ever on the opening step`() {
        // BLOCKED deliberately does not emphasise: the board is already pulsing
        // the arrow that did the blocking.
        assertTrue(TutorialStep.TAP_FREE.highlightsFreeArrow)
        assertFalse(TutorialStep.BLOCKED.highlightsFreeArrow)
        assertFalse(TutorialStep.CHAIN.highlightsFreeArrow)
        assertFalse(TutorialStep.FINISHED.highlightsFreeArrow)
    }

    // ---- the end -------------------------------------------------------------

    @Test
    fun `finishing the board ends the lesson from any step`() {
        for (step in TutorialStep.entries) {
            val after = TutorialState(step).onBoardFinished()
            assertEquals("from $step", TutorialStep.FINISHED, after.step)
        }
    }

    @Test
    fun `a finished tutorial never comes back on its own`() {
        val finished = TutorialState.Inactive
        assertEquals(finished, finished.onArrowEscaped())
        assertEquals(finished, finished.onMoveBlocked())
        assertEquals(finished, finished.onBoardFinished())
    }

    @Test
    fun `every step has something to say except the last`() {
        for (step in TutorialStep.entries) {
            if (step == TutorialStep.FINISHED) {
                assertEquals("", step.message)
            } else {
                assertTrue("$step had no message", step.message.isNotBlank())
                // Short: this is a line under a board, not a paragraph.
                assertTrue(
                    "$step's message is ${step.message.length} characters",
                    step.message.length <= 44
                )
            }
        }
    }

    @Test
    fun `two clean taps always end the lesson, however many blocked ones are mixed in`() {
        // A run of blocked taps deliberately does not advance anything — the
        // explanation stays up until the player gets one right — so what is
        // bounded is the number of *successful* taps, not the number of taps.
        // Exhaustive over every interleaving up to six taps long.
        fun walk(state: TutorialState, escapes: Int, taps: Int) {
            if (!state.isActive) {
                assertTrue("the lesson ran past two clean taps", escapes <= 2)
                return
            }
            assertTrue(
                "the lesson was still running after $escapes clean taps",
                escapes < 2
            )
            if (taps >= 6) return
            walk(state.onArrowEscaped(), escapes + 1, taps + 1)
            walk(state.onMoveBlocked(), escapes, taps + 1)
        }
        walk(TutorialState.Starting, escapes = 0, taps = 0)
    }

    @Test
    fun `a player who only ever taps blocked arrows is still taught by the board ending`() {
        // The one way out of the blocked loop that does not need a clean tap:
        // running out of lives, which finishes the board.
        var state = TutorialState.Starting
        repeat(3) { state = state.onMoveBlocked() }
        assertTrue(state.isActive)
        assertFalse(state.onBoardFinished().isActive)
    }

    // ---- the persisted bit ---------------------------------------------------

    @Test
    fun `the flag store starts false and remembers being set`() {
        val store = InMemoryTutorialFlagStore()
        assertFalse("a fresh install has not seen the tutorial", store.isTutorialCompleted())
        store.setTutorialCompleted(true)
        assertTrue(store.isTutorialCompleted())
        store.setTutorialCompleted(false)
        assertFalse(store.isTutorialCompleted())
    }

    @Test
    fun `the two lessons are remembered separately`() {
        val store = InMemoryTutorialFlagStore()
        store.setTutorialCompleted(true)
        assertFalse("Level 1 being taught must not mark Level 2", store.isDependencyLessonCompleted())
        store.setDependencyLessonCompleted(true)
        store.setTutorialCompleted(false)
        assertTrue("and the other way round", store.isDependencyLessonCompleted())
    }
}
