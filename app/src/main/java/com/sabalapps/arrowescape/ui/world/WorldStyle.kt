package com.sabalapps.arrowescape.ui.world

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.sabalapps.arrowescape.R

/**
 * How a [GameWorld] is painted: the artwork, the accent drawn from it, and how
 * hard the scrim has to work to keep the chrome readable over it.
 *
 * This is the only place the world system touches `R`, which is what keeps
 * [GameWorld] and [GameWorlds] pure Kotlin and unit-testable on a plain JVM. It
 * is also deliberately data rather than drawing code: [WorldBackground] does all
 * the rendering, so a world is a handful of numbers and not a special case.
 *
 * The scrim strengths are per world because the artwork is not uniform. Sky
 * Garden is bright at the top and needs real help behind the HUD; Cosmic is
 * already near-black and would go muddy if scrimmed the same amount. Sunset
 * Canyon and Crystal Night carry their detail low, so they get the strongest
 * bottom scrim — that is where the footer line and the Hint pill sit.
 *
 * @property background the full-bleed artwork, 1440×2560 portrait, `nodpi` so it
 *   is decoded at its authored size rather than scaled up for the bucket.
 * @property accent a colour lifted from the artwork, used at very low strength to
 *   tint the board surface so the board belongs to the scene instead of sitting
 *   on top of it. Never used for text.
 * @property topScrim how much of the theme's scrim colour is laid over the top of
 *   the artwork, where the HUD card sits. 0 is untouched artwork.
 * @property bottomScrim the same at the bottom, behind the footer line and the
 *   Hint pill.
 */
data class WorldStyle(
    @DrawableRes val background: Int,
    val accent: Color,
    val topScrim: Float,
    val bottomScrim: Float
) {
    companion object {

        /** The style for [world]. A plain lookup; no state, no context. */
        fun of(world: GameWorld): WorldStyle = when (world) {
            GameWorld.SKY_GARDEN -> WorldStyle(
                background = R.drawable.bg_sky_garden,
                accent = Color(0xFF6FB7E8),
                // The brightest artwork of the five, and bright at the top,
                // which is exactly where the HUD card is.
                topScrim = 0.34f,
                bottomScrim = 0.26f
            )

            GameWorld.FOREST -> WorldStyle(
                background = R.drawable.bg_forest,
                accent = Color(0xFF5EA873),
                topScrim = 0.30f,
                bottomScrim = 0.26f
            )

            GameWorld.SUNSET_CANYON -> WorldStyle(
                background = R.drawable.bg_sunset_canyon,
                accent = Color(0xFFE2804A),
                topScrim = 0.30f,
                // Detail sits low in this one; the footer needs the help.
                bottomScrim = 0.34f
            )

            GameWorld.CRYSTAL_NIGHT -> WorldStyle(
                background = R.drawable.bg_crystal_night,
                accent = Color(0xFF8E7BE8),
                // Already dark at the top; more scrim would only flatten it.
                topScrim = 0.22f,
                // Glowing crystal forms near the lower corners.
                bottomScrim = 0.32f
            )

            GameWorld.COSMIC -> WorldStyle(
                background = R.drawable.bg_cosmic,
                accent = Color(0xFF7E6BE0),
                // The darkest artwork. Scrimmed like the others it goes muddy
                // and the stars disappear, and it needs the least help anyway.
                topScrim = 0.18f,
                bottomScrim = 0.20f
            )
        }

        /**
         * The Daily Challenge's accent: a warm gold, sitting on top of whichever
         * world the day landed in rather than replacing it.
         *
         * Golden because it is the one warm note that is neither the tile's own
         * indigo nor the red a blocked tap owns, and because it reads as "today"
         * against all five backdrops — including Sunset Canyon, where it is
         * close enough to the artwork to look intentional rather than clashing.
         * It is the same hue family as the theme's `tertiary`, which is defined
         * for exactly this.
         */
        val DAILY_ACCENT = Color(0xFFF2C063)

        /** How strongly [DAILY_ACCENT] washes the artwork. Restrained on purpose. */
        const val DAILY_WASH = 0.13f
    }
}
