package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.startup.SplashChoreography
import com.sabalapps.arrowescape.startup.SplashProgress
import com.sabalapps.arrowescape.startup.StartupProgress
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The loading screen: four arrow discs fly in, spin into place around a gold star, ARROW /
 * ESCAPE settles beneath them, and a progress bar fills from 0% to 100% while the game's
 * real startup work finishes behind it. Then the emblem gives one small glow pulse and the
 * screen hands over to the game.
 *
 * It begins on exactly the navy the Android system splash is, so the hand-off from the system's
 * window to this one is invisible; the glow and the arrows come *up out of* that colour.
 *
 * ## What is real and what is smoothing
 *
 * [progress] is real: the startup coordinator's state. The bar and the number only *ease
 * towards* its target ([SplashProgress]) — they never jump with the tasks, never run ahead of
 * them, and never read 100% before the game can open. The only thing here that waits for the
 * sake of looking good is a floor of about a second on the fill, so the assembly can be seen
 * on a phone where startup takes 40ms.
 *
 * ## Accessibility
 *
 * One node: "Loading Arrow Escape". The percentage is not announced (1%, 2%, 3%… would be
 * unbearable), the arrows are decoration, and with animations off nothing flies — the
 * finished emblem fades in and the bar still fills.
 *
 * @param progress the real startup progress, read each frame.
 * @param onReady called once, the moment the bar is full — while the closing pulse plays.
 * @param onFinished called exactly once, when the bar is full, the assembly done and the glow
 *   pulse played (or, so nothing can hold the player here, [SplashProgress.HARD_CAP_MS] passed).
 */
@Composable
fun SplashScreen(
    progress: () -> StartupProgress,
    onReady: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val currentProgress by rememberUpdatedState(progress)
    val currentFinished by rememberUpdatedState(onFinished)
    val currentReady by rememberUpdatedState(onReady)

    // The clock and the shown values are plain state read only in draw / layer blocks, so a
    // frame repaints a Canvas and recomposes nothing. The one thing that does recompose is
    // the percentage, and only when the whole number changes.
    var clockMs by remember { mutableLongStateOf(0L) }
    var shown by remember { mutableFloatStateOf(0f) }
    var readyAtMs by remember { mutableLongStateOf(-1L) }
    var percent by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val model = SplashProgress()
        var last = withFrameNanos { it }
        val begin = last
        var finished = false
        while (!finished) {
            val now = withFrameNanos { it }
            // A long frame (a GC, a slow first composition) is clamped, so the animation
            // resumes where it was instead of jumping.
            val dt = ((now - last) / 1_000_000L).coerceIn(0L, 64L)
            last = now
            val real = (now - begin) / 1_000_000L
            model.advance(dt, currentProgress().target(real))
            clockMs = model.elapsedMs
            shown = model.shown
            if (model.percent != percent) percent = model.percent
            if (readyAtMs < 0L && SplashProgress.canFinish(model.shown, model.elapsedMs)) {
                readyAtMs = model.elapsedMs
                // The game can start composing underneath now, during the glow pulse, so the
                // screen it hands over to is already there when the loading screen leaves.
                currentReady()
            }
            if (readyAtMs >= 0L && model.elapsedMs >= readyAtMs + SplashProgress.PULSE_MS) finished = true
        }
        currentFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GamePalette.Navy)
            .clearAndSetSemantics { contentDescription = "Loading Arrow Escape" },
        contentAlignment = Alignment.Center
    ) {
        // The glow and the gradient come up out of the system splash's navy.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val up = if (reducedMotion) SplashChoreography.reducedProgress(clockMs)
                    else SplashChoreography.backgroundProgress(clockMs)
                    val pulse = pulseAt(clockMs, readyAtMs)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to GamePalette.NavyLift.copy(alpha = 0.85f * up)
                        )
                    )
                    val centre = Offset(size.width / 2f, size.height * 0.42f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to GamePalette.ElectricBlue.copy(alpha = (0.34f + 0.22f * pulse) * up),
                            1f to Color.Transparent,
                            center = centre,
                            radius = size.minDimension * (0.78f + 0.10f * pulse)
                        ),
                        radius = size.minDimension,
                        center = centre
                    )
                    drawSparkleDust(clockMs, up, reducedMotion)
                }
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(EMBLEM_SIZE)) {
                drawEmblem(clockMs, readyAtMs, reducedMotion)
            }
            Spacer(Modifier.height(10.dp))
            val titleSize = fixedSp(36.sp)
            // Both words together, stacked: a lock-up that cannot overflow a narrow phone
            // however large the player's font is set.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val t = if (reducedMotion) SplashChoreography.reducedProgress(clockMs)
                    else SplashChoreography.titleProgress(clockMs)
                    alpha = t
                    translationY = (1f - t) * 14.dp.toPx() * (if (reducedMotion) 0f else 1f)
                }
            ) {
                Text(
                    text = "ARROW",
                    style = AppText.brand.copy(fontSize = titleSize, lineHeight = titleSize * 1.05f, letterSpacing = 6.sp)
                        .onArt(),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "ESCAPE",
                    style = AppText.brand.copy(fontSize = titleSize, lineHeight = titleSize * 1.05f, letterSpacing = 6.sp)
                        .onArt(),
                    color = GamePalette.GoldLight,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(36.dp))
            LoadingBar(
                fraction = { shown },
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .widthIn(max = 260.dp)
                    .height(10.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$percent%",
                style = AppText.button.copy(
                    fontSize = fixedSp(15.sp),
                    color = Color.White.copy(alpha = 0.80f)
                ),
                maxLines = 1
            )
        }
    }
}

private val EMBLEM_SIZE = 236.dp

/**
 * [size] in sp, held at the size it is drawn at whatever the system font scale is. The
 * lock-up is a logo, not reading text; at 200% it would be wider than the screen.
 */
@Composable
private fun fixedSp(size: TextUnit): TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale <= 1f) size else (size.value / scale * min(scale, 1.15f)).sp
}

private fun pulseAt(clockMs: Long, readyAtMs: Long): Float =
    if (readyAtMs < 0L) 0f else SplashChoreography.pulse(clockMs - readyAtMs)

/** The progress bar: a track, a gradient fill with a lit leading edge. */
@Composable
private fun LoadingBar(fraction: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.16f), size = size, cornerRadius = radius)
        val width = size.width * fraction().coerceIn(0f, 1f)
        if (width <= 0f) return@Canvas
        val fill = Size(max(width, size.height), size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(GamePalette.TealDeep, GamePalette.Teal, GamePalette.Cyan),
                startX = 0f,
                endX = fill.width
            ),
            size = fill,
            cornerRadius = radius
        )
        // The sheen that makes it read as a raised, glossy bar like the game's buttons.
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.40f),
                0.55f to Color.Transparent
            ),
            size = fill,
            cornerRadius = radius
        )
    }
}

// ---------------------------------------------------------------------------------------
// The emblem
// ---------------------------------------------------------------------------------------

/** Up, right, down, left: the order the discs fly in. */
private val DISC_ANGLES = floatArrayOf(0f, 90f, 180f, 270f)

/** Where each disc starts, as a multiple of the emblem's radius from its centre. */
private val DISC_START = arrayOf(
    Offset(-1.15f, -1.30f),
    Offset(1.35f, -1.05f),
    Offset(1.10f, 1.30f),
    Offset(-1.30f, 1.10f)
)

/** How far each disc spins in, in degrees. */
private val DISC_SPIN = floatArrayOf(-150f, 140f, -130f, 160f)

private const val DISC_RADIUS = 0.33f
private const val DISC_REACH = 0.57f

private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

/** Eases out past the mark and settles back: the discs land with a little give. */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.25f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun DrawScope.drawEmblem(clockMs: Long, readyAtMs: Long, reducedMotion: Boolean) {
    val radius = min(size.width, size.height) / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val pulse = pulseAt(clockMs, readyAtMs)
    // The whole emblem breathes out a touch on the glow pulse (not when animations are off).
    val breathe = if (reducedMotion) 1f else 1f + 0.05f * pulse
    val fade = if (reducedMotion) SplashChoreography.reducedProgress(clockMs) else 1f

    scale(scale = breathe, pivot = centre) {
        // The star the discs gather around.
        val starT = if (reducedMotion) 1f else easeOutBack(SplashChoreography.centreProgress(clockMs))
        val starAlpha = if (reducedMotion) fade else SplashChoreography.centreProgress(clockMs)
        if (starAlpha > 0f) {
            drawStar(centre, radius * 0.17f * starT, GamePalette.GoldLight, starAlpha, pulse)
        }

        // The four small sparkles in the gaps.
        for (i in 0 until SplashChoreography.PIECES) {
            val t = if (reducedMotion) 1f else SplashChoreography.sparkleProgress(i, clockMs)
            val alpha = if (reducedMotion) fade else t
            if (alpha <= 0f) continue
            val angle = (45f + 90f * i) * (PI.toFloat() / 180f)
            val at = Offset(
                centre.x + kotlin.math.cos(angle) * radius * 0.80f,
                centre.y + kotlin.math.sin(angle) * radius * 0.80f
            )
            val twinkle = if (reducedMotion) 1f else 0.75f + 0.25f * sin((clockMs / 140f) + i * 1.7f)
            drawStar(at, radius * 0.075f * easeOutCubic(t) * twinkle, GamePalette.Teal, alpha, pulse)
        }

        // The four arrow discs.
        for (i in 0 until SplashChoreography.PIECES) {
            val t = if (reducedMotion) 1f else SplashChoreography.pieceProgress(i, clockMs)
            val alpha = if (reducedMotion) fade else min(1f, t * 3f)
            if (alpha <= 0f) continue

            val direction = Math.toRadians(DISC_ANGLES[i].toDouble() - 90.0)
            val home = Offset(
                centre.x + kotlin.math.cos(direction).toFloat() * radius * DISC_REACH,
                centre.y + kotlin.math.sin(direction).toFloat() * radius * DISC_REACH
            )
            val start = Offset(centre.x + DISC_START[i].x * radius, centre.y + DISC_START[i].y * radius)
            val travelled = easeOutBack(t)
            val at = Offset(lerp(start.x, home.x, travelled), lerp(start.y, home.y, travelled))
            val spin = DISC_SPIN[i] * (1f - easeOutCubic(t))
            val size = lerp(0.55f, 1f, easeOutCubic(t))
            drawArrowDisc(
                centre = at,
                radius = radius * DISC_RADIUS * size,
                arrowDegrees = DISC_ANGLES[i],
                spinDegrees = spin,
                alpha = alpha,
                glow = pulse
            )
        }
    }
}

/** A glossy blue disc with a white arrow, in the game's own mark. */
private fun DrawScope.drawArrowDisc(
    centre: Offset,
    radius: Float,
    arrowDegrees: Float,
    spinDegrees: Float,
    alpha: Float,
    glow: Float
) {
    // The soft blue glow under it.
    drawCircle(
        brush = Brush.radialGradient(
            0f to GamePalette.ElectricBlue.copy(alpha = (0.50f + 0.25f * glow) * alpha),
            1f to Color.Transparent,
            center = centre,
            radius = radius * 1.7f
        ),
        radius = radius * 1.7f,
        center = centre
    )
    rotate(degrees = spinDegrees, pivot = centre) {
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(GamePalette.BlueTop.copy(alpha = alpha), GamePalette.BlueBottom.copy(alpha = alpha)),
                startY = centre.y - radius,
                endY = centre.y + radius
            ),
            radius = radius,
            center = centre
        )
        drawCircle(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.32f * alpha),
                0.5f to Color.Transparent,
                startY = centre.y - radius,
                endY = centre.y + radius
            ),
            radius = radius,
            center = centre
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.75f * alpha),
            radius = radius,
            center = centre,
            style = Stroke(width = radius * 0.06f)
        )
        rotate(degrees = arrowDegrees, pivot = centre) {
            drawArrowStroke(centre, radius * 1.04f, Color.White.copy(alpha = alpha), GamePalette.Navy.copy(alpha = 0.30f * alpha))
        }
    }
}

/** The game's up-arrow (the same unit geometry as [ArrowGlyph]) centred on [centre], [side] across. */
private fun DrawScope.drawArrowStroke(centre: Offset, side: Float, color: Color, shadow: Color) {
    val left = centre.x - side / 2f
    val top = centre.y - side / 2f
    fun at(x: Float, y: Float) = Offset(left + x * side, top + y * side)
    val path = Path().apply {
        val a = at(0.5f, 0.80f)
        val b = at(0.5f, 0.30f)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        val h0 = at(0.24f, 0.545f)
        val h1 = at(0.5f, 0.265f)
        val h2 = at(0.76f, 0.545f)
        moveTo(h0.x, h0.y)
        lineTo(h1.x, h1.y)
        lineTo(h2.x, h2.y)
    }
    val stroke = Stroke(width = side * 0.145f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    translate(top = side * 0.045f) { drawPath(path, shadow, style = stroke) }
    drawPath(path, color, style = stroke)
}

/** A four-point sparkle: two crossed diamonds, with a soft halo. */
private fun DrawScope.drawStar(centre: Offset, radius: Float, color: Color, alpha: Float, glow: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = (0.55f + 0.25f * glow) * alpha),
            1f to Color.Transparent,
            center = centre,
            radius = radius * 3.2f
        ),
        radius = radius * 3.2f,
        center = centre
    )
    val inner = radius * 0.32f
    val path = Path().apply {
        moveTo(centre.x, centre.y - radius)
        lineTo(centre.x + inner, centre.y - inner)
        lineTo(centre.x + radius, centre.y)
        lineTo(centre.x + inner, centre.y + inner)
        lineTo(centre.x, centre.y + radius)
        lineTo(centre.x - inner, centre.y + inner)
        lineTo(centre.x - radius, centre.y)
        lineTo(centre.x - inner, centre.y - inner)
        close()
    }
    drawPath(path, Color.White.copy(alpha = alpha))
    drawPath(path, color.copy(alpha = 0.55f * alpha), style = Stroke(width = radius * 0.10f, join = StrokeJoin.Round))
}

/** A few faint motes, so the navy is not a flat field: positions fixed, only the twinkle moves. */
private fun DrawScope.drawSparkleDust(clockMs: Long, up: Float, reducedMotion: Boolean) {
    for ((i, mote) in DUST.withIndex()) {
        val twinkle = if (reducedMotion) 0.6f else 0.5f + 0.5f * sin(clockMs / 260f + i * 2.1f)
        drawCircle(
            color = Color.White.copy(alpha = mote.alpha * twinkle * up),
            radius = mote.radius.dp.toPx(),
            center = Offset(size.width * mote.x, size.height * mote.y)
        )
    }
}

private class Mote(val x: Float, val y: Float, val radius: Float, val alpha: Float)

private val DUST = listOf(
    Mote(0.12f, 0.16f, 1.6f, 0.55f),
    Mote(0.86f, 0.12f, 1.2f, 0.45f),
    Mote(0.24f, 0.30f, 1.0f, 0.40f),
    Mote(0.78f, 0.34f, 1.8f, 0.50f),
    Mote(0.08f, 0.52f, 1.2f, 0.35f),
    Mote(0.92f, 0.58f, 1.4f, 0.45f),
    Mote(0.18f, 0.80f, 1.6f, 0.45f),
    Mote(0.70f, 0.86f, 1.2f, 0.40f),
    Mote(0.46f, 0.07f, 1.2f, 0.35f)
)
