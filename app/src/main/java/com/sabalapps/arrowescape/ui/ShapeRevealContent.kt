package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabalapps.arrowescape.shape.ShapeContour
import com.sabalapps.arrowescape.ui.discovery.DiscoveryGlow
import com.sabalapps.arrowescape.ui.discovery.DiscoveryRevealFx
import com.sabalapps.arrowescape.ui.world.GameWorld
import kotlin.math.min

/**
 * What a shape reveal *says*, as pure data so the wording can be pinned by a plain JVM test.
 *
 * A Daily's first clear is "TODAY'S SHAPE REVEALED!". A replay of a day already in the books is plainly
 * "TODAY'S SHAPE" — it is not new, and the game does not pretend it is. Endless is always "SHAPE
 * REVEALED!" and goes straight on. [spoken] is the one line a screen reader hears, and it is the only
 * place the name is announced.
 */
class ShapeRevealCopy(val headline: String, val firstReveal: Boolean, val spoken: String) {
    companion object {
        fun of(daily: ResultContext.Daily?, endless: ResultContext.Endless?, name: String): ShapeRevealCopy {
            val first = daily?.alreadyCountedToday != true
            return when {
                daily != null && first -> ShapeRevealCopy(
                    "TODAY'S SHAPE REVEALED!", true,
                    "Today's shape revealed: $name. Completed today. Current streak ${daily.streak}, best ${daily.bestStreak}."
                )
                daily != null -> ShapeRevealCopy("TODAY'S SHAPE", false, "Today's shape: $name. Already completed today.")
                else -> ShapeRevealCopy("SHAPE REVEALED!", true, "Shape revealed: $name. Puzzle ${endless?.puzzleNumber ?: 0}.")
            }
        }
    }
}

/** The sparkles of a shape reveal: fewer than a collectable's, because it comes round more often. */
private const val SHAPE_REVEAL_SPARKS = 22

/** The art may be at most this big; the room and the width shrink it from there. */
private val MAX_SHAPE_ART = 220.dp

/**
 * A Daily or Endless win, as a reveal: the outline has confirmed the shape, and now the shape
 * itself — filled, polished, **named** — takes the screen, then whatever the mode has to say, then
 * the way on.
 *
 * It is the quick sibling of the Campaign's [DiscoveryResultContent] and works the same way: one
 * linear clock runs for [ShapeRevealSchedule.totalMs], every piece derives its own progress from it
 * **in the draw phase**, and the buttons swallow presses until they have arrived — a stray tap from
 * the last arrow cannot skip the reveal it just earned.
 *
 * The picture is [contour], traced from the board that was just solved, smoothed for the polished
 * look ([ShapeRevealArt]). It starts at [revealFrom] — the middle of the stage the arrows were in —
 * and glides up into its slot as it fades in, so what was a shape made of arrows becomes the shape.
 * No emoji, no stock icon: if the picture on screen is not the puzzle just solved, this is broken.
 *
 * @param name the revealed shape's name. Reaches this composable only once the board is won.
 * @param headline "SHAPE REVEALED!", "TODAY'S SHAPE REVEALED!" or "TODAY'S SHAPE" for a replay.
 * @param firstReveal gold for a first reveal, a cooler white for a revisit.
 * @param spoken what a screen reader hears, as one heading ("Shape revealed: Fish").
 * @param detail what the mode adds under the name (the day and the streak, or the puzzle number).
 * @param actions the buttons.
 */
@Composable
internal fun ShapeRevealContent(
    name: String,
    contour: ShapeContour,
    world: GameWorld,
    headline: String,
    firstReveal: Boolean,
    spoken: String,
    schedule: ShapeRevealSchedule,
    reducedMotion: Boolean,
    room: Dp,
    revealFrom: Offset?,
    detail: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    val clock = remember { Animatable(0f) }
    var slotCentre by remember { mutableStateOf<Offset?>(null) }
    val clockMs = schedule.totalMs
    LaunchedEffect(Unit) {
        clock.animateTo(clockMs.toFloat(), tween(clockMs, easing = LinearEasing))
    }
    val colours = remember(world) { DiscoveryGlow.of(world) }
    val sparks = remember(contour, colours) {
        sparks(seed = contour.perimeter * 31 + contour.area, count = SHAPE_REVEAL_SPARKS, palette = colours.sparkPalette)
    }
    val rise = if (reducedMotion) 0.dp else 10.dp
    val pressable by remember { derivedStateOf { clock.value >= schedule.interactiveMs } }

    BoxWithConstraints(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        val art = minOf(MAX_SHAPE_ART, maxWidth * 0.58f, room * 0.30f).coerceAtLeast(120.dp)

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // What was found: one spoken node for the lot, announced as soon as it appears.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = spoken
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RevealTitle(
                    text = headline,
                    glow = if (firstReveal) GamePalette.Gold else colours.primary,
                    modifier = Modifier.stage(clock, schedule.headlineMs, schedule.fadeMs, rise)
                )
                Spacer(Modifier.height(AppSpace.tight))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(art * 1.08f)
                        .onGloballyPositioned { slot ->
                            val origin = slot.positionInRoot()
                            slotCentre = Offset(origin.x + slot.size.width / 2f, origin.y + slot.size.height / 2f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(art * 1.08f)
                            .graphicsLayer {
                                val from = revealFrom
                                val to = slotCentre
                                if (from != null && to != null && !reducedMotion) {
                                    val left = 1f - androidx.compose.animation.core.FastOutSlowInEasing.transform(
                                        clock.window(schedule.artStartMs, schedule.artMs)
                                    )
                                    translationX = (from.x - to.x) * left
                                    translationY = (from.y - to.y) * left
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        DiscoveryRevealFx(
                            colours = colours,
                            perfect = false,
                            sparks = if (schedule.burstMs > 0) sparks else emptyList(),
                            glow = {
                                val rising = clock.window(schedule.glowStartMs, schedule.glowMs)
                                val settling = clock.window(schedule.glowStartMs + schedule.glowMs, 400)
                                rising * (1f - 0.2f * settling)
                            },
                            burst = {
                                if (schedule.burstMs <= 0) 0f else clock.window(schedule.peakMs, schedule.burstMs)
                            },
                            modifier = Modifier.requiredSize(art * 1.8f)
                        )
                        ShapeRevealArt(
                            contour = contour,
                            colours = colours,
                            modifier = Modifier
                                .size(art)
                                .graphicsLayer {
                                    val p = androidx.compose.animation.core.FastOutSlowInEasing.transform(
                                        clock.window(schedule.artStartMs, schedule.artMs)
                                    )
                                    alpha = p
                                    val s = if (reducedMotion) 1f else 0.90f + 0.10f * p
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                    }
                }

                Spacer(Modifier.height(AppSpace.hair))
                RevealTitle(
                    text = name,
                    glow = null,
                    large = true,
                    modifier = Modifier.stage(clock, schedule.nameMs, schedule.fadeMs, rise)
                )
            }

            Spacer(Modifier.height(AppSpace.tight))
            Box(modifier = Modifier.stage(clock, schedule.detailsMs, schedule.fadeMs, rise)) { detail() }

            Spacer(Modifier.height(AppSpace.betweenSections))
            Box(modifier = Modifier.stage(clock, schedule.actionsMs, schedule.fadeMs, rise)) {
                Column(modifier = Modifier.fillMaxWidth()) { actions() }
                if (!pressable) {
                    // Until the buttons have properly arrived a press on them is swallowed.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) { detectTapGestures { } }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// The picture
// -------------------------------------------------------------------------------------------------

/** A traced contour with every loop smoothed, in grid units, ready to draw at any size. */
private class SmoothedShape(
    val path: Path,
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float
) {
    companion object {
        fun of(contour: ShapeContour): SmoothedShape {
            val path = Path().apply { fillType = PathFillType.EvenOdd }
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (loop in contour.loops) {
                val points = loop.smoothed(2)
                points.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    minX = min(minX, p.x); maxX = maxOf(maxX, p.x)
                    minY = min(minY, p.y); maxY = maxOf(maxY, p.y)
                }
                path.close()
            }
            if (minX > maxX) return SmoothedShape(path, 0f, 0f, 1f, 1f)
            return SmoothedShape(path, minX, minY, (maxX - minX).coerceAtLeast(0.001f), (maxY - minY).coerceAtLeast(0.001f))
        }
    }
}

/** Ink and sticker ring, in grid cells — the house style of the Campaign's collectables, on a shape. */
private const val INK_HALF = 0.085f
private const val RING_HALF = 0.07f

/**
 * The solved shape, polished: its traced outline with the staircase taken out of the diagonals, a
 * two-tone fill, a deep ink edge, a thin white sticker ring outside it and a little gloss — the same
 * look as the Campaign's discoveries, so a revealed shape sits beside them without being one.
 *
 * It is the puzzle's own silhouette, fitted and centred in whatever box it is given. Decoration: no
 * semantics — the words around it carry the name.
 */
@Composable
internal fun ShapeRevealArt(contour: ShapeContour, colours: DiscoveryGlow, modifier: Modifier = Modifier) {
    val shape = remember(contour) { SmoothedShape.of(contour) }
    val top = remember(colours) { lerp(colours.secondary, Color.White, 0.45f) }
    val bottom = remember(colours) { lerp(colours.primary, GamePalette.Indigo, 0.35f) }
    Canvas(modifier = modifier) { drawShapeArt(shape, top, bottom) }
}

private fun DrawScope.drawShapeArt(shape: SmoothedShape, top: Color, bottom: Color) {
    val margin = 0.5f // room for the ring beyond the outline, in cells
    val k = min(size.width / (shape.width + margin * 2), size.height / (shape.height + margin * 2))
    if (k <= 0f) return
    val tx = (size.width - shape.width * k) / 2f - shape.minX * k
    val ty = (size.height - shape.height * k) / 2f - shape.minY * k
    withTransform({
        translate(tx, ty)
        scale(k, k, pivot = Offset.Zero)
    }) {
        val ring = Stroke(width = (INK_HALF + RING_HALF) * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ink = Stroke(width = INK_HALF * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(shape.path, Color.White, style = ring)
        drawPath(shape.path, GamePalette.Navy, style = ink)
        drawPath(
            shape.path,
            Brush.verticalGradient(
                0f to top,
                1f to bottom,
                startY = shape.minY,
                endY = shape.minY + shape.height
            )
        )
        // Gloss: a soft light from the top-left, clipped to the shape.
        clipPath(shape.path) {
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.42f),
                    0.5f to Color.Transparent,
                    start = Offset(shape.minX, shape.minY),
                    end = Offset(shape.minX + shape.width * 0.8f, shape.minY + shape.height * 0.8f)
                ),
                topLeft = Offset(shape.minX, shape.minY),
                size = Size(shape.width, shape.height)
            )
        }
        // Two twinkles, the collectables' own signature.
        sparkle(Offset(shape.minX + shape.width * 0.10f, shape.minY + shape.height * 0.12f), 0.30f)
        sparkle(Offset(shape.minX + shape.width * 0.92f, shape.minY + shape.height * 0.80f), 0.22f)
    }
}

private fun DrawScope.sparkle(at: Offset, reach: Float) {
    val w = 0.05f
    val colour = Color.White
    drawLine(colour, Offset(at.x - reach, at.y), Offset(at.x + reach, at.y), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(colour, Offset(at.x, at.y - reach), Offset(at.x, at.y + reach), strokeWidth = w, cap = StrokeCap.Round)
    drawCircle(colour, radius = w * 1.6f, center = at)
}
