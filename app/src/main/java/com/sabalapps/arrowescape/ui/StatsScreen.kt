package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.ui.world.GameWorld
import com.sabalapps.arrowescape.ui.world.MenuBackdrop

/** Below this width a 2×2 grid would squeeze each card under what its title needs. */
private val TWO_COLUMNS_FROM = 340.dp

/**
 * The numbers, as four substantial cards over the world's artwork.
 *
 * Deliberately no charts. There is nothing here with a shape worth drawing — a
 * count of cleared levels has no trend, and a streak is one number — so a chart
 * would be decoration pretending to be information. What makes the screen
 * satisfying instead is scale: each card leads with one very large figure and a
 * caption, with the supporting detail beneath it at body size. The eye gets
 * "14 / 30", "1", "56" without reading a word.
 *
 * Where the phone is wide enough the four sit in a 2×2 grid, each row stretched to
 * its taller card; on a narrow one they stack. Every value is one the game already
 * keeps because it needs it; see [PlayerStats].
 */
@Composable
fun StatsScreen(
    stats: PlayerStats,
    /** Whether the Daily has been cleared for [today]; the stats alone cannot say. */
    dailyDoneToday: Boolean,
    /** The world the player is in, for the backdrop. */
    world: GameWorld,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    MenuBackdrop(world = world, dim = 0.08f) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val twoColumns = maxWidth >= TWO_COLUMNS_FROM

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(
                    title = "Statistics",
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .widthIn(max = MAX_CONTENT_WIDTH + 28.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                        .padding(top = AppSpace.tight, bottom = AppSpace.betweenSections),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.betweenCards)
                ) {
                    val cards = listOf<@Composable (Modifier) -> Unit>(
                        { m -> CampaignCard(stats, m) },
                        { m -> EndlessCard(stats, m) },
                        { m -> DailyCard(stats, dailyDoneToday, m) },
                        { m -> OverallCard(stats, m) }
                    )
                    if (twoColumns) {
                        cards.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(AppSpace.betweenCards)
                            ) {
                                pair.forEach { card ->
                                    card(Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    } else {
                        cards.forEach { card -> card(Modifier) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignCard(stats: PlayerStats, modifier: Modifier) {
    StatCard(
        title = "Campaign",
        badgeBrush = GameBrush.Blue,
        glyph = { MountainGlyph(Color.White, Modifier.size(AppIcon.medium + 2.dp)) },
        modifier = modifier
    ) {
        BigNumber(
            value = "${stats.campaignDiscoveries}",
            unit = "/ ${stats.campaignTotal}",
            caption = "Discoveries Found",
            description = "Discoveries found, ${stats.campaignDiscoveries} of ${stats.campaignTotal}"
        )
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        GameDivider()
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription =
                    "Stars earned, ${stats.campaignStars} of ${stats.campaignStarsMax}"
            }
        ) {
            StarGlyph(
                fill = Brush.verticalGradient(listOf(GamePalette.GoldLight, GamePalette.Gold)),
                outline = GamePalette.GoldDeep.copy(alpha = 0.8f),
                modifier = Modifier.size(AppIcon.medium)
            )
            Spacer(Modifier.width(6.dp))
            SupportText(
                bold = "${stats.campaignStars}",
                rest = " Stars Earned"
            )
        }
    }
}

@Composable
private fun EndlessCard(stats: PlayerStats, modifier: Modifier) {
    StatCard(
        title = "Endless Mode",
        badgeBrush = GameBrush.Teal,
        glyph = { InfinityGlyph(Color.White, Modifier.size(AppIcon.medium + 2.dp)) },
        modifier = modifier
    ) {
        BigNumber(
            value = "${stats.endlessCompleted}",
            caption = "Puzzles Cleared",
            description = "Puzzles cleared, ${stats.endlessCompleted}"
        )
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        GameDivider()
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        SupportRow(label = "Current", value = stats.endlessTierLabel)
        Spacer(Modifier.height(AppSpace.hair + 2.dp))
        SupportRow(label = "Best run", value = countOf(stats.endlessBestStreak, "puzzle"))
    }
}

@Composable
private fun DailyCard(stats: PlayerStats, doneToday: Boolean, modifier: Modifier) {
    StatCard(
        title = "Daily Challenge",
        badgeBrush = GameBrush.Orange,
        glyph = {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(AppIcon.medium + 2.dp)
            )
        },
        modifier = modifier
    ) {
        BigNumber(
            value = "${stats.dailyCurrentStreak}",
            caption = "Current Streak",
            description = "Current streak, ${countOf(stats.dailyCurrentStreak, "day")}"
        )
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        GameDivider()
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Best streak, ${countOf(stats.dailyBestStreak, "day")}"
            }
        ) {
            SupportText(bold = "${stats.dailyBestStreak}", rest = " Best Streak")
        }
        Spacer(Modifier.height(AppSpace.hair + 2.dp))
        if (doneToday) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Completed today"
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = GamePalette.GreenDeep,
                    modifier = Modifier.size(AppIcon.small + 2.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Completed Today",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = GamePalette.GreenDeep,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            SupportRow(label = "Days cleared", value = "${stats.dailyCompleted}")
        }
    }
}

@Composable
private fun OverallCard(stats: PlayerStats, modifier: Modifier) {
    StatCard(
        title = "Overall",
        badgeBrush = GameBrush.Purple,
        glyph = { BarChartGlyph(Color.White, Modifier.size(AppIcon.medium + 2.dp)) },
        modifier = modifier
    ) {
        BigNumber(
            value = "${stats.totalPuzzles}",
            caption = "Total Puzzles Solved",
            description = "Total puzzles cleared, ${stats.totalPuzzles}"
        )
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        GameDivider()
        Spacer(Modifier.height(AppSpace.tight + 2.dp))
        Text(
            text = "Across Campaign, Endless and Daily",
            style = AppText.caption.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.FAINT)
            )
        )
    }
}

/** A plain count with its unit, singular where it matters. */
private fun countOf(value: Int, unit: String): String =
    if (value == 1) "1 $unit" else "$value ${unit}s"

/**
 * One stat card: a gradient badge and the mode's name, then whatever the mode has
 * to say. The shared glass shell, so all four are the same object with different
 * contents.
 */
@Composable
private fun StatCard(
    title: String,
    badgeBrush: Brush,
    glyph: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GameGlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameBadge(
                    brush = badgeBrush,
                    size = 38.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) { glyph() }
                Spacer(Modifier.width(AppSpace.tight))
                Text(
                    text = title,
                    style = AppText.cardTitle.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(AppSpace.betweenCards))
            content()
        }
    }
}

/**
 * The one big figure a card is about, with its caption. [unit] rides beside the
 * figure at a smaller size so "14 / 30" stays one phrase. One merged semantics
 * node, because three fragments read aloud separately mean nothing.
 */
@Composable
private fun BigNumber(
    value: String,
    caption: String,
    description: String,
    unit: String? = null
) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        }
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = AppText.number, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.FAINT)
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.MUTED),
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/** A bold figure followed by its label: "42 Stars Earned". */
@Composable
private fun SupportText(bold: String, rest: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = bold,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1
        )
        Text(
            text = rest,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.MUTED)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 1.dp)
        )
    }
}

/** A label and its value on one line. One node, so TalkBack reads them together. */
@Composable
private fun SupportRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.MUTED)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(AppSpace.tight))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
