package com.sabalapps.arrowescape.ui.discovery

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.sabalapps.arrowescape.ui.GamePalette
import com.sabalapps.arrowescape.ui.Spark
import com.sabalapps.arrowescape.ui.drawBurst
import com.sabalapps.arrowescape.ui.world.GameWorld

/**
 * The two colours a world lights its discoveries with.
 *
 * Used only for the glow, the halo and the sparkle accents — never for the art
 * itself, which keeps one look across all thirty so the collection reads as a set.
 *
 *  - Sky Garden: cyan and soft gold
 *  - Forest: green and teal
 *  - Sunset Canyon: orange and warm gold
 *  - Crystal Night: violet and cyan
 *  - Cosmic: indigo and violet
 */
@Immutable
class DiscoveryGlow(val primary: Color, val secondary: Color) {

    /** What the reveal's sparkles are made of: both glow colours, white, and a blend. */
    val sparkPalette: List<Color> = listOf(
        primary,
        secondary,
        Color.White,
        lerp(primary, secondary, 0.5f),
        Color.White
    )

    companion object {
        fun of(world: GameWorld): DiscoveryGlow = when (world) {
            GameWorld.SKY_GARDEN -> DiscoveryGlow(Color(0xFF5BE3F0), Color(0xFFFFE28A))
            GameWorld.FOREST -> DiscoveryGlow(Color(0xFF5BD68A), Color(0xFF14C4B4))
            GameWorld.SUNSET_CANYON -> DiscoveryGlow(Color(0xFFFF8A3D), Color(0xFFFFC53D))
            GameWorld.CRYSTAL_NIGHT -> DiscoveryGlow(Color(0xFF9A86F0), Color(0xFF5BE3F0))
            GameWorld.COSMIC -> DiscoveryGlow(Color(0xFF5B6CFF), Color(0xFFB07CFF))
        }
    }
}

/**
 * The light and the sparkle behind and around a revealed discovery, on one Canvas.
 *
 * Both are read in the draw phase from lambdas, so the reveal's clock repaints this
 * one Canvas each frame and recomposes nothing. A Perfect Escape warms the glow with
 * gold and (with the caller's larger [sparks]) throws more sparkle; the same two
 * drawings are used either way, so a flawless clear is more of the same effect, not
 * a second one.
 *
 * Decorative: no semantics.
 *
 * @param glow how lit the halo is, 0–1.
 * @param burst progress of the sparkle burst, 0–1; outside (0, 1) draws none.
 */
@Composable
internal fun DiscoveryRevealFx(
    colours: DiscoveryGlow,
    perfect: Boolean,
    sparks: List<Spark>,
    glow: () -> Float,
    burst: () -> Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val lit = glow().coerceIn(0f, 1f)
        if (lit > 0.001f) drawHalo(colours, lit, perfect)
        val progress = burst()
        if (sparks.isNotEmpty() && progress > 0f && progress < 1f) drawBurst(sparks, progress)
    }
}

private fun DrawScope.drawHalo(colours: DiscoveryGlow, lit: Float, perfect: Boolean) {
    val radius = size.minDimension / 2f
    // A wide pool of the world's own colour…
    drawCircle(
        brush = Brush.radialGradient(
            0f to colours.primary.copy(alpha = 0.50f * lit),
            0.55f to colours.primary.copy(alpha = 0.22f * lit),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    // …with a tighter, brighter core of its partner behind the art.
    drawCircle(
        brush = Brush.radialGradient(
            0f to colours.secondary.copy(alpha = 0.55f * lit),
            1f to Color.Transparent,
            center = center,
            radius = radius * 0.62f
        ),
        radius = radius * 0.62f,
        center = center
    )
    if (perfect) {
        // The Perfect Escape's warmth: gold under everything, stronger at the middle.
        drawCircle(
            brush = Brush.radialGradient(
                0f to GamePalette.Gold.copy(alpha = 0.42f * lit),
                0.6f to GamePalette.Gold.copy(alpha = 0.14f * lit),
                1f to Color.Transparent,
                center = center,
                radius = radius * 0.82f
            ),
            radius = radius * 0.82f,
            center = center
        )
    }
}

/**
 * The gold that rides with a finished set: a soft pool of gold behind the row of
 * discoveries and, as the pieces arrive, one small burst of sparkle.
 *
 * Deliberately a smaller echo of [DiscoveryRevealFx] rather than a second cinematic:
 * the same two ideas (a warm light and a burst), one colour family, drawn on one
 * Canvas from the reveal's own clock in the draw phase. Decorative: no semantics.
 *
 * @param glow how lit the pool is, 0–1.
 * @param burst progress of the sparkle burst, 0–1; outside (0, 1) draws none.
 */
@Composable
internal fun CompletionFx(
    sparks: List<Spark>,
    glow: () -> Float,
    burst: () -> Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val lit = glow().coerceIn(0f, 1f)
        if (lit > 0.001f) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to GamePalette.Gold.copy(alpha = 0.40f * lit),
                    0.55f to GamePalette.Gold.copy(alpha = 0.14f * lit),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        val progress = burst()
        if (sparks.isNotEmpty() && progress > 0f && progress < 1f) drawBurst(sparks, progress)
    }
}
