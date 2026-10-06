package com.sabalapps.arrowescape.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arrow assembly's timing: everything arrives, in time, in order. */
class SplashChoreographyTest {

    private val times = (0L..2_000L step 5)

    private fun allProgresses(t: Long): List<Float> = buildList {
        for (i in 0 until SplashChoreography.PIECES) {
            add(SplashChoreography.pieceProgress(i, t))
            add(SplashChoreography.sparkleProgress(i, t))
        }
        add(SplashChoreography.centreProgress(t))
        add(SplashChoreography.titleProgress(t))
        add(SplashChoreography.backgroundProgress(t))
        add(SplashChoreography.reducedProgress(t))
    }

    @Test
    fun `nothing has started at time zero except the glow`() {
        for (i in 0 until SplashChoreography.PIECES) {
            assertEquals(0f, SplashChoreography.pieceProgress(i, 0), 0f)
            assertEquals(0f, SplashChoreography.sparkleProgress(i, 0), 0f)
        }
        assertEquals(0f, SplashChoreography.titleProgress(0), 0f)
        assertEquals(0f, SplashChoreography.centreProgress(0), 0f)
    }

    @Test
    fun `every progress stays within zero and one, and never goes backwards`() {
        var previous = allProgresses(0)
        for (t in times) {
            val now = allProgresses(t)
            for (k in now.indices) {
                assertTrue("index $k at $t: ${now[k]}", now[k] in 0f..1f)
                assertTrue("index $k went backwards at $t", now[k] >= previous[k])
            }
            previous = now
        }
    }

    @Test
    fun `the whole emblem is assembled before the bar can finish`() {
        assertTrue(
            "assembled at ${SplashChoreography.assembledAtMs}ms",
            SplashChoreography.assembledAtMs <= SplashProgress.ASSEMBLY_MS
        )
        val t = SplashChoreography.assembledAtMs
        assertTrue(allProgresses(t).dropLast(1).all { it == 1f })
    }

    @Test
    fun `the discs arrive one after another, not all at once`() {
        val at = 300L
        val progress = (0 until SplashChoreography.PIECES).map { SplashChoreography.pieceProgress(it, at) }
        assertEquals(progress.sortedDescending(), progress)
        assertTrue(progress.first() > progress.last())
    }

    @Test
    fun `the title comes up after the first arrows have started`() {
        assertTrue(SplashChoreography.TITLE_START_MS > 100)
        assertTrue(SplashChoreography.titleProgress(SplashChoreography.TITLE_START_MS + SplashChoreography.TITLE_MS) == 1f)
    }

    @Test
    fun `under reduced motion the finished emblem simply fades in`() {
        assertEquals(0f, SplashChoreography.reducedProgress(0), 0f)
        assertEquals(1f, SplashChoreography.reducedProgress(SplashChoreography.REDUCED_FADE_MS), 0f)
        assertTrue(SplashChoreography.REDUCED_FADE_MS < SplashProgress.ASSEMBLY_MS)
    }

    @Test
    fun `the closing glow pulse rises and falls, starting and ending at nothing`() {
        assertEquals(0f, SplashChoreography.pulse(0), 1e-6f)
        assertEquals(1f, SplashChoreography.pulse(SplashProgress.PULSE_MS / 2), 1e-3f)
        assertEquals(0f, SplashChoreography.pulse(SplashProgress.PULSE_MS), 1e-3f)
        assertEquals(0f, SplashChoreography.pulse(10_000), 1e-3f)
    }
}
