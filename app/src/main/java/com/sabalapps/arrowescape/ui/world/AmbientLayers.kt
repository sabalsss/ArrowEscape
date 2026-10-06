package com.sabalapps.arrowescape.ui.world

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * The drawing half of the ambient background system: the one animation that
 * drives it, the artwork's motion, the soft drifting washes and the particle
 * field.
 *
 * [WorldAmbience] says *what* each world does; this file is *how*, once, for all
 * five. Nothing here knows which world it is painting.
 *
 * ## Where the cost is, and is not
 *
 * - **One animation for the whole screen.** [rememberAmbiencePhase] is a single
 *   `Float` ramping 0→1 over 48s. Zoom, drift, wander and twinkle are all closed
 *   -form functions of it.
 * - **No recomposition per frame.** The phase is only ever read inside a
 *   `graphicsLayer` block or a `Canvas` draw lambda, so a frame re-runs a layer
 *   update and a draw pass and stops there. No composable re-executes, no state
 *   is hoisted into the composition, and nothing invalidates layout.
 * - **No per-frame allocation.** The drift brushes are built once per world and
 *   then merely transformed; the particles are expanded once per world
 *   ([ambientMotes]) and then merely read. A frame of ambience allocates nothing.
 * - **No blur, no shader, no bitmap work.** Softness comes from gradients that
 *   already exist and from stacked translucent circles. The WebP is decoded once
 *   by `painterResource`, exactly as in Phase 6A, and is never regenerated,
 *   re-scaled on the CPU or copied.
 *
 * Drawn per frame, worst case (Cosmic, 22 points, no halos): 22 `drawCircle`
 * calls plus one GPU-composited image layer and one gradient layer.
 */

/** 2π, as a Float. Every loop in this file is a sine of a multiple of it. */
private const val TAU: Float = 6.2831855f

/**
 * Keeps a [Twinkle.BREATH] particle from going fully dark at the bottom of its
 * cycle: it should read as *breathing*, not blinking. `0.60 ± 0.40` leaves a
 * particle at 20% of its base alpha at its dimmest.
 */
private const val BREATH_FLOOR = 0.60f
private const val BREATH_SWING = 0.40f

/**
 * A [Twinkle.SPARKLE] particle's floor, and how sharply its one peak per cycle
 * rises. The sixth power is what makes the bright part short — a sparkle is an
 * event, and at these counts anything wider reads as a pulsing light.
 */
private const val SPARKLE_FLOOR = 0.06f

/** Halo radii and strengths for a `glow` particle. Two circles, no blur. */
private const val HALO_OUTER_SCALE = 3.4f
private const val HALO_INNER_SCALE = 1.9f
private const val HALO_OUTER_ALPHA = 0.16f
private const val HALO_INNER_ALPHA = 0.30f

/**
 * The single animated value the whole ambient system runs off: 0→1, linear, over
 * [WorldAmbience.AMBIENCE_CYCLE_MS], restarting forever.
 *
 * Returned as a [State] rather than a `Float` on purpose — callers read `.value`
 * inside a layer or draw lambda, which is what keeps the animation out of the
 * composition. It stops the moment the background leaves composition, so only
 * the world actually on screen is ever animating, and the other four are not
 * even decoded.
 */
@Composable
internal fun rememberAmbiencePhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "worldAmbience")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Linear and restarting: the phase must advance at a constant rate
            // and wrap cleanly, because everything downstream is periodic in it.
            animation = tween(
                durationMillis = WorldAmbience.AMBIENCE_CYCLE_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "worldAmbiencePhase"
    )
}

/**
 * The artwork's slow swell and drift, as a `graphicsLayer` on the `Image`.
 *
 * A layer transform, not a layout change: the image's bounds never move, so
 * nothing re-measures and nothing else on screen can be affected by this. The
 * drift stays inside the slack the zoom creates — see the invariant on
 * [ImageMotion] — so the artwork's edge never walks into frame.
 */
internal fun Modifier.ambientImageMotion(
    motion: ImageMotion,
    phase: State<Float>
): Modifier = this.graphicsLayer {
    val p = phase.value
    // 0 at the start of each swell, 1 at its top: a cosine, so the turnarounds
    // ease themselves and the breath has no corners in it.
    val swell = 0.5f - 0.5f * cos(TAU * p * motion.zoomCycles)
    val zoom = motion.zoomFrom + (motion.zoomTo - motion.zoomFrom) * swell
    scaleX = zoom
    scaleY = zoom
    translationX = sin(TAU * p * motion.driftXCycles) * motion.driftX * size.width
    translationY = cos(TAU * p * motion.driftYCycles) * motion.driftY * size.height
}

/**
 * One soft translucent wash — cloud, mist, haze or nebula — drifting a slow
 * circle around its resting place.
 *
 * The brush is built once per [drift] and never again; every frame after that is
 * a GPU transform of a layer that already exists. That is why this is the layer
 * the expensive-sounding effects are built out of: there is no cheaper way to
 * move a soft shape than to not redraw it.
 *
 * @param modifier must size the layer to the whole background, normally
 *   `Modifier.matchParentSize()`.
 */
@Composable
internal fun AmbientDriftLayer(
    drift: AmbientDrift,
    phase: State<Float>,
    modifier: Modifier = Modifier
) {
    val brush = remember(drift) {
        Brush.radialGradient(
            0.00f to drift.colour.copy(alpha = drift.alpha),
            0.55f to drift.colour.copy(alpha = drift.alpha * 0.42f),
            1.00f to Color.Transparent
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                val a = TAU * (phase.value * drift.cycles + drift.phase)
                // `Brush.radialGradient`'s default radius is half the shorter
                // side, so the blob's natural diameter is one screen width in
                // portrait. Both scales are corrected for that, which is what
                // makes `width`/`height` honestly mean "fraction of the screen"
                // on a 16:9 and a 21:9 phone alike.
                val natural = size.minDimension
                val breathe = drift.breathe
                scaleX = drift.width * (size.width / natural) *
                    (1f + breathe * sin(a * 2f))
                scaleY = drift.height * (size.height / natural) *
                    (1f + breathe * cos(a * 2f))
                // sin across, cos down: a circle, so it returns to where it
                // started without ever reversing direction.
                translationX = ((drift.centreX - 0.5f) + drift.travelX * sin(a)) * size.width
                translationY = ((drift.centreY - 0.5f) + drift.travelY * cos(a)) * size.height
            }
            .background(brush)
    )
}

/**
 * The ambient particle field: every mote, firefly, dust speck and star for one
 * world, on one `Canvas`.
 *
 * Returns without composing anything when the world has no particles, so a
 * future still world costs nothing rather than an empty layer.
 */
@Composable
internal fun AmbientParticleLayer(
    motes: List<AmbientMote>,
    phase: State<Float>,
    modifier: Modifier = Modifier
) {
    if (motes.isEmpty()) return
    Canvas(modifier = modifier) {
        drawAmbientMotes(motes, phase.value)
    }
}

/**
 * One ambient particle, fully determined before the first frame.
 *
 * There is no velocity, no lifetime and no integration step here: a mote's
 * position at any moment is `home + wander · sin(2π · cycles · phase + offset)`,
 * evaluated fresh each frame from the master phase. Nothing accumulates, so
 * nothing can drift out of band, desynchronise or need resetting — and because
 * `cycles` is a whole number, the field is exactly where it began every time the
 * phase wraps.
 */
internal class AmbientMote(
    val homeX: Float,
    val homeY: Float,
    val radiusDp: Float,
    val baseAlpha: Float,
    val colour: Color,
    val wanderX: Float,
    val wanderY: Float,
    val cyclesX: Int,
    val cyclesY: Int,
    val phaseX: Float,
    val phaseY: Float,
    val twinkle: Twinkle,
    val twinkleCycles: Int,
    val twinklePhase: Float,
    val glow: Boolean
)

/**
 * Expands a world's particle recipes into the fixed field that will be drawn.
 *
 * Called once per world — `remember(world)` in [WorldBackground] — and never
 * during a frame.
 *
 * The seed is derived from the world's stable [GameWorld.id] rather than its
 * ordinal, so reordering the enum cannot reshuffle the sky, and from `String`'s
 * *specified* `hashCode`, so the same world looks the same on every device and
 * every future runtime. The practical effect is that leaving a board and coming
 * back — or being killed and restored — brings back the same arrangement of
 * motes rather than a new one, which is the difference between a place and a
 * screensaver.
 */
internal fun ambientMotes(world: GameWorld, ambience: WorldAmbience): List<AmbientMote> {
    val motes = ArrayList<AmbientMote>(ambience.particles.sumOf { it.count })
    ambience.particles.forEachIndexed { index, spec ->
        val random = Random(world.id.hashCode() * 31 + index)
        repeat(spec.count) {
            motes += AmbientMote(
                // Kept off the extreme edges, where a point is half-clipped and
                // reads as a rendering fault rather than as a mote.
                homeX = random.between(0.04f, 0.96f),
                homeY = random.between(spec.band.start, spec.band.endInclusive),
                radiusDp = random.between(spec.radiusDp.start, spec.radiusDp.endInclusive),
                baseAlpha = random.between(spec.alpha.start, spec.alpha.endInclusive),
                colour = spec.colours[random.nextInt(spec.colours.size)],
                wanderX = spec.wander * random.between(0.55f, 1.00f),
                wanderY = spec.wander * random.between(0.40f, 0.85f),
                // 1-3 oscillations per master cycle: 48s, 24s or 16s to wander
                // out and back. Different counts on the two axes trace a figure
                // rather than a line.
                cyclesX = 1 + random.nextInt(3),
                cyclesY = 1 + random.nextInt(3),
                phaseX = random.nextFloat(),
                phaseY = random.nextFloat(),
                twinkle = spec.twinkle,
                twinkleCycles = spec.twinkleCycles.random(random),
                twinklePhase = random.nextFloat(),
                glow = spec.glow
            )
        }
    }
    return motes
}

/**
 * Draws the field for one frame. Circles only — no paths, no gradients, no
 * saved layers, no allocation.
 */
private fun DrawScope.drawAmbientMotes(motes: List<AmbientMote>, phase: Float) {
    // `wander` is a fraction of width on both axes, so the vertical amplitude is
    // converted here. A mote therefore wanders a round shape rather than a tall
    // ellipse, on any aspect ratio.
    val aspect = size.width / size.height
    motes.forEach { mote ->
        val alpha = mote.baseAlpha * mote.brightness(phase)
        // Below this a circle contributes nothing a screen can show; skipping it
        // is what makes a field of mostly-dark sparkles free.
        if (alpha <= 0.004f) return@forEach

        val centre = Offset(
            x = (mote.homeX +
                mote.wanderX * sin(TAU * (phase * mote.cyclesX + mote.phaseX))) * size.width,
            y = (mote.homeY +
                mote.wanderY * aspect *
                sin(TAU * (phase * mote.cyclesY + mote.phaseY))) * size.height
        )
        val radius = mote.radiusDp.dp.toPx()

        if (mote.glow) {
            // Two concentric washes stand in for a blur. Cheap, and at this
            // alpha indistinguishable from one.
            drawCircle(
                mote.colour.copy(alpha = alpha * HALO_OUTER_ALPHA),
                radius * HALO_OUTER_SCALE,
                centre
            )
            drawCircle(
                mote.colour.copy(alpha = alpha * HALO_INNER_ALPHA),
                radius * HALO_INNER_SCALE,
                centre
            )
        }
        drawCircle(mote.colour.copy(alpha = alpha), radius, centre)
    }
}

/** This mote's 0-1 brightness multiplier at [phase]. */
private fun AmbientMote.brightness(phase: Float): Float = when (twinkle) {
    Twinkle.NONE -> 1f

    Twinkle.BREATH ->
        BREATH_FLOOR + BREATH_SWING * sin(TAU * (phase * twinkleCycles + twinklePhase))

    Twinkle.SPARKLE -> {
        // A sawtooth folded into a triangle, then raised to the sixth: dark for
        // most of the cycle with one brief, soft peak. No `Random` per frame and
        // no state — the same sparkle at the same moment, every time.
        val t = phase * twinkleCycles + twinklePhase
        val triangle = 1f - abs((t - floor(t)) - 0.5f) * 2f
        val sharp = triangle * triangle * triangle
        SPARKLE_FLOOR + (1f - SPARKLE_FLOOR) * sharp * sharp
    }
}

/** A uniform Float in `[from, to)`. Only ever called while precomputing a field. */
private fun Random.between(from: Float, to: Float): Float = from + nextFloat() * (to - from)
