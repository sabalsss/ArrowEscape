package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.ui.discovery.RevealSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The completion state machine and its timelines. What is pinned is the *shape* of the sequence —
 * the order, that nothing is skipped, that a loss never confirms, that a result can't come early —
 * not the exact milliseconds, which are tuning.
 */
class CompletionFlowTest {

    private val kinds = CompletionKind.entries
    private val daily = GameMode.Daily(GameDate(2026, 10, 4), 1L, EndlessTier.MEDIUM)
    private val endless = GameMode.Endless(1L, 1, EndlessTier.EASY)

    // ---- which flow ----------------------------------------------------------------

    @Test
    fun `each mode finishes the way it should`() {
        assertEquals(CompletionKind.Discovery, completionKindFor(GameMode.Campaign, hidesDiscovery = true))
        assertEquals(CompletionKind.Plain, completionKindFor(GameMode.Campaign, hidesDiscovery = false))
        assertEquals(CompletionKind.Shape, completionKindFor(daily, hidesDiscovery = false))
        assertEquals(CompletionKind.Shape, completionKindFor(endless, hidesDiscovery = false))
        assertEquals(CompletionKind.Plain, completionKindFor(GameMode.Tutorial, hidesDiscovery = false))
    }

    // ---- the phases ----------------------------------------------------------------

    @Test
    fun `a board in play is playing and a lost one is lost, whatever the clock says`() {
        for (kind in kinds) for (reduced in listOf(false, true)) {
            val schedule = ShapeConfirmSchedule.forKind(kind, reduced)
            for (t in listOf(0L, 100L, 5_000L)) {
                assertEquals(CompletionPhase.Playing, CompletionFlow.phase(GameStatus.PLAYING, t, schedule))
                assertEquals(CompletionPhase.Lost, CompletionFlow.phase(GameStatus.LOST, t, schedule))
            }
        }
    }

    @Test
    fun `a win walks final escape, confirming, revealed — in that order and never backwards`() {
        for (kind in kinds) for (reduced in listOf(false, true)) {
            val schedule = ShapeConfirmSchedule.forKind(kind, reduced)
            var seen = CompletionPhase.FinalEscape.ordinal
            assertEquals(CompletionPhase.FinalEscape, CompletionFlow.phase(GameStatus.WON, 0, schedule))
            for (t in 0L..3_000L step 10) {
                val phase = CompletionFlow.phase(GameStatus.WON, t, schedule)
                assertTrue("$kind $reduced t=$t went backwards: $phase", phase.ordinal >= seen)
                seen = phase.ordinal
            }
            assertEquals(CompletionPhase.Revealed, CompletionFlow.phase(GameStatus.WON, 3_000, schedule))
        }
    }

    @Test
    fun `a win always passes through the confirmation before any result may show`() {
        for (kind in kinds) for (reduced in listOf(false, true)) {
            val schedule = ShapeConfirmSchedule.forKind(kind, reduced)
            val confirming = (0L..schedule.handoffMs.toLong()).filter {
                CompletionFlow.phase(GameStatus.WON, it, schedule) == CompletionPhase.ShapeConfirming
            }
            assertTrue("$kind $reduced never confirms", confirming.isNotEmpty())
            // No result at any time inside the confirmation or before it.
            for (t in 0L until schedule.handoffMs) {
                assertFalse(CompletionFlow.resultMayShow(CompletionFlow.phase(GameStatus.WON, t, schedule)))
            }
            assertTrue(CompletionFlow.resultMayShow(CompletionFlow.phase(GameStatus.WON, schedule.handoffMs.toLong(), schedule)))
        }
    }

    @Test
    fun `a loss shows its card at once and never confirms a shape`() {
        for (kind in kinds) {
            val schedule = ShapeConfirmSchedule.forKind(kind, false)
            assertTrue(CompletionFlow.resultMayShow(CompletionFlow.phase(GameStatus.LOST, 0, schedule)))
            for (t in 0L..2000L step 50) {
                val phase = CompletionFlow.phase(GameStatus.LOST, t, schedule)
                assertTrue(phase != CompletionPhase.ShapeConfirming && phase != CompletionPhase.FinalEscape)
            }
        }
    }

    @Test
    fun `playing never allows a result`() {
        assertFalse(CompletionFlow.resultMayShow(CompletionPhase.Playing))
        assertFalse(CompletionFlow.resultMayShow(CompletionPhase.FinalEscape))
        assertFalse(CompletionFlow.resultMayShow(CompletionPhase.ShapeConfirming))
    }

    // ---- the confirmation's timeline -----------------------------------------------

    private val timelines = listOf(ShapeConfirmSchedule.Discovery, ShapeConfirmSchedule.Fast, ShapeConfirmSchedule.Reduced)

    @Test
    fun `the outline traces, then locks, then hands over — in that order`() {
        for (s in timelines) {
            assertTrue(s.traceStartMs >= s.ghostStartMs)
            assertTrue("lock before the trace is done", s.lockMs >= s.traceEndMs)
            assertTrue("hand-off before the lock", s.handoffMs > s.lockMs)
            assertTrue(s.fillStartMs + s.fillMs <= s.handoffMs + 1)
        }
    }

    @Test
    fun `the outline starts after the last arrow is mostly out and never takes long`() {
        // The arrow's flight is 370ms and fades through its last 45%, so by ~200ms it is nearly gone.
        assertTrue(ShapeConfirmSchedule.Discovery.traceStartMs in 150..260)
        assertTrue("Campaign hand-off was ${ShapeConfirmSchedule.Discovery.handoffMs}ms", ShapeConfirmSchedule.Discovery.handoffMs in 600..900)
        assertTrue("Daily/Endless hand-off was ${ShapeConfirmSchedule.Fast.handoffMs}ms", ShapeConfirmSchedule.Fast.handoffMs in 300..560)
        assertTrue(ShapeConfirmSchedule.Fast.handoffMs < ShapeConfirmSchedule.Discovery.handoffMs)
    }

    @Test
    fun `reduced motion is a short fade to a still outline`() {
        val s = ShapeConfirmSchedule.Reduced
        assertFalse(s.animated)
        assertTrue(ShapeConfirmSchedule.Discovery.animated && ShapeConfirmSchedule.Fast.animated)
        assertTrue(s.handoffMs <= 400)
        assertTrue(s.traceMs <= 200)
    }

    @Test
    fun `the schedule follows the kind and the motion setting`() {
        assertEquals(ShapeConfirmSchedule.Discovery, ShapeConfirmSchedule.forKind(CompletionKind.Discovery, false))
        assertEquals(ShapeConfirmSchedule.Fast, ShapeConfirmSchedule.forKind(CompletionKind.Shape, false))
        assertEquals(ShapeConfirmSchedule.Fast, ShapeConfirmSchedule.forKind(CompletionKind.Plain, false))
        for (kind in kinds) assertEquals(ShapeConfirmSchedule.Reduced, ShapeConfirmSchedule.forKind(kind, true))
    }

    // ---- the Daily / Endless reveal ------------------------------------------------

    private val standard = ShapeRevealSchedule.Standard
    private val reduced = ShapeRevealSchedule.Reduced

    @Test
    fun `the shape reveal arrives on the confirmation's hand-off`() {
        assertEquals(ShapeConfirmSchedule.Fast.handoffMs.toLong(), standard.leadInMs)
        assertEquals(ShapeConfirmSchedule.Reduced.handoffMs.toLong(), reduced.leadInMs)
    }

    @Test
    fun `the art lands before the peak and the words follow in order`() {
        assertTrue(standard.artStartMs + standard.artMs <= standard.peakMs + 120)
        val order = listOf(standard.artStartMs, standard.headlineMs, standard.nameMs, standard.detailsMs, standard.actionsMs)
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `a shape reveal's buttons cannot be pressed before they are visible`() {
        assertTrue(standard.interactiveMs > standard.actionsMs)
        assertTrue(reduced.interactiveMs > reduced.actionsMs)
    }

    @Test
    fun `endless and daily stay fast — the whole thing is over in about a second`() {
        assertTrue("available at ${standard.availableOverallMs}ms", standard.availableOverallMs in 800..1300)
        assertTrue(standard.availableOverallMs < RevealSchedule.Standard.availableOverallMs - 500)
        assertTrue(reduced.availableOverallMs < standard.availableOverallMs)
        assertEquals(0, reduced.burstMs)
    }

    @Test
    fun `the peak cue lands after the outline has locked, once, in every flow`() {
        for (kind in kinds) for (reduced in listOf(false, true)) {
            val schedule = ShapeConfirmSchedule.forKind(kind, reduced)
            val peak = peakOverallMs(kind, reduced)
            assertTrue("$kind $reduced peak $peak vs lock ${schedule.lockMs}", peak > schedule.lockMs)
            assertEquals(schedule.handoffMs.toLong(), handoffOverallMs(kind, reduced))
        }
    }

    @Test
    fun `the result's scrim and the board's fade share one span per kind`() {
        assertEquals(RevealSchedule.Standard.scrimMs, resultEnterMs(CompletionKind.Discovery, false))
        assertEquals(ShapeRevealSchedule.Standard.scrimMs, resultEnterMs(CompletionKind.Shape, false))
        assertEquals(300, resultEnterMs(CompletionKind.Plain, false))
        assertEquals(120, resultEnterMs(CompletionKind.Plain, true))
    }

    // ---- what the shape reveal says -------------------------------------------------

    @Test
    fun `a daily's first clear is revealed, a replay is simply today's shape`() {
        val first = ShapeRevealCopy.of(ResultContext.Daily("Medium", 3, 7, alreadyCountedToday = false), null, "Fish")
        assertEquals("TODAY'S SHAPE REVEALED!", first.headline)
        assertTrue(first.firstReveal)
        assertTrue(first.spoken.contains("Fish"))
        assertTrue(first.spoken.contains("Completed today"))
        assertTrue(first.spoken.contains("streak 3"))

        val replay = ShapeRevealCopy.of(ResultContext.Daily("Medium", 3, 7, alreadyCountedToday = true), null, "Fish")
        assertEquals("TODAY'S SHAPE", replay.headline)
        assertFalse(replay.firstReveal)
        assertFalse("a replay must not be announced as a reveal", replay.spoken.contains("revealed", ignoreCase = true))
        assertTrue(replay.spoken.contains("Fish"))
    }

    @Test
    fun `endless is always shape revealed, and says which puzzle`() {
        val copy = ShapeRevealCopy.of(null, ResultContext.Endless(12, "Hard"), "House")
        assertEquals("SHAPE REVEALED!", copy.headline)
        assertTrue(copy.spoken.startsWith("Shape revealed: House"))
        assertTrue(copy.spoken.contains("12"))
    }

    @Test
    fun `no wording is invented for a board that has not been won — the name is a required input`() {
        // The only way to get this copy is to supply a name, and the name only exists after a win.
        val copy = ShapeRevealCopy.of(null, ResultContext.Endless(1, "Easy"), "Key")
        assertTrue(copy.spoken.contains("Key"))
    }
}
