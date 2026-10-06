package com.sabalapps.arrowescape.tutorial

import com.sabalapps.arrowescape.game.EscapeAnalysis
import com.sabalapps.arrowescape.game.HintEngine
import com.sabalapps.arrowescape.game.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Level 2's lesson, as a state machine, over Level 2's real board. Nothing here re-derives
 * which arrows are free: what the first removal opens is asked of `EscapeAnalysis`, the same
 * thing the game asks.
 */
class DependencyLessonTest {

    private val board = Levels.byId(2)!!.arrows

    /** What taking [id] off [boards] opens, by the game's own analysis. */
    private fun freedBy(id: Int, boards: List<com.sabalapps.arrowescape.game.ArrowTile>): Set<Int> =
        EscapeAnalysis.newlyFreed(boards, boards.filterNot { it.id == id })

    @Test
    fun `the lesson opens asking to clear one path`() {
        val lesson = DependencyLesson.Starting
        assertEquals(DependencyStep.CLEAR_ONE, lesson.step)
        assertEquals("Clear one path to free another.", lesson.step.message)
        assertTrue(lesson.isActive)
        assertTrue(lesson.step.showsHand)
    }

    @Test
    fun `an inactive lesson shows nothing`() {
        val lesson = DependencyLesson.Inactive
        assertFalse(lesson.isActive)
        assertEquals("", lesson.step.message)
        assertFalse(lesson.step.showsHand)
        assertNull(lesson.targetId(board))
    }

    @Test
    fun `the opening hand is on the arrow the hint engine would pick, and that arrow frees something`() {
        val target = DependencyLesson.Starting.targetId(board)
        assertEquals(HintEngine.hint(board)?.id, target)
        // The point of Level 2's lesson: this first tap must open a path.
        assertTrue("the first guided move freed nothing", freedBy(target!!, board).isNotEmpty())
    }

    @Test
    fun `a removal that frees something moves the lesson on and remembers what it freed`() {
        val first = DependencyLesson.Starting.targetId(board)!!
        val freed = freedBy(first, board)
        val after = DependencyLesson.Starting.onArrowEscaped(first, freed)

        assertEquals(DependencyStep.TAP_FREED, after.step)
        assertEquals(freed, after.freed)
        assertEquals("That freed another arrow. Tap it!", after.step.message)
        assertTrue(after.step.showsHand)
    }

    @Test
    fun `a removal that frees nothing teaches nothing, so the lesson waits`() {
        val after = DependencyLesson.Starting.onArrowEscaped(escapedId = 0, freedIds = emptySet())
        assertEquals(DependencyLesson.Starting, after)
    }

    @Test
    fun `the hand follows the freed arrow, and only arrows that are still on the board`() {
        val first = DependencyLesson.Starting.targetId(board)!!
        val freed = freedBy(first, board)
        val afterFirst = board.filterNot { it.id == first }
        val lesson = DependencyLesson.Starting.onArrowEscaped(first, freed)

        assertEquals(freed.min(), lesson.targetId(afterFirst))

        // Take one of the freed arrows off and the hand moves to another, never to one that left.
        if (freed.size > 1) {
            val next = freed.min()
            val boardAfterNext = afterFirst.filterNot { it.id == next }
            assertEquals(freed.filter { it != next }.min(), lesson.targetId(boardAfterNext))
        }
        // With none of them left there is nothing to point at.
        assertNull(lesson.targetId(afterFirst.filterNot { it.id in freed }))
    }

    @Test
    fun `tapping a freed arrow lands the idea`() {
        val first = DependencyLesson.Starting.targetId(board)!!
        val freed = freedBy(first, board)
        val lesson = DependencyLesson.Starting.onArrowEscaped(first, freed)

        val after = lesson.onArrowEscaped(freed.min(), emptySet())
        assertEquals(DependencyStep.UNDERSTOOD, after.step)
        assertEquals("You've got it!", after.step.message)
        assertTrue(after.step.isTaught)
        assertFalse("the hand has done its job", after.step.showsHand)
    }

    @Test
    fun `taking some other arrow first leaves the lesson where it was`() {
        val first = DependencyLesson.Starting.targetId(board)!!
        val freed = freedBy(first, board)
        val lesson = DependencyLesson.Starting.onArrowEscaped(first, freed)

        val unrelated = board.map { it.id }.first { it != first && it !in freed }
        assertEquals(lesson, lesson.onArrowEscaped(unrelated, emptySet()))
    }

    @Test
    fun `the next move after it landed stops the lesson`() {
        val understood = DependencyLesson(DependencyStep.UNDERSTOOD)
        assertEquals(DependencyStep.FINISHED, understood.onArrowEscaped(0, emptySet()).step)
        assertEquals(DependencyStep.FINISHED, understood.onMoveBlocked().step)
    }

    @Test
    fun `a blocked tap mid-lesson changes nothing`() {
        val first = DependencyLesson.Starting.targetId(board)!!
        val tapFreed = DependencyLesson.Starting.onArrowEscaped(first, freedBy(first, board))
        assertEquals(DependencyLesson.Starting, DependencyLesson.Starting.onMoveBlocked())
        assertEquals(tapFreed, tapFreed.onMoveBlocked())
    }

    @Test
    fun `finishing the board ends the lesson from any step`() {
        for (step in DependencyStep.entries) {
            assertEquals("from $step", DependencyStep.FINISHED, DependencyLesson(step).onBoardFinished().step)
        }
    }

    @Test
    fun `a finished lesson never comes back on its own`() {
        val done = DependencyLesson.Inactive
        assertEquals(done, done.onArrowEscaped(1, setOf(2)))
        assertEquals(done, done.onMoveBlocked())
        assertEquals(done, done.onBoardFinished())
    }

    @Test
    fun `every step that speaks is short`() {
        for (step in DependencyStep.entries) {
            if (step == DependencyStep.FINISHED) {
                assertEquals("", step.message)
            } else {
                assertTrue("$step", step.message.isNotBlank())
                assertTrue("$step is ${step.message.length} characters", step.message.length <= 44)
            }
        }
    }

    @Test
    fun `the lesson is taught from the moment it lands`() {
        assertFalse(DependencyStep.CLEAR_ONE.isTaught)
        assertFalse(DependencyStep.TAP_FREED.isTaught)
        assertTrue(DependencyStep.UNDERSTOOD.isTaught)
        assertTrue(DependencyStep.FINISHED.isTaught)
        assertNotNull(DependencyStep.entries.firstOrNull { it.isTaught })
    }

    @Test
    fun `no sequence of taps can leave the lesson running after the board is cleared`() {
        // Play Level 2 out through the hint engine and the real analysis, feeding the lesson
        // exactly what the ViewModel would. However it goes, it ends.
        var boards = board
        var lesson = DependencyLesson.Starting
        var guard = 0
        while (boards.isNotEmpty()) {
            val tap = HintEngine.hint(boards)!!.id
            val freed = freedBy(tap, boards)
            boards = boards.filterNot { it.id == tap }
            lesson = lesson.onArrowEscaped(tap, freed)
            check(guard++ < 100)
        }
        assertTrue(lesson.onBoardFinished().step == DependencyStep.FINISHED)
    }
}
