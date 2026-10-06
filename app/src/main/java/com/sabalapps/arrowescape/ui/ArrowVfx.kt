package com.sabalapps.arrowescape.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sabalapps.arrowescape.game.Direction
import kotlin.math.max
import kotlin.math.min

/**
 * The small effects that sit around an arrow's existing flight and its existing
 * blocked feedback: a tapering trail, a few sparks, an accent where the arrow
 * crosses the board edge, a brief lift on the arrows the move just freed, and a
 * restrained impact on a blocked tap.
 *
 * Everything here is a plain `DrawScope` function called from one `drawBehind`
 * per cell, driven by the `Animatable`s the cell already runs. There is no
 * particle engine and no object pool — a "particle" is a `drawCircle` at a
 * position computed from one float, and the only per-frame allocation anywhere
 * is the trail's two gradient brushes, for the one or two arrows ever in
 * flight. Nothing loops: every effect is a pure function of a progress value
 * that reaches its end and stops, so there is no state left to clean up and
 * nothing to leak.
 *
 * The visual language is deliberately split four ways so the board never says
 * two things with the same gesture:
 *
 * | signal | gesture |
 * |---|---|
 * | hint | slow teal breathing border (`GameScreen`, unchanged) |
 * | newly freed | fast, small scale-and-brighten, no border |
 * | escape | fast directional trail, sparks, edge accent |
 * | blocked | red shake, flash, blocker pulse, impact ring |
 *
 * All of it is **universal**: the same colours on all five worlds and in all
 * four modes, Daily included. A world tints its board surface, not the
 * interaction — tapping an arrow has to feel like the same game everywhere.
 */

/** How far behind the arrow the trail reaches, in cells. */
private const val TRAIL_CELLS = 1.4f

/** Where the trail starts, as a fraction of the tile, so it leaves the back edge. */
private const val TRAIL_START = 0.44f

/** How quickly the trail reaches full strength after the launch. */
private const val TRAIL_FADE_IN = 0.06f

/** Sparks in the launch burst, and in the blocked-tap impact. */
private const val ESCAPE_SPARKS = 5
private const val BLOCK_SPARKS = 4

/** How much of the flight the launch sparks live for. */
private const val SPARK_LIFE = 0.45f

/**
 * How much of the flight the edge accent lasts, once the arrow reaches the edge.
 * The flight is 300ms, so this is roughly 150ms — long enough to register as
 * "it got out", short enough not to read as an explosion.
 */
private const val EXIT_LIFE = 0.5f

/**
 * Eight unit directions on a fixed ring. Sparks start at `tile.id % 8` and walk
 * the ring, so two removals in a row do not throw the same pattern while the
 * whole thing stays a pure function of the board — no RNG, no per-frame state.
 */
private val SPARK_RING: FloatArray = floatArrayOf(
    1f, 0f,
    0.7071f, 0.7071f,
    0f, 1f,
    -0.7071f, 0.7071f,
    -1f, 0f,
    -0.7071f, -0.7071f,
    0f, -1f,
    0.7071f, -0.7071f
)

private const val RING_POINTS = 8

/**
 * `(1 - t)` raised to [power], clamped. The fade every effect here uses.
 *
 * An integer power on purpose: these are two or three multiplies rather than a
 * `pow` call, and nothing here needs a curve a whole number cannot describe.
 */
private fun decay(t: Float, power: Int): Float {
    val remaining = (1f - t).coerceIn(0f, 1f)
    var out = remaining
    repeat(power - 1) { out *= remaining }
    return out
}

/**
 * The glowing streak behind an escaping arrow.
 *
 * Drawn as a wide soft halo with a narrow brighter core inside it, both fading
 * to nothing at the tail, so the glow is strongest right behind the arrow.
 * Its length is capped by how far the arrow has *actually* travelled,
 * which keeps a trail from ever reaching back further than the arrow has been —
 * so early in the flight it is barely there, and by the time it is full length
 * the cell it came from is empty. That is also what stops it from washing over
 * the arrows still standing behind it.
 *
 * @param travelled distance covered so far, in pixels
 * @param extent the cell's size along the direction of travel, in pixels
 */
internal fun DrawScope.drawEscapeTrail(
    direction: Direction,
    progress: Float,
    travelled: Float,
    extent: Float,
    halo: Color,
    core: Color
) {
    val strength = decayFlight(progress) *
        (progress / TRAIL_FADE_IN).coerceIn(0f, 1f)
    if (strength <= 0.01f) return

    val start = extent * TRAIL_START
    val length = min(extent * TRAIL_CELLS, max(0f, travelled))
    if (length <= 1f) return

    val thickness = min(size.width, size.height)
    drawTrailPass(direction, start, length, thickness * 0.30f, halo, strength * 0.46f)
    drawTrailPass(direction, start, length, thickness * 0.13f, core, strength * 0.78f)
}

/**
 * One pass of the trail: a single rounded band running back from the arrow,
 * filled with a gradient that fades to nothing at the tail.
 *
 * One rect rather than a row of segments, because a stack of segments with
 * stepped alphas reads as a string of beads however carefully the steps are
 * sized — the eye picks out the banding. A gradient is the thing that actually
 * looks like a trail, and two `Brush` allocations per frame for the one or two
 * arrows ever in flight is not a cost worth designing around.
 */
private fun DrawScope.drawTrailPass(
    direction: Direction,
    start: Float,
    length: Float,
    halfWidth: Float,
    color: Color,
    strength: Float
) {
    // The trail lies opposite the direction of travel.
    val backX = -direction.dCol.toFloat()
    val backY = -direction.dRow.toFloat()
    val centreX = size.width / 2f
    val centreY = size.height / 2f

    val headX = centreX + backX * start
    val headY = centreY + backY * start
    val tailX = centreX + backX * (start + length)
    val tailY = centreY + backY * (start + length)

    val left = min(headX, tailX) - if (backX == 0f) halfWidth else 0f
    val right = max(headX, tailX) + if (backX == 0f) halfWidth else 0f
    val top = min(headY, tailY) - if (backY == 0f) halfWidth else 0f
    val bottom = max(headY, tailY) + if (backY == 0f) halfWidth else 0f

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(color.copy(alpha = strength), color.copy(alpha = 0f)),
            start = Offset(headX, headY),
            end = Offset(tailX, tailY)
        ),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(min(halfWidth, length * 0.5f))
    )
}

/**
 * The small burst left at the cell the arrow launched from: a soft glow that
 * goes out almost at once, and [ESCAPE_SPARKS] dots thrown outward.
 *
 * [origin] is in the cell's own drawing space, which moves with the arrow, so
 * the caller cancels the flight offset out — the burst stays put on the board
 * while the arrow leaves it.
 */
internal fun DrawScope.drawLaunchSparks(
    origin: Offset,
    tileId: Int,
    progress: Float,
    glow: Color,
    spark: Color
) {
    val t = (progress / SPARK_LIFE).coerceIn(0f, 1f)
    if (t >= 1f) return
    val unit = min(size.width, size.height)

    val glowAlpha = decay((progress / 0.22f).coerceIn(0f, 1f), 2) * 0.5f
    if (glowAlpha > 0.01f) {
        drawCircle(
            color = glow.copy(alpha = glowAlpha),
            radius = unit * (0.34f + 0.16f * t),
            center = origin
        )
    }

    val alpha = decay(t, 2) * 0.85f
    if (alpha <= 0.01f) return
    val distance = unit * (0.24f + 0.56f * t)
    val dot = unit * 0.055f * (1f - 0.5f * t)
    for (i in 0 until ESCAPE_SPARKS) {
        val ring = ((tileId + i) % RING_POINTS) * 2
        drawCircle(
            color = spark.copy(alpha = alpha),
            radius = dot,
            center = Offset(
                origin.x + SPARK_RING[ring] * distance,
                origin.y + SPARK_RING[ring + 1] * distance
            )
        )
    }
}

/**
 * Where the arrow crosses off the board: a quick flash and one small expanding
 * ring. The player should read "it got out", not "something exploded", so this
 * is short, thin and does not grow past a cell.
 *
 * @param reached how far past the crossing the flight is, 0..1
 */
internal fun DrawScope.drawExitAccent(at: Offset, reached: Float, color: Color) {
    val t = reached.coerceIn(0f, 1f)
    if (t >= 1f) return
    val unit = min(size.width, size.height)

    val flash = decay(t, 4) * 0.55f
    if (flash > 0.01f) {
        drawCircle(color = color.copy(alpha = flash), radius = unit * 0.2f, center = at)
    }
    val ring = decay(t, 2) * 0.75f
    if (ring > 0.01f) {
        drawCircle(
            color = color.copy(alpha = ring),
            radius = unit * (0.14f + 0.6f * t),
            center = at,
            style = Stroke(width = unit * 0.085f * (1f - 0.6f * t))
        )
    }
}

/**
 * The impact under a blocked tap: one thin ring and [BLOCK_SPARKS] dots on the
 * diagonals, so the hit has some weight without competing with the shake or
 * with the blocker's pulse — which is still the strongest signal here, because
 * it is the one that teaches the rule.
 *
 * Both are drawn behind the tile, so both start *outside* it: a half-unit is the
 * tile's own edge, and anything inside that is simply painted over. The sparks
 * sit on the diagonals, which is where the gaps between tiles are, so they never
 * land on a neighbouring arrow.
 */
internal fun DrawScope.drawBlockedImpact(progress: Float, ring: Color, spark: Color) {
    val t = progress.coerceIn(0f, 1f)
    if (t >= 1f) return
    val unit = min(size.width, size.height)
    val centre = Offset(size.width / 2f, size.height / 2f)

    val ringAlpha = decay(t, 2) * 0.65f
    if (ringAlpha > 0.01f) {
        drawCircle(
            color = ring.copy(alpha = ringAlpha),
            radius = unit * (0.58f + 0.38f * t),
            center = centre,
            style = Stroke(width = unit * 0.075f * (1f - 0.5f * t))
        )
    }

    val alpha = decay(t, 2) * 0.85f
    if (alpha <= 0.01f) return
    val distance = unit * (0.78f + 0.32f * t)
    val dot = unit * 0.055f * (1f - 0.4f * t)
    for (i in 0 until BLOCK_SPARKS) {
        // The odd entries of the ring are the four diagonals.
        val index = (i * 2 + 1) * 2
        drawCircle(
            color = spark.copy(alpha = alpha),
            radius = dot,
            center = Offset(
                centre.x + SPARK_RING[index] * distance,
                centre.y + SPARK_RING[index + 1] * distance
            )
        )
    }
}

/**
 * The soft halo behind an arrow the last move just freed. Paired with a small
 * scale and brightness lift in the cell itself; together they last about a third
 * of a second and leave nothing behind. No border, because the breathing teal
 * border belongs to the hint and must stay unmistakable.
 */
internal fun DrawScope.drawAwakenHalo(strength: Float, corner: Float, color: Color) {
    if (strength <= 0.01f) return
    val spread = min(size.width, size.height) * 0.09f * strength
    drawRoundRect(
        color = color.copy(alpha = 0.20f * strength),
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + spread * 2f, size.height + spread * 2f),
        cornerRadius = CornerRadius(corner + spread)
    )
}

/**
 * The trail fades on the same curve the arrow itself does, so the two leave
 * together rather than the glow outliving the thing that cast it.
 */
private fun decayFlight(progress: Float): Float =
    ((1f - progress) / 0.45f).coerceIn(0f, 1f)

/**
 * How far along the flight the arrow's leading edge crosses the board boundary.
 *
 * The flight carries the arrow a cell and a half clear of the edge, so the
 * crossing happens partway through: an arrow already against the edge leaves at
 * once, one on the far side of the board leaves late. Returning it as a fraction
 * of the flight is what lets the edge accent fire at the right moment without a
 * second timer.
 */
internal fun exitFraction(
    direction: Direction,
    baseX: Float,
    baseY: Float,
    cellWidthPx: Float,
    cellHeightPx: Float,
    boardWidthPx: Float,
    boardHeightPx: Float,
    travel: Float
): Float {
    if (travel <= 0f) return 0f
    val distance = when (direction) {
        Direction.UP -> baseY
        Direction.DOWN -> boardHeightPx - baseY - cellHeightPx
        Direction.LEFT -> baseX
        Direction.RIGHT -> boardWidthPx - baseX - cellWidthPx
    }
    return (distance / travel).coerceIn(0f, 1f)
}

/** The width of the edge-accent window, as a fraction of the flight. */
internal const val EXIT_WINDOW = EXIT_LIFE
