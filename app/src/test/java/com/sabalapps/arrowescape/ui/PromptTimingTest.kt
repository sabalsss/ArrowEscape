package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.ui.discovery.RevealSchedule
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prompt waits for the reward: it can only start once the discovery reveal has fully settled. */
class PromptTimingTest {

    @Test
    fun `a prompt waits until the whole reveal has arrived and its buttons work`() {
        for (reduced in listOf(false, true)) {
            val reveal = RevealSchedule.forMotion(reduced)
            val delay = PromptTiming.settleDelayMs(reduced)
            assertTrue("reduced=$reduced: after the buttons are pressable", delay > reveal.interactiveMs)
            assertTrue("reduced=$reduced: after the last piece has faded in", delay > reveal.actionsMs + reveal.fadeMs)
            assertTrue("reduced=$reduced: after the peak and its burst", delay > reveal.peakMs + reveal.burstMs)
            assertTrue("reduced=$reduced: after the stars", delay > reveal.starsMs + reveal.fadeMs)
        }
    }

    @Test
    fun `there is a beat to look at what was found before anything else speaks`() {
        for (reduced in listOf(false, true)) {
            val reveal = RevealSchedule.forMotion(reduced)
            assertTrue(PromptTiming.settleDelayMs(reduced) - reveal.totalMs >= PromptTiming.GRACE_MS)
        }
    }

    @Test
    fun `it is not so long that the player has already left`() {
        assertTrue(PromptTiming.settleDelayMs(reducedMotion = false) < 4_000)
    }
}
