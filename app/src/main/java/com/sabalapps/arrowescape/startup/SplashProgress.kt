package com.sabalapps.arrowescape.startup

import kotlin.math.exp
import kotlin.math.floor

/**
 * The loading screen's clock and the number it shows, kept apart from the real work.
 *
 * Two things are mixed here and must not be confused:
 *
 *  - **the real target** — how far startup has *actually* got ([StartupProgress.target]);
 *  - **the shown value** — what the bar and the percentage read, which only ever *eases
 *    towards* that target.
 *
 * So the bar moves smoothly instead of jumping 0 → 40 → 100 as tasks settle; it never runs
 * ahead of the work (shown ≤ target, always); it cannot go backwards; and it cannot read 100%
 * until the target is 1 — which is not until the game can really open.
 *
 * There is also a floor under how fast it may fill ([FILL_MS]). Startup is usually
 * near-instant, and a bar that jumped to 100% in one frame would be gone before the arrows
 * had assembled. The floor is what keeps the animation readable; it is restrained — about a
 * second — and it is the *only* thing in startup that waits for the sake of looking good.
 */
class SplashProgress {

    /** 0..1. */
    var shown: Float = 0f
        private set

    /** Milliseconds of animation so far. */
    var elapsedMs: Long = 0L
        private set

    /** The whole number the screen prints. 100 only once [shown] is exactly 1. */
    val percent: Int get() = if (shown >= 1f) 100 else floor(shown * 100f).toInt().coerceIn(0, 99)

    /** Moves the clock on by [dtMs] and eases [shown] towards [target]. Returns the new value. */
    fun advance(dtMs: Long, target: Float): Float {
        val dt = dtMs.coerceAtLeast(0L)
        elapsedMs += dt
        val ceiling = target.coerceIn(0f, 1f)
        val byTime = fillCurve(elapsedMs)
        val desired = minOf(ceiling, byTime)
        var next = shown + (desired - shown) * (1f - exp(-dt / TAU_MS))
        if (desired >= 1f && 1f - next < SNAP) next = 1f
        // Monotone, and never past the real target.
        shown = next.coerceIn(shown, maxOf(shown, ceiling))
        return shown
    }

    companion object {
        /** The fastest the bar may fill from empty, in milliseconds. */
        const val FILL_MS = 900L

        /** How tightly the shown value follows its goal: smaller is snappier. */
        private const val TAU_MS = 70f

        private const val SNAP = 0.004f

        /** A gentle ease-out of time: quick at first, settling into the end. */
        fun fillCurve(elapsedMs: Long): Float {
            val t = (elapsedMs.toFloat() / FILL_MS).coerceIn(0f, 1f)
            return 1f - (1f - t) * (1f - t)
        }

        /** Longest the loading screen may stay up whatever is still outstanding. */
        const val HARD_CAP_MS = 6_000L

        /** After the bar is full: the logo's small glow pulse, then the hand-off. */
        const val PULSE_MS = 240L

        /** The arrows are assembled, and the title in, by this time. */
        const val ASSEMBLY_MS = 900L

        /**
         * Whether the loading screen may hand over to the game: the bar is full and the
         * arrows have finished assembling — or, so that nothing can hold the player here for
         * good, [HARD_CAP_MS] has passed whatever is outstanding.
         */
        fun canFinish(shown: Float, elapsedMs: Long): Boolean =
            (shown >= 1f && elapsedMs >= ASSEMBLY_MS) || elapsedMs >= HARD_CAP_MS
    }
}
