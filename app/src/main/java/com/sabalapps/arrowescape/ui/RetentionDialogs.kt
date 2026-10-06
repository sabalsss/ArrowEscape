package com.sabalapps.arrowescape.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sabalapps.arrowescape.retention.RetentionPrompt

/**
 * The optional prompt that follows a result, as a card over a soft scrim in the game's own
 * glass. Neutral on purpose: it asks, it thanks nobody for anything, and every button is
 * the same weight a player would expect — [GameSecondaryButton]s and a quiet text action,
 * with the one filled button reserved for the thing the card is *for*.
 *
 * "Not Now" is the Back button as well, and tapping the scrim does nothing: a prompt that
 * can be dismissed by a stray tap is a prompt that is never read, and one that cannot be
 * dismissed at all is an advertisement.
 */
@Composable
fun RetentionPromptLayer(
    prompt: RetentionPrompt?,
    onRate: () -> Unit,
    onShare: () -> Unit,
    onEnableReminder: () -> Unit,
    onDismiss: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    // The last real prompt, kept while the card animates out so it does not empty mid-fade.
    var shown by remember { mutableStateOf<RetentionPrompt?>(null) }
    if (prompt != null) shown = prompt

    AnimatedVisibility(
        visible = prompt != null,
        enter = fadeIn(tween(if (reducedMotion) 120 else 220)),
        exit = fadeOut(tween(if (reducedMotion) 100 else 160)),
        modifier = modifier.fillMaxSize()
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC070A1F))
                // Swallows every touch: the result underneath is not reachable until the card is
                // answered. Pointer input rather than `clickable`, so the scrim is not announced
                // as an unlabeled button — screen-reader focus is never trapped or confused by it.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = prompt != null,
                // The card rises a little as it fades in — and only fades with animations off.
                enter = if (reducedMotion) {
                    fadeIn(tween(120))
                } else {
                    fadeIn(tween(240)) + slideInVertically(tween(260)) { it / 12 }
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(100))
                } else {
                    fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 16 }
                }
            ) {
                when (val current = shown) {
                    RetentionPrompt.DailyReminderInvite -> PromptCard(
                        title = "Want a daily mystery?",
                        body = "Get one reminder when today's puzzle is ready.",
                        primaryLabel = "Enable Daily Reminder",
                        onPrimary = onEnableReminder,
                        secondaryLabel = null,
                        onSecondary = {},
                        dismissLabel = "Not Now",
                        onDismiss = onDismiss
                    )

                    is RetentionPrompt.RateAndShare -> PromptCard(
                        title = "Enjoying Arrow Escape?",
                        body = "If you're having fun, you can support the game.",
                        primaryLabel = "Rate on Play Store",
                        onPrimary = onRate,
                        secondaryLabel = if (current.showShare) "Share with a Friend" else null,
                        onSecondary = onShare,
                        dismissLabel = "Not Now",
                        onDismiss = onDismiss
                    )

                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit
) {
    GameGlassCard(
        tone = GlassTone.Dark,
        shape = RoundedCornerShape(AppRadius.dialog),
        elevation = AppElevation.hero,
        modifier = Modifier
            .padding(horizontal = AppSpace.screenH + 6.dp)
            .widthIn(max = 380.dp)
            // Announced when it arrives, without taking focus from anything.
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.card + 4.dp, vertical = AppSpace.card + 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = AppText.screenTitle.copy(color = Color.White),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(AppSpace.tight))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
                    .copy(color = Color.White.copy(alpha = 0.82f)),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpace.card + 4.dp))
            GamePrimaryButton(label = primaryLabel, onClick = onPrimary)
            if (secondaryLabel != null) {
                Spacer(Modifier.height(AppSpace.tight + 2.dp))
                GameSecondaryButton(
                    label = secondaryLabel,
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(AppSpace.tight))
            // The quiet way out: a text action, as tall as a touch target and no heavier than that.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppIcon.touch)
                    .clip(RoundedCornerShape(AppRadius.control))
                    .gameClickable(onClick = onDismiss, pressedScale = 0.97f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dismissLabel,
                    style = AppText.button.copy(color = Color.White.copy(alpha = 0.72f))
                )
            }
        }
    }
}

/**
 * The small nod after a first clear of Level 1 or 2: a pill that fades up near the top,
 * stays a couple of seconds and goes. It is said once, never repeats, and is announced
 * politely for screen readers.
 */
@Composable
fun CoachToast(
    text: String?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text

    AnimatedVisibility(
        visible = text != null,
        enter = if (reducedMotion) {
            fadeIn(tween(120))
        } else {
            fadeIn(tween(260)) + slideInVertically(tween(300)) { -it / 2 }
        },
        exit = fadeOut(tween(if (reducedMotion) 100 else 260)),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(percent = 50)
        val style = glassStyle(GlassTone.Dark)
        Box(
            modifier = Modifier
                .softShadow(8.dp, shape)
                .clip(shape)
                .background(style.fill)
                .padding(horizontal = 20.dp, vertical = 11.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✨  $shown",
                style = AppText.cardTitle.copy(color = Color.White),
                textAlign = TextAlign.Center
            )
        }
    }
}
