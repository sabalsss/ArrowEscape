package com.sabalapps.arrowescape.ui.discovery

import com.sabalapps.arrowescape.ui.ShapeConfirmSchedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reveal's timeline is tuning, not law, so what is pinned here is its *shape*:
 * the order things arrive in, where the peak lands, and that the whole sequence
 * stays inside about two seconds. A retune that keeps those true is fine.
 */
class RevealScheduleTest {

    private val standard = RevealSchedule.Standard
    private val reduced = RevealSchedule.Reduced

    @Test
    fun `the art begins to emerge only after the solved shape has been confirmed`() {
        // The last arrow's flight is 70ms of wind-up and 300ms of travel, and the outline then
        // traces and locks; the layer arrives on the confirmation's hand-off and not before.
        assertTrue(standard.leadInMs >= 370)
        assertEquals(ShapeConfirmSchedule.Discovery.handoffMs.toLong(), standard.leadInMs)
        assertTrue(standard.leadInMs >= ShapeConfirmSchedule.Discovery.lockMs)
        assertTrue(standard.overallMs(standard.artStartMs) in 760..900)
    }

    @Test
    fun `the art has landed by the peak, and the peak is the emotional beat`() {
        assertTrue(standard.artStartMs + standard.artMs <= standard.peakMs)
        assertTrue(standard.overallMs(standard.peakMs) in 1100..1300)
    }

    @Test
    fun `the glow strengthens around the peak`() {
        assertTrue(standard.overallMs(standard.glowStartMs) in 800..1100)
        assertTrue(standard.overallMs(standard.glowStartMs + standard.glowMs) in 1100..1500)
    }

    @Test
    fun `the sparkle burst is long enough to read and short enough to be gone by the buttons`() {
        assertTrue(standard.burstMs in 400..900)
        // It launches on the peak, so it has thinned out soon after the buttons can be pressed.
        assertTrue(standard.peakMs + standard.burstMs <= standard.interactiveMs + 400)
    }

    @Test
    fun `the words arrive in the order discovery, name, collection, stars, then buttons`() {
        val order = listOf(
            standard.artStartMs, standard.headlineMs, standard.nameMs,
            standard.collectionMs, standard.starsMs, standard.actionsMs
        )
        assertEquals(order.sorted(), order)
        assertTrue("strictly after the art starts", standard.headlineMs > standard.artStartMs)
    }

    @Test
    fun `stars never arrive before the discovery is named`() {
        assertTrue(standard.starsMs > standard.nameMs)
        assertTrue(standard.starsMs > standard.collectionMs)
    }

    @Test
    fun `a button cannot be pressed before it is visible`() {
        assertTrue(standard.interactiveMs > standard.actionsMs)
        assertTrue(reduced.interactiveMs > reduced.actionsMs)
    }

    @Test
    fun `the result is available about two seconds after launch, confirmation included`() {
        assertTrue(standard.availableOverallMs in 1700..2100)
        assertTrue(standard.leadInMs + standard.totalMs <= 2100)
    }

    @Test
    fun `reduced motion skips the burst and gets to the result faster`() {
        assertEquals(0, reduced.burstMs)
        assertTrue(reduced.availableOverallMs < standard.availableOverallMs)
        assertTrue(reduced.leadInMs + reduced.totalMs < 800)
    }

    @Test
    fun `reduced motion still has a peak for the sound and the haptic`() {
        assertTrue(reduced.peakMs >= 0)
        assertTrue(reduced.peakMs <= reduced.interactiveMs)
    }

    @Test
    fun `the motion setting picks the schedule`() {
        assertEquals(reduced, RevealSchedule.forMotion(true))
        assertEquals(standard, RevealSchedule.forMotion(false))
    }

    @Test
    fun `the art follows the outline's hand-off closely, so the two read as one transition`() {
        val gap = standard.overallMs(standard.artStartMs) - ShapeConfirmSchedule.Discovery.handoffMs
        assertTrue("gap was ${gap}ms", gap in 0..150)
    }

    @Test
    fun `a completion beat starts after the stars begin and never holds the buttons`() {
        assertTrue(standard.completionMs >= standard.starsMs)
        // Six pieces: the beat can outlast the ordinary sequence, but the clock covers it
        // and the buttons are not waiting on it.
        val end = standard.completionEndMs(6)
        assertEquals(end, standard.totalMsFor(6))
        assertTrue(standard.interactiveMs < end)
        assertTrue("whole reveal ran ${standard.leadInMs + end}ms", standard.leadInMs + end <= 2400)
    }

    @Test
    fun `a reveal with no completion runs exactly as long as it always did`() {
        assertEquals(standard.totalMs, standard.totalMsFor(0))
        assertEquals(reduced.totalMs, reduced.totalMsFor(0))
    }

    @Test
    fun `reduced motion brings the completion in at once and without a burst`() {
        assertEquals(0, reduced.completionStaggerMs)
        assertEquals(0, reduced.completionBurstMs)
        assertTrue(reduced.leadInMs + reduced.totalMsFor(6) < 800)
    }
}
