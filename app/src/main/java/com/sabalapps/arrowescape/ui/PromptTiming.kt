package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.ui.discovery.RevealSchedule

/**
 * When, after a Campaign win's result appears, the optional prompts may start.
 *
 * The result *is* the reward: the outline, the reveal, the name, the stars, the buttons. A
 * prompt waits for all of it to have arrived and then for [GRACE_MS] more — a beat to look at
 * what was found — and only then does anything else speak. This is measured from the moment the
 * result layer first composes, which is itself already after the last arrow, the outline and the
 * hand-off; so a prompt can never land on the final arrow, the shape confirmation or the
 * NEW DISCOVERY beat.
 *
 * (A first clear that finishes a world or the album has a longer beat of its own, which is
 * why the policy does not prompt on those clears at all.)
 */
object PromptTiming {

    /** The pause after the reveal has fully arrived and its buttons can be pressed. */
    const val GRACE_MS = 1_100L

    /** Layer-clock milliseconds from the result appearing to the earliest a prompt may show. */
    fun settleDelayMs(reducedMotion: Boolean): Long {
        val reveal = RevealSchedule.forMotion(reducedMotion)
        return reveal.totalMs + GRACE_MS
    }
}
