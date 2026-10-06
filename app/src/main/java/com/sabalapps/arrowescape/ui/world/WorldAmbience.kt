package com.sabalapps.arrowescape.ui.world

import androidx.compose.ui.graphics.Color

/**
 * How a [GameWorld] *moves*: the recipe for the slow, atmospheric life behind a
 * board.
 *
 * This is the animated counterpart of [WorldStyle] and it is built the same way —
 * **data, not drawing code.** Five rows of numbers here, one renderer in
 * [WorldBackground]/`AmbientLayers.kt`, so a world's ambience is a handful of
 * amplitudes and counts rather than a screen of its own. A sixth world would be
 * another row.
 *
 * ## Everything runs off one phase
 *
 * The whole system is driven by a *single* animated float that ramps 0→1 linearly
 * over [AMBIENCE_CYCLE_MS] and restarts. Every motion in it — the artwork's zoom
 * and drift, the atmosphere blobs, every particle's wander and twinkle — is a
 * sine (or a wrapped sawtooth) of that phase multiplied by an **integer** number
 * of cycles. Two consequences, both deliberate:
 *
 * - **It loops with no pop.** At the wrap from 1 back to 0 every `sin(2π·n·phase)`
 *   is exactly where it started, because `n` is a whole number. Nothing has to
 *   cross-fade and nothing jumps.
 * - **It costs one animation.** Not one per world, per layer or per particle.
 *   The phase is read inside `graphicsLayer`/`Canvas` lambdas, so a frame of
 *   ambience never recomposes anything — it re-runs a layer block and a draw
 *   pass, which is what those phases are for.
 *
 * The cycle is long (48s) so the scene's exact repeat is far outside the span a
 * player would notice, while the individual cycle counts keep each element
 * moving on its own, much shorter beat.
 *
 * ## Why it is this restrained
 *
 * This is ambience, not gameplay: it exists to stop the artwork reading as a
 * screenshot. The board, the arrows, the HUD and every readable surface are
 * stationary by construction — none of them is inside the background — and
 * nothing here responds to a tap, so there is no parallax to fight the grid.
 * Amplitudes are therefore chosen to sit just at the edge of perception: the
 * player should notice the scene is alive only if they stop and look for it.
 */
data class WorldAmbience(
    /** How the WebP itself breathes and drifts. */
    val motion: ImageMotion,
    /** Soft translucent washes that slide across the scene. Cheapest layer there is. */
    val drifts: List<AmbientDrift>,
    /** Precomputed point particles. One or two sets per world, never more. */
    val particles: List<AmbientParticles>
) {
    companion object {

        /**
         * The ambience for [world]. A plain lookup, like [WorldStyle.of] — no
         * state, no context, no allocation beyond the data itself.
         */
        fun of(world: GameWorld): WorldAmbience = when (world) {

            // ----------------------------------------------------------------
            // Bright, peaceful, airy. The most movement of the five, because
            // the artwork is sky and sky is the one subject that reads as wrong
            // when it is perfectly still.
            // ----------------------------------------------------------------
            GameWorld.SKY_GARDEN -> WorldAmbience(
                motion = ImageMotion(
                    zoomFrom = 1.035f,
                    zoomTo = 1.075f,
                    zoomCycles = 2,
                    driftX = 0.012f,
                    driftY = 0.006f,
                    driftXCycles = 1,
                    driftYCycles = 2
                ),
                drifts = listOf(
                    // Two cloud-like washes across the upper half, moving at
                    // different speeds in opposite directions — which is what
                    // reads as cloud rather than as the whole image sliding.
                    AmbientDrift(
                        colour = Color.White,
                        alpha = 0.075f,
                        centreX = 0.42f, centreY = 0.26f,
                        width = 1.35f, height = 0.34f,
                        travelX = 0.085f, travelY = 0.012f,
                        cycles = 1, phase = 0.00f, breathe = 0.05f
                    ),
                    AmbientDrift(
                        colour = Color(0xFFDCEEFF),
                        alpha = 0.060f,
                        centreX = 0.62f, centreY = 0.44f,
                        width = 1.10f, height = 0.26f,
                        travelX = 0.070f, travelY = 0.010f,
                        cycles = 2, phase = 0.55f, breathe = 0.06f
                    )
                ),
                particles = listOf(
                    AmbientParticles(
                        count = 12,
                        colours = listOf(Color.White, Color(0xFFCFE8FF), Color(0xFFEAF6FF)),
                        radiusDp = 1.3f..2.9f,
                        alpha = 0.14f..0.30f,
                        band = 0.06f..0.78f,
                        wander = 0.055f,
                        twinkle = Twinkle.BREATH,
                        twinkleCycles = 4..9
                    ),
                    // The "occasional soft sparkle": five points that are dark
                    // nearly all the time and brief when they are not.
                    AmbientParticles(
                        count = 5,
                        colours = listOf(Color.White),
                        radiusDp = 1.0f..1.8f,
                        alpha = 0.34f..0.55f,
                        band = 0.10f..0.70f,
                        wander = 0.018f,
                        twinkle = Twinkle.SPARKLE,
                        twinkleCycles = 2..4
                    )
                )
            )

            // ----------------------------------------------------------------
            // Calm, magical forest. Mist low, fireflies in the lower half where
            // undergrowth is — never a storm of leaves across the puzzle.
            // ----------------------------------------------------------------
            GameWorld.FOREST -> WorldAmbience(
                motion = ImageMotion(
                    zoomFrom = 1.035f,
                    zoomTo = 1.065f,
                    zoomCycles = 2,
                    driftX = 0.008f,
                    driftY = 0.008f,
                    driftXCycles = 1,
                    driftYCycles = 3
                ),
                drifts = listOf(
                    AmbientDrift(
                        colour = Color(0xFFBFE3D2),
                        alpha = 0.085f,
                        centreX = 0.45f, centreY = 0.70f,
                        width = 1.40f, height = 0.30f,
                        travelX = 0.075f, travelY = 0.008f,
                        cycles = 1, phase = 0.20f, breathe = 0.07f
                    ),
                    AmbientDrift(
                        colour = Color(0xFFD8EFE4),
                        alpha = 0.060f,
                        centreX = 0.55f, centreY = 0.88f,
                        width = 1.25f, height = 0.22f,
                        travelX = 0.060f, travelY = 0.010f,
                        cycles = 2, phase = 0.70f, breathe = 0.05f
                    )
                ),
                particles = listOf(
                    // Fireflies: the only particles in the game that get a halo,
                    // and the only ones allowed to go nearly dark and back.
                    AmbientParticles(
                        count = 10,
                        colours = listOf(Color(0xFFBFF08A), Color(0xFFE8F5A0), Color(0xFFD6F2B4)),
                        radiusDp = 1.4f..2.5f,
                        alpha = 0.22f..0.44f,
                        band = 0.44f..0.94f,
                        wander = 0.070f,
                        twinkle = Twinkle.BREATH,
                        twinkleCycles = 3..7,
                        glow = true
                    ),
                    AmbientParticles(
                        count = 6,
                        colours = listOf(Color(0xFFDFF0DA), Color.White),
                        radiusDp = 1.0f..2.0f,
                        alpha = 0.10f..0.20f,
                        band = 0.14f..0.68f,
                        wander = 0.045f,
                        twinkle = Twinkle.BREATH,
                        twinkleCycles = 5..10
                    )
                )
            )

            // ----------------------------------------------------------------
            // Warm, dry, adventurous. The "heat haze" is faked: two warm
            // translucent blobs that slide and breathe slightly out of step.
            // A real distortion shader would cost more than the rest of the
            // screen put together and buy nothing at this amplitude.
            // ----------------------------------------------------------------
            GameWorld.SUNSET_CANYON -> WorldAmbience(
                motion = ImageMotion(
                    zoomFrom = 1.040f,
                    zoomTo = 1.080f,
                    zoomCycles = 3,
                    driftX = 0.010f,
                    driftY = 0.005f,
                    driftXCycles = 2,
                    driftYCycles = 1
                ),
                drifts = listOf(
                    AmbientDrift(
                        colour = Color(0xFFFFB070),
                        alpha = 0.075f,
                        centreX = 0.48f, centreY = 0.62f,
                        width = 1.45f, height = 0.40f,
                        travelX = 0.055f, travelY = 0.014f,
                        // The larger breathe of the five: a slow swell is what
                        // sells rising heat without distorting a single pixel.
                        cycles = 2, phase = 0.10f, breathe = 0.12f
                    ),
                    AmbientDrift(
                        colour = Color(0xFFFFD9A8),
                        alpha = 0.055f,
                        centreX = 0.52f, centreY = 0.86f,
                        width = 1.30f, height = 0.28f,
                        travelX = 0.050f, travelY = 0.012f,
                        cycles = 3, phase = 0.62f, breathe = 0.10f
                    )
                ),
                particles = listOf(
                    // Dust, not motes: many more, far smaller, far dimmer, and
                    // barely twinkling. It should read as air, not as lights.
                    AmbientParticles(
                        count = 16,
                        colours = listOf(Color(0xFFFFE2BF), Color(0xFFFFD0A0), Color.White),
                        radiusDp = 0.8f..1.7f,
                        alpha = 0.09f..0.22f,
                        band = 0.18f..0.96f,
                        wander = 0.060f,
                        twinkle = Twinkle.BREATH,
                        twinkleCycles = 6..12
                    )
                )
            )

            // ----------------------------------------------------------------
            // Magical, deep, premium. One slow violet wash and a sparse field of
            // motes. The crystals in the artwork deliberately do **not** pulse:
            // five glowing forms breathing together would turn a calm scene into
            // a slot machine.
            // ----------------------------------------------------------------
            GameWorld.CRYSTAL_NIGHT -> WorldAmbience(
                motion = ImageMotion(
                    zoomFrom = 1.035f,
                    zoomTo = 1.060f,
                    zoomCycles = 2,
                    driftX = 0.007f,
                    driftY = 0.007f,
                    driftXCycles = 1,
                    driftYCycles = 2
                ),
                drifts = listOf(
                    AmbientDrift(
                        colour = Color(0xFF8E7BE8),
                        alpha = 0.085f,
                        centreX = 0.50f, centreY = 0.58f,
                        width = 1.30f, height = 0.52f,
                        travelX = 0.045f, travelY = 0.018f,
                        cycles = 1, phase = 0.35f, breathe = 0.08f
                    )
                ),
                particles = listOf(
                    AmbientParticles(
                        count = 12,
                        colours = listOf(Color(0xFFCFC4FF), Color.White, Color(0xFFA8E8FF)),
                        radiusDp = 1.2f..2.6f,
                        alpha = 0.16f..0.34f,
                        band = 0.08f..0.94f,
                        wander = 0.050f,
                        twinkle = Twinkle.BREATH,
                        twinkleCycles = 4..9,
                        glow = true
                    ),
                    AmbientParticles(
                        count = 5,
                        colours = listOf(Color.White, Color(0xFFE2DBFF)),
                        radiusDp = 0.9f..1.6f,
                        alpha = 0.34f..0.58f,
                        band = 0.12f..0.88f,
                        wander = 0.015f,
                        twinkle = Twinkle.SPARKLE,
                        twinkleCycles = 2..4
                    )
                )
            )

            // ----------------------------------------------------------------
            // Deep, expansive, the final world. The most particles and the least
            // of everything else: stars read as still, so the nebula barely
            // moves and the zoom is the gentlest of the five. Stars twinkle by
            // breathing, never by flashing.
            // ----------------------------------------------------------------
            GameWorld.COSMIC -> WorldAmbience(
                motion = ImageMotion(
                    zoomFrom = 1.035f,
                    zoomTo = 1.055f,
                    zoomCycles = 2,
                    driftX = 0.006f,
                    driftY = 0.008f,
                    driftXCycles = 1,
                    driftYCycles = 2
                ),
                drifts = listOf(
                    AmbientDrift(
                        colour = Color(0xFF7E6BE0),
                        alpha = 0.065f,
                        centreX = 0.50f, centreY = 0.46f,
                        width = 1.40f, height = 0.66f,
                        travelX = 0.035f, travelY = 0.016f,
                        cycles = 1, phase = 0.15f, breathe = 0.06f
                    )
                ),
                particles = listOf(
                    AmbientParticles(
                        count = 18,
                        colours = listOf(Color.White, Color(0xFFD6DCFF), Color(0xFFBFD8FF)),
                        radiusDp = 0.7f..1.5f,
                        alpha = 0.20f..0.46f,
                        // Stars go everywhere, including behind the board, where
                        // the board's own 0.90-alpha surface all but erases them.
                        band = 0.02f..0.98f,
                        wander = 0.022f,
                        twinkle = Twinkle.BREATH,
                        twinkleCycles = 5..12
                    ),
                    AmbientParticles(
                        count = 4,
                        colours = listOf(Color.White),
                        radiusDp = 0.8f..1.4f,
                        alpha = 0.34f..0.52f,
                        band = 0.06f..0.92f,
                        wander = 0.010f,
                        twinkle = Twinkle.SPARKLE,
                        twinkleCycles = 2..3
                    )
                )
            )
        }

        /**
         * The master cycle. Everything in the system is an integer number of
         * cycles of this, so the scene is exactly periodic over it — and 48s is
         * long enough that the repeat is not a thing a player can hold in mind.
         */
        const val AMBIENCE_CYCLE_MS: Int = 48_000
    }
}

/**
 * The artwork's own motion: a slow swell plus a drift of a few tenths of a
 * percent of the screen.
 *
 * ## The one invariant
 *
 * `ContentScale.Crop` already makes the WebP cover the screen exactly, so the
 * *only* slack available to translate into is the slack the zoom creates. At
 * scale `z` that is `(z - 1) / 2` of each dimension, so
 *
 * ```
 * driftX < (zoomFrom - 1) / 2   and   driftY < (zoomFrom - 1) / 2
 * ```
 *
 * must hold — against `zoomFrom`, the *smallest* scale in the cycle, not the
 * largest. Every row above satisfies it with room to spare (the tightest is Sky
 * Garden: 0.012 against a 0.0175 budget). Break it and the artwork's edge walks
 * into frame at the bottom of the zoom.
 *
 * @property zoomFrom the scale at the bottom of the swell. Never below ~1.03, or
 *   there is no slack to drift in.
 * @property zoomTo the scale at the top of it.
 * @property zoomCycles how many full swells per [WorldAmbience.AMBIENCE_CYCLE_MS].
 *   2 is a 24s breath, 3 a 16s one.
 * @property driftX horizontal travel, as a fraction of screen *width*.
 * @property driftY vertical travel, as a fraction of screen *height*.
 * @property driftXCycles horizontal oscillations per master cycle.
 * @property driftYCycles vertical oscillations per master cycle. Deliberately
 *   different from [driftXCycles] in every world, so the image wanders a figure
 *   rather than sliding up and down a line.
 */
data class ImageMotion(
    val zoomFrom: Float,
    val zoomTo: Float,
    val zoomCycles: Int,
    val driftX: Float,
    val driftY: Float,
    val driftXCycles: Int,
    val driftYCycles: Int
)

/**
 * A soft translucent blob that slides across the scene: cloud, mist, haze or
 * nebula depending on its colour and where it sits.
 *
 * Rendered as **one `Box` with a static radial-gradient background, moved by a
 * `graphicsLayer`** — so the brush is built once at composition and every frame
 * after that is a GPU transform of an existing layer. No per-frame allocation,
 * no `Canvas` arithmetic, and no runtime blur: the gradient *is* the soft edge.
 *
 * @property alpha strength at the blob's centre. All five worlds sit under 0.09;
 *   this layer is meant to be felt rather than seen.
 * @property centreX where it rests, as a fraction of the screen. 0.5 is centred.
 * @property centreY the same vertically.
 * @property width its size as a multiple of screen width (so > 1 is wider than
 *   the screen, which is how a band gets soft ends off-frame).
 * @property height the same as a multiple of screen height.
 * @property travelX how far it slides either side of [centreX], as a fraction of
 *   screen width.
 * @property travelY the same vertically.
 * @property cycles laps per master cycle. The drift is circular — `sin` across,
 *   `cos` down — so it returns to where it began without reversing.
 * @property breathe how much it swells over the lap, as a fraction of its size.
 *   This is the whole "heat haze" trick in Sunset Canyon.
 * @property phase 0-1 offset into the master cycle, so two blobs in one world
 *   are never in step.
 */
data class AmbientDrift(
    val colour: Color,
    val alpha: Float,
    val centreX: Float,
    val centreY: Float,
    val width: Float,
    val height: Float,
    val travelX: Float,
    val travelY: Float,
    val cycles: Int,
    val phase: Float,
    val breathe: Float
)

/** How a particle's brightness moves. */
enum class Twinkle {
    /** Fixed brightness. Unused today; here so "still" is sayable. */
    NONE,

    /** A smooth sine between roughly 55% and 100% of base alpha. Motes, fireflies, stars. */
    BREATH,

    /** Dark most of the cycle, with one short bright peak. The "occasional sparkle". */
    SPARKLE
}

/**
 * One set of ambient point particles — a *recipe*, not the particles themselves.
 *
 * `AmbientLayers.kt` expands this once per world into a fixed list of motes with
 * concrete positions, phases and cycle counts, drawn from a seeded `Random`. That
 * expansion happens on world change and never again: **nothing allocates a
 * particle during a frame**, and the field is identical every time the same world
 * is opened, including after process death. There is no particle engine here, no
 * spawning, no lifetimes and no pooling — just N points whose position is a
 * closed-form function of the phase.
 *
 * Counts are small on purpose (4-18 per set, at most two sets, so never more than
 * 22 points on screen) and alphas are low, because these sit *behind* the board
 * and must never compete with an arrow for attention.
 *
 * @property colours one is picked per particle, so a set reads as a family.
 * @property radiusDp the size range, in dp. Sub-pixel on purpose at the low end.
 * @property alpha the base brightness range, before [twinkle] modulates it.
 * @property band the vertical slice of the screen the set occupies, as fractions.
 * @property wander how far a particle strays from its home, as a fraction of
 *   screen *width* — applied to both axes, corrected by the aspect ratio at draw
 *   time so the wander is round rather than a tall ellipse.
 * @property twinkleCycles the range of brightness cycles per master cycle; a
 *   particle gets one integer from it, which is what keeps the set out of step
 *   with itself.
 * @property glow draw a wide dim halo under the point. Fireflies and crystal
 *   motes only — it is two extra circles and it is what makes a dot read as a
 *   light rather than a speck of dust.
 */
data class AmbientParticles(
    val count: Int,
    val colours: List<Color>,
    val radiusDp: ClosedFloatingPointRange<Float>,
    val alpha: ClosedFloatingPointRange<Float>,
    val band: ClosedFloatingPointRange<Float>,
    val wander: Float,
    val twinkle: Twinkle,
    val twinkleCycles: IntRange,
    val glow: Boolean = false
)
