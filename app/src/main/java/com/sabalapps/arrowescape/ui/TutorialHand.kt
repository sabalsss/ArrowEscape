package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The friendly pointing hand of the first two levels: it drifts in beside an arrow, taps it,
 * ripples, and fades — gently, over and over, until the player does the thing.
 *
 * Drawn on a [Canvas] from a handful of rounded shapes. No Lottie, no GIF, no bitmap: a
 * path, a clock and a ripple. It draws over the board and takes no touches (no pointer
 * modifier anywhere in its chain), so a tap on the arrow beneath it is just a tap on the
 * arrow; it carries no semantics, because the caption under the board says the same thing
 * in words.
 *
 * With animations off ([reducedMotion]) nothing loops: the hand rests on the arrow, a ring
 * marks it, and that is all.
 *
 * @param centreX where the fingertip lands — the arrow's centre, in pixels, in the same
 *   coordinate space as the board's own children.
 * @param mirrored the hand trails to the left instead of the right, for an arrow near the
 *   right-hand edge, so the hand is never pushed off the screen.
 */
@Composable
internal fun TutorialHand(
    centreX: Float,
    centreY: Float,
    cellPx: Float,
    mirrored: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handDp: Dp = with(density) { (cellPx * HAND_CELLS).toDp() }.coerceIn(HAND_MIN, HAND_MAX)
    val handPx = with(density) { handDp.toPx() }
    val shape = remember { handShape() }

    val phase = if (reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "tutorialHand").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(LOOP_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "tutorialHandPhase"
        )
    }

    // The canvas is the hand's own box, positioned so the fingertip (a fixed point on the
    // drawing) sits on the arrow's centre. The ripple is drawn at the fingertip, so it is
    // centred on the arrow as well; it overflows the box, which Canvas does not clip.
    val tipX = if (mirrored) 1f - TIP_X else TIP_X
    Canvas(
        modifier = modifier
            .offset {
                IntOffset(
                    (centreX - tipX * handPx).roundToInt(),
                    (centreY - TIP_Y * handPx).roundToInt()
                )
            }
            .size(handDp)
            .clearAndSetSemantics {}
    ) {
        val state = phase?.value?.let(::handFrame) ?: RESTING
        val tip = Offset(tipX * size.width, TIP_Y * size.height)

        // The ring: a ripple that grows from the fingertip while animated, a still halo
        // on the arrow when not.
        if (reducedMotion) {
            drawRing(tip, cellPx * 0.62f, 1f)
        } else if (state.ripple > 0f) {
            drawRing(tip, cellPx * (0.30f + 0.52f * state.ripple), 1f - state.ripple)
        }

        // The hand itself: it comes from down-and-away, lands on the arrow, presses, leaves.
        val away = cellPx * 0.95f * (1f - state.arrive)
        val dx = if (mirrored) -away else away
        translate(left = dx, top = away * 0.9f + state.press * cellPx * 0.07f) {
            // The drawing always points the one way; mirroring flips it about the box's
            // centre line, which carries the fingertip from TIP_X to 1 - TIP_X.
            val draw: DrawScope.() -> Unit = {
                scale(
                    scale = state.scale,
                    pivot = Offset(TIP_X * size.width, TIP_Y * size.height)
                ) { drawHand(shape, size.width, state.alpha) }
            }
            if (mirrored) {
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, 0f)) { draw() }
            } else {
                draw()
            }
        }
    }
}

/** One frame of the loop, derived from the 0..1 phase. */
private data class HandFrame(
    /** 0 = far away, 1 = on the arrow. */
    val arrive: Float,
    /** 0..1: how far into the tap the fingertip is pressed. */
    val press: Float,
    /** Hand scale: a small squash while pressing. */
    val scale: Float,
    val alpha: Float,
    /** 0 = no ripple, then 0..1 over the ripple's life. */
    val ripple: Float
)

/** What a hand at rest looks like: on the arrow, fully visible, not pressing. */
private val RESTING = HandFrame(arrive = 1f, press = 0f, scale = 1f, alpha = 1f, ripple = 0f)

/**
 * The loop, in tenths of a cycle: glide in (0–.30), press (.30–.42), release (.42–.52),
 * ripple (.40–.78), hold, then fade out (.80–1). Every segment is an ease so nothing starts
 * or stops abruptly; the hand is never the loudest thing on the screen.
 */
private fun handFrame(t: Float): HandFrame {
    fun seg(from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
    fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
    fun easeInOut(x: Float) = x * x * (3f - 2f * x)

    val arrive = easeOut(seg(0f, 0.30f))
    val down = easeInOut(seg(0.30f, 0.42f))
    val up = easeInOut(seg(0.42f, 0.52f))
    val press = down - down * up
    val fadeIn = seg(0f, 0.16f)
    val fadeOut = 1f - seg(0.80f, 1f)
    val ripple = seg(0.40f, 0.78f).let { if (it <= 0f || it >= 1f) 0f else easeOut(it) }
    return HandFrame(
        arrive = arrive,
        press = press,
        scale = 1f - 0.09f * press,
        alpha = (fadeIn * fadeOut).coerceIn(0f, 1f),
        ripple = ripple
    )
}

private fun DrawScope.drawRing(centre: Offset, radius: Float, alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    drawCircle(RING_FILL.copy(alpha = 0.22f * a), radius, centre)
    drawCircle(Color.White.copy(alpha = 0.85f * a), radius, centre, style = Stroke(width = 2.5.dp.toPx()))
}

private fun DrawScope.drawHand(shape: Path, side: Float, alpha: Float) {
    if (alpha <= 0f) return
    scale(scale = side, pivot = Offset.Zero) {
        // A soft drop shadow: the same shape, offset down and dimmed.
        translate(left = 0.012f, top = 0.035f) {
            drawPath(shape, Color(0xFF05081C).copy(alpha = 0.30f * alpha))
        }
        drawPath(shape, Color.White.copy(alpha = alpha))
        drawPath(
            shape,
            HAND_EDGE.copy(alpha = 0.55f * alpha),
            style = Stroke(width = 0.018f)
        )
    }
}

/**
 * The hand as one path in a unit square: an index finger pointing up from a rounded palm,
 * three folded knuckles and a thumb. [PathOperation.Union] merges the parts so the outline
 * is one clean silhouette rather than a stack of overlapping shapes.
 */
private fun handShape(): Path {
    fun rect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) = Path().apply {
        addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius, radius)))
    }
    val parts = listOf(
        // The index finger: a capsule, its tip at (TIP_X, TIP_Y).
        rect(TIP_X - 0.085f, TIP_Y - 0.015f, TIP_X + 0.085f, 0.66f, 0.085f),
        // The palm.
        rect(0.20f, 0.44f, 0.80f, 0.97f, 0.20f),
        // Three folded fingers: knuckles along the top of the palm.
        rect(0.34f, 0.40f, 0.48f, 0.60f, 0.07f),
        rect(0.47f, 0.40f, 0.61f, 0.60f, 0.07f),
        rect(0.60f, 0.42f, 0.74f, 0.62f, 0.07f),
        // The thumb, tucked against the palm.
        rect(0.08f, 0.56f, 0.34f, 0.72f, 0.08f)
    )
    return parts.reduce { acc, next ->
        Path().apply { op(acc, next, PathOperation.Union) }
    }
}

private const val LOOP_MS = 2_000

/** How big the hand is, as a multiple of one board cell, kept within sensible dp limits. */
private const val HAND_CELLS = 1.8f
private val HAND_MIN = 56.dp
private val HAND_MAX = 88.dp

/** Where the fingertip is on the unit drawing. */
private const val TIP_X = 0.34f
private const val TIP_Y = 0.07f

private val RING_FILL = Color(0xFF52DED0)
private val HAND_EDGE = Color(0xFF1C2563)
