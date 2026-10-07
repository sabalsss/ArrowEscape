package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.ui.discovery.DiscoveryArtwork
import com.sabalapps.arrowescape.ui.discovery.CompletionFx
import com.sabalapps.arrowescape.ui.discovery.DiscoveryGlow
import com.sabalapps.arrowescape.ui.discovery.DiscoveryRevealFx
import com.sabalapps.arrowescape.ui.discovery.RevealSchedule
import com.sabalapps.arrowescape.ui.world.DiscoveryCompletion
import com.sabalapps.arrowescape.ui.world.DiscoveryResult
import com.sabalapps.arrowescape.ui.world.WorldStyle

/** The sparkles of a plain reveal and of a Perfect Escape's richer one. */
private const val REVEAL_SPARKS = 26
private const val PERFECT_REVEAL_SPARKS = 42

/**
 * The art may be at most this big; the room and the width shrink it from there.
 * The discovery is the thing the player came for, so it gets the most room on the
 * screen — more than the stars ever did.
 */
private val MAX_ART = 240.dp

/**
 * A Campaign win, as a reveal: the arrows are gone and what they were hiding
 * emerges, glows, and is named; then where the collection stands; then how well it
 * was solved; then what to do next.
 *
 * The order is the point. **Discovery is what you found, stars are how well you
 * solved it**, so the artwork and its name come first and biggest, the collection
 * count after them, and the stars — smaller than the result screen has ever drawn
 * them — only then. A one-star clear shows the same discovery as a three-star one.
 *
 * ## One clock
 *
 * A single linear [Animatable] runs for [RevealSchedule.totalMs], and every piece
 * derives its own progress from it *in the draw phase* (`graphicsLayer` and `Canvas`
 * lambdas), so the whole sequence recomposes nothing per frame. The pieces that must
 * not exist before their moment — the stars, which animate in on their own, and the
 * buttons, which must not take a press — are composed or enabled from that clock
 * instead of being merely transparent.
 *
 * ## What a screen reader gets
 *
 * One heading that says the whole reveal ("New discovery. Butterfly. Forest
 * collection, 4 of 6 discovered."), then the stars and the buttons as ordinary
 * nodes. The artwork, the halo, the pips and the sparkles are decoration.
 *
 * ## Where it comes from
 *
 * The art starts at [revealFrom] — the middle of the stage the arrow shape was drawn
 * in — and glides up into its place in this layout as it fades in, so what was a
 * picture made of arrows becomes the thing the arrows were hiding, in the same spot.
 * The glide is measured between two anchors that carry no transform of their own
 * (so it cannot feed back into itself), is skipped under reduced motion, and is
 * zero if either position is not known.
 *
 * @param room the height the result layer has to work with; it sizes the artwork.
 * @param revealFrom the stage centre in this layer's coordinates, or null.
 */
@Composable
internal fun DiscoveryResultContent(
    discovery: DiscoveryResult,
    campaign: ResultContext.Campaign,
    stars: Int?,
    finished: Boolean,
    schedule: RevealSchedule,
    reducedMotion: Boolean,
    room: Dp,
    revealFrom: Offset?,
    onContinue: () -> Unit,
    onReplay: () -> Unit,
    onLevelSelect: () -> Unit,
    onViewDiscoveries: () -> Unit
) {
    val clock = remember { Animatable(0f) }
    // Where the artwork's slot is, in the same coordinates as [revealFrom]. Read only
    // in a graphicsLayer block, so a change repaints and recomposes nothing.
    var slotCentre by remember { mutableStateOf<Offset?>(null) }
    // A first clear that finishes a world (or the album) ends with a short beat of its
    // own, which the clock has to run long enough to include. It sits behind the
    // ordinary sequence, so the buttons are not held for it.
    val completionSet = discovery.completionSet
    val clockMs = schedule.totalMsFor(completionSet.size)
    LaunchedEffect(Unit) {
        clock.animateTo(
            targetValue = clockMs.toFloat(),
            animationSpec = tween(clockMs, easing = LinearEasing)
        )
    }
    val perfect = stars != null && StarRating.isPerfect(stars)
    val colours = remember(discovery.world) { DiscoveryGlow.of(discovery.world) }
    val accent = WorldStyle.of(discovery.world).accent
    val sparks = remember(discovery.discovery.levelId, perfect, colours) {
        sparks(
            seed = discovery.discovery.levelId,
            count = if (perfect) PERFECT_REVEAL_SPARKS else REVEAL_SPARKS,
            palette = colours.sparkPalette
        )
    }
    val rise = if (reducedMotion) 0.dp else 10.dp
    val showStars by remember { derivedStateOf { clock.value >= schedule.starsMs } }
    val pressable by remember { derivedStateOf { clock.value >= schedule.interactiveMs } }

    BoxWithConstraints(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        val art = minOf(MAX_ART, maxWidth * 0.62f, room * 0.28f).coerceAtLeast(120.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- what was found: one spoken node for the lot -------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        heading()
                        // Announced as soon as the reveal arrives, like the shape reveal's.
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                        contentDescription = discovery.spokenSummary
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RevealTitle(
                    text = discovery.headline,
                    // Gold for a first find; a cooler white for a revisit.
                    glow = if (discovery.isFirstClear) GamePalette.Gold else colours.primary,
                    modifier = Modifier.stage(clock, schedule.headlineMs, schedule.fadeMs, rise)
                )
                Spacer(Modifier.height(AppSpace.tight))

                // The hero: halo and sparkle behind and around the art. The outer box
                // is the slot — it never moves, which is what makes it a fair thing to
                // measure — and everything inside it rides the glide. The FX Canvas is
                // bigger than the slot and overflows it, so the glow is not cut off at
                // a box edge, and it is a sibling, not a parent, of the art.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(art * 1.08f)
                        .onGloballyPositioned { slot ->
                            val origin = slot.positionInRoot()
                            slotCentre = Offset(
                                origin.x + slot.size.width / 2f,
                                origin.y + slot.size.height / 2f
                            )
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
                                    val left = 1f - FastOutSlowInEasing.transform(
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
                            perfect = perfect,
                            sparks = if (schedule.burstMs > 0) sparks else emptyList(),
                            glow = {
                                val rising = clock.window(schedule.glowStartMs, schedule.glowMs)
                                val settling = clock.window(schedule.glowStartMs + schedule.glowMs, 500)
                                rising * (1f - 0.2f * settling)
                            },
                            burst = {
                                if (schedule.burstMs <= 0) 0f
                                else clock.window(schedule.peakMs, schedule.burstMs)
                            },
                            modifier = Modifier.requiredSize(art * 1.9f)
                        )
                        DiscoveryArtwork(
                            discovery = discovery.discovery,
                            modifier = Modifier
                                .size(art)
                                .graphicsLayer {
                                    val p = FastOutSlowInEasing.transform(
                                        clock.window(schedule.artStartMs, schedule.artMs)
                                    )
                                    alpha = p
                                    // A small settle up to full size: the art emerges,
                                    // it does not zoom. Reduced motion keeps it still.
                                    val s = if (reducedMotion) 1f else 0.90f + 0.10f * p
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                    }
                }

                Spacer(Modifier.height(AppSpace.hair))
                RevealTitle(
                    text = discovery.discovery.name,
                    glow = null,
                    large = true,
                    modifier = Modifier.stage(clock, schedule.nameMs, schedule.fadeMs, rise)
                )
                Spacer(Modifier.height(AppSpace.tight))

                if (discovery.completion != DiscoveryCompletion.NONE) {
                    CompletionLine(
                        discovery = discovery,
                        clock = clock,
                        schedule = schedule,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.stage(clock, schedule.collectionMs, schedule.fadeMs, rise)
                    )
                } else {
                    CollectionLine(
                        discovery = discovery,
                        accent = accent,
                        modifier = Modifier.stage(clock, schedule.collectionMs, schedule.fadeMs, rise)
                    )
                }
            }

            // ---- how well: smaller than the discovery, and later ----------------
            if (stars != null) {
                Spacer(Modifier.height(AppSpace.betweenCards))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.height(STARS_BLOCK_HEIGHT)
                ) {
                    if (showStars) {
                        BigStars(
                            earned = stars,
                            scale = 0.58f,
                            animate = !reducedMotion,
                            startDelayMs = 40
                        )
                        when {
                            perfect -> PerfectRibbon(
                                modifier = Modifier.offset(y = (-8).dp),
                                delayMs = 260,
                                animate = !reducedMotion
                            )

                            campaign.isNewBest -> {
                                Spacer(Modifier.height(AppSpace.hair))
                                StarNote("New best")
                            }

                            campaign.bestStars > stars -> {
                                Spacer(Modifier.height(AppSpace.hair))
                                StarNote("Best ${"★".repeat(campaign.bestStars)} kept")
                            }
                        }
                    }
                }
            }

            // ---- what next ------------------------------------------------------
            Spacer(Modifier.height(AppSpace.betweenCards))
            Box(modifier = Modifier.stage(clock, schedule.actionsMs, schedule.fadeMs, rise)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (finished) {
                        // There is no Level 31 to explore on to, so the last level
                        // offers the album instead of a button that goes nowhere.
                        GamePrimaryButton(
                            label = "View Discoveries",
                            onClick = onViewDiscoveries,
                            icon = { ButtonGlyph.Grid() }
                        )
                    } else {
                        GamePrimaryButton(
                            label = "Continue Exploring",
                            onClick = onContinue,
                            icon = { ButtonGlyph.Play() }
                        )
                    }
                    SecondaryRow(
                        ResultButton("Replay", onReplay) { ButtonGlyph.Refresh() },
                        ResultButton("Level Select", onLevelSelect) { ButtonGlyph.Grid() }
                    )
                }
                if (!pressable) {
                    // Until the buttons have properly arrived, a press on them is
                    // swallowed: a stray tap from the last arrow must not skip the
                    // reveal it just earned.
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

/** Room the stars block holds, so the buttons below do not jump when the stars arrive. */
private val STARS_BLOCK_HEIGHT = 92.dp

/** 0 before [start] ms on this clock, 1 after [start] + [duration], linear between. */
internal fun Animatable<Float, AnimationVector1D>.window(start: Int, duration: Int): Float =
    if (duration <= 0) {
        if (value >= start) 1f else 0f
    } else {
        ((value - start) / duration).coerceIn(0f, 1f)
    }

/** Fades a piece in from [rise] below, once the clock reaches [startMs]. */
internal fun Modifier.stage(
    clock: Animatable<Float, AnimationVector1D>,
    startMs: Int,
    fadeMs: Int,
    rise: Dp
): Modifier = graphicsLayer {
    val p = FastOutSlowInEasing.transform(clock.window(startMs, fadeMs))
    alpha = p
    translationY = (1f - p) * rise.toPx()
}

/** The headline and the name share one tracked, shadowed white style. */
@Composable
internal fun RevealTitle(
    text: String,
    glow: Color?,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Text(
        text = text,
        style = if (large) {
            AppText.resultTitle.copy(color = Color.White, shadow = OnArtShadow)
        } else {
            AppText.resultTitle.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.8.sp,
                color = Color.White,
                shadow = Shadow(
                    color = (glow ?: Color.Black).copy(alpha = 0.78f),
                    offset = Offset.Zero,
                    blurRadius = 26f
                )
            )
        },
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * "FOREST COLLECTION · 4 / 6 DISCOVERED", with six pips under it — one per
 * discovery in the world, this one lit — and a gold note when the world, or the
 * whole album, is complete.
 */
@Composable
private fun CollectionLine(
    discovery: DiscoveryResult,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
            )
            Spacer(Modifier.width(AppSpace.tight))
            Text(
                text = discovery.collectionTitle,
                style = AppText.section.copy(
                    color = Color.White.copy(alpha = 0.92f),
                    shadow = OnArtShadow
                ),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(AppSpace.hair))
        Text(
            text = discovery.progressLabel,
            style = AppText.cardTitle.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                shadow = OnArtShadow
            ),
            maxLines = 1
        )
        Spacer(Modifier.height(AppSpace.tight))
        WorldPips(
            collected = discovery.worldCollected,
            total = discovery.worldTotal,
            accent = accent
        )
        val note = when {
            discovery.allFound -> "ALL DISCOVERIES FOUND"
            discovery.worldComplete -> "WORLD COMPLETE"
            else -> null
        }
        if (note != null) {
            Spacer(Modifier.height(AppSpace.tight + 2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GameBrush.Gold)
                    .border(
                        AppStroke.hairline,
                        Color.White.copy(alpha = 0.65f),
                        RoundedCornerShape(50)
                    )
                    .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = GamePalette.Brown,
                    modifier = Modifier.size(AppIcon.small)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = note,
                    style = AppText.section.copy(color = GamePalette.Brown),
                    maxLines = 1
                )
            }
        }
    }
}

/** The most a completion piece is drawn at, and the least before it would be too small to read. */
private val COMPLETION_PIECE_MAX = 46.dp
private val COMPLETION_PIECE_MIN = 28.dp
private val COMPLETION_PIECE_GAP = 6.dp

private val CompletionGold = Color(0xFFFFD66B)

/**
 * The beat a first clear gets when it *finishes* something — "SKY GARDEN COMPLETE!"
 * and its six discoveries side by side, or "ALL DISCOVERIES FOUND" and the finale of
 * each of the five worlds. It takes the place of the ordinary collection line, in the
 * same slot and at about the same height, so nothing below it moves.
 *
 * What it says, it says in words first (the title and the count); the row of
 * artwork and the gold are the same message again, for the eye. Each piece arrives
 * on the reveal's clock, one after another, with a small gold burst as they do —
 * the same two effects the reveal already uses, in gold, once. Reduced motion
 * brings the pieces in together, still, with no burst.
 *
 * Only ever composed for a completed set, so every piece it draws is already found.
 */
@Composable
private fun CompletionLine(
    discovery: DiscoveryResult,
    clock: Animatable<Float, AnimationVector1D>,
    schedule: RevealSchedule,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val pieces = discovery.completionSet
    val campaign = discovery.completion == DiscoveryCompletion.CAMPAIGN
    val sparks = remember(discovery.discovery.levelId, pieces.size) {
        sparks(
            seed = discovery.discovery.levelId + 1000,
            count = 24,
            palette = listOf(
                GamePalette.Gold,
                CompletionGold,
                Color.White,
                GamePalette.Gold,
                Color.White
            )
        )
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = discovery.completionTitle.orEmpty(),
            style = AppText.cardTitle.copy(
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = CompletionGold,
                shadow = Shadow(
                    color = GamePalette.Gold.copy(alpha = 0.70f),
                    offset = Offset.Zero,
                    blurRadius = 22f
                )
            ),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = discovery.completionCount.orEmpty(),
            style = AppText.cardTitle.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                shadow = OnArtShadow
            ),
            maxLines = 1
        )
        Spacer(Modifier.height(AppSpace.tight))

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val count = pieces.size.coerceAtLeast(1)
            val fit = (maxWidth - COMPLETION_PIECE_GAP * (count - 1)) / count
            val piece = minOf(COMPLETION_PIECE_MAX, fit).coerceAtLeast(COMPLETION_PIECE_MIN)
            Box(contentAlignment = Alignment.Center) {
                CompletionFx(
                    sparks = if (schedule.completionBurstMs > 0) sparks else emptyList(),
                    glow = { clock.window(schedule.completionMs, 400) },
                    burst = {
                        if (schedule.completionBurstMs <= 0) 0f
                        else clock.window(schedule.completionMs, schedule.completionBurstMs)
                    },
                    // Overflows the row on every side without adding to its height: a bare
                    // requiredSize here made the row report 3.6 pieces tall and pushed the
                    // buttons below it off a short screen.
                    modifier = Modifier
                        .matchParentSize()
                        .wrapContentSize(unbounded = true)
                        .requiredSize(piece * 3.6f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(COMPLETION_PIECE_GAP),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pieces.forEachIndexed { index, found ->
                        val start = schedule.completionMs + schedule.completionStaggerMs * index
                        Box(
                            modifier = Modifier
                                .size(piece)
                                .graphicsLayer {
                                    val p = FastOutSlowInEasing.transform(
                                        clock.window(start, schedule.fadeMs)
                                    )
                                    alpha = p
                                    val s = if (reducedMotion) 1f else 0.70f + 0.30f * p
                                    scaleX = s
                                    scaleY = s
                                }
                                .clip(CircleShape)
                                .background(
                                    WorldStyle.of(found.world).accent.copy(
                                        alpha = if (campaign) 0.55f else 0.30f
                                    )
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = if (campaign) 0.80f else 0.45f),
                                    CircleShape
                                )
                                .padding(piece * 0.08f),
                            contentAlignment = Alignment.Center
                        ) {
                            DiscoveryArtwork(
                                discovery = found,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
