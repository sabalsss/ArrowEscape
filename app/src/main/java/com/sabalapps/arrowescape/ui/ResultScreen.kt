package com.sabalapps.arrowescape.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.ui.discovery.RevealSchedule
import com.sabalapps.arrowescape.ui.world.DiscoveryResult
import com.sabalapps.arrowescape.ui.world.GameWorld
import com.sabalapps.arrowescape.ui.world.WorldStyle
import kotlinx.coroutines.delay

/**
 * Which puzzle the screen is reporting on. The screen is shared between the modes
 * because the outcome is the same event either way; only the wording, the panel
 * and what comes next differ.
 */
sealed interface ResultContext {

    /**
     * A numbered level. The last one in the catalogue ends the campaign.
     *
     * [stars] is what this run earned, 1-3, or [StarRating.NONE] when there is
     * nothing to rate — every loss, and any caller that is not rating the run.
     * [bestStars] is the record after this run, so it is never lower than
     * [stars]; [isNewBest] is true only when this run raised it.
     *
     * [discovery] is what the cleared level was hiding, and is what turns a win into
     * a reveal: the result is then the discovery first and the stars after it. Null
     * on a loss and for any level that hides nothing, which keep the plain layout.
     */
    data class Campaign(
        val isLastLevel: Boolean,
        val stars: Int = StarRating.NONE,
        val bestStars: Int = StarRating.NONE,
        val isNewBest: Boolean = false,
        val discovery: DiscoveryResult? = null
    ) : ResultContext

    /** A generated puzzle. There is no last one, so there is always a next. */
    data class Endless(val puzzleNumber: Int, val tierLabel: String) : ResultContext

    /**
     * The day's challenge. There is no next one until tomorrow, so a win offers
     * Play Again and Home and never a Next button — generating another board on
     * demand is exactly what a daily must not do.
     *
     * [alreadyCountedToday] is true when the day was already in the books before
     * this clear, which is what stops a replay reporting a streak it did not earn.
     */
    data class Daily(
        val tierLabel: String,
        val streak: Int,
        val bestStreak: Int,
        val alreadyCountedToday: Boolean
    ) : ResultContext

    /** The lesson, replayed from Settings. It goes back where it came from. */
    data object Tutorial : ResultContext
}

/** How long the stars take to arrive, and when the ribbon follows. */
private const val STAR_REVEAL_MS = 520
private const val STAR_START_MS = 200L
private const val PERFECT_DELAY_MS = 640L

/**
 * End-of-board screen, for both outcomes and every mode.
 *
 * It is a full-screen layer over the world — not a dialog — so the world the
 * player was just playing in stays visible behind a deep scrim, and the result
 * reads as a moment in the game rather than a window on top of it. The gameplay
 * controls do not stay: `GameScreen` fades the HUD, board and footer out as this
 * layer arrives, so only the world artwork is behind the scrim. A win is a
 * celebration: a headline, three big stars (the third raised, the lot springing
 * in one after another), a PERFECT ESCAPE ribbon for a flawless clear, a summary
 * panel and one large blue Next Level. A loss is quieter and darker but never
 * ugly: three dimmed hearts, the same panel, and a warm coral Retry that the
 * player should want to press.
 *
 * Only what the game reliably knows is shown. Arrows cleared and blocked taps are
 * facts of the board (blocked taps is exactly the lives lost, since each blocked
 * tap costs one); the rating and the hint line are Campaign-only, because that is
 * the only mode that rates a run or persists the hint. There is no score, no
 * move count and no time, because none is tracked.
 *
 * Nothing about *when* this appears changed: a win still waits for the existing
 * celebration, and a loss still shows at once. Back does what it always did —
 * close to Home where there is no level grid, and nothing where there is.
 *
 * @param modifier carries the system-bar insets, so the scrim fills the screen
 *   while the content stays clear of the bars.
 * @param hintUsed null where the game does not track it reliably.
 * @param dailyDate the friendly date, for a Daily win's completion layout.
 */
@Composable
fun ResultScreen(
    won: Boolean,
    livesLeft: Int,
    arrowsCleared: Int,
    arrowsTotal: Int,
    hintUsed: Boolean?,
    world: GameWorld,
    heading: String,
    subheading: String,
    dailyDate: String?,
    context: ResultContext,
    onContinue: () -> Unit,
    onReplay: () -> Unit,
    onLevelSelect: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the Discoveries album; what the last level offers instead of "next". */
    onViewDiscoveries: () -> Unit = {},
    /**
     * Where the finished board's picture was, in this layer's coordinates, so a
     * discovery can emerge from there. Null when it is not known; nothing breaks.
     */
    revealFrom: Offset? = null,
    /** What finishing turned into; picks how long the scrim and the board's fade take. */
    kind: CompletionKind = CompletionKind.Plain,
    /**
     * The solved Daily / Endless shape, once the board is won: its name and the outline it was traced
     * to. Null for every other result — and null *is* "nothing to say yet", so a result that has no
     * shape falls back to the plain card instead of inventing one.
     */
    shape: ShapeResult? = null
) {
    val endless = context as? ResultContext.Endless
    val daily = context as? ResultContext.Daily
    val tutorial = context == ResultContext.Tutorial
    val campaign = context as? ResultContext.Campaign
    // Stars are a Campaign win and nothing else. A loss carries no rating.
    val stars = campaign?.stars?.takeIf { won && StarRating.isValid(it) }
    // Clearing the last level is the end of the catalogue, so that card offers a
    // replay and the level grid instead of a Next button that goes nowhere.
    val finished = won && campaign != null && campaign.isLastLevel
    // Every mode without a level grid behind it goes Home instead, and back
    // closes the screen the same way rather than being swallowed.
    val homeBound = endless != null || daily != null || tutorial

    // The layer sits above the board, so it has to answer back itself: the app's
    // own back handler would otherwise pop the game screen out from under a result.
    BackHandler(enabled = true) { if (homeBound) onHome() }

    val reducedMotion = rememberReducedMotion()
    // A Campaign win that hides a discovery is a reveal: it brings its own scrim up
    // on the reveal's clock and times everything on it itself (see
    // `DiscoveryResultContent`), so the layer-wide fade and rise are not applied.
    val discovery = campaign?.discovery?.takeIf { won }
    val shapeResult = shape?.takeIf { won && (daily != null || endless != null) }
    // Either reveal brings its own scrim up and times everything on its own clock.
    val revealing = discovery != null || shapeResult != null
    val schedule = remember(reducedMotion) { RevealSchedule.forMotion(reducedMotion) }
    val shapeSchedule = remember(reducedMotion) { ShapeRevealSchedule.forMotion(reducedMotion) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // The same span the gameplay chrome behind this layer fades out over.
        val ms = resultEnterMs(kind, reducedMotion)
        enter.animateTo(1f, tween(ms, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!revealing) Modifier.graphicsLayer { alpha = enter.value } else Modifier)
            // Swallow every touch, so nothing reaches the finished board below.
            .pointerInput(Unit) { detectTapGestures { } }
            .then(if (!revealing) Modifier.background(resultScrim(won)) else Modifier)
    ) {
        if (revealing) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = enter.value }
                    .background(resultScrim(won))
            )
        }
        // The mood light: warm gold over a win, a low coral smoulder over a loss,
        // centred high so it sits behind the headline and the stars.
        val mood = (if (won) GamePalette.Gold else GamePalette.Coral)
            .copy(alpha = if (won) 0.22f else 0.11f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (revealing) Modifier.graphicsLayer { alpha = enter.value } else Modifier)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(mood, Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.28f),
                            radius = size.width * 1.15f
                        )
                    )
                }
        )

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val room = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = room)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .then(
                        if (revealing) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer {
                                val e = enter.value
                                translationY = (1f - e) * 36.dp.toPx()
                                scaleX = 0.94f + 0.06f * e
                                scaleY = 0.94f + 0.06f * e
                            }
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (discovery != null) {
                    DiscoveryResultContent(
                        discovery = discovery,
                        campaign = campaign,
                        stars = stars,
                        finished = finished,
                        schedule = schedule,
                        reducedMotion = reducedMotion,
                        room = room,
                        revealFrom = revealFrom,
                        onContinue = onContinue,
                        onReplay = onReplay,
                        onLevelSelect = onLevelSelect,
                        onViewDiscoveries = onViewDiscoveries
                    )
                } else if (shapeResult != null) {
                    ShapeResultContent(
                        shape = shapeResult,
                        world = world,
                        daily = daily,
                        endless = endless,
                        dailyDate = dailyDate.orEmpty(),
                        schedule = shapeSchedule,
                        reducedMotion = reducedMotion,
                        room = room,
                        revealFrom = revealFrom,
                        onContinue = onContinue,
                        onReplay = onReplay,
                        onHome = onHome
                    )
                } else if (won && daily != null) {
                    DailyCompletedContent(
                        date = dailyDate.orEmpty(),
                        streak = daily.streak,
                        bestStreak = daily.bestStreak,
                        message = resultMessage(won, livesLeft, finished, context),
                        onPlayAgain = onReplay,
                        onHome = onHome
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ResultTitle(
                            text = when {
                                finished -> "All Levels Complete!"
                                won && tutorial -> "That is the whole rule"
                                won && endless != null -> "Puzzle Complete!"
                                won -> "Level Complete!"
                                else -> "Game Over"
                            },
                            won = won
                        )

                        if (!won) {
                            Spacer(Modifier.height(AppSpace.betweenCards))
                            LifeHearts(livesLeft = livesLeft)
                        }

                        if (stars != null) {
                            Spacer(Modifier.height(AppSpace.betweenCards))
                            BigStars(earned = stars)
                            when {
                                // The Perfect Escape is the one line worth its own
                                // billing, so it gets a ribbon and the others do not.
                                StarRating.isPerfect(stars) -> {
                                    PerfectRibbon(modifier = Modifier.offset(y = (-16).dp))
                                    Spacer(Modifier.height(AppSpace.tight - 6.dp))
                                }

                                campaign.isNewBest -> {
                                    Spacer(Modifier.height(AppSpace.tight))
                                    StarNote("New best")
                                }

                                campaign.bestStars > stars -> {
                                    // Replaying a level below your own record keeps
                                    // the record, and says so rather than quietly
                                    // looking like a downgrade.
                                    Spacer(Modifier.height(AppSpace.tight))
                                    StarNote("Best ${"★".repeat(campaign.bestStars)} kept")
                                }
                            }
                        }

                        Spacer(Modifier.height(AppSpace.betweenCards + 2.dp))
                        SummaryPanel(
                            world = world,
                            heading = heading,
                            subheading = subheading,
                            won = won,
                            stars = stars,
                            arrowsCleared = arrowsCleared,
                            arrowsTotal = arrowsTotal,
                            blockedTaps = (GameState.STARTING_LIVES - livesLeft).coerceAtLeast(0),
                            hintUsed = hintUsed
                        )

                        Spacer(Modifier.height(AppSpace.tight + 2.dp))
                        Text(
                            text = resultMessage(won, livesLeft, finished, context),
                            style = MaterialTheme.typography.bodyMedium.onArt()
                                .copy(color = Color.White.copy(alpha = 0.86f)),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(AppSpace.betweenSections))
                        Actions(
                            won = won,
                            finished = finished,
                            campaign = campaign != null,
                            endless = endless != null,
                            tutorial = tutorial,
                            onContinue = onContinue,
                            onReplay = onReplay,
                            onLevelSelect = onLevelSelect,
                            onHome = onHome
                        )
                    }
                }
            }
        }
    }
}

/** A deep navy over the world: lighter for a win, darker for a loss. */
private fun resultScrim(won: Boolean): Brush = Brush.verticalGradient(
    listOf(
        GamePalette.Navy.copy(alpha = if (won) 0.66f else 0.76f),
        GamePalette.Navy.copy(alpha = if (won) 0.80f else 0.88f)
    )
)

@Composable
internal fun ResultTitle(text: String, won: Boolean) {
    Text(
        text = text,
        style = AppText.resultTitle.copy(
            color = Color.White,
            // A warm glow on a win; a plain drop shadow on a loss.
            shadow = if (won) {
                Shadow(GamePalette.Gold.copy(alpha = 0.75f), Offset.Zero, 28f)
            } else {
                OnArtShadow
            }
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
}

/** What the player can do next, with the one obvious thing filled. */
@Composable
private fun Actions(
    won: Boolean,
    finished: Boolean,
    campaign: Boolean,
    endless: Boolean,
    tutorial: Boolean,
    onContinue: () -> Unit,
    onReplay: () -> Unit,
    onLevelSelect: () -> Unit,
    onHome: () -> Unit
) {
    val replay = ResultButton("Replay", onReplay) { ButtonGlyph.Refresh() }
    val levelSelect = ResultButton("Level Select", onLevelSelect) { ButtonGlyph.Grid() }
    val home = ResultButton("Home", onHome) { ButtonGlyph.Home() }

    when {
        finished -> {
            GamePrimaryButton(
                label = "Replay Level",
                onClick = onReplay,
                icon = { ButtonGlyph.Refresh(AppIcon.badge) }
            )
            SecondaryRow(levelSelect, home)
        }

        won && tutorial -> {
            GamePrimaryButton(label = "Done", onClick = onHome)
            SecondaryRow(replay)
        }

        won && endless -> {
            GamePrimaryButton(
                label = "Next Puzzle",
                onClick = onContinue,
                icon = { ButtonGlyph.Play() }
            )
            SecondaryRow(replay, home)
        }

        won -> {
            GamePrimaryButton(
                label = "Next Level",
                onClick = onContinue,
                icon = { ButtonGlyph.Play() }
            )
            SecondaryRow(replay, levelSelect)
        }

        else -> {
            // The one coral button in the game: Retry, after a loss.
            GamePrimaryButton(
                label = "Retry",
                onClick = onReplay,
                tone = ActionTone.Coral,
                icon = { ButtonGlyph.Refresh(AppIcon.badge) }
            )
            if (campaign) SecondaryRow(levelSelect, home) else SecondaryRow(home)
        }
    }
}

internal class ResultButton(
    val label: String,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit
)

/** The quieter row under the primary button: one to three equal-width glass buttons. */
@Composable
internal fun SecondaryRow(vararg buttons: ResultButton) {
    Spacer(Modifier.height(AppSpace.betweenCards))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.betweenCards)
    ) {
        buttons.forEach { button ->
            GameSecondaryButton(
                label = button.label,
                onClick = button.onClick,
                icon = button.icon,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** The glyphs the result buttons share, at one weight and size. */
internal object ButtonGlyph {
    @Composable
    fun Play() = Icon(
        imageVector = Icons.Rounded.PlayArrow,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(AppIcon.badge + 2.dp)
    )

    @Composable
    fun Refresh(size: androidx.compose.ui.unit.Dp = AppIcon.medium) = Icon(
        imageVector = Icons.Rounded.Refresh,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(size)
    )

    @Composable
    fun Home() = Icon(
        imageVector = Icons.Rounded.Home,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(AppIcon.medium)
    )

    @Composable
    fun Grid() = GridGlyph(Color.White, Modifier.size(AppIcon.medium - 2.dp))
}

// -------------------------------------------------------------------------
// Stars, ribbon, hearts
// -------------------------------------------------------------------------

internal val StarGold = Brush.verticalGradient(
    listOf(Color(0xFFFFF3B0), Color(0xFFFFC82E), Color(0xFFF59E1B))
)
internal val StarGhost = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f))
)

/**
 * The run's stars: three big glyphs, the middle one larger and raised and the
 * outer two tilted away from it, the earned ones gold with a glow and springing
 * in one after another.
 *
 * Built from the same primitive the old card used — one [Animatable] and a
 * `scale` — rather than a second effects system. The stagger is what makes three
 * stars feel like more than two: the third one lands on its own beat.
 *
 * @param scale shrinks the whole row. The discovery result uses it so the stars
 *   sit *under* the thing that was found rather than competing with it.
 * @param animate false lands every star at once (reduced motion).
 */
@Composable
internal fun BigStars(
    earned: Int,
    scale: Float = 1f,
    animate: Boolean = true,
    startDelayMs: Long = STAR_START_MS
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(earned) {
        reveal.snapTo(0f)
        if (!animate) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        delay(startDelayMs)
        reveal.animateTo(1f, tween(STAR_REVEAL_MS, easing = FastOutSlowInEasing))
    }
    val filled = earned.coerceIn(0, StarRating.MAX)

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$filled of ${StarRating.MAX} stars"
        }
    ) {
        for (slot in 0 until StarRating.MAX) {
            val isFilled = slot < filled
            val middle = slot == StarRating.MAX / 2
            // Each star owns a window of the reveal, so they arrive in order.
            val window = 1f / StarRating.MAX
            val local = ((reveal.value - slot * window * 0.7f) / window).coerceIn(0f, 1f)
            // Overshoots a few percent just before it settles at 1, so the glyph
            // lands rather than fades in.
            val pop = if (isFilled) 0.4f + 1.5f * local - 0.9f * local * local else 1f

            Box(
                modifier = Modifier
                    .size((if (middle) 96.dp else 68.dp) * scale)
                    .graphicsLayer {
                        rotationZ = when {
                            middle -> 0f
                            slot < StarRating.MAX / 2 -> -12f
                            else -> 12f
                        }
                    }
                    .drawBehind {
                        if (isFilled && local > 0f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        GamePalette.Gold.copy(alpha = 0.55f * local),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.minDimension * 0.86f
                            )
                        }
                    }
                    .scale(pop)
                    .alpha(if (isFilled) local else 1f),
                contentAlignment = Alignment.Center
            ) {
                StarGlyph(
                    fill = if (isFilled) StarGold else StarGhost,
                    outline = if (isFilled) {
                        Color(0xFFB36B00).copy(alpha = 0.75f)
                    } else {
                        Color.White.copy(alpha = 0.26f)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** A swallow-tailed banner: both ends notched, which is what makes it a ribbon. */
private object RibbonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val notch = size.height * 0.32f
        return Outline.Generic(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - notch, size.height / 2f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                lineTo(notch, size.height / 2f)
                close()
            }
        )
    }
}

/**
 * PERFECT ESCAPE, on a gold ribbon: the one line a three-star clear earns, and the
 * only place the ribbon appears. It springs in after the third star has landed, so
 * the two never arrive together. The rule that earns it is untouched.
 */
@Composable
internal fun PerfectRibbon(
    modifier: Modifier = Modifier,
    delayMs: Long = PERFECT_DELAY_MS,
    animate: Boolean = true
) {
    val enter = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(delayMs)
        enter.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    Box(
        modifier = modifier
            .scale(enter.value)
            .alpha(enter.value.coerceIn(0f, 1f))
            .softShadow(8.dp, RibbonShape)
            .clip(RibbonShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFE690), Color(0xFFFFC02E), Color(0xFFF29A12))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.40f),
                        0.5f to Color.Transparent
                    )
                )
        )
        Text(
            text = "PERFECT ESCAPE",
            style = AppText.button.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.0.sp,
                color = GamePalette.Brown
            ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 9.dp)
        )
    }
}

/** The quiet line beside the stars: a new best, or a best that was kept. */
@Composable
internal fun StarNote(text: String) {
    Text(
        text = text,
        style = AppText.button.copy(fontSize = 13.sp, color = Color.White),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    )
}

/**
 * The lives, as three hearts. After a loss they are all spent, so they show as
 * dim hearts dropping into place — the outcome stated gently, not shouted. (If a
 * life were left, it would be coral.)
 */
@Composable
private fun LifeHearts(livesLeft: Int) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        reveal.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$livesLeft of ${GameState.STARTING_LIVES} lives remaining"
        }
    ) {
        repeat(GameState.STARTING_LIVES) { index ->
            val alive = index < livesLeft
            val window = 1f / GameState.STARTING_LIVES
            val local = ((reveal.value - index * window * 0.6f) / window).coerceIn(0f, 1f)
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = if (alive) GamePalette.Coral else Color.White.copy(alpha = 0.20f),
                modifier = Modifier
                    .size(44.dp)
                    .scale(0.5f + 0.5f * local)
                    .alpha(local)
            )
        }
    }
}

// -------------------------------------------------------------------------
// The summary panel
// -------------------------------------------------------------------------

/**
 * The glass panel under the headline: which board it was, then the numbers the
 * game actually has.
 *
 * Arrows cleared reads as a bare total on a win and as "12 / 20" on a loss. Blocked
 * taps is lives lost. The rating is Campaign-only, and so is the hint line —
 * [hintUsed] is null everywhere the game does not track it reliably, and a null
 * simply leaves the row out rather than inventing a zero.
 */
@Composable
private fun SummaryPanel(
    world: GameWorld,
    heading: String,
    subheading: String,
    won: Boolean,
    stars: Int?,
    arrowsCleared: Int,
    arrowsTotal: Int,
    blockedTaps: Int,
    hintUsed: Boolean?
) {
    val accent = WorldStyle.of(world).accent
    GameGlassCard(tone = GlassTone.Dark, elevation = AppElevation.raised) {
        Column(modifier = Modifier.padding(horizontal = AppSpace.card, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameBadge(
                    brush = Brush.verticalGradient(listOf(accent, lerp(accent, GamePalette.Navy, 0.45f))),
                    size = 46.dp,
                    shape = CircleShape
                ) { MountainGlyph(Color.White, Modifier.size(AppIcon.badge - 2.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = heading,
                        style = AppText.cardTitle.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = subheading,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.72f)
                        ),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(AppSpace.tight + 2.dp))
            GameDivider(color = Color.White.copy(alpha = 0.14f))

            if (stars != null && won) {
                SummaryLine(
                    label = "Stars earned",
                    description = "Stars earned, $stars of ${StarRating.MAX}",
                    icon = {
                        StarGlyph(
                            fill = StarGold,
                            outline = Color(0xFFB36B00).copy(alpha = 0.7f),
                            modifier = Modifier.size(AppIcon.medium)
                        )
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(StarRating.MAX) { slot ->
                            val lit = slot < stars
                            StarGlyph(
                                fill = if (lit) StarGold else StarGhost,
                                outline = if (lit) Color(0xFFB36B00).copy(alpha = 0.7f) else null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
            SummaryLine(
                label = "Arrows cleared",
                description = if (won) {
                    "Arrows cleared, $arrowsTotal"
                } else {
                    "Arrows cleared, $arrowsCleared of $arrowsTotal"
                },
                icon = {
                    ArrowGlyph(
                        direction = Direction.UP,
                        color = Color.White,
                        shadowColor = Color.Transparent,
                        modifier = Modifier.size(AppIcon.medium)
                    )
                }
            ) { SummaryValue(if (won) "$arrowsTotal" else "$arrowsCleared / $arrowsTotal") }
            SummaryLine(
                label = "Blocked taps",
                description = "Blocked taps, $blockedTaps",
                icon = { NoEntryGlyph(Color.White, Modifier.size(AppIcon.medium)) }
            ) { SummaryValue("$blockedTaps") }
            if (hintUsed != null) {
                SummaryLine(
                    label = "Hint used",
                    description = "Hint used, ${if (hintUsed) "yes" else "no"}",
                    icon = {
                        BulbGlyph(
                            color = GamePalette.GoldLight,
                            modifier = Modifier.size(AppIcon.medium)
                        )
                    }
                ) { SummaryValue(if (hintUsed) "Yes" else "No") }
            }
        }
    }
}

/** One row of the panel: a glyph, a label, and a value at the far end. One node. */
@Composable
private fun SummaryLine(
    label: String,
    description: String,
    icon: @Composable () -> Unit,
    value: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(top = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White.copy(alpha = 0.86f),
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

@Composable
private fun SummaryValue(text: String) {
    Text(
        text = text,
        style = AppText.cardTitle.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        ),
        maxLines = 1
    )
}

private fun resultMessage(
    won: Boolean,
    livesLeft: Int,
    finished: Boolean,
    context: ResultContext
): String = when {
    finished -> "That is every level cleared. Replay any of them from the level grid."
    // Campaign wins with a rating speak to the rating, before the generic
    // "perfect clear" line below can claim the same ground.
    won && context is ResultContext.Campaign && context.stars == StarRating.MAX ->
        "Not a single blocked tap, and no hint. Flawless."
    won && context is ResultContext.Campaign && context.stars == 2 ->
        "Cleared. Three stars means a clean run — no blocked taps, no hints."
    won && context is ResultContext.Campaign && context.stars == StarRating.MIN ->
        "Cleared the hard way. There is a tidier route through this one."
    won && context is ResultContext.Daily && context.alreadyCountedToday ->
        "Today was already in the books, so the streak stays where it is."
    won && context is ResultContext.Daily -> "Today's board is done. Back tomorrow for the next one."
    won && context == ResultContext.Tutorial ->
        "Clear a path, and keep clearing paths. That is the whole game."
    won && livesLeft == GameState.STARTING_LIVES ->
        "Perfect clear — every arrow escaped without a single blocked tap."
    won && context is ResultContext.Endless -> "Cleared. The next one is already waiting."
    won -> "Every arrow found a way out. Nicely planned."
    context is ResultContext.Daily ->
        "Out of lives, but today's puzzle is still today's. Have another go."
    else -> "Every move was blocked one time too many. Have another go."
}


/** A solved Daily / Endless shape: what it is called, and the outline it was traced to. */
class ShapeResult(val name: String, val contour: com.sabalapps.arrowescape.shape.ShapeContour)

/**
 * The reveal for a Daily or Endless win: the headline and the words say what the mode needs said, and
 * [ShapeRevealContent] draws the shape itself. A Daily's first clear is "TODAY'S SHAPE REVEALED!"; a replay of
 * a day already in the books is plainly "TODAY'S SHAPE", because it is not new; Endless is always "SHAPE
 * REVEALED!" and goes straight on.
 */
@Composable
private fun ShapeResultContent(
    shape: ShapeResult,
    world: GameWorld,
    daily: ResultContext.Daily?,
    endless: ResultContext.Endless?,
    dailyDate: String,
    schedule: ShapeRevealSchedule,
    reducedMotion: Boolean,
    room: androidx.compose.ui.unit.Dp,
    revealFrom: Offset?,
    onContinue: () -> Unit,
    onReplay: () -> Unit,
    onHome: () -> Unit
) {
    val copy = ShapeRevealCopy.of(daily, endless, shape.name)
    val firstReveal = copy.firstReveal
    ShapeRevealContent(
        name = shape.name,
        contour = shape.contour,
        world = world,
        headline = copy.headline,
        firstReveal = firstReveal,
        spoken = copy.spoken,
        schedule = schedule,
        reducedMotion = reducedMotion,
        room = room,
        revealFrom = revealFrom,
        detail = {
            if (daily != null) {
                DailyShapeDetail(date = dailyDate, streak = daily.streak, bestStreak = daily.bestStreak)
            } else if (endless != null) {
                Text(
                    text = "Puzzle ${endless.puzzleNumber} \u00b7 ${endless.tierLabel}",
                    style = MaterialTheme.typography.bodyLarge.onArt()
                        .copy(color = Color.White.copy(alpha = 0.90f)),
                    textAlign = TextAlign.Center
                )
            }
        },
        actions = {
            if (daily != null) {
                GamePrimaryButton(label = "Done", onClick = onHome)
                SecondaryRow(ResultButton("Play Again", onReplay) { ButtonGlyph.Refresh() })
            } else {
                GamePrimaryButton(
                    label = "Next Puzzle",
                    onClick = onContinue,
                    icon = { ButtonGlyph.Play() }
                )
                SecondaryRow(
                    ResultButton("Replay", onReplay) { ButtonGlyph.Refresh() },
                    ResultButton("Home", onHome) { ButtonGlyph.Home() }
                )
            }
        }
    )
}

/** "Completed Today", the date, and the two streaks — what a Daily adds under the shape's name. */
@Composable
private fun DailyShapeDetail(date: String, streak: Int, bestStreak: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Completed Today",
            style = AppText.resultTitle.copy(
                fontSize = 24.sp,
                color = GamePalette.GoldLight,
                shadow = OnArtShadow
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium.onArt().copy(color = Color.White.copy(alpha = 0.90f)),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpace.betweenCards))
        StreakCard(streak = streak, bestStreak = bestStreak)
    }
}
