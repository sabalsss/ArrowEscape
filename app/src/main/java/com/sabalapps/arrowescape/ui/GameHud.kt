package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.game.GameState

/** The red of a life: a pinker, warmer red than the error colour a blocked tap owns. */
private val HeartRed = Color(0xFFFF4D6A)

/**
 * The gameplay heads-up display: a handful of small floating controls over the
 * artwork instead of one wide card across the top of it.
 *
 * ```
 *  (←)  [ Level 15        ]  [♥ ♥ ♥]  (↻) (⚙)
 *       [ Sunset Canyon   ]
 *  ( ━━━━━━━━━━━━━━━━━━━━━━━━━━━  12 left )
 * ```
 *
 * Dark glass throughout, because the artwork behind a HUD runs from bright sky to
 * near-black space and white-on-navy is the one pairing that holds up over all
 * five. The board keeps its own light surface below; this is the game's frame,
 * not its content.
 *
 * Every round control is a full 48dp target with a 42dp disc inside it.
 *
 * ## Width
 *
 * The row is budgeted for 320dp. Below [livesInTopRow]'s threshold the three hearts
 * leave the top row and sit beside the progress bar instead, because beside the
 * level name they would squeeze "Endless #51" into an ellipsis. Both placements
 * are the same hearts with the same label.
 *
 * ## What it still says
 *
 * The same facts the old header did, merged the same way for TalkBack: "N of M
 * arrows remaining", "N of 3 lives remaining", "Restart level", "Settings". The
 * difficulty tier is the subtitle in Endless and Daily (gold in Daily).
 */
@Composable
fun GameHud(
    title: String,
    subtitle: String,
    /** The full name for TalkBack when [title] is abbreviated to fit. */
    description: String,
    goldSubtitle: Boolean,
    remaining: Int,
    total: Int,
    lives: Int,
    livesInTopRow: Boolean,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameIconButton(onClick = onBack, label = "Back") { BackGlyph() }
            Spacer(Modifier.width(2.dp))
            LevelPill(
                title = title,
                subtitle = subtitle,
                description = description,
                gold = goldSubtitle,
                modifier = Modifier.weight(1f)
            )
            if (livesInTopRow) {
                Spacer(Modifier.width(6.dp))
                LivesPill(lives = lives, heartSize = 19.dp)
            }
            Spacer(Modifier.width(2.dp))
            GameIconButton(onClick = onRestart, label = "Restart level") {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(AppIcon.medium)
                )
            }
            GameIconButton(onClick = onOpenSettings, label = "Settings") {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(AppIcon.medium)
                )
            }
        }

        // Progress, on the line below, in the same glass family. The bar and the count
        // are one capsule, so "12 left" belongs to the bar it counts instead of floating
        // on the artwork beside it; the capsule is no taller than the lives pill that
        // shares its row on a narrow phone, so it adds nothing there.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressChip(
                remaining = remaining,
                total = total,
                modifier = Modifier.weight(1f)
            )
            if (!livesInTopRow) {
                Spacer(Modifier.width(6.dp))
                LivesPill(lives = lives, heartSize = 15.dp, compact = true)
            }
        }
    }
}

/**
 * The progress bar and the number of arrows still on the board, in one slim glass
 * capsule. The bar eases between values — it is reporting a move. One node for
 * TalkBack: "N of M arrows remaining".
 */
@Composable
private fun ProgressChip(remaining: Int, total: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .softShadow(3.dp, shape)
            .clip(shape)
            .background(GameBrush.Navy)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$remaining of $total arrows remaining"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameProgressBar(
            fraction = if (total == 0) 0f else (total - remaining).toFloat() / total,
            height = 6.dp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(AppSpace.tight))
        Text(
            text = "$remaining left",
            style = AppText.caption.copy(
                lineHeight = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

/**
 * The level and where it is, as one small pill: a bold name over a quiet line.
 * Two lines that each fit at 320dp are worth more than one that does not, which is
 * why the world (or the tier) is a second line rather than a suffix.
 */
@Composable
private fun LevelPill(
    title: String,
    subtitle: String,
    description: String,
    gold: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppRadius.control - 2.dp)
    Column(
        modifier = modifier
            .heightIn(min = AppIcon.control)
            .softShadow(5.dp, shape)
            .clip(shape)
            .background(GameBrush.Navy)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$description, $subtitle"
            },
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = AppText.caption.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = if (gold) GamePalette.GoldLight else Color.White.copy(alpha = 0.76f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The lives, as hearts in a pill. One node for TalkBack: "N of 3 lives remaining". */
@Composable
private fun LivesPill(lives: Int, heartSize: Dp, compact: Boolean = false) {
    val shape = RoundedCornerShape(if (compact) 14.dp else AppRadius.control - 2.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp),
        modifier = Modifier
            .heightIn(min = if (compact) 26.dp else AppIcon.control)
            .softShadow(if (compact) 3.dp else 5.dp, shape)
            .clip(shape)
            .background(GameBrush.Navy)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$lives of ${GameState.STARTING_LIVES} lives remaining"
            }
    ) {
        repeat(GameState.STARTING_LIVES) { index ->
            val alive = index < lives
            val scale by animateFloatAsState(
                targetValue = if (alive) 1f else 0.78f,
                animationSpec = tween(220),
                label = "lifeScale"
            )
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = if (alive) HeartRed else Color.White.copy(alpha = 0.26f),
                modifier = Modifier
                    .size(heartSize)
                    .scale(scale)
                    .clearAndSetSemantics {}
            )
        }
    }
}

/**
 * The line under the board and the Hint button beside it: the game's bottom
 * controls.
 *
 * The line sits on a light glass pill rather than straight on the artwork. It is
 * the smallest text on the screen and the only text with a picture immediately
 * behind it, and while the tutorial is running it *is* the lesson — so it is the
 * one place contrast could not be left to chance. The bulb on its left is the
 * game's own glyph, in the colour of a bulb.
 *
 * Hint is a strong-but-secondary violet pill: bold enough to be found, nowhere near
 * the weight of a call to action. Its logic is untouched; only its look changed.
 */
@Composable
fun GameFooter(
    message: String,
    /** True for a tutorial caption, which is an instruction rather than a note. */
    emphasised: Boolean,
    hintEnabled: Boolean,
    onHint: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InstructionPill(
            message = message,
            emphasised = emphasised,
            modifier = Modifier.weight(1f)
        )
        HintButton(enabled = hintEnabled, onClick = onHint)
    }
}

@Composable
private fun InstructionPill(message: String, emphasised: Boolean, modifier: Modifier = Modifier) {
    val style = glassStyle(GlassTone.Light)
    val shape = RoundedCornerShape(AppRadius.control - 2.dp)
    Row(
        modifier = modifier
            .heightIn(min = AppIcon.touch)
            .softShadow(6.dp, shape)
            .clip(shape)
            .background(style.fill)
            .border(AppStroke.hairline, style.border, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BulbGlyph(
            color = if (emphasised) MaterialTheme.colorScheme.primary else Color(0xFFF2A100),
            modifier = Modifier
                .size(20.dp)
                .clearAndSetSemantics {}
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Medium,
                color = if (emphasised) {
                    MaterialTheme.colorScheme.primary
                } else {
                    style.content.copy(alpha = 0.80f)
                }
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // A lesson's caption is read out when it changes — politely, without taking focus —
            // because it is the one place the tutorial exists in words as well as in motion.
            modifier = Modifier.semantics { if (emphasised) liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** The bulb pill. Labelled for TalkBack on the node that can actually be activated. */
@Composable
private fun HintButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AppRadius.control - 2.dp)
    Box(
        modifier = Modifier
            .heightIn(min = AppIcon.touch)
            .gameClickable(onClick = onClick, enabled = enabled, pressedScale = 0.94f)
            .semantics { contentDescription = "Hint, highlight an arrow that can escape" }
            // A disabled Hint is still a control the player has to read as
            // disabled, so it keeps its shape and dims rather than vanishing.
            .alpha(if (enabled) 1f else 0.45f)
            .softShadow(if (enabled) 8.dp else 2.dp, shape)
            .clip(shape)
            .background(GameBrush.Purple)
            .border(AppStroke.emphasis, Color.White.copy(alpha = 0.42f), shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.28f),
                        0.5f to Color.Transparent
                    )
                )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
        ) {
            BulbGlyph(color = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Hint",
                style = AppText.button.copy(color = Color.White),
                maxLines = 1
            )
        }
    }
}
