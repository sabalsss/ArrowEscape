package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.sabalapps.arrowescape.game.GameStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * What happens between the last arrow leaving the board and the result arriving — and the one place
 * that sequences it.
 *
 * ## One clock
 *
 * A single linear [Animatable] counts milliseconds from the frame the last arrow launches. The
 * confirmation outline reads it in its draw phase, and [WinCelebration.phase] is derived from it
 * with the pure `CompletionFlow`, so nothing recomposes per frame and the sequence cannot disagree
 * with itself. Three cues hang off it, each fired exactly once per win from this one effect:
 *
 *  - **lock** (the outline closes): the small confirmation sound and haptic;
 *  - **hand-off**: the result layer may compose ([WinCelebration.resultVisible]);
 *  - **peak** (the reveal's emotional beat): the win stinger and the celebration haptic —
 *    [onPeak] is called here and nowhere else.
 *
 * Losing has no celebration: a loss never starts the clock, and its card shows the moment the board
 * resolves, exactly as before. A recomposition cannot restart a win: the effect is keyed on the win
 * itself and on the two inputs that cannot change during one (the kind and the motion setting).
 */

/**
 * Gold, teal, white and coral. The first two are the theme's own secondary and
 * tertiary at their dark-scheme brightness, which is what reads over both board
 * surfaces; the coral is the celebration's own, and is not an error colour.
 */
private val SPARK_COLOURS = listOf(
    Color(0xFFF2C063),
    Color(0xFF52DED0),
    Color(0xFFFFFFFF),
    Color(0xFFFF9E8A)
)

internal class Spark(
    val angle: Float,
    val distance: Float,
    val size: Float,
    val colour: Color,
    val confetti: Boolean,
    val spin: Float,
    /** 0-0.25 stagger, so the burst scatters rather than moving as one ring. */
    val delay: Float
)

/**
 * [count] sparks, scattered from [seed]. [palette] defaults to the celebration's
 * gold, teal, white and coral; the discovery reveal passes its world's own colours.
 */
internal fun sparks(seed: Int, count: Int, palette: List<Color> = SPARK_COLOURS): List<Spark> {
    val random = Random(seed)
    return List(count) {
        // Biased upward: a burst that throws everything sideways reads as an
        // explosion, and this is meant to read as applause.
        val angle = (random.nextFloat() * 2f - 1f) * 2.1f - 1.57f
        Spark(
            angle = angle,
            distance = 0.30f + random.nextFloat() * 0.26f,
            size = 2.5f + random.nextFloat() * 3.5f,
            colour = palette[random.nextInt(palette.size)],
            confetti = random.nextFloat() < 0.45f,
            spin = (random.nextFloat() * 2f - 1f) * 540f,
            delay = random.nextFloat() * 0.25f
        )
    }
}

/** What the board and the result read about the finish. */
class WinCelebration internal constructor(
    /** Milliseconds since the last arrow launched; 0 outside a win. Read in draw lambdas only. */
    val clock: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val schedule: ShapeConfirmSchedule,
    private val phaseState: State<CompletionPhase>,
    /** False until the confirmation has handed over and the result may take the screen. */
    resultReady: State<Boolean>
) {
    /** Where the finish is. Changes only a handful of times per win. */
    val phase: CompletionPhase by phaseState

    val resultVisible: Boolean by resultReady
}

/**
 * Drives the finish of one board and reports when the result may show.
 *
 * @param status the board's status. Only [GameStatus.WON] starts the sequence.
 * @param kind what the finish turns into; picks the confirmation's pace and where the peak lands.
 * @param reducedMotion a short fade to a still outline instead of the trace; sounds and haptics still fire.
 * @param onLock invoked once, as the outline locks in.
 * @param onPeak invoked once, on the reveal's peak beat: the caller plays `sfx_level_complete` and
 *   the celebration haptic here.
 */
@Composable
fun rememberWinCelebration(
    status: GameStatus,
    kind: CompletionKind,
    reducedMotion: Boolean,
    onLock: () -> Unit,
    onPeak: () -> Unit
): WinCelebration {
    val clock = remember { Animatable(0f) }
    val resultReady = remember { mutableStateOf(false) }
    val schedule = ShapeConfirmSchedule.forKind(kind, reducedMotion)
    val lock by rememberUpdatedState(onLock)
    val peak by rememberUpdatedState(onPeak)
    val currentStatus by rememberUpdatedState(status)
    val won = status == GameStatus.WON

    LaunchedEffect(won, kind, reducedMotion) {
        if (!won) {
            // A new board: stand everything down so the next win starts clean.
            clock.snapTo(0f)
            resultReady.value = false
            return@LaunchedEffect
        }
        resultReady.value = false
        clock.snapTo(0f)
        coroutineScope {
            launch {
                clock.animateTo(
                    targetValue = schedule.handoffMs.toFloat(),
                    animationSpec = tween(schedule.handoffMs, easing = LinearEasing)
                )
            }
            launch {
                delay(schedule.lockMs.toLong())
                lock()
            }
            launch {
                delay(schedule.handoffMs.toLong())
                resultReady.value = true
                delay((peakOverallMs(kind, reducedMotion) - schedule.handoffMs).coerceAtLeast(0L))
                peak()
            }
        }
    }

    val phase = remember(schedule) {
        derivedStateOf { CompletionFlow.phase(currentStatus, clock.value.toLong(), schedule) }
    }
    return remember(clock, schedule, phase) { WinCelebration(clock, schedule, phase, resultReady) }
}

internal fun DrawScope.drawBurst(sparks: List<Spark>, progress: Float) {
    val shortest = min(size.width, size.height)
    sparks.forEach { spark ->
        val local = ((progress - spark.delay) / (1f - spark.delay)).coerceIn(0f, 1f)
        if (local <= 0f) return@forEach
        // Ease out: fast off the mark, drifting by the end.
        val eased = 1f - (1f - local) * (1f - local) * (1f - local)
        val travel = spark.distance * shortest * eased
        // A little gravity, so the confetti falls away instead of hanging.
        val drop = shortest * 0.10f * local * local
        val position = Offset(
            x = center.x + cos(spark.angle) * travel,
            y = center.y + sin(spark.angle) * travel + drop
        )
        val alpha = if (local < 0.55f) 1f else 1f - (local - 0.55f) / 0.45f
        val scale = 1f - 0.35f * local
        val colour = spark.colour.copy(alpha = spark.colour.alpha * alpha.coerceIn(0f, 1f))
        if (spark.confetti) {
            val side = spark.size.dp.toPx() * scale * 1.6f
            rotate(degrees = spark.spin * local, pivot = position) {
                drawRect(
                    color = colour,
                    topLeft = Offset(position.x - side / 2f, position.y - side / 4f),
                    size = Size(side, side / 2f)
                )
            }
        } else {
            drawCircle(color = colour, radius = spark.size.dp.toPx() * scale, center = position)
        }
    }
}
