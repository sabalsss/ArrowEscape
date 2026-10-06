package com.sabalapps.arrowescape.ui.world

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.sabalapps.arrowescape.ui.rememberReducedMotion

/**
 * The world artwork behind a board, the slow ambient life in it, and the scrim
 * that keeps the chrome readable over all of it.
 *
 * One renderer for all five worlds and for the Daily treatment — the world is a
 * parameter, never a branch — which is what keeps [com.sabalapps.arrowescape.ui.GameScreen]
 * single and means a sixth world would be two rows of data ([WorldStyle] and
 * [WorldAmbience]) rather than a new composable.
 *
 * ## Layer order
 *
 * Bottom to top, and the order is the whole design:
 *
 * 1. the theme's background colour, so a decode hiccup shows the theme and not
 *    whatever was on screen before;
 * 2. the WebP, cropped to fill, very slowly swelling and drifting;
 * 3. the atmosphere — soft translucent washes sliding across the scene;
 * 4. the ambient particles;
 * 5. the top/bottom scrim, which therefore dims any particle that wanders behind
 *    the HUD or the footer, exactly where a moving dot would be most distracting;
 * 6. the Daily Challenge's golden wash, when it is today's board.
 *
 * Everything that moves is in 2-4. **Nothing in the gameplay layer is in here at
 * all** — the board, the arrows, the HUD, the progress bar, the Hint pill, the
 * tutorial captions and the result card are all siblings of this composable in
 * `GameScreen`, drawn over it and untouched by it. The ambience takes no input,
 * reads no gesture and has no relationship to the board's geometry, so there is
 * no parallax and a tap cannot move the scene.
 *
 * ## Crop
 *
 * [ContentScale.Crop] with [Alignment.Center]: the artwork fills the screen,
 * keeps its aspect ratio and is never stretched. A 1440×2560 (9:16) image on a
 * taller phone — 20:9 and 21:9 are both common — is cropped at the left and
 * right edges, which is why the artwork was composed with its subject central
 * and its detail in the lower corners. The board sits over the calm middle in
 * every case.
 *
 * The ambient zoom starts *above* 1.0 and the drift is bounded by the slack that
 * creates (see [ImageMotion]), so the crop stays a crop at every point in the
 * cycle and no edge is ever exposed. [clipToBounds] on the root makes that
 * belt-and-braces: nothing in any layer can paint outside the background.
 *
 * ## Cost
 *
 * One `painterResource` for the world on screen — the other four are never
 * decoded — and **one animated `Float` for the entire scene**. The artwork and
 * the washes move as GPU layer transforms; the particles are a fixed, precomputed
 * list drawn as circles on a single `Canvas`. The phase is only ever read inside
 * layer and draw lambdas, so an ambient frame recomposes nothing, re-measures
 * nothing and allocates nothing. No blur, no shader, no Lottie, no bitmap work.
 * See `AmbientLayers.kt`.
 *
 * Because the animation lives in this composable, it exists only while this
 * composable does: leaving the game screen stops it, and the four worlds not on
 * screen are neither animated nor loaded.
 *
 * ## Reduced motion
 *
 * With motion turned off in the system accessibility settings, layers 2-4 lose
 * their animation entirely and the still WebP is shown exactly as Phase 6A drew
 * it: no zoom, no drift, no washes, no particles, and no infinite transition
 * running behind the board. This is the strictest reading of the setting and also
 * the cheapest, which seemed like the right place to land for a layer that is
 * pure decoration. Gameplay animation is unaffected and keeps following its own
 * rules — the arrow flight still plays, because that one is the game answering a
 * tap rather than ambience.
 *
 * ## Accessibility
 *
 * Every layer here is decorative and none of it is in the accessibility tree:
 * the [Image] is given a null `contentDescription`, which contributes no
 * semantics node at all, and the scrim, wash and particle layers have no
 * semantics of their own to contribute. Nothing about the world is announced,
 * because nothing about the world is information — the board is unchanged in all
 * five.
 *
 * @param world which artwork to draw, and which ambience to run in it.
 * @param daily true for the Daily Challenge, which adds a restrained golden wash
 *   over whichever world the day landed in. See [WorldStyle.DAILY_ACCENT].
 * @param reducedMotion stand the ambience down and show the still artwork.
 *   Defaults to the system setting; a parameter so a preview can force either.
 */
@Composable
fun WorldBackground(
    world: GameWorld,
    daily: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion()
) {
    val style = WorldStyle.of(world)
    val scrim = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            // The ambient zoom draws slightly outside the layer it is given, and
            // `Crop` can too. Clipping here — at the full screen, where a clip
            // costs nothing and hides nothing — keeps that contained.
            .clipToBounds()
            // Behind the artwork, so the first frame and any decode hiccup show
            // the theme's own background rather than whatever was there before.
            .background(scrim)
    ) {
        if (reducedMotion) {
            // Phase 6A's background, unchanged: one still image, no transition.
            Image(
                painter = painterResource(style.background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.matchParentSize()
            )
        } else {
            val ambience = remember(world) { WorldAmbience.of(world) }
            // Expanded once per world, never per frame. Deterministic, so the
            // same world always comes back with the same sky.
            val motes = remember(world, ambience) { ambientMotes(world, ambience) }
            val phase = rememberAmbiencePhase()

            Image(
                painter = painterResource(style.background),
                // Decorative: null contributes no semantics node, so the artwork
                // never reaches TalkBack.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .ambientImageMotion(ambience.motion, phase)
            )

            ambience.drifts.forEach { drift ->
                AmbientDriftLayer(
                    drift = drift,
                    phase = phase,
                    modifier = Modifier.matchParentSize()
                )
            }

            AmbientParticleLayer(
                motes = motes,
                phase = phase,
                modifier = Modifier.matchParentSize()
            )
        }

        // Top and bottom scrim in one pass. Transparent across the middle third,
        // so the board's own surface does the separating there and the artwork
        // stays visible behind it. Sitting above the ambience, it also dims any
        // particle that drifts up behind the HUD or down behind the footer.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to scrim.copy(alpha = style.topScrim),
                        0.30f to Color.Transparent,
                        0.70f to Color.Transparent,
                        1.0f to scrim.copy(alpha = style.bottomScrim)
                    )
                )
        )

        if (daily) {
            // The day's own layer: a warm light rising from the bottom, over
            // whichever of the five worlds today landed in. Strongest where the
            // footer is and gone by the middle of the board, so it reads as a
            // treatment of the scene rather than a colour cast over the arrows.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to WorldStyle.DAILY_ACCENT.copy(
                                alpha = WorldStyle.DAILY_WASH * 0.55f
                            ),
                            0.45f to Color.Transparent,
                            1.0f to WorldStyle.DAILY_ACCENT.copy(
                                alpha = WorldStyle.DAILY_WASH
                            )
                        )
                    )
            )
        }
    }
}
