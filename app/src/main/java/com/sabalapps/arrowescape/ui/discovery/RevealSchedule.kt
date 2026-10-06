package com.sabalapps.arrowescape.ui.discovery

import com.sabalapps.arrowescape.ui.ShapeConfirmSchedule

/**
 * The timeline of a Campaign discovery reveal, in one place.
 *
 * Pure data — no Compose — so the *shape* of the sequence (the art lands before the
 * peak, the buttons arrive after the words, the whole thing stays inside about two
 * seconds) can be pinned by a plain JVM test rather than rediscovered on a device.
 * The numbers are tuning, not law; the orderings in `RevealScheduleTest` are the
 * part that must hold.
 *
 * ## Two clocks
 *
 * Everything is measured from one of two origins:
 *
 *  - **launch** — the frame the last arrow starts its escape. Only [leadInMs] is on
 *    this clock: how long the flight is given to settle before the reveal begins.
 *  - **layer** — the frame the result layer first composes, which is [leadInMs]
 *    after launch. Every other field is on this clock.
 *
 * Reading both off one object is what keeps the audio and haptic peak (fired by the
 * celebration, on the launch clock) and the burst of sparkles (drawn by the reveal,
 * on the layer clock) landing on the same beat: [overallMs] converts.
 *
 * ## The sequence
 *
 * ```
 * launch +0      last arrow escapes (its own 370ms flight)
 * layer  +0      (launch +380)  the layer arrives; the scrim starts to come up
 *        +130    the artwork starts to emerge          — alpha and a small scale
 *        +300    the world-coloured glow starts to rise
 *        +520    PEAK: sound, haptic and sparkles      (launch +900)
 *        +560    NEW DISCOVERY!
 *        +700    the name        +820  the collection  +940  the stars
 *        +1020   the buttons fade in, +1120 they can be pressed
 *        +1000   (a first clear that finishes a world or the album only) the set's
 *                pieces arrive one by one, with a small gold burst
 * ```
 *
 * ## The pause
 *
 * The last arrow is gone by launch +370 and the art is not there until launch +510:
 * a beat of an empty stage, a rising scrim and the halo settling — "what is it going
 * to be?". It is kept deliberately short, and the art is given the time back by being
 * a little quicker, so the peak does not move.
 */
data class RevealSchedule(
    /** Launch clock: from the last arrow's launch until the result layer composes. */
    val leadInMs: Long,
    val scrimMs: Int,
    val artStartMs: Int,
    val artMs: Int,
    val glowStartMs: Int,
    val glowMs: Int,
    /** The emotional peak: the sound and haptic fire, and the sparkles launch. */
    val peakMs: Int,
    /** How long the burst of sparkles lasts; 0 means none (reduced motion). */
    val burstMs: Int,
    val headlineMs: Int,
    val nameMs: Int,
    val collectionMs: Int,
    val starsMs: Int,
    /** When the buttons begin to fade in. */
    val actionsMs: Int,
    /** When a press on them is honoured. Never before they are visible. */
    val interactiveMs: Int,
    /** How long each piece takes to fade in. */
    val fadeMs: Int,
    /**
     * The completion beat — only a first clear that finishes a world or the album has
     * one. The set's pieces arrive one by one from [completionMs], [completionStaggerMs]
     * apart, and a small gold burst ([completionBurstMs], 0 = none) lands as they do.
     * It sits *behind* the ordinary sequence: the buttons are not held for it.
     */
    val completionMs: Int,
    val completionStaggerMs: Int,
    val completionBurstMs: Int
) {
    /** A layer-clock time on the launch clock. */
    fun overallMs(layerMs: Int): Long = leadInMs + layerMs

    /** When the result is fully available, on the launch clock. */
    val availableOverallMs: Long get() = overallMs(actionsMs + fadeMs)

    /** The layer-clock length the reveal's own clock has to run for. */
    val totalMs: Int get() = maxOf(interactiveMs, actionsMs + fadeMs, starsMs + fadeMs)

    /** When the last of [pieces] completion pieces has fully arrived. */
    fun completionEndMs(pieces: Int): Int =
        completionMs + completionStaggerMs * (pieces - 1).coerceAtLeast(0) + fadeMs

    /** The clock's length for a reveal that ends with a [pieces]-piece completion beat (0 = none). */
    fun totalMsFor(pieces: Int): Int =
        if (pieces <= 0) totalMs else maxOf(totalMs, completionEndMs(pieces))

    companion object {
        val Standard = RevealSchedule(
            leadInMs = ShapeConfirmSchedule.Discovery.handoffMs.toLong(),
            scrimMs = 420,
            artStartMs = 60,
            artMs = 380,
            glowStartMs = 200,
            glowMs = 380,
            peakMs = 440,
            burstMs = 720,
            headlineMs = 480,
            nameMs = 620,
            collectionMs = 740,
            starsMs = 860,
            actionsMs = 940,
            interactiveMs = 1040,
            fadeMs = 260,
            completionMs = 920,
            completionStaggerMs = 70,
            completionBurstMs = 600
        )

        /**
         * Reduced motion: the artwork fades in, the words follow at once and the
         * buttons are there almost immediately. No scale, no burst. The sound and
         * the haptic still fire — neither is animation.
         */
        val Reduced = RevealSchedule(
            leadInMs = ShapeConfirmSchedule.Reduced.handoffMs.toLong(),
            scrimMs = 140,
            artStartMs = 0,
            artMs = 180,
            glowStartMs = 0,
            glowMs = 180,
            peakMs = 180,
            burstMs = 0,
            headlineMs = 100,
            nameMs = 120,
            collectionMs = 140,
            starsMs = 160,
            actionsMs = 180,
            interactiveMs = 240,
            fadeMs = 120,
            completionMs = 160,
            completionStaggerMs = 0,
            completionBurstMs = 0
        )

        fun forMotion(reducedMotion: Boolean): RevealSchedule = if (reducedMotion) Reduced else Standard
    }
}
