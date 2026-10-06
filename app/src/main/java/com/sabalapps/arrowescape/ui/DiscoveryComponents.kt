package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabalapps.arrowescape.ui.world.WorldCollection
import com.sabalapps.arrowescape.ui.world.WorldStyle

/*
 * The small pieces the discovery screens share — the album, Level Select, Home —
 * so "a mystery" and "a world's heading" look the same wherever they appear.
 *
 * Nothing here can name a discovery: they take counts, a world and a level number.
 * The only code that is ever handed an identity is the code that draws a collected
 * slot, and it gets it from `DiscoverySlot.Collected`.
 */

/**
 * The "?" that stands in for something not yet found. A circle and a glyph, sized by
 * [size] and not by the font scale, so it is the same mystery at every setting.
 */
@Composable
internal fun MysteryMark(
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    filled: Boolean = true
) {
    val fontSize = with(LocalDensity.current) { (size * 0.52f).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) tint.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, tint.copy(alpha = 0.42f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            style = AppText.cardTitle.copy(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.ExtraBold,
                color = tint.copy(alpha = 0.92f)
            ),
            maxLines = 1
        )
    }
}

/** A dashed rounded outline drawn behind a surface: the edge of a space not yet filled. */
internal fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier =
    drawBehind {
        val inset = width.toPx() / 2f
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
            )
        )
    }

/** A gold "✓ WORLD COMPLETE" / "✓ COMPLETE" pill: the restrained mark of a finished set. */
@Composable
internal fun CompleteChip(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(GameBrush.Gold)
            .border(AppStroke.hairline, Color.White.copy(alpha = 0.65f), shape)
            .padding(start = 7.dp, end = 11.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = GamePalette.Brown,
            modifier = Modifier.size(AppIcon.small)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = AppText.section.copy(color = GamePalette.Brown),
            maxLines = 1
        )
    }
}

/**
 * One pip per discovery in a world: [collected] lit in the world's colour, the rest
 * empty. The count says "5 / 6"; this is what lets the eye see it — five lit and one
 * slot waiting — without reading anything.
 *
 * When exactly one is missing, that last slot is drawn as a ring in the world's own
 * colour instead of the faint white the others use: still, quiet, and plainly the one
 * place left to fill. No motion and no copy — near-completion is made easy to
 * perceive, not urgent.
 *
 * Decoration: no semantics; the count is always said in words beside it.
 */
@Composable
internal fun WorldPips(
    collected: Int,
    total: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    pip: Dp = 10.dp,
    gap: Dp = 7.dp
) {
    val lastOne = total > 1 && collected == total - 1
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.clearAndSetSemantics {}
    ) {
        repeat(total) { index ->
            val found = index < collected
            val theLastSlot = lastOne && index == total - 1
            Box(
                modifier = Modifier
                    .size(pip)
                    .clip(CircleShape)
                    .background(
                        when {
                            found -> accent
                            theLastSlot -> accent.copy(alpha = 0.22f)
                            else -> Color.White.copy(alpha = 0.18f)
                        }
                    )
                    .border(
                        if (theLastSlot) 1.5.dp else 1.dp,
                        when {
                            found -> Color.White.copy(alpha = 0.85f)
                            theLastSlot -> accent.copy(alpha = 0.95f)
                            else -> Color.White.copy(alpha = 0.32f)
                        },
                        CircleShape
                    )
            )
        }
    }
}

/**
 * A world's heading: its dot and name, how many of its six are found, and — once all
 * are — a gold COMPLETE chip. One merged heading node for TalkBack.
 *
 * Used by the album and by Level Select, which is what makes "4 / 6 DISCOVERED" read
 * the same in both.
 */
@Composable
internal fun WorldDiscoveryHeader(
    collection: WorldCollection,
    modifier: Modifier = Modifier
) {
    val accent = WorldStyle.of(collection.world).accent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = collection.spokenSummary
                heading()
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
            )
            Spacer(Modifier.width(AppSpace.tight))
            Text(
                text = collection.world.displayName.uppercase(),
                style = AppText.section.copy(color = Color.White, shadow = OnArtShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (collection.isComplete) {
                Spacer(Modifier.weight(1f))
                CompleteChip("COMPLETE")
            }
        }
        Spacer(Modifier.size(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = collection.progressLabel,
                style = AppText.caption.copy(
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Bold,
                    shadow = OnArtShadow
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp)
            )
            Spacer(Modifier.width(AppSpace.tight))
            WorldPips(
                collected = collection.collected,
                total = collection.total,
                accent = accent,
                pip = 8.dp,
                gap = 5.dp
            )
        }
    }
}
