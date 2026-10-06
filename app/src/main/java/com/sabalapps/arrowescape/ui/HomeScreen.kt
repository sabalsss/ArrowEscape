package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.sabalapps.arrowescape.R
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.daily.DailyProgress
import com.sabalapps.arrowescape.endless.EndlessProgress
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.ui.discovery.DiscoveryArtwork
import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import com.sabalapps.arrowescape.ui.world.GameWorld
import com.sabalapps.arrowescape.ui.world.GameWorlds
import com.sabalapps.arrowescape.ui.world.MenuBackdrop
import com.sabalapps.arrowescape.ui.world.WorldArtImage

/**
 * The front door, built as a game hub: the world the player is currently in
 * fills the screen behind it, and the things they can do float over it as glass.
 *
 * ## Order
 *
 * Ordered by what the player most likely came to do, with the weight of each
 * object saying how much it wants to be pressed:
 *
 *  1. **Continue Exploring** — the biggest, glowing, the only saturated-blue surface
 *     on the screen. Resume the journey. It carries a thumbnail of the world the next
 *     level is played in, so it says where as well as what — and never *what is
 *     hidden there*: the next discovery stays a mystery.
 *  2. **Discoveries** — how many are found, and a few thumbnails of *only* what has
 *     already been found. The collection is what is particular to Arrow Escape, so
 *     it comes straight after the way back into it.
 *  3. **Daily Challenge** — warm gold, because it is the one thing here that
 *     expires. It shows the date and whether today is done. Useful, but secondary
 *     to the campaign's collection, and never competes with Continue: gold-on-cream
 *     against Continue's deep blue.
 *  4. **Game Modes** — Campaign and Endless as white glass cards, each with its own
 *     coloured badge.
 *  5. **Stats** and **Settings** — pinned to the bottom as two dark glass buttons.
 *     They are navigation, not play, so they sit below the content rather than in it.
 *
 * Campaign, Endless and Daily are shown with separate counters because that is
 * exactly how they are stored: nothing here reads one mode's progress to describe
 * another.
 */
/**
 * The blocks of Home's content, in the order they are shown. Stats and Settings are
 * not here: they are pinned below the content, not part of it.
 */
internal enum class HomeSection { Continue, Discoveries, Daily, Modes }

/** Top to bottom. [HomeScreen] lays its content out by walking this list. */
internal val HomeContentOrder: List<HomeSection> = listOf(
    HomeSection.Continue,
    HomeSection.Discoveries,
    HomeSection.Daily,
    HomeSection.Modes
)

@Composable
fun HomeScreen(
    progress: PlayerProgress,
    /** The level Continue would open, whether resuming or starting fresh. */
    resumeLevelId: Int,
    hasSavedGame: Boolean,
    /** Endless counters. Independent of [progress] in both directions. */
    endlessProgress: EndlessProgress,
    /** True when there is an unfinished generated board to drop back into. */
    hasEndlessSavedGame: Boolean,
    /** Daily counters, and what they say about [today]. */
    dailyProgress: DailyProgress,
    /** Read through the injected clock, never from a direct call to the clock here. */
    today: GameDate,
    onContinue: () -> Unit,
    onDaily: () -> Unit,
    onLevelSelect: () -> Unit,
    onEndless: () -> Unit,
    onDiscoveries: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Counted against the catalogue, so a stray completed id can never inflate it.
    val found = CampaignDiscoveries.collectedCount(progress)
    val total = CampaignDiscoveries.totalCount
    val worlds = GameWorld.PROGRESSION.size
    val endlessNumber = endlessProgress.puzzleNumber
    val endlessLabel = if (hasEndlessSavedGame) "Resume Endless #$endlessNumber" else "Endless Mode"
    val dailyDone = dailyProgress.isCompletedOn(today)

    // The world the next board is played in, derived exactly as the game screen
    // derives it, so Home is dressed in the scene the player is about to enter.
    val world = GameWorlds.forCampaignLevel(resumeLevelId)

    MenuBackdrop(world = world, ambient = true) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The content scrolls if a short phone or a large font needs it to,
            // and otherwise sits centred in the room above the pinned buttons,
            // so a tall screen is spaced out rather than left with a dead band.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val room = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = room)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpace.screenH, vertical = AppSpace.tight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BrandMark()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Arrow Escape",
                        style = AppText.brand.onArt(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(AppSpace.hair))
                    Text(
                        text = "Solve. Reveal. Discover.",
                        style = MaterialTheme.typography.bodyMedium.onArt()
                            .copy(color = Color.White.copy(alpha = 0.94f)),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(AppSpace.betweenSections))

                    HomeContentOrder.forEachIndexed { index, section ->
                        // Cards in a run sit a card apart; the Game Modes group, which
                        // has a heading of its own, a section apart.
                        if (index > 0) {
                            Spacer(
                                Modifier.height(
                                    if (section == HomeSection.Modes) {
                                        AppSpace.betweenSections
                                    } else {
                                        AppSpace.betweenCards
                                    }
                                )
                            )
                        }
                        when (section) {
                            HomeSection.Continue -> ContinueHero(
                                levelId = resumeLevelId,
                                resuming = hasSavedGame,
                                fresh = found == 0,
                                world = world,
                                found = found,
                                total = total,
                                onClick = onContinue
                            )

                            HomeSection.Discoveries ->
                                DiscoveriesCard(progress = progress, onClick = onDiscoveries)

                            HomeSection.Daily -> DailyCard(
                                done = dailyDone,
                                today = today,
                                streak = dailyProgress.currentStreak,
                                onClick = onDaily
                            )

                            HomeSection.Modes -> {
                                GameSectionHeader(
                                    text = "Game Modes",
                                    modifier = Modifier
                                        .widthIn(max = MAX_CONTENT_WIDTH)
                                        .fillMaxWidth()
                                        .padding(start = AppSpace.hair)
                                )
                                Spacer(Modifier.height(AppSpace.tight))
                                ModeCard(
                                    title = "Campaign",
                                    subtitle = "$found / $total discovered",
                                    badgeBrush = GameBrush.Blue,
                                    glyph = { MountainGlyph(Color.White, Modifier.size(AppIcon.badge + 4.dp)) },
                                    progress = if (total > 0) found.toFloat() / total else 0f,
                                    description = "Campaign, explore $worlds worlds, $found of $total discoveries found",
                                    onClick = onLevelSelect
                                )
                                Spacer(Modifier.height(10.dp))
                                ModeCard(
                                    title = endlessLabel,
                                    subtitle = "Mystery shapes · #$endlessNumber",
                                    badgeBrush = GameBrush.Teal,
                                    glyph = { InfinityGlyph(Color.White, Modifier.size(AppIcon.badge + 4.dp)) },
                                    progress = null,
                                    description = "$endlessLabel, ${endlessProgress.tier.label}",
                                    onClick = onEndless
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(AppSpace.tight))
                }
            }

            Row(
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH + AppSpace.screenH * 2)
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.screenH)
                    .padding(top = AppSpace.tight, bottom = AppSpace.screenV),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.betweenCards)
            ) {
                NavPill(label = "Stats", onClick = onStats) {
                    BarChartGlyph(Color.White, Modifier.size(AppIcon.medium))
                }
                NavPill(label = "Settings", onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(AppIcon.medium)
                    )
                }
            }
        }
    }
}

/** The app icon as the game's mark. */
@Composable
private fun BrandMark() {
    Image(
        painter = painterResource(R.drawable.app_icon),
        contentDescription = null,
        modifier = Modifier
            .size(112.dp)
            .clearAndSetSemantics {}
    )
}

/**
 * Continue Exploring: the strongest object on the screen. A deep indigo card with
 * the world's artwork fading in from its right edge, a large Play disc, where the
 * journey is up to, and how much of it is found, with the progress as a bar along
 * the bottom.
 *
 * It says *where* the next level is — the level and its world — and never *what is
 * hidden there*. The next mystery stays a mystery on the front door, as everywhere:
 * nothing on this card is derived from the discovery behind the level.
 */
@Composable
private fun ContinueHero(
    levelId: Int,
    resuming: Boolean,
    fresh: Boolean,
    world: GameWorld,
    found: Int,
    total: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AppRadius.hero)
    val title = if (!resuming && fresh) "Start Exploring" else "Continue Exploring"
    val description = (if (resuming) {
        "$title, resume Level $levelId"
    } else {
        "$title, start Level $levelId"
    }) + ", ${world.displayName}, $found of $total discoveries found"
    val base = Color(0xFF2236B8)
    Box(
        modifier = Modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .gameClickable(onClick = onClick, pressedScale = 0.98f)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .shadow(
                elevation = AppElevation.hero,
                shape = shape,
                clip = false,
                ambientColor = GamePalette.ElectricBlue.copy(alpha = 0.45f),
                spotColor = GamePalette.ElectricBlue.copy(alpha = 0.90f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B2A97), Color(0xFF2C41D6), Color(0xFF17217A))
                )
            )
            .border(
                AppStroke.emphasis,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.14f))
                ),
                shape
            )
    ) {
        // The world, fading in from the right.
        Row(modifier = Modifier.matchParentSize()) {
            Spacer(Modifier.weight(0.34f))
            Box(modifier = Modifier.weight(0.66f).fillMaxHeight()) {
                WorldArtImage(
                    world = world,
                    sampleSize = 4,
                    modifier = Modifier.fillMaxSize().alpha(0.92f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.00f to base,
                                0.50f to base.copy(alpha = 0.55f),
                                1.00f to Color.Transparent
                            )
                        )
                )
            }
        }
        // Light from above, and a settling shadow below for the progress bar.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.White.copy(alpha = 0.20f),
                        0.45f to Color.Transparent,
                        1.00f to GamePalette.Navy.copy(alpha = 0.40f)
                    )
                )
        )

        Column(modifier = Modifier.padding(horizontal = AppSpace.card, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .softShadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(Color.White, Color(0xFFD5E1FF)))
                        )
                        .border(AppStroke.emphasis, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = GamePalette.BlueBottom,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AppText.resultTitle.copy(
                            fontSize = 22.sp,
                            lineHeight = 25.sp,
                            color = Color.White,
                            shadow = OnArtShadow
                        ),
                        // Two lines rather than an ellipsis on a narrow phone: this
                        // is the card's name.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Level $levelId · ${world.displayName}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.Medium,
                            shadow = OnArtShadow
                        ),
                        // Two lines rather than an ellipsis on a 320dp phone: the
                        // world name is the part of this card that says *where*.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$found / $total discoveries",
                        style = AppText.caption.copy(
                            color = GamePalette.GoldLight,
                            fontWeight = FontWeight.Bold,
                            shadow = OnArtShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GameProgressBar(
                fraction = if (total > 0) found.toFloat() / total else 0f,
                height = 9.dp,
                fill = Brush.horizontalGradient(listOf(Color.White, GamePalette.Cyan)),
                trackColor = Color.White.copy(alpha = 0.26f)
            )
        }
    }
}

/**
 * Discoveries: how many are found, and — as small overlapping stickers — the latest
 * few. **Only ever things already found**; with none found there is a single "?" and
 * nothing that says what is waiting. Quieter than Continue by design: this is the way
 * into the album, not the next thing to do.
 *
 * "Latest" is the highest cleared levels, which is exactly the order the Campaign
 * was found in (it unlocks strictly in sequence), so nothing was recorded to know it.
 */
@Composable
private fun DiscoveriesCard(progress: PlayerProgress, onClick: () -> Unit) {
    val found = CampaignDiscoveries.collectedCount(progress)
    val total = CampaignDiscoveries.totalCount
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.FAINT)
    val complete = found == total && total > 0
    val description = if (complete) {
        "Discoveries, all $total found, view collection"
    } else {
        "Discoveries, $found of $total found, view collection"
    }
    GameGlassCard(
        onClick = onClick,
        contentDescription = description
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // What is left for stickers once the badge, the words and the chevron
            // have theirs. Each sticker after the first overlaps the last, so the
            // pile grows by less than its own width.
            val room = maxWidth - 28.dp - AppIcon.badgeBox - 14.dp - 112.dp - AppSpace.tight - 28.dp
            val stickers = (((room - THUMB_SIZE) / THUMB_STEP).toInt() + 1).coerceIn(0, MAX_THUMBS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameBadge(brush = if (complete) GameBrush.Orange else GameBrush.Purple) {
                    SparkleGlyph(Color.White, Modifier.size(AppIcon.badge + 4.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Discoveries",
                        style = AppText.cardTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (complete) "ALL FOUND" else "$found / $total FOUND",
                        style = AppText.caption.copy(
                            color = if (complete) GamePalette.GoldDeep else GamePalette.ElectricBlue,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    GameProgressBar(
                        fraction = if (total > 0) found.toFloat() / total else 0f,
                        height = 6.dp,
                        fill = if (complete) {
                            Brush.horizontalGradient(listOf(GamePalette.GoldDeep, GamePalette.Gold))
                        } else {
                            Brush.horizontalGradient(listOf(GamePalette.Cyan, GamePalette.ElectricBlue))
                        },
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                }
                if (stickers > 0) {
                    Spacer(Modifier.width(AppSpace.tight))
                    LatestStickers(progress = progress, count = stickers)
                }
                Spacer(Modifier.width(AppSpace.hair))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = faint,
                    modifier = Modifier.size(AppIcon.badge + 2.dp)
                )
            }
        }
    }
}

private val THUMB_SIZE = 38.dp

/** How far each sticker after the first sits from the last. */
private val THUMB_STEP = 26.dp
private const val MAX_THUMBS = 4

/**
 * Up to [count] small stickers of what has been found, oldest to newest, so the
 * newest sits on top of the pile. With nothing found it is one "?": an empty album,
 * not a preview of what is in it. Decorative — the card's own label speaks the count.
 */
@Composable
private fun LatestStickers(progress: PlayerProgress, count: Int) {
    val latest = CampaignDiscoveries.latestCollected(progress, count).asReversed()
    val width = if (latest.isEmpty()) THUMB_SIZE else THUMB_SIZE + THUMB_STEP * (latest.size - 1)
    Box(
        modifier = Modifier
            .width(width)
            .height(THUMB_SIZE)
            .clearAndSetSemantics {}
    ) {
        if (latest.isEmpty()) {
            MysteryMark(
                size = THUMB_SIZE - 4.dp,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        latest.forEachIndexed { index, discovery ->
            Box(
                modifier = Modifier
                    .padding(start = THUMB_STEP * index)
                    .size(THUMB_SIZE)
                    .softShadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFE5ECFF)))
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                DiscoveryArtwork(discovery = discovery, modifier = Modifier.size(THUMB_SIZE - 8.dp))
            }
        }
    }
}

/**
 * The Daily Challenge, as a gold ticket. It shows the date and whether today is
 * done, which is the whole reason it is a card: a button cannot carry those two
 * facts, and they are the point of a daily. There is no countdown — a ticking
 * clock is a pressure device, and this is a puzzle a day.
 *
 * Done is marked in green, with a check that springs in; undone is simply the
 * invitation. Either way it stays cream beside Continue's deep blue, so it is
 * never the louder of the two.
 */
@Composable
private fun DailyCard(done: Boolean, today: GameDate, streak: Int, onClick: () -> Unit) {
    val state = if (done) "Completed today" else "Today's mystery shape"
    val streakNote = when {
        streak <= 0 -> null
        streak == 1 -> "1 day streak"
        else -> "$streak day streak"
    }
    val description = buildString {
        append("Daily Challenge, ${today.friendly()}, $state")
        streakNote?.let { append(", $it") }
    }
    GameGlassCard(
        tone = GlassTone.Gold,
        elevation = AppElevation.raised,
        onClick = onClick,
        contentDescription = description
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DailyBadge(done = done)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Challenge",
                    style = AppText.cardTitle.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GamePalette.Brown
                    ),
                    // Wraps to two lines on the narrowest phones rather than
                    // ellipsising the name of the card.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (done) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = GamePalette.GreenDeep,
                            modifier = Modifier.size(AppIcon.small)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = state,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = GamePalette.GreenDeep,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = state,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF9A4A00),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = today.friendly(),
                    style = AppText.caption.copy(color = GamePalette.Brown.copy(alpha = 0.62f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (streakNote != null) {
                Spacer(Modifier.width(AppSpace.tight))
                // The card's description already says "N day streak", so the chip
                // reads nothing of its own.
                StreakChip(streak = streak, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

/** The calendar badge, with a green check that springs onto it once today is done. */
@Composable
private fun DailyBadge(done: Boolean) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(done) {
        if (done) {
            pop.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            pop.snapTo(0f)
        }
    }
    Box(modifier = Modifier.size(AppIcon.badgeBox + 6.dp)) {
        GameBadge(
            brush = GameBrush.Orange,
            size = AppIcon.badgeBox,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(AppIcon.badge + 4.dp)
            )
        }
        if (done) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .scale(pop.value)
                    .softShadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(GameBrush.Green)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** The streak count: the flame in its own colours, and a number, in a warm pill. */
@Composable
fun StreakChip(streak: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(Color(0xFFFF8A1F).copy(alpha = 0.16f))
            .border(AppStroke.hairline, Color(0xFFFF8A1F).copy(alpha = 0.45f), shape)
            .padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp)
    ) {
        FlameGlyph(modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$streak",
            style = AppText.button.copy(
                color = Color(0xFF9A3F00),
                fontWeight = FontWeight.ExtraBold
            ),
            maxLines = 1
        )
    }
}

/**
 * One of the two ongoing tracks, as a glass game card: a gradient badge in the
 * mode's own colour, the name, one line of state, and a chevron saying it opens
 * something.
 *
 * @param progress drawn as a bar under the text when the track has an end.
 *   Endless has none, so it passes null rather than an invented denominator.
 */
@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    badgeBrush: Brush,
    glyph: @Composable () -> Unit,
    progress: Float?,
    description: String,
    onClick: () -> Unit
) {
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.FAINT)
    GameGlassCard(
        onClick = onClick,
        contentDescription = description
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameBadge(brush = badgeBrush) { glyph() }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppText.cardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(color = faint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress != null) {
                    Spacer(Modifier.height(8.dp))
                    GameProgressBar(
                        fraction = progress,
                        height = 7.dp,
                        fill = Brush.horizontalGradient(
                            listOf(GamePalette.Cyan, GamePalette.ElectricBlue)
                        ),
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                }
            }
            Spacer(Modifier.width(AppSpace.tight))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = faint,
                modifier = Modifier.size(AppIcon.badge + 2.dp)
            )
        }
    }
}

/** One of the two bottom buttons: dark glass, an icon and a word. Equal width. */
@Composable
private fun RowScope.NavPill(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(AppRadius.control)
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = AppSize.navButton)
            .gameClickable(onClick = onClick, pressedScale = 0.96f)
            .softShadow(8.dp, shape)
            .clip(shape)
            .background(GameBrush.Navy)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.24f), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.5f to Color.Transparent
                    )
                )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                style = AppText.button.copy(color = Color.White),
                maxLines = 1
            )
        }
    }
}
