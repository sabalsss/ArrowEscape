package com.sabalapps.arrowescape.tutorial

import com.sabalapps.arrowescape.game.HintEngine
import com.sabalapps.arrowescape.game.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the board is told to show: the caption, the hand and the glow, for each lesson step. */
class OnboardingGuideTest {

    private val level1 = Levels.byId(1)!!.arrows
    private val level2 = Levels.byId(2)!!.arrows

    @Test
    fun `nothing is taught when neither lesson is running`() {
        val guide = OnboardingGuide.resolve(TutorialState.Inactive, DependencyLesson.Inactive, level1)
        assertEquals(OnboardingGuide.None, guide)
        assertFalse(guide.isActive)
        assertNull(guide.caption)
    }

    @Test
    fun `level one opens with a caption, a hand and a glow on the same free arrow`() {
        val guide = OnboardingGuide.resolve(TutorialState.Starting, DependencyLesson.Inactive, level1)
        assertEquals("Tap an arrow with a clear path", guide.caption)
        assertEquals(HintEngine.hint(level1)?.id, guide.handTargetId)
        assertEquals(guide.handTargetId, guide.spotlightId)
        assertTrue(guide.isActive)
    }

    @Test
    fun `after a blocked tap the hand stays but the glow does not`() {
        val guide = OnboardingGuide.resolve(TutorialState(TutorialStep.BLOCKED), DependencyLesson.Inactive, level1)
        assertEquals("Another arrow is blocking its path", guide.caption)
        assertEquals(HintEngine.hint(level1)?.id, guide.handTargetId)
        assertNull("the board is already pulsing the blocker", guide.spotlightId)
    }

    @Test
    fun `once an arrow has escaped there is a word and no hand`() {
        val guide = OnboardingGuide.resolve(TutorialState(TutorialStep.CHAIN), DependencyLesson.Inactive, level1)
        assertEquals("Nice! It escaped ✨", guide.caption)
        assertNull(guide.handTargetId)
        assertNull(guide.spotlightId)
    }

    @Test
    fun `level two opens with the hand on the arrow worth taking`() {
        val guide = OnboardingGuide.resolve(TutorialState.Inactive, DependencyLesson.Starting, level2)
        assertEquals("Clear one path to free another.", guide.caption)
        assertEquals(HintEngine.hint(level2)?.id, guide.handTargetId)
        assertEquals(guide.handTargetId, guide.spotlightId)
    }

    @Test
    fun `level two's hand moves to the freed arrow`() {
        val lesson = DependencyLesson(DependencyStep.TAP_FREED, freed = setOf(9, 4))
        val guide = OnboardingGuide.resolve(TutorialState.Inactive, lesson, level2)
        assertEquals(4, guide.handTargetId)
        assertEquals(4, guide.spotlightId)
    }

    @Test
    fun `once the idea has landed there is a word and no hand`() {
        val guide = OnboardingGuide.resolve(TutorialState.Inactive, DependencyLesson(DependencyStep.UNDERSTOOD), level2)
        assertEquals("You've got it!", guide.caption)
        assertNull(guide.handTargetId)
    }

    @Test
    fun `a running tutorial speaks before a running lesson, and they are never both shown`() {
        val guide = OnboardingGuide.resolve(TutorialState.Starting, DependencyLesson.Starting, level1)
        assertEquals(TutorialStep.TAP_FREE.message, guide.caption)
    }

    @Test
    fun `an empty board has nothing to point at`() {
        val guide = OnboardingGuide.resolve(TutorialState.Starting, DependencyLesson.Inactive, emptyList())
        assertNull(guide.handTargetId)
        assertNull(guide.spotlightId)
    }

    @Test
    fun `the encouragement is for a first clear of level one or two, and nothing else`() {
        assertEquals("Great start!", OnboardingCoach.lineForWin(1, isFirstClear = true))
        assertEquals("You're getting the hang of it!", OnboardingCoach.lineForWin(2, isFirstClear = true))
        assertNull(OnboardingCoach.lineForWin(1, isFirstClear = false))
        assertNull(OnboardingCoach.lineForWin(2, isFirstClear = false))
        for (level in 3..30) assertNull("level $level", OnboardingCoach.lineForWin(level, isFirstClear = true))
    }

    @Test
    fun `the encouragement is calm, not a fanfare`() {
        for (line in listOf(OnboardingCoach.lineForWin(1, true)!!, OnboardingCoach.lineForWin(2, true)!!)) {
            assertTrue(line.length <= 32)
            assertTrue("no shouting: $line", line != line.uppercase())
            assertFalse(line.contains("!!"))
        }
    }
}
