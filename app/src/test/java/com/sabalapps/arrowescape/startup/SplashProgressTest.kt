package com.sabalapps.arrowescape.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The number on the loading screen: smooth, honest, bounded. */
class SplashProgressTest {

    private val frame = 16L

    /** Runs the model for [ms] at 60fps against a target that may change with the clock. */
    private fun run(ms: Long, model: SplashProgress = SplashProgress(), target: (Long) -> Float): List<Float> {
        val shown = ArrayList<Float>()
        var t = 0L
        while (t < ms) {
            shown += model.advance(frame, target(model.elapsedMs))
            t += frame
        }
        return shown
    }

    @Test
    fun `it starts at zero percent`() {
        val model = SplashProgress()
        assertEquals(0, model.percent)
        assertEquals(0f, model.shown, 0f)
    }

    @Test
    fun `it never goes backwards, and never past one, whatever the target does`() {
        val shown = run(3_000) { t -> if ((t / 400) % 2 == 0L) 1f else 0.2f }
        assertEquals(shown.sorted(), shown)
        assertTrue(shown.all { it in 0f..1f })
    }

    @Test
    fun `it never runs ahead of the real target`() {
        for (target in listOf(0f, 0.3f, 0.5f, 0.97f)) {
            val shown = run(5_000) { target }
            assertTrue("target $target, reached ${shown.max()}", shown.all { it <= target + 1e-6f })
        }
    }

    @Test
    fun `it cannot read 100 percent before the real work is done`() {
        val model = SplashProgress()
        run(10_000, model) { StartupProgress.HOLD_BELOW }
        assertTrue(model.percent < 100)
        assertTrue(model.shown < 1f)
    }

    @Test
    fun `when everything is instant it still takes about a second to fill, so the assembly can be seen`() {
        val model = SplashProgress()
        val shown = run(5_000, model) { 1f }
        val firstFull = shown.indexOfFirst { it >= 1f }
        assertTrue("never filled", firstFull >= 0)
        val fullAtMs = (firstFull + 1) * frame
        assertTrue("filled in ${fullAtMs}ms: too abrupt to read", fullAtMs >= 800)
        assertTrue("filled in ${fullAtMs}ms: a forced wait", fullAtMs <= 1_400)
        assertEquals(100, model.percent)
    }

    @Test
    fun `an instant start is not a jump from zero to done`() {
        val shown = run(400) { 1f }
        // Smooth: no frame moves the bar by more than a few percent.
        shown.zipWithNext().forEach { (a, b) -> assertTrue("jump ${b - a}", b - a < 0.08f) }
    }

    @Test
    fun `real work that is slow holds the bar, then it finishes when the work does`() {
        val model = SplashProgress()
        // 3 seconds with the real work at 60%...
        run(3_000, model) { 0.6f }
        assertTrue(model.shown <= 0.6f)
        assertTrue("it should have caught up with the target", model.shown > 0.55f)
        // ...then the work completes.
        run(1_500, model) { 1f }
        assertEquals(1f, model.shown, 0f)
        assertEquals(100, model.percent)
    }

    @Test
    fun `the percentage is a whole number from 0 to 100 and only 100 when full`() {
        val model = SplashProgress()
        var sawNinetyNine = false
        repeat(400) {
            model.advance(frame, 1f)
            assertTrue(model.percent in 0..100)
            if (model.shown < 1f) assertTrue(model.percent <= 99)
            if (model.percent == 99) sawNinetyNine = true
        }
        assertEquals(100, model.percent)
        assertTrue("it eased into the end rather than stepping onto it", sawNinetyNine)
    }

    @Test
    fun `a huge frame gap does not overshoot or jump past the target`() {
        val model = SplashProgress()
        model.advance(10_000, 0.4f)
        assertTrue(model.shown <= 0.4f)
        model.advance(10_000, 1f)
        assertEquals(1f, model.shown, 0f)
    }

    @Test
    fun `a negative time step is ignored`() {
        val model = SplashProgress()
        model.advance(100, 1f)
        val before = model.shown
        model.advance(-500, 1f)
        assertTrue(model.shown >= before)
    }

    // ---- when the screen may leave -------------------------------------------

    @Test
    fun `it may leave once the bar is full and the arrows have assembled`() {
        assertTrue(SplashProgress.canFinish(shown = 1f, elapsedMs = SplashProgress.ASSEMBLY_MS))
    }

    @Test
    fun `it may not leave with a bar that is not full`() {
        assertFalse(SplashProgress.canFinish(shown = 0.99f, elapsedMs = 3_000))
    }

    @Test
    fun `it may not leave before the arrows have assembled`() {
        assertFalse(SplashProgress.canFinish(shown = 1f, elapsedMs = SplashProgress.ASSEMBLY_MS - 1))
    }

    @Test
    fun `nothing can keep it up for ever`() {
        assertTrue(SplashProgress.canFinish(shown = 0.2f, elapsedMs = SplashProgress.HARD_CAP_MS))
    }

    @Test
    fun `the normal path is between a second and two seconds`() {
        // Instant startup: the fill floor, then the pulse. The whole thing, until the hand-off.
        val total = SplashProgress.FILL_MS.coerceAtLeast(SplashProgress.ASSEMBLY_MS) + SplashProgress.PULSE_MS
        assertTrue("normal startup is ${total}ms", total in 800..1_800)
    }
}
