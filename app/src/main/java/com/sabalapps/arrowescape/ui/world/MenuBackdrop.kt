package com.sabalapps.arrowescape.ui.world

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.sabalapps.arrowescape.ui.rememberReducedMotion

/** The deep navy every menu scrim is made of, so the five worlds share one shadow. */
private val MENU_NAVY = Color(0xFF0A0E2B)

/** Decode size for a full-screen menu backdrop: 720×1280 from the 1440×2560 art. */
internal const val BACKDROP_SAMPLE = 2

/**
 * How strongly each world needs to be dimmed to carry white type on top of it.
 *
 * Not the game screen's scrims ([WorldStyle.topScrim]): those are tuned to keep
 * a *white* board readable and use the theme's background colour, which in the
 * light theme is near-white and would wash a menu out. A menu's text sits
 * straight on the artwork, so the scrim here is navy, and the brightest worlds
 * (Sky Garden, Sunset Canyon) get the most of it while the two night worlds —
 * already dark — get the least, or they go muddy.
 */
private fun menuScrim(world: GameWorld): Float = when (world) {
    GameWorld.SKY_GARDEN -> 0.46f
    GameWorld.FOREST -> 0.42f
    GameWorld.SUNSET_CANYON -> 0.44f
    GameWorld.CRYSTAL_NIGHT -> 0.30f
    GameWorld.COSMIC -> 0.22f
}

/**
 * The backdrop every menu sits on: a world's artwork, a navy scrim that keeps
 * white type readable over it, and a wash of the world's own accent at the top.
 *
 * This is how the artwork *participates* in Home, Level Select, Stats and
 * Settings instead of being buried under a flat page colour: the picture is
 * there, a little dimmer than in the game, with glass cards floating over it.
 *
 * ## Layers, bottom to top
 *
 *  1. a gradient of the world's accent into navy — what shows for the few frames
 *     before the artwork decodes, and what a failed decode degrades to;
 *  2. the down-sampled artwork ([rememberWorldArt]), cropped to fill;
 *  3. for [ambient] screens, the same slow drift layers and motes the game screen
 *     uses, taken from [WorldAmbience] unchanged — it is the existing system, not a
 *     second one — and none of it when reduced motion is on;
 *  4. the scrim, and the accent glow behind the title.
 *
 * Everything is static unless [ambient] is set, which only Home does: a scrolling
 * level grid or a settings list is a screen to be read, and continuous motion
 * behind a scrolling list is cost for no benefit. Decorative throughout — no
 * layer here reaches TalkBack.
 *
 * @param dim extra scrim on top of the world's own, for screens that put a lot of
 *   small type straight on the artwork.
 */
@Composable
fun MenuBackdrop(
    world: GameWorld,
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
    dim: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val style = WorldStyle.of(world)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val art = rememberWorldArt(world, BACKDROP_SAMPLE)
    val artAlpha by animateFloatAsState(
        targetValue = if (art != null) 1f else 0f,
        animationSpec = tween(260),
        label = "backdropArt"
    )
    val reducedMotion = rememberReducedMotion()
    val strength = (menuScrim(world) + dim + if (dark) 0.10f else 0f).coerceIn(0f, 0.9f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerpColor(style.accent, MENU_NAVY, 0.55f),
                        MENU_NAVY
                    )
                )
            )
    ) {
        if (art != null) {
            if (ambient && !reducedMotion) {
                val ambience = remember(world) { WorldAmbience.of(world) }
                val motes = remember(world, ambience) { ambientMotes(world, ambience) }
                val phase = rememberAmbiencePhase()
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .matchParentSize()
                        .ambientImageMotion(ambience.motion, phase)
                        .alpha(artAlpha)
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
            } else {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(artAlpha)
                )
            }
        }

        // Scrim: lighter through the middle where the artwork has its best
        // moment, heavier at the bottom where the lower corners carry the most
        // detail and the pinned buttons sit.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to MENU_NAVY.copy(alpha = strength * 0.95f),
                        0.38f to MENU_NAVY.copy(alpha = strength * 0.55f),
                        0.72f to MENU_NAVY.copy(alpha = strength * 0.80f),
                        1.00f to MENU_NAVY.copy(alpha = (strength * 1.30f).coerceAtMost(0.88f))
                    )
                )
        )

        // The world's own colour, as a soft glow behind the title area, so the
        // top of the screen has a centre and the world is felt as well as seen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        listOf(style.accent.copy(alpha = 0.26f), Color.Transparent)
                    )
                )
        )

        content()
    }
}

/** A straight blend of [a] towards [b]; the one colour mix the menus need. */
internal fun lerpColor(a: Color, b: Color, fraction: Float): Color = Color(
    red = a.red + (b.red - a.red) * fraction,
    green = a.green + (b.green - a.green) * fraction,
    blue = a.blue + (b.blue - a.blue) * fraction,
    alpha = a.alpha + (b.alpha - a.alpha) * fraction
)
