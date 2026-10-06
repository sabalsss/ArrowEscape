package com.sabalapps.arrowescape.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True when the player has turned animations off in the system accessibility
 * settings.
 *
 * Android exposes this as `ANIMATOR_DURATION_SCALE`, which the "Remove
 * animations" accessibility toggle and the developer options animation scales
 * both write to; zero means "do not animate". Compose has no wrapper for it, so
 * it is read once per composition tree and remembered — a player who changes it
 * mid-game sees the new setting the next time the screen is built, which is the
 * same behaviour as the rest of the system.
 *
 * Only the *decorative, repeating* animations check this. A looping glow is the
 * kind of motion the setting exists to stop; the one-shot arrow flight is the
 * game telling the player what just happened, so it keeps playing.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    return remember(context, inspecting) {
        if (inspecting) return@remember false
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
            // A device with the setting missing throws rather than returning a
            // default on some OEM builds; a hint that pulses is a better failure
            // than a crash, so anything unreadable counts as "motion is fine".
        }.getOrDefault(false)
    }
}
