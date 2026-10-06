package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.ui.discovery.DiscoveryArtwork
import com.sabalapps.arrowescape.ui.world.CampaignDiscovery
import com.sabalapps.arrowescape.ui.world.DiscoveryCollection
import com.sabalapps.arrowescape.ui.world.DiscoverySlot
import com.sabalapps.arrowescape.ui.world.GameWorld
import com.sabalapps.arrowescape.ui.world.GameWorlds
import com.sabalapps.arrowescape.ui.world.MenuBackdrop
import com.sabalapps.arrowescape.ui.world.WorldArtImage
import com.sabalapps.arrowescape.ui.world.WorldCollection
import com.sabalapps.arrowescape.ui.world.WorldStyle

private val MAX_GRID_WIDTH = 520.dp

/** Six levels to a world, so six to a row: one chapter, one line of tiles. */
private const val TILES_PER_ROW = GameWorlds.CAMPAIGN_LEVELS_PER_WORLD

/** Below this width the screen's own padding gives way so a tile stays a 48dp target. */
private val NARROW_BELOW = 360.dp

/**
 * How a single level tile should read. Derived, never stored.
 *
 * [PERFECT] is a completed level whose best is all three stars. It is its own state
 * rather than a flag on [COMPLETED] because it is the one result in the game worth
 * finding at a glance across a grid of thirty — the gold tile is the reward for a
 * Perfect Escape, three screens away from where it was earned.
 *
 * Gold therefore means exactly one thing. A level cleared with one or two stars is
 * [COMPLETED] (blue, with the stars it earned), and so is one cleared before stars
 * were recorded (blue, with a tick): neither is ever gold.
 */
internal enum class TileState { LOCKED, UNLOCKED, COMPLETED, PERFECT }

/**
 * The state of [levelId]'s tile. [PERFECT] when, and only when, the level is cleared
 * and its best is [StarRating.MAX] stars; the rule lives here, in one place, so the
 * tile colour and the star row beneath it cannot disagree.
 */
internal fun tileStateFor(progress: PlayerProgress, levelId: Int): TileState = when {
    !progress.isUnlocked(levelId) -> TileState.LOCKED
    !progress.isCompleted(levelId) -> TileState.UNLOCKED
    progress.starsFor(levelId) == StarRating.MAX -> TileState.PERFECT
    else -> TileState.COMPLETED
}

/**
 * The level grid, as five worlds to explore.
 *
 * Each world is a band with its own artwork behind it — the same five pictures
 * the game is played over — so the screen reads as a journey: Sky Garden done,
 * Forest done, you are in Sunset Canyon, Crystal Night is dark and locked, Cosmic
 * waits. A world the player has not reached yet is the same picture drained of
 * colour and dimmed, which says "not yet" before a single padlock is noticed.
 *
 * It is also where the discoveries are *seen* growing. A world's heading counts how
 * many of its six are found ("4 / 6 DISCOVERED", then a gold COMPLETE), a level
 * that has been cleared carries a miniature of what it hid, and a level that is
 * open but not cleared carries a "?" — and nothing else: the mystery tile has no
 * name, no picture and no hint of what kind of thing it is, and neither does a
 * locked one. Those two are built from `DiscoverySlot`s that cannot hold an
 * identity, so there is no code path here that could draw one.
 *
 * Locked levels are visibly inert and are **not clickable**, so a tap on one does
 * nothing at all rather than opening something and bouncing back. Nothing here
 * hints at a level's solution — only its number and status.
 *
 * ## Tiles
 *
 * Small, glossy and six to a line. Five looks, one per state: gold for a Perfect
 * Escape, blue for a clear, a bright lit tile with an electric ring for the level
 * the player is on, a light world-edged tile for one that is open but untried,
 * and a dark glass tile with a padlock for one that is not. A cleared tile is the
 * number, the discovery, the stars; an open one is the number and a "?". Where the player is
 * and what they have done stay separately readable: the current level breathes
 * very slowly (unless animations are off), and nothing else on the screen moves.
 *
 * A tile's touch target is its whole grid cell — the visible tile is inset inside
 * it — and a cell is never under 48dp wide, which is why padding gives way on a
 * narrow phone rather than the tiles.
 */
@Composable
fun LevelSelectScreen(
    progress: PlayerProgress,
    onLevelChosen: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the album. A second way in; Home is the main one. */
    onOpenDiscoveries: () -> Unit = {}
) {
    val levels = LevelProgression.all
    val listState = rememberLazyListState()
    // What the player may know about each level's discovery. Everything below that
    // draws a discovery goes through these slots.
    val collection = remember(progress) { DiscoveryCollection.of(progress) }

    // The catalogue split into its world sections, in progression order, asking
    // `GameWorlds` which world each level belongs to rather than re-deriving the
    // boundaries here.
    val sections = remember(levels) {
        GameWorld.PROGRESSION
            .map { world -> world to levels.filter { GameWorlds.forCampaignLevel(it.id) == world } }
            .filter { (_, group) -> group.isNotEmpty() }
    }

    // Open on the chapter the player is up to rather than always at the top. One
    // item per section, so the index is exact.
    val targetIndex = remember(sections, progress.currentLevel) {
        sections.indexOfFirst { (_, group) -> group.any { it.id == progress.currentLevel } }
            .coerceAtLeast(0)
    }
    LaunchedEffect(targetIndex) {
        if (targetIndex > 0) listState.scrollToItem(targetIndex)
    }

    val found = collection.collected
    val starsEarned = remember(progress) {
        levels.sumOf { progress.starsFor(it.id).coerceAtLeast(0) }
    }
    val currentWorld = GameWorlds.forCampaignLevel(progress.currentLevel)

    MenuBackdrop(world = currentWorld, dim = 0.10f) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val narrow = maxWidth < NARROW_BELOW
            val screenPad = if (narrow) 8.dp else 12.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(
                    title = "Select a Level",
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = screenPad),
                    trailing = {
                        if (starsEarned > 0) StarPill(count = starsEarned)
                    }
                )

                Column(
                    modifier = Modifier
                        .widthIn(max = MAX_GRID_WIDTH)
                        .fillMaxWidth()
                        .padding(horizontal = screenPad + 8.dp)
                        .padding(top = AppSpace.hair, bottom = AppSpace.tight)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppIcon.touch)
                            .gameClickable(onClick = onOpenDiscoveries, pressedScale = 0.98f)
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (collection.allFound) {
                                    "All ${collection.total} discoveries found, view discoveries"
                                } else {
                                    "$found of ${collection.total} discovered, view discoveries"
                                }
                            }
                    ) {
                        Text(
                            text = if (collection.allFound) {
                                "All discoveries found"
                            } else {
                                "$found of ${collection.total} discovered"
                            },
                            style = AppText.caption.copy(
                                color = Color.White.copy(alpha = 0.94f),
                                fontWeight = FontWeight.SemiBold,
                                shadow = OnArtShadow
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Discoveries",
                            style = AppText.caption.copy(
                                color = GamePalette.GoldLight,
                                fontWeight = FontWeight.Bold,
                                shadow = OnArtShadow
                            )
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GamePalette.GoldLight,
                            modifier = Modifier.size(AppIcon.medium)
                        )
                    }
                    GameProgressBar(
                        fraction = if (collection.total > 0) found.toFloat() / collection.total else 0f,
                        height = 8.dp
                    )
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(AppSpace.betweenCards + 2.dp),
                    contentPadding = PaddingValues(
                        start = screenPad,
                        end = screenPad,
                        top = AppSpace.tight,
                        bottom = 24.dp
                    ),
                    modifier = Modifier
                        .widthIn(max = MAX_GRID_WIDTH)
                        .fillMaxWidth()
                ) {
                    items(sections, key = { it.first.id }) { (world, group) ->
                        WorldSection(
                            world = world,
                            group = group,
                            collection = collection.worlds.first { it.world == world },
                            progress = progress,
                            narrow = narrow,
                            onLevelChosen = onLevelChosen
                        )
                    }
                }
            }
        }
    }
}

/**
 * One world, as a chapter band: its artwork behind a navy scrim, a heading, and
 * its levels in rows of six.
 *
 * The band draws its own artwork rather than sharing one big image, so each
 * chapter looks like the place it is — and a world the player has not reached is
 * drained of colour and dimmed. The scrim is stronger than on Home because there
 * is a lot of small type and a lot of small tiles straight over the picture. A world
 * with all six found keeps the band and turns its edge gold, beside a COMPLETE chip.
 */
@Composable
private fun WorldSection(
    world: GameWorld,
    group: List<Level>,
    collection: WorldCollection,
    progress: PlayerProgress,
    narrow: Boolean,
    onLevelChosen: (Int) -> Unit
) {
    val accent = WorldStyle.of(world).accent
    val reached = group.any { progress.isUnlocked(it.id) }
    val complete = collection.isComplete
    val shape = RoundedCornerShape(AppRadius.card)
    val pad = if (narrow) 6.dp else 10.dp
    val gap = if (narrow) 4.dp else 6.dp

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
            world = world,
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
                            GamePalette.Navy.copy(alpha = if (reached) 0.40f else 0.66f),
                            GamePalette.Navy.copy(alpha = if (reached) 0.64f else 0.84f)
                        )
                    )
                )
        )
        if (complete) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to GamePalette.Gold.copy(alpha = 0.16f),
                            0.4f to Color.Transparent
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(horizontal = pad, vertical = 10.dp)) {
            WorldDiscoveryHeader(
                collection = collection,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(6.dp))
            group.chunked(TILES_PER_ROW).forEach { rowLevels ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowLevels.forEach { level ->
                        val stars = progress.starsFor(level.id)
                        val slot = collection.slots.firstOrNull { it.levelId == level.id }
                        LevelTile(
                            level = level,
                            state = tileStateFor(progress, level.id),
                            // The identity, and only for a level that is cleared: a
                            // mystery or locked slot has none to hand over.
                            discovery = (slot as? DiscoverySlot.Collected)?.discovery,
                            // Nothing for a locked level, and nothing for a level
                            // cleared before stars existed — see `PlayerProgress`.
                            stars = stars,
                            isCurrent = level.id == progress.currentLevel,
                            worldAccent = accent,
                            inset = gap / 2,
                            onClick = { onLevelChosen(level.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // A short final row keeps its tiles the same size as the rest.
                    repeat(TILES_PER_ROW - rowLevels.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelTile(
    level: Level,
    state: TileState,
    /** What the level hid, for a cleared level; null for every other state. */
    discovery: CampaignDiscovery?,
    stars: Int,
    isCurrent: Boolean,
    worldAccent: Color,
    inset: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unlocked = state != TileState.LOCKED
    val completed = state == TileState.COMPLETED || state == TileState.PERFECT
    // The lit "you are here" tile is the open, untried level the player is on.
    // If they are on a level they have already cleared, it keeps its earned look
    // and gains the ring.
    val lit = isCurrent && state == TileState.UNLOCKED
    // Only a completed level has a rating to show, and only one cleared since
    // stars existed has one on record.
    val rated = StarRating.isValid(stars) && completed

    // What a screen reader may say. A cleared level names what it uncovered; an open
    // one says only that it is undiscovered; a locked one says only that it is locked.
    val label = when {
        state == TileState.LOCKED -> "Level ${level.id}, locked"
        completed && discovery != null && rated ->
            "Level ${level.id}, ${discovery.name} discovered, $stars of ${StarRating.MAX} stars"
        completed && discovery != null -> "Level ${level.id}, ${discovery.name} discovered"
        completed && rated -> "Level ${level.id}, completed, $stars of ${StarRating.MAX} stars"
        completed -> "Level ${level.id}, completed"
        else -> "Level ${level.id}, undiscovered"
    } + when {
        isCurrent && state == TileState.UNLOCKED -> ", current level. This is your next mystery."
        isCurrent -> ", current level"
        state == TileState.UNLOCKED -> ". This is your next mystery."
        else -> ""
    }

    val shape = RoundedCornerShape(AppRadius.levelTile)
    val fill: Brush = when {
        state == TileState.LOCKED ->
            Brush.verticalGradient(listOf(GamePalette.Navy.copy(alpha = 0.52f), GamePalette.Navy.copy(alpha = 0.66f)))
        state == TileState.PERFECT ->
            Brush.verticalGradient(listOf(Color(0xFFFFE790), Color(0xFFFFB92E)))
        state == TileState.COMPLETED ->
            Brush.verticalGradient(listOf(Color(0xFF5B86FF), Color(0xFF2C41D6)))
        lit ->
            Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE7FF)))
        else ->
            Brush.verticalGradient(listOf(Color(0xFFF8FAFF), Color(0xFFE3E9FA)))
    }
    val numberColor = when {
        state == TileState.LOCKED -> Color.White.copy(alpha = 0.40f)
        state == TileState.PERFECT -> GamePalette.Brown
        state == TileState.COMPLETED -> Color.White
        lit -> GamePalette.BlueBottom
        else -> Color(0xFF1B2260)
    }

    // The ring breathes, extremely slowly, and only for the lit tile. It stands
    // down completely with reduced motion: a steady ring is just as findable.
    val reducedMotion = rememberReducedMotion()
    val breath = if (lit && !reducedMotion) {
        rememberInfiniteTransition(label = "currentTile").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "currentBreath"
        )
    } else {
        null
    }

    val ringColor: Color? = when {
        lit -> GamePalette.ElectricBlue
        isCurrent && unlocked -> Color.White
        else -> null
    }

    Box(
        modifier = modifier
            // A little taller than wide: a cleared tile holds a number, a picture and
            // three stars, and the picture needs somewhere to be.
            .aspectRatio(TILE_ASPECT)
            .then(
                if (unlocked) {
                    // The whole cell is the target; the tile is inset inside it.
                    Modifier.gameClickable(onClick = onClick, pressedScale = 0.9f)
                } else {
                    // Not clickable at all: a locked tap is inert by construction
                    // rather than by an early return somewhere downstream.
                    Modifier.semantics { disabled() }
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .then(
                    if (lit) {
                        // A soft halo in the world's own light, breathing with the ring.
                        Modifier.drawBehind {
                            val grow = 5.dp.toPx()
                            val a = breath?.value ?: 0.8f
                            drawRoundRect(
                                color = GamePalette.ElectricBlue.copy(alpha = 0.22f * a),
                                topLeft = Offset(-grow, -grow),
                                size = Size(size.width + grow * 2, size.height + grow * 2),
                                cornerRadius = CornerRadius(AppRadius.levelTile.toPx() + grow)
                            )
                            drawRoundRect(
                                color = GamePalette.Cyan.copy(alpha = 0.20f * a),
                                topLeft = Offset(-grow / 2, -grow / 2),
                                size = Size(size.width + grow, size.height + grow),
                                cornerRadius = CornerRadius(AppRadius.levelTile.toPx() + grow / 2)
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .softShadow(if (unlocked) 4.dp else 0.dp, shape)
                .clip(shape)
                .background(fill)
                .border(
                    width = when {
                        ringColor != null -> AppStroke.ring
                        state == TileState.LOCKED -> AppStroke.hairline
                        else -> AppStroke.emphasis
                    },
                    color = when {
                        ringColor != null -> ringColor.copy(
                            alpha = if (lit) 0.55f + 0.45f * (breath?.value ?: 1f) else 1f
                        )
                        state == TileState.LOCKED -> Color.White.copy(alpha = 0.10f)
                        state == TileState.PERFECT -> Color.White.copy(alpha = 0.85f)
                        state == TileState.COMPLETED -> Color.White.copy(alpha = 0.50f)
                        else -> worldAccent.copy(alpha = 0.85f)
                    },
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (unlocked) {
                // The lit upper half that makes a tile read as glossy and raised.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (completed) 0.26f else 0.45f),
                                0.5f to Color.Transparent
                            )
                        )
                )
            }
            if (state == TileState.LOCKED) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(AppIcon.medium)
                )
            } else if (completed && discovery != null) {
                // The number first, so it is still the first thing the eye finds;
                // then the discovery, as large as the room allows; then the stars.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${level.id}",
                        style = AppText.cardTitle.copy(
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = numberColor
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        DiscoveryArtwork(
                            discovery = discovery,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(1.dp)
                        )
                    }
                    if (rated) {
                        TileStars(earned = stars, perfect = state == TileState.PERFECT)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = numberColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${level.id}",
                        style = AppText.cardTitle.copy(
                            fontSize = 17.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = numberColor
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (state == TileState.UNLOCKED) {
                        // Open but not cleared: a mystery, and only that.
                        Spacer(Modifier.height(3.dp))
                        MysteryMark(
                            size = 18.dp,
                            tint = numberColor,
                            filled = false,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    } else if (completed) {
                        // A cleared level that has no discovery (not a catalogue
                        // level) keeps the tick, as it always did — unless it has a
                        // rating, so a gold tile can never be drawn without its stars.
                        if (rated) {
                            Spacer(Modifier.height(3.dp))
                            TileStars(earned = stars, perfect = state == TileState.PERFECT)
                        } else {
                            Spacer(Modifier.height(1.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = numberColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Width over height of a level tile. */
private const val TILE_ASPECT = 0.82f

/**
 * The best stars on a completed tile: three small glyphs under the level number,
 * earned ones gold and the rest a faint ghost, so "three of three" and "one of
 * three" are told apart at a glance without reading anything. On the gold tile that
 * only a three-star best earns, all three are solid brown.
 *
 * The tile's own contentDescription already says the count, so this contributes
 * no semantics.
 */
@Composable
private fun TileStars(earned: Int, perfect: Boolean) {
    val lit = earned.coerceIn(0, StarRating.MAX)
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.5.dp),
        modifier = Modifier.clearAndSetSemantics {}
    ) {
        for (slot in 0 until StarRating.MAX) {
            val isLit = slot < lit
            StarGlyph(
                fill = when {
                    !isLit -> Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.18f))
                    )
                    // On a gold tile a pale star vanishes into the gold — and reads as an
                    // unearned one — so a perfect tile's stars are the deep brown its
                    // number is: three solid stars, unmistakably all earned. Anywhere
                    // else they carry the gold themselves.
                    perfect -> Brush.verticalGradient(listOf(Color(0xFF8A5600), GamePalette.Brown))
                    else -> Brush.verticalGradient(listOf(GamePalette.GoldLight, GamePalette.Gold))
                },
                outline = when {
                    !isLit || perfect -> null
                    else -> GamePalette.Navy.copy(alpha = 0.40f)
                },
                modifier = Modifier.size(9.dp)
            )
        }
    }
}
