package com.sabalapps.arrowescape.feedback

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The haptic strengths the game uses, weakest first.
 *
 * These go through [View.performHapticFeedback], which already honours the
 * user's system "touch feedback" setting and needs no VIBRATE permission.
 */
enum class HapticEffect {
    /** Successful move: barely-there tick. */
    LIGHT_SUCCESS,

    /** The solved shape's outline locking in: a very small, light confirmation — lighter than a win. */
    SHAPE_LOCK,

    /** Blocked move: a firmer "no". */
    REJECTION,

    /** Level complete: a satisfying confirm. */
    CELEBRATION,

    /** Game over: subtle, not punishing. */
    FAILURE
}

class Haptics(private val view: View) {

    fun perform(effect: HapticEffect, enabled: Boolean) {
        if (!enabled) return
        runCatching { view.performHapticFeedback(constantFor(effect)) }
    }

    private fun constantFor(effect: HapticEffect): Int {
        val apiR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        return when (effect) {
            HapticEffect.LIGHT_SUCCESS -> HapticFeedbackConstants.CLOCK_TICK
            HapticEffect.SHAPE_LOCK ->
                if (apiR) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.KEYBOARD_TAP
            HapticEffect.REJECTION ->
                if (apiR) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            HapticEffect.CELEBRATION ->
                if (apiR) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            HapticEffect.FAILURE ->
                if (apiR) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.VIRTUAL_KEY
        }
    }
}
