package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.ui.discovery.RevealSchedule

/**
 * What the end of a puzzle turns into. Decided once from the mode and the level, never from
 * whether a result has arrived, so the choice cannot race the win that makes it.
 *
 *  - [Discovery]: a Campaign level that hides a discovery — the outline, then the collectable.
 *  - [Shape]: a Daily or Endless puzzle — the outline, then the shape revealed and named.
 *  - [Plain]: anything with nothing to reveal (the replayed tutorial) — the outline, then the
 *    ordinary result card.
 */
enum class CompletionKind { Discovery, Shape, Plain }

fun completionKindFor(mode: GameMode, hidesDiscovery: Boolean): CompletionKind = when (mode) {
    GameMode.Campaign -> if (hidesDiscovery) CompletionKind.Discovery else CompletionKind.Plain
    is GameMode.Endless, is GameMode.Daily -> CompletionKind.Shape
    GameMode.Tutorial -> CompletionKind.Plain
}

/**
 * Where a board is in finishing — one value instead of a handful of booleans.
 *
 * ```
 * Playing ──(last arrow taps)──▶ FinalEscape ──▶ ShapeConfirming ──▶ Revealed
 *    └────(third life lost)───▶ Lost
 * ```
 *
 *  - [Playing]: the board is live.
 *  - [Lost]: out of lives. A loss has **no** confirmation and no reveal — the remaining arrows stay
 *    exactly as they were and the Game Over card shows at once.
 *  - [FinalEscape]: the last arrow is leaving and the board settles; the faint ghost of the solved
 *    shape may begin to rise.
 *  - [ShapeConfirming]: the outline of the puzzle's own occupied cells is tracing and filling.
 *  - [Revealed]: the result layer is up (the reveal, or the plain card). Its buttons gate themselves
 *    until they have arrived.
 *
 * It is a pure function of the game status and the time since the last arrow launched, so it is
 * the same on every device, cannot be re-entered by a recomposition, and is not persisted: after a
 * process death a won board is simply a saved win, and a board in play is just in play.
 */
enum class CompletionPhase { Playing, Lost, FinalEscape, ShapeConfirming, Revealed }

object CompletionFlow {

    /**
     * The phase at [elapsedMs] after the final arrow launched (the *launch clock* every schedule
     * below uses). Monotone in time: a win only ever moves forward, so nothing — a late callback, a
     * recomposition — can send it back to an earlier phase or make it skip the confirmation.
     */
    fun phase(status: GameStatus, elapsedMs: Long, schedule: ShapeConfirmSchedule): CompletionPhase =
        when (status) {
            GameStatus.PLAYING -> CompletionPhase.Playing
            GameStatus.LOST -> CompletionPhase.Lost
            GameStatus.WON -> when {
                elapsedMs < schedule.traceStartMs -> CompletionPhase.FinalEscape
                elapsedMs < schedule.handoffMs -> CompletionPhase.ShapeConfirming
                else -> CompletionPhase.Revealed
            }
        }

    /** True once the result layer may be composed: a loss at once, a win after the confirmation. */
    fun resultMayShow(phase: CompletionPhase): Boolean =
        phase == CompletionPhase.Revealed || phase == CompletionPhase.Lost
}

/**
 * The shape-confirmation beat, on the **launch clock** — time zero is the frame the last arrow
 * starts to leave (its own flight takes 370ms). Pure data, so the *shape* of the sequence (the
 * outline traces before it locks, the lock lands before the hand-off, it stays short) can be pinned by
 * a plain JVM test; the numbers are tuning.
 *
 * ```
 * 0        last arrow launches
 * ghost    a faint fill of the solved silhouette starts to rise, where the arrows were
 * trace    the outline draws itself around the shape, all loops at once
 * fill     the inside lights up and the line strengthens
 * lock     the outline closes: the small confirmation sound and haptic
 * handoff  the result layer arrives; the outline fades out with the board while the reveal comes in
 * ```
 *
 * [animated] false (reduced motion) means nothing moves: the finished outline fades in over the
 * trace window and that is all. The silhouette still appears, so recognition never depends on motion.
 */
class ShapeConfirmSchedule(
    val ghostStartMs: Int,
    val ghostMs: Int,
    val traceStartMs: Int,
    val traceMs: Int,
    val fillStartMs: Int,
    val fillMs: Int,
    val lockMs: Int,
    val handoffMs: Int,
    val animated: Boolean
) {
    /** When the outline has finished drawing. */
    val traceEndMs: Int get() = traceStartMs + traceMs

    companion object {
        /** The Campaign: the emotional beat, given room. About three quarters of a second. */
        val Discovery = ShapeConfirmSchedule(
            ghostStartMs = 110, ghostMs = 260,
            traceStartMs = 200, traceMs = 460,
            fillStartMs = 430, fillMs = 330,
            lockMs = 660, handoffMs = 760,
            animated = true
        )

        /** Daily, Endless and the plain card: the same beat, much faster, to keep the flow. */
        val Fast = ShapeConfirmSchedule(
            ghostStartMs = 60, ghostMs = 160,
            traceStartMs = 110, traceMs = 300,
            fillStartMs = 270, fillMs = 200,
            lockMs = 410, handoffMs = 470,
            animated = true
        )

        /** Reduced motion: a brief fade to a still outline, then straight on. */
        val Reduced = ShapeConfirmSchedule(
            ghostStartMs = 0, ghostMs = 0,
            traceStartMs = 100, traceMs = 140,
            fillStartMs = 100, fillMs = 140,
            lockMs = 240, handoffMs = 300,
            animated = false
        )

        fun forKind(kind: CompletionKind, reducedMotion: Boolean): ShapeConfirmSchedule = when {
            reducedMotion -> Reduced
            kind == CompletionKind.Discovery -> Discovery
            else -> Fast
        }
    }
}

/**
 * The reveal a Daily or Endless win gets after the outline: the solved shape, filled and named.
 * On the **layer clock** — time zero is the frame the result layer first composes, which is
 * [leadInMs] after the last arrow launched. Same idea as [RevealSchedule], just faster: the
 * Campaign reveal is the emotional collectable, this one has to keep a run of puzzles flowing.
 */
data class ShapeRevealSchedule(
    /** Launch clock: from the last arrow's launch until the result layer composes. */
    val leadInMs: Long,
    val scrimMs: Int,
    val artStartMs: Int,
    val artMs: Int,
    val glowStartMs: Int,
    val glowMs: Int,
    /** The beat the win sound and haptic land on, and the sparkles launch. */
    val peakMs: Int,
    /** 0 = no burst (reduced motion). */
    val burstMs: Int,
    val headlineMs: Int,
    val nameMs: Int,
    val detailsMs: Int,
    val actionsMs: Int,
    /** When a press on the buttons is honoured; never before they are visible. */
    val interactiveMs: Int,
    val fadeMs: Int
) {
    fun overallMs(layerMs: Int): Long = leadInMs + layerMs

    /** When the result is fully available, on the launch clock. */
    val availableOverallMs: Long get() = overallMs(actionsMs + fadeMs)

    /** The length the reveal's own clock runs for. */
    val totalMs: Int get() = maxOf(interactiveMs, actionsMs + fadeMs, detailsMs + fadeMs)

    companion object {
        val Standard = ShapeRevealSchedule(
            leadInMs = ShapeConfirmSchedule.Fast.handoffMs.toLong(),
            scrimMs = 280,
            artStartMs = 30, artMs = 300,
            glowStartMs = 80, glowMs = 260,
            peakMs = 220, burstMs = 520,
            headlineMs = 160, nameMs = 300, detailsMs = 400,
            actionsMs = 480, interactiveMs = 580,
            fadeMs = 200
        )

        val Reduced = ShapeRevealSchedule(
            leadInMs = ShapeConfirmSchedule.Reduced.handoffMs.toLong(),
            scrimMs = 120,
            artStartMs = 0, artMs = 160,
            glowStartMs = 0, glowMs = 160,
            peakMs = 160, burstMs = 0,
            headlineMs = 80, nameMs = 100, detailsMs = 120,
            actionsMs = 140, interactiveMs = 220,
            fadeMs = 100
        )

        fun forMotion(reducedMotion: Boolean): ShapeRevealSchedule = if (reducedMotion) Reduced else Standard
    }
}

/**
 * When the beat the win sound and haptic land on arrives, on the launch clock — the moment the
 * reveal peaks. One answer per kind, so `rememberWinCelebration` fires `onPeak` exactly once and
 * exactly there.
 */
fun peakOverallMs(kind: CompletionKind, reducedMotion: Boolean): Long = when (kind) {
    CompletionKind.Discovery -> RevealSchedule.forMotion(reducedMotion).let { it.overallMs(it.peakMs) }
    CompletionKind.Shape -> ShapeRevealSchedule.forMotion(reducedMotion).let { it.overallMs(it.peakMs) }
    // The plain card has no art to peak on: the cue lands a beat after the card arrives.
    CompletionKind.Plain -> ShapeConfirmSchedule.forKind(kind, reducedMotion).handoffMs + PLAIN_PEAK_AFTER_MS
}

private const val PLAIN_PEAK_AFTER_MS = 120L

/** When the result layer composes after the last arrow launches, for [kind]. */
fun handoffOverallMs(kind: CompletionKind, reducedMotion: Boolean): Long =
    ShapeConfirmSchedule.forKind(kind, reducedMotion).handoffMs.toLong()
