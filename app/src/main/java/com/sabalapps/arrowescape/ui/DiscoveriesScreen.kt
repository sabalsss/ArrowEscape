package com.sabalapps.arrowescape.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.ui.discovery.DiscoveryArtwork
import com.sabalapps.arrowescape.ui.discovery.DiscoveryGlow
import com.sabalapps.arrowescape.ui.world.DiscoveryCollection
import com.sabalapps.arrowescape.ui.world.DiscoverySlot
import com.sabalapps.arrowescape.ui.world.GameWorld
import com.sabalapps.arrowescape.ui.world.MenuBackdrop
import com.sabalapps.arrowescape.ui.world.WorldArtImage
import com.sabalapps.arrowescape.ui.world.WorldCollection
import com.sabalapps.arrowescape.ui.world.WorldStyle

private val MAX_ALBUM_WIDTH = 520.dp

/** Below this width the screen's own padding gives way so the cards keep their room. */
private val NARROW_BELOW = 360.dp

/** A band this wide (inside its padding) fits three cards across; narrower, two. */
private val THREE_ACROSS_FROM = 300.dp

/** The dark ink the cards' text is set in, on the pale sticker cards. */
private val CardInk = Color(0xFF1B2260)

/**
 * The album: every discovery in the game, in its world, as a page of stickers.
 *
 * It is meant to feel like an exploration album — warm, curious, a little
 * collectable — and not like an inventory or an achievement list. Five world pages,
 * six slots each, and three kinds of slot:
 *
 *  - **found**: the real artwork and its name, on a pale sticker card, with the
 *    best stars small underneath. Tapping it opens a closer look;
 *  - **a mystery** (open to play, not cleared): a dashed card with a "?" and the
 *    level it is behind. Nothing about what it is. Tapping it offers to play it;
 *  - **locked** (not reachable yet): a dark card with a padlock. Inert.
 *
 * ## Spoilers
 *
 * Everything here is drawn from [DiscoveryCollection], whose only slot type that
 * carries an identity is [DiscoverySlot.Collected]. The mystery and locked cards are
 * handed a level number and nothing else, so they have no name to print, no artwork
 * to draw and nothing to speak. The closer-look sheet is rebuilt from the player's
 * *current* record every time — a saved selection that is not a found discovery or
 * an open level simply shows nothing.
 *
 * ## Layout
 *
 * Cards sit three across where the band is wide enough and two across where it is
 * not, so a 320dp phone gets bigger, not smaller, cards. Each card is at least 48dp
 * in every direction and every one of them is a whole target.
 */
@Composable
fun DiscoveriesScreen(
    progress: PlayerProgress,
    /** The world the player is in, for the backdrop. */
    world: GameWorld,
    /** Opens an open-but-uncleared level. */
    onPlayLevel: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val collection = remember(progress) { DiscoveryCollection.of(progress) }
    // Which slot the closer look is on, by level id. Re-resolved against the
    // current collection below, so a stale or hostile value shows nothing.
    var selectedLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = selectedLevel?.let { collection.slotFor(it) }
        ?.takeIf { it !is DiscoverySlot.Locked }

    BackHandler(enabled = selected != null) { selectedLevel = null }

    MenuBackdrop(world = world, dim = 0.10f) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val narrow = maxWidth < NARROW_BELOW
            val screenPad = if (narrow) 8.dp else 12.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // While the closer look is up the album behind it leaves the
                    // accessibility tree, as a game board does behind its result.
                    .then(if (selected != null) Modifier.clearAndSetSemantics {} else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(
                    title = "Discoveries",
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = screenPad)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppSpace.betweenCards + 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(
                        start = screenPad,
                        end = screenPad,
                        top = AppSpace.tight,
                        bottom = 28.dp
                    ),
                    modifier = Modifier
                        .widthIn(max = MAX_ALBUM_WIDTH)
                        .fillMaxWidth()
                ) {
                    item(key = "header") { AlbumHeader(collection) }
                    items(collection.worlds, key = { it.world.id }) { worldCollection ->
                        WorldPage(
                            collection = worldCollection,
                            narrow = narrow,
                            onSlot = { slot ->
                                if (slot !is DiscoverySlot.Locked) selectedLevel = slot.levelId
                            }
                        )
                    }
                }
            }

            if (selected != null) {
                DiscoveryLook(
                    slot = selected,
                    onPlay = {
                        selectedLevel = null
                        onPlayLevel(selected.levelId)
                    },
                    onDismiss = { selectedLevel = null }
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// The header
// -------------------------------------------------------------------------

/**
 * "DISCOVERIES · 14 / 30 FOUND" with a bar under it. When all thirty are found it is
 * a gold card that says so: the finished album is the reward, and there is nothing
 * to claim.
 */
@Composable
private fun AlbumHeader(collection: DiscoveryCollection) {
    val done = collection.allFound
    GameGlassCard(
        tone = if (done) GlassTone.Gold else GlassTone.Dark,
        elevation = AppElevation.raised,
        contentDescription = if (done) {
            "Discoveries, all ${collection.total} found"
        } else {
            "Discoveries, ${collection.collected} of ${collection.total} found"
        }
    ) {
        val content = if (done) GamePalette.Brown else Color.White
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            GameBadge(brush = if (done) GameBrush.Orange else GameBrush.Purple) {
                SparkleGlyph(Color.White, Modifier.size(AppIcon.badge + 4.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (done) "ALL DISCOVERIES FOUND" else "DISCOVERIES",
                    style = AppText.section.copy(color = content.copy(alpha = if (done) 1f else 0.78f)),
                    maxLines = 2,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = collection.headerLabel,
                    style = AppText.cardTitle.copy(
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = content
                    ),
                    maxLines = 1
                )
                Spacer(Modifier.height(AppSpace.tight))
                GameProgressBar(
                    fraction = if (collection.total > 0) {
                        collection.collected.toFloat() / collection.total
                    } else {
                        0f
                    },
                    height = 8.dp,
                    fill = if (done) {
                        Brush.horizontalGradient(listOf(GamePalette.GoldDeep, GamePalette.Gold))
                    } else {
                        Brush.horizontalGradient(listOf(GamePalette.Cyan, GamePalette.ElectricBlue))
                    },
                    trackColor = content.copy(alpha = 0.20f)
                )
                if (done) {
                    // The five worlds, each by the discovery that ended it: the finished
                    // set shown whole. Drawn only here, where every one is found.
                    Spacer(Modifier.height(AppSpace.tight + 2.dp))
                    WorldFinaleRow(collection)
                }
            }
        }
    }
}

/**
 * One sticker per world — its last discovery — on a disc of that world's colour. Only
 * ever built from [DiscoverySlot.Collected], so it cannot draw what is not found.
 * Decoration: the header's own spoken line already says all of them are.
 */
@Composable
private fun WorldFinaleRow(collection: DiscoveryCollection) {
    val finales = collection.worlds.mapNotNull { it.slots.lastOrNull() as? DiscoverySlot.Collected }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics {}
    ) {
        finales.forEach { slot ->
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(WorldStyle.of(slot.discovery.world).accent.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.80f), CircleShape)
                    .padding(3.dp)
            ) {
                DiscoveryArtwork(discovery = slot.discovery, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// -------------------------------------------------------------------------
// One world's page
// -------------------------------------------------------------------------

/**
 * One world, as a page of the album: its artwork behind a navy scrim, its heading,
 * and its six slots. A world the player has not reached is the same picture drained
 * of colour, as in Level Select; a finished world gets a gold edge.
 */
@Composable
private fun WorldPage(
    collection: WorldCollection,
    narrow: Boolean,
    onSlot: (DiscoverySlot) -> Unit
) {
    val accent = WorldStyle.of(collection.world).accent
    val reached = collection.slots.any { it !is DiscoverySlot.Locked }
    val complete = collection.isComplete
    val shape = RoundedCornerShape(AppRadius.card)
    val pad = if (narrow) 8.dp else 10.dp
    val gap = if (narrow) 8.dp else 10.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(AppElevation.card, shape)
            .clip(shape)
            .border(
                AppStroke.emphasis,
                Brush.verticalGradient(
                    if (complete) {
                        listOf(GamePalette.Gold.copy(alpha = 0.95f), GamePalette.GoldDeep.copy(alpha = 0.55f))
                    } else {
                        listOf(
                            accent.copy(alpha = if (reached) 0.85f else 0.35f),
                            accent.copy(alpha = if (reached) 0.35f else 0.15f)
                        )
                    }
                ),
                shape
            )
    ) {
        WorldArtImage(
            world = collection.world,
            sampleSize = 4,
            muted = !reached,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            GamePalette.Navy.copy(alpha = if (reached) 0.46f else 0.68f),
                            GamePalette.Navy.copy(alpha = if (reached) 0.70f else 0.86f)
                        )
                    )
                )
        )
        if (complete) {
            // A faint warmth along the top of a finished page, and nothing else.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to GamePalette.Gold.copy(alpha = 0.16f),
                            0.35f to Color.Transparent
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(horizontal = pad, vertical = 12.dp)) {
            WorldDiscoveryHeader(
                collection = collection,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(AppSpace.tight + 2.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= THREE_ACROSS_FROM) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    collection.slots.chunked(columns).forEach { rowSlots ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            rowSlots.forEach { slot ->
                                SlotCard(
                                    slot = slot,
                                    accent = accent,
                                    onClick = { onSlot(slot) },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            // A short last row keeps its cards the same width.
                            repeat(columns - rowSlots.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** One slot, in whichever of its three states it is in. */
@Composable
private fun SlotCard(
    slot: DiscoverySlot,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (slot) {
        is DiscoverySlot.Collected -> CollectedCard(slot, accent, onClick, modifier)
        is DiscoverySlot.Mystery -> MysteryCard(slot, onClick, modifier)
        is DiscoverySlot.Locked -> LockedCard(slot, modifier)
    }
}

/** The shape every card shares, so a row of mixed states is a row of equal cards. */
private val CARD_SHAPE = RoundedCornerShape(AppRadius.tile)

/** The square at the top of a card that holds the art, the "?" or the padlock. */
private val CARD_LABEL_MIN_HEIGHT = 44.dp

/** A found discovery: the real artwork, its name, and the best stars, small. */
@Composable
private fun CollectedCard(
    slot: DiscoverySlot.Collected,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rated = StarRating.isValid(slot.stars)
    Column(
        modifier = modifier
            .gameClickable(onClick = onClick, pressedScale = 0.96f, onClickLabel = "Take a closer look")
            .semantics(mergeDescendants = true) { contentDescription = slot.spokenLabel }
            .softShadow(6.dp, CARD_SHAPE)
            .clip(CARD_SHAPE)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.97f), Color(0xFFE5ECFF).copy(alpha = 0.97f))
                )
            )
            .border(
                AppStroke.emphasis,
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.40f))),
                CARD_SHAPE
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .drawBehind {
                    // A soft pool of the world's colour, so the sticker sits in a place.
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to accent.copy(alpha = 0.30f),
                            1f to Color.Transparent
                        ),
                        radius = size.minDimension * 0.46f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            DiscoveryArtwork(
                discovery = slot.discovery,
                modifier = Modifier.fillMaxSize(0.82f)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CARD_LABEL_MIN_HEIGHT)
                .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = slot.discovery.name,
                style = AppText.cardTitle.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CardInk
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (rated) {
                Spacer(Modifier.height(3.dp))
                CardStars(slot.stars)
            }
        }
    }
}

/** The best stars, as three glyphs a little over 10dp: there if they fit, secondary. */
@Composable
private fun CardStars(earned: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.clearAndSetSemantics {}
    ) {
        repeat(StarRating.MAX) { slot ->
            val lit = slot < earned
            StarGlyph(
                fill = if (lit) {
                    Brush.verticalGradient(listOf(GamePalette.GoldLight, GamePalette.Gold))
                } else {
                    Brush.verticalGradient(listOf(CardInk.copy(alpha = 0.14f), CardInk.copy(alpha = 0.10f)))
                },
                outline = if (lit) GamePalette.GoldDeep.copy(alpha = 0.7f) else null,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Open to play, not cleared. A dashed card, a "?" and the level it is behind — and
 * deliberately nothing else: no name, no art, no hint of what kind of thing it is.
 */
@Composable
private fun MysteryCard(
    slot: DiscoverySlot.Mystery,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .gameClickable(onClick = onClick, pressedScale = 0.96f, onClickLabel = "See this level")
            .semantics(mergeDescendants = true) { contentDescription = slot.spokenLabel }
            .clip(CARD_SHAPE)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.08f))
                )
            )
            .dashedBorder(Color.White.copy(alpha = 0.55f), AppRadius.tile),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            MysteryMark(size = 46.dp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CARD_LABEL_MIN_HEIGHT)
                .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Undiscovered",
                style = AppText.cardTitle.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    shadow = OnArtShadow
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Level ${slot.levelId}",
                style = AppText.caption.copy(
                    color = Color.White.copy(alpha = 0.78f),
                    shadow = OnArtShadow
                ),
                maxLines = 1
            )
        }
    }
}

/** Not reachable yet. A padlock, and nothing a player could learn from. Inert. */
@Composable
private fun LockedCard(slot: DiscoverySlot.Locked, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            // Not clickable at all, rather than clickable and ignored: a tap on a
            // locked card is inert by construction.
            .semantics(mergeDescendants = true) {
                contentDescription = slot.spokenLabel
                disabled()
            }
            .clip(CARD_SHAPE)
            .background(
                Brush.verticalGradient(
                    listOf(GamePalette.Navy.copy(alpha = 0.52f), GamePalette.Navy.copy(alpha = 0.68f))
                )
            )
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.12f), CARD_SHAPE),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.size(AppIcon.badge)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CARD_LABEL_MIN_HEIGHT)
                .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "Locked",
                style = AppText.caption.copy(color = Color.White.copy(alpha = 0.50f)),
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------------------
// The closer look
// -------------------------------------------------------------------------

/**
 * A lightweight look at one slot, over the album.
 *
 * A found discovery: the art large, its name, its world, "DISCOVERED" and the level
 * it was behind — nothing more; there is no lore and no rarity to read. A mystery:
 * "UNDISCOVERED", its level, and a button to play it. Never reached for a locked
 * slot (the caller filters), and it carries no name for a mystery to begin with.
 */
@Composable
private fun DiscoveryLook(
    slot: DiscoverySlot,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(if (reducedMotion) 120 else 200, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(GamePalette.Navy.copy(alpha = 0.74f))
            // A tap outside the card closes it; the card swallows its own taps.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        GameGlassCard(
            tone = GlassTone.Dark,
            shape = RoundedCornerShape(AppRadius.dialog),
            elevation = AppElevation.hero,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
                .graphicsLayer {
                    val s = if (reducedMotion) 1f else 0.94f + 0.06f * enter.value
                    scaleX = s
                    scaleY = s
                }
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (slot) {
                    is DiscoverySlot.Collected -> CollectedLook(slot, onDismiss)
                    is DiscoverySlot.Mystery -> MysteryLook(slot, onPlay, onDismiss)
                    is DiscoverySlot.Locked -> Unit
                }
            }
        }
    }
}

@Composable
private fun CollectedLook(slot: DiscoverySlot.Collected, onDismiss: () -> Unit) {
    val accent = WorldStyle.of(slot.discovery.world).accent
    val colours = remember(slot.discovery.world) { DiscoveryGlow.of(slot.discovery.world) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                heading()
                contentDescription =
                    "${slot.discovery.name}, discovered, ${slot.discovery.world.displayName}, " +
                    "Level ${slot.discovery.levelId}"
            }
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to colours.secondary.copy(alpha = 0.45f),
                            0.55f to colours.primary.copy(alpha = 0.24f),
                            1f to Color.Transparent
                        ),
                        radius = size.minDimension * 0.62f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            DiscoveryArtwork(discovery = slot.discovery, modifier = Modifier.fillMaxSize(0.92f))
        }
        Spacer(Modifier.height(AppSpace.tight))
        Text(
            text = slot.discovery.name,
            style = AppText.resultTitle.copy(color = Color.White, shadow = OnArtShadow),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Spacer(Modifier.height(AppSpace.tight))
        WorldChip(name = slot.discovery.world.displayName, accent = accent)
        Spacer(Modifier.height(AppSpace.betweenCards))
        CompleteChip("DISCOVERED")
        Spacer(Modifier.height(AppSpace.hair + 2.dp))
        Text(
            text = "Level ${slot.discovery.levelId}",
            style = AppText.caption.copy(color = Color.White.copy(alpha = 0.72f))
        )
    }
    Spacer(Modifier.height(AppSpace.betweenSections))
    GameSecondaryButton(
        label = "Close",
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MysteryLook(
    slot: DiscoverySlot.Mystery,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                heading()
                contentDescription = slot.spokenLabel
            }
    ) {
        MysteryMark(size = 96.dp)
        Spacer(Modifier.height(AppSpace.betweenCards))
        Text(
            text = "UNDISCOVERED",
            style = AppText.resultTitle.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.8.sp,
                color = Color.White,
                shadow = OnArtShadow
            ),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Spacer(Modifier.height(AppSpace.hair))
        Text(
            text = "Level ${slot.levelId}",
            style = AppText.cardTitle.copy(color = Color.White.copy(alpha = 0.82f))
        )
    }
    Spacer(Modifier.height(AppSpace.betweenSections))
    GamePrimaryButton(label = "Play Level", onClick = onPlay)
    Spacer(Modifier.height(AppSpace.betweenCards))
    GameSecondaryButton(
        label = "Close",
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth()
    )
}
