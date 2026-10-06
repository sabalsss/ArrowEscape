package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabalapps.arrowescape.notification.ReminderSchedule
import com.sabalapps.arrowescape.settings.GameSettings
import com.sabalapps.arrowescape.settings.ThemeOption
import com.sabalapps.arrowescape.ui.world.GameWorld
import com.sabalapps.arrowescape.ui.world.MenuBackdrop

/**
 * What the Daily Reminder row needs to render itself.
 *
 * [on] is the switch's state — the player wants it *and* the system will show it. [blocked]
 * is the one case where those disagree: the player wants it, and notifications are off for
 * the game in system settings. The row then says so, plainly, instead of a switch that
 * silently does nothing.
 */
data class ReminderRowState(val on: Boolean = false, val blocked: Boolean = false) {
    companion object {
        fun of(wanted: Boolean, allowed: Boolean) =
            ReminderRowState(on = wanted && allowed, blocked = wanted && !allowed)
    }
}

/** "6:00 PM", from the one place the reminder's time of day is defined. */
internal fun reminderTimeLabel(): String {
    val hour = ReminderSchedule.HOUR
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "%d:%02d %s".format(h12, ReminderSchedule.MINUTE, if (hour < 12) "AM" else "PM")
}

/**
 * Sound, haptics, theme, the daily reminder, support, and the one way back to the tutorial — as a screen of
 * its own over the world's artwork, in the same glass as everything else, rather
 * than a utility sheet that slid up over whatever was behind it.
 *
 * Same four settings as ever; nothing was added. Three sections, each a single
 * glass card, because four settings only need to be *clear*, not clever:
 *
 *  - **Sound & Feedback** — two rows, each a whole-row switch (tap anywhere on it),
 *    so the target is a full row tall rather than the thumb of a toggle.
 *  - **Appearance** — Light, Dark and System as three icon choices in one card. The
 *    chosen one fills with the brand blue and springs slightly as it lands.
 *  - **Daily Mystery** — one switch: a reminder, around [reminderTimeLabel], when today's
 *    Daily Challenge is ready. Off by default; there is no settings page behind it.
 *  - **Help** — Replay Tutorial, Rate and Share. Pressing Rate or Share here is recorded
 *    as the player having acted on the request, exactly as it is from the prompt.
 *
 * It scrolls, so every row stays reachable on a short screen at a large font
 * scale, and no row is sized so that enlarging the text clips it. It is opened
 * from both Home and the game's HUD, and back returns to whichever it came from.
 */
@Composable
fun SettingsScreen(
    settings: GameSettings,
    /** The world to sit over — the player's current one, so it matches Home. */
    world: GameWorld,
    onSoundChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onThemeChanged: (ThemeOption) -> Unit,
    reminder: ReminderRowState,
    onReminderChanged: (Boolean) -> Unit,
    onRate: () -> Unit,
    onShare: () -> Unit,
    /**
     * Opens the lesson again. This is the only route back to it: the tutorial
     * shows itself once, on the first ever Level 1, and after that the player has
     * to ask.
     */
    onReplayTutorial: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    MenuBackdrop(world = world, dim = 0.10f) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                title = "Settings",
                onBack = onBack,
                backLabel = "Back",
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Column(
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH + AppSpace.screenH * 2)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpace.screenH)
                    .padding(top = AppSpace.tight, bottom = AppSpace.betweenSections)
            ) {
                // ---- Sound & Feedback --------------------------------------
                GameSectionHeader(text = "Sound & Feedback", modifier = Modifier.padding(start = AppSpace.hair))
                Spacer(Modifier.height(AppSpace.tight))
                GameGlassCard {
                    ToggleRow(
                        title = "Sound Effects",
                        subtitle = "Arrows, blocked taps and results",
                        checked = settings.soundEnabled,
                        onCheckedChange = onSoundChanged,
                        badgeBrush = GameBrush.Blue
                    ) { SpeakerGlyph(Color.White, Modifier.size(AppIcon.medium)) }
                    GameDivider(modifier = Modifier.padding(horizontal = AppSpace.card))
                    ToggleRow(
                        title = "Haptics",
                        subtitle = "Vibrate on taps and results",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onHapticsChanged,
                        badgeBrush = GameBrush.Teal
                    ) { VibrateGlyph(Color.White, Modifier.size(AppIcon.medium)) }
                }

                // ---- Appearance --------------------------------------------
                Spacer(Modifier.height(AppSpace.betweenSections))
                GameSectionHeader(text = "Appearance", modifier = Modifier.padding(start = AppSpace.hair))
                Spacer(Modifier.height(AppSpace.tight))
                GameGlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                            .padding(AppSpace.tight),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(ThemeOption.LIGHT, ThemeOption.DARK, ThemeOption.SYSTEM).forEach { option ->
                            ThemeChoice(
                                option = option,
                                selected = settings.theme == option,
                                onClick = { onThemeChanged(option) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ---- Daily Mystery -------------------------------------------
                Spacer(Modifier.height(AppSpace.betweenSections))
                GameSectionHeader(text = "Daily Mystery", modifier = Modifier.padding(start = AppSpace.hair))
                Spacer(Modifier.height(AppSpace.tight))
                GameGlassCard {
                    ToggleRow(
                        title = "Daily Reminder",
                        subtitle = when {
                            reminder.blocked -> "Notifications are off for Arrow Escape. Tap to allow them"
                            reminder.on -> "One reminder around ${reminderTimeLabel()}, if today's shape is still hidden"
                            else -> "A nudge when today's mystery shape is ready"
                        },
                        checked = reminder.on,
                        onCheckedChange = onReminderChanged,
                        badgeBrush = GameBrush.Gold
                    ) { BellGlyph(GamePalette.Brown, Modifier.size(AppIcon.medium)) }
                }

                // ---- Help ---------------------------------------------------
                Spacer(Modifier.height(AppSpace.betweenSections))
                GameSectionHeader(text = "Help", modifier = Modifier.padding(start = AppSpace.hair))
                Spacer(Modifier.height(AppSpace.tight))
                GameGlassCard(
                    onClick = onReplayTutorial,
                    contentDescription = "Replay tutorial, play the first board with hints again"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GameBadge(brush = GameBrush.Purple, size = 42.dp, shape = CircleShape) {
                            BookGlyph(Color.White, Modifier.size(AppIcon.medium))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Replay Tutorial",
                            style = AppText.cardTitle,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.FAINT),
                            modifier = Modifier.size(AppIcon.badge + 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(AppSpace.betweenCards))
                SettingsLinkCard(
                    title = "Rate Arrow Escape",
                    contentDescription = "Rate Arrow Escape on Google Play",
                    badgeBrush = GameBrush.Gold,
                    onClick = onRate
                ) { StarGlyph(color = GamePalette.Brown, modifier = Modifier.size(AppIcon.medium)) }

                Spacer(Modifier.height(AppSpace.betweenCards))
                SettingsLinkCard(
                    title = "Share with a Friend",
                    contentDescription = "Share Arrow Escape with a friend",
                    badgeBrush = GameBrush.Teal,
                    onClick = onShare
                ) { ShareGlyph(Color.White, Modifier.size(AppIcon.medium)) }
            }
        }
    }
}

/** A whole-card row that does one thing when pressed: a badge, a title, a chevron. */
@Composable
private fun SettingsLinkCard(
    title: String,
    contentDescription: String,
    badgeBrush: Brush,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    GameGlassCard(onClick = onClick, contentDescription = contentDescription) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameBadge(brush = badgeBrush, size = 42.dp, shape = CircleShape) { glyph() }
            Spacer(Modifier.width(12.dp))
            Text(text = title, style = AppText.cardTitle, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AppAlpha.FAINT),
                modifier = Modifier.size(AppIcon.badge + 2.dp)
            )
        }
    }
}

/**
 * A setting and its switch, as one whole-row target.
 *
 * The row is a single `toggleable` and the switch inside it is display-only, so
 * TalkBack announces one control — "Sound Effects, switch, on" — instead of a
 * label, a sentence and an unnamed toggle, and a thumb can hit anywhere on the
 * row. It is 68dp tall at the default font scale and grows from there, never the
 * other way.
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badgeBrush: Brush,
    glyph: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameBadge(brush = badgeBrush, size = 42.dp, shape = CircleShape) { glyph() }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppText.cardTitle)
            Text(
                text = subtitle,
                style = AppText.caption.copy(color = scheme.onSurface.copy(alpha = AppAlpha.FAINT))
            )
        }
        Spacer(Modifier.width(AppSpace.tight))
        Switch(
            checked = checked,
            // Display only: the row above owns the interaction.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GamePalette.ElectricBlue,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = scheme.onSurface.copy(alpha = 0.30f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/**
 * One of the three themes: an icon over a word. The chosen one fills with the
 * brand blue and springs a few percent as it is chosen — the state changing is
 * the one thing on this card worth a little motion.
 */
@Composable
private fun ThemeChoice(
    option: ThemeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AppRadius.tile - 4.dp)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "themeScale"
    )
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "themeFill"
    )
    val content = if (selected) Color.White else scheme.onSurface.copy(alpha = AppAlpha.MUTED)

    Column(
        modifier = modifier
            .heightIn(min = 76.dp)
            .scale(scale)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(
                Brush.verticalGradient(
                    listOf(
                        GamePalette.BlueTop.copy(alpha = fill),
                        GamePalette.BlueBottom.copy(alpha = fill)
                    )
                )
            )
            .border(
                AppStroke.hairline,
                if (selected) Color.White.copy(alpha = 0.55f) else scheme.onSurface.copy(alpha = 0.12f),
                shape
            )
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { contentDescription = "${option.label} theme" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when (option) {
                ThemeOption.LIGHT -> SunGlyph(content, Modifier.size(24.dp))
                ThemeOption.DARK -> MoonGlyph(content, Modifier.size(24.dp))
                ThemeOption.SYSTEM -> SystemThemeGlyph(content, Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = option.label,
            style = AppText.button.copy(
                fontSize = 13.sp,
                color = content,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}
