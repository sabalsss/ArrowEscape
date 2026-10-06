package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * The two drawing pieces only a [BoardPresentation.Shape] board uses: the soft
 * halo that supports the whole formation, and the finish that makes each arrow a
 * little physical piece rather than a flat square.
 *
 * Both are plain `Canvas` drawing — no blur, no per-frame allocation, nothing
 * that moves — and neither carries semantics: they are decoration.
 */

/**
 * The arrow piece's body: electric blue easing to indigo from top to bottom.
 *
 * The same blue the game's primary buttons use, taken from the fixed palette
 * rather than the Material scheme, because a piece belongs to the game and not to
 * the page it sits on: it must be the same blue on every world, in both themes,
 * and *opaque*, since with no board surface underneath nothing else would hold
 * the world's artwork back from showing through it.
 */
internal val ShapePieceTop: Color = lerp(GamePalette.BlueTop, GamePalette.BlueBottom, 0.12f)
internal val ShapePieceBottom: Color = lerp(GamePalette.BlueTop, GamePalette.BlueBottom, 0.82f)

private const val PIECE_SHEEN_ALPHA = 0.34f
private const val PIECE_RIM_TOP_ALPHA = 0.50f
private const val PIECE_RIM_ACCENT_ALPHA = 0.45f
private val PIECE_RIM_WIDTH = 1.2.dp

/**
 * The finish on an arrow piece, drawn over its fill and under its glyph: a soft
 * highlight across the top, and a small inner border that runs from white at the
 * top to a hint of the world's own colour at the bottom.
 *
 * The world colour is deliberately only that rim — a faint reflection on the lower
 * edge. The body stays the one blue in every world, so a piece is recognisably
 * the same object everywhere and its contrast with the white glyph never moves.
 */
internal fun Modifier.arrowPieceFinish(corner: Dp, worldAccent: Color): Modifier =
    drawWithCache {
        val cornerPx = corner.toPx()
        val rimWidth = PIECE_RIM_WIDTH.toPx()
        val half = rimWidth / 2f
        val sheen = Brush.verticalGradient(
            0f to Color.White.copy(alpha = PIECE_SHEEN_ALPHA),
            0.46f to Color.Transparent,
            startY = 0f,
            endY = size.height
        )
        val rim = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = PIECE_RIM_TOP_ALPHA),
                worldAccent.copy(alpha = PIECE_RIM_ACCENT_ALPHA)
            ),
            startY = 0f,
            endY = size.height
        )
        onDrawBehind {
            drawRoundRect(
                brush = sheen,
                size = size,
                cornerRadius = CornerRadius(cornerPx)
            )
            drawRoundRect(
                brush = rim,
                topLeft = Offset(half, half),
                size = Size(size.width - rimWidth, size.height - rimWidth),
                cornerRadius = CornerRadius((cornerPx - half).coerceAtLeast(0f)),
                style = Stroke(width = rimWidth)
            )
        }
    }

// How strongly the halo's two layers read at the centre of the formation. Both are
// low on purpose: it is there to lift the arrows off a busy backdrop, not to be
// seen as an object of its own — the arrows are the focus.
private const val POOL_ALPHA = 0.24f
private const val GLOW_ALPHA = 0.30f

// The closing build-up, as how much each layer is lifted *beyond* what it would
// be at that point. Small on purpose: the halo is already dimming as the shape is
// taken apart, and this only stops it dimming into the last move.
private const val POOL_LIFT = 0.30f
private const val ACCENT_LIFT = 0.40f
private const val FOCUS_LIFT = 0.15f
private const val FOCUS_TIGHTEN = 0.10f

/**
 * One gradient radius in an arbitrary unit, scaled to the real ellipse by a
 * canvas transform. A unit circle through a non-uniform scale is the cheapest way
 * to get an *elliptical* radial gradient, which is what hugs a tall or a wide
 * formation without a rectangle anywhere in it.
 */
private const val HALO_UNIT = 100f

/**
 * The Campaign shape's support: a soft pool of the world's colour, deepened with
 * navy, behind the whole formation. It makes the silhouette read over a complex
 * backdrop and makes the group feel lit and floating in its world.
 *
 * Two concentric radial gradients, both elliptical and both reaching nothing at
 * their rim, so there is no edge, no card and no rectangle: a glow cannot be
 * mistaken for a board. The ellipse hugs the formation's own proportions — a tall
 * Rocket gets a tall halo, a wide Cloud a wide one — and is held inside the canvas,
 * so it fades out before the space it was given ends rather than being cut off at
 * it.
 *
 * The halo is **not** an outline of the discovery and does not follow the
 * silhouette's cells; it supports the arrow group as a whole.
 *
 * Static: the only thing that ever changes it is [strength], and the caller
 * decides whether that eases or snaps.
 *
 * @param strength 0–1, read in the draw phase so a change repaints without
 *   recomposing anything.
 */
@Composable
internal fun CampaignShapeHalo(
    shapeWidth: Dp,
    shapeHeight: Dp,
    spread: Dp,
    accent: Color,
    strength: State<Float>,
    modifier: Modifier = Modifier,
    anticipation: State<Float>? = null
) {
    Canvas(modifier = modifier) {
        val level = strength.value
        if (level <= 0.01f) return@Canvas
        // 0..1 across the three closing stages; each step is its own 0..1 ramp, so
        // an ease between stages blends them rather than jumping.
        val build = (anticipation?.value ?: 0f).coerceIn(0f, 1f) * ShapeAnticipation.STAGES
        val poolStep = build.coerceIn(0f, 1f)
        val accentStep = (build - 1f).coerceIn(0f, 1f)
        val focusStep = (build - 2f).coerceIn(0f, 1f)
        val poolLevel = level * (1f + POOL_LIFT * poolStep) * (1f + FOCUS_LIFT * focusStep)
        val accentLevel = level * (1f + ACCENT_LIFT * accentStep) * (1f + FOCUS_LIFT * focusStep)
        val focus = 1f - FOCUS_TIGHTEN * focusStep

        val radiusX = min(shapeWidth.toPx() / 2f + spread.toPx(), size.width / 2f) * focus
        val radiusY = min(shapeHeight.toPx() / 2f + spread.toPx(), size.height / 2f) * focus
        if (radiusX <= 0f || radiusY <= 0f) return@Canvas

        val middle = center
        val pool = lerp(GamePalette.Navy, accent, 0.25f)
        scale(scaleX = radiusX / HALO_UNIT, scaleY = radiusY / HALO_UNIT, pivot = middle) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to pool.copy(alpha = POOL_ALPHA * poolLevel),
                    0.55f to pool.copy(alpha = POOL_ALPHA * poolLevel * 0.62f),
                    0.85f to pool.copy(alpha = POOL_ALPHA * poolLevel * 0.18f),
                    1f to Color.Transparent,
                    center = middle,
                    radius = HALO_UNIT
                ),
                radius = HALO_UNIT,
                center = middle
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to accent.copy(alpha = GLOW_ALPHA * accentLevel),
                    0.50f to accent.copy(alpha = GLOW_ALPHA * accentLevel * 0.62f),
                    0.80f to accent.copy(alpha = GLOW_ALPHA * accentLevel * 0.20f),
                    1f to Color.Transparent,
                    center = middle,
                    radius = HALO_UNIT
                ),
                radius = HALO_UNIT,
                center = middle
            )
        }
    }
}
