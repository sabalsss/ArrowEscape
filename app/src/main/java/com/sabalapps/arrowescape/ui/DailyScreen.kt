package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.daily.DailyProgress
import com.sabalapps.arrowescape.shape.GridContourTracer
import com.sabalapps.arrowescape.ui.discovery.DiscoveryGlow
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.ui.world.GameWorlds
import com.sabalapps.arrowescape.ui.world.MenuBackdrop

/**
 * What the Daily Challenge shows once today is done: a calendar badge with a
 * green check, "Completed Today", the date, the two streaks, and the way to play
 * it again or go home.
 *
 * It is the same composition in two places. After *winning* the daily it is the
 * result (over the board's own world, from [ResultScreen]); and when the player
 * opens the daily from Home after already clearing it, it is the page they land
 * on ([DailyScreen]) instead of being dropped straight back into a finished
 * puzzle. Both read the same [DailyProgress] and neither changes any rule — there
 * is no reward, no currency, no gift: a day cleared is a day cleared, and Play
 * Again is exactly the replay the game always offered, which leaves the streak
 * where it is.
 *
 * @param showTitle whether to print "Daily Challenge" above the badge. The screen
 *   has it in its top bar already; the result has no top bar.
 */
@Composable
fun DailyCompletedContent(
    date: String,
    streak: Int,
    bestStreak: Int,
    message: String?,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    /**
     * Today's shape, for a day that is already done: the picture and its name take the place of the
     * calendar badge. Never passed for a day that is not done — that is what keeps the name hidden.
     */
    shape: DailyShapeArt? = null
) {
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showTitle) {
            Text(
                text = "Daily Challenge",
                style = AppText.screenTitle.copy(fontSize = 24.sp).onArt(),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(AppSpace.betweenCards))
        }
        if (shape != null) {
            ShapeRevealArt(
                contour = shape.contour,
                colours = DiscoveryGlow.of(shape.world),
                modifier = Modifier.size(150.dp)
            )
            Spacer(Modifier.height(AppSpace.tight))
            Text(
                text = shape.name,
                style = AppText.resultTitle.copy(fontSize = 30.sp, color = Color.White, shadow = OnArtShadow),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = "Today's shape: ${shape.name}" }
            )
        } else {
            CompletedBadge()
        }
        Spacer(Modifier.height(AppSpace.betweenCards + 2.dp))
        Text(
            text = "Completed Today",
            style = AppText.resultTitle.copy(
                fontSize = 28.sp,
                color = GamePalette.GoldLight,
                shadow = OnArtShadow
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.bodyLarge.onArt().copy(
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Spacer(Modifier.height(AppSpace.tight))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.onArt()
                    .copy(color = Color.White.copy(alpha = 0.82f)),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(AppSpace.betweenSections))
        StreakCard(streak = streak, bestStreak = bestStreak)
        Spacer(Modifier.height(AppSpace.betweenSections + 4.dp))
        GamePrimaryButton(
            label = "Play Again",
            onClick = onPlayAgain,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(AppIcon.badge)
                )
            }
        )
        Spacer(Modifier.height(AppSpace.betweenCards))
        GameSecondaryButton(
            label = "Home",
            onClick = onHome,
            modifier = Modifier.fillMaxWidth(),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(AppIcon.medium)
                )
            }
        )
    }
}

/**
 * The daily page for a day that is already done, reached from Home. See
 * [DailyCompletedContent]. It sits over the same world the day's board is played
 * in, so the artwork is the one the player will see if they play it again.
 */
@Composable
fun DailyScreen(
    date: GameDate,
    dailyProgress: DailyProgress,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val world = remember(date) { GameWorlds.forDailySeed(DailyChallenge.seedFor(date)) }
    // This page is only reached once today is done, so today's picture may be named here.
    val shape = remember(date, world) {
        val variant = DailyChallenge.shapeFor(date)
        DailyShapeArt(
            name = variant.template.name,
            contour = GridContourTracer.trace(variant.mask),
            world = world
        )
    }
    MenuBackdrop(world = world, dim = 0.08f) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                title = "Daily Challenge",
                onBack = onBack,
                backLabel = "Back to home",
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val room = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = room)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpace.screenH, vertical = AppSpace.betweenCards),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    DailyCompletedContent(
                        date = date.friendly(),
                        streak = dailyProgress.currentStreak,
                        bestStreak = dailyProgress.bestStreak,
                        message = "Today is in the books. Play it again whenever you like " +
                            "— your streak stays where it is.",
                        onPlayAgain = onPlayAgain,
                        onHome = onHome,
                        showTitle = false,
                        shape = shape
                    )
                }
            }
        }
    }
}

/** A calendar on a glowing disc, with a green check that springs onto its corner. */
@Composable
private fun CompletedBadge() {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    Box(
        modifier = Modifier
            .size(136.dp)
            .semantics { contentDescription = "Daily challenge completed" }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(GamePalette.Gold.copy(alpha = 0.40f), Color.Transparent)
                        ),
                        radius = size.minDimension * 0.82f
                    )
                }
                .softShadow(AppElevation.raised, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2B3A9E).copy(alpha = 0.92f), GamePalette.Navy.copy(alpha = 0.92f))
                    )
                )
                .border(AppStroke.emphasis, Color.White.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 6.dp)
                .size(44.dp)
                .scale(pop.value)
                .softShadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(GameBrush.Green)
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** Current streak and best streak side by side, on one glass card. */
@Composable
internal fun StreakCard(streak: Int, bestStreak: Int) {
    val currentLabel = if (streak == 1) "1 day" else "$streak days"
    GameGlassCard(
        contentDescription = "Current daily streak $currentLabel, best $bestStreak"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = AppSpace.card)
        ) {
            StreakStat(
                value = streak,
                label = "Current Streak",
                modifier = Modifier.weight(1f)
            ) { FlameGlyph(Modifier.size(32.dp)) }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(AppStroke.hairline)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
            StreakStat(
                value = bestStreak,
                label = "Best Streak",
                modifier = Modifier.weight(1f)
            ) {
                StarGlyph(
                    fill = Brush.verticalGradient(listOf(GamePalette.GoldLight, GamePalette.Gold)),
                    outline = GamePalette.GoldDeep.copy(alpha = 0.8f),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun StreakStat(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            glyph()
            Spacer(Modifier.width(6.dp))
            Text(text = "$value", style = AppText.number, maxLines = 1)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.MUTED),
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/** Today's shape as the summary page shows it: its name, its outline, and the world it is lit with. */
class DailyShapeArt(
    val name: String,
    val contour: com.sabalapps.arrowescape.shape.ShapeContour,
    val world: com.sabalapps.arrowescape.ui.world.GameWorld
)
