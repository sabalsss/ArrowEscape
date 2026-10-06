package com.sabalapps.arrowescape.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.notification.ReminderPreferenceRepository
import com.sabalapps.arrowescape.notification.ReminderServices
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.retention.RetentionCoordinator
import com.sabalapps.arrowescape.retention.RetentionPromptRepository
import com.sabalapps.arrowescape.settings.SettingsRepository
import com.sabalapps.arrowescape.settings.ThemeOption
import com.sabalapps.arrowescape.startup.LaunchRouter
import com.sabalapps.arrowescape.startup.StartupViewModel

/**
 * The whole app, in the order the player meets it: the loading screen, then the game.
 *
 * ```
 * Android system splash ─▶ SplashScreen (Compose) ─▶ ArrowEscapeApp
 *   navy, no logo           assembles, 0→100%         Home — or the Daily Challenge
 * ```
 *
 * The system splash and this loading screen share one navy, so the first is simply the second
 * before its arrows arrive. The game is composed underneath the loading screen during its
 * closing pulse (bar full, game ready), which then fades away over it: there is no frame where
 * Home is half-built and none where it appears from nothing.
 *
 * The loading screen is not a destination. It is not in the game's back stack and nothing
 * returns to it; the destination the launch asked for is read **once**, when the game is first
 * composed, and handed to `ArrowEscapeApp` as the stack it *starts* with — so a reminder tap
 * does not go Home → Daily, and does not go Home first at all.
 */
@Composable
fun AppRoot(
    startup: StartupViewModel,
    router: LaunchRouter,
    debugSink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Saved, so a recreated activity does not play the loading screen over a game in progress.
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var gameComposed by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()

    Box(modifier = modifier.fillMaxSize()) {
        if (gameComposed) GameRoot(router = router, debugSink = debugSink)

        AnimatedVisibility(
            visible = !splashDone,
            exit = fadeOut(tween(if (reducedMotion) 120 else 260)),
            modifier = Modifier.fillMaxSize()
        ) {
            ArrowEscapeTheme(theme = ThemeOption.SYSTEM) {
                SplashScreen(
                    progress = { startup.progress.value },
                    onReady = { gameComposed = true },
                    onFinished = { splashDone = true }
                )
            }
        }
    }
}

/** The game proper, built once the loading screen has said it can be. */
@Composable
private fun GameRoot(router: LaunchRouter, debugSink: (String) -> Unit) {
    val app = LocalContext.current.applicationContext
    val settingsRepository = remember { SettingsRepository.get(app) }
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModelFactory(
            progress = ProgressRepository.get(app),
            endless = EndlessRepository.get(app),
            daily = DailyRepository.get(app),
            // The settings store is also where the tutorial's persisted bits live;
            // see SettingsRepository.
            tutorialFlags = settingsRepository,
            debugSink = debugSink
        )
    )
    val reminders = remember { ReminderServices.get(app) }
    val retention = rememberRetentionUi(
        coordinator = remember {
            RetentionCoordinator(
                prompts = RetentionPromptRepository.get(app),
                reminders = ReminderPreferenceRepository.get(app)
            )
        },
        reminders = reminders
    )

    // Resolved once: what this launch asked for becomes the stack the game starts with.
    val initialDestination = remember { router.consume() }
    val pendingDestination by router.pending.collectAsStateWithLifecycle()

    ArrowEscapeTheme(theme = settings.theme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalRetentionUi provides retention) {
                ArrowEscapeApp(
                    viewModel = gameViewModel,
                    settings = settings,
                    onSoundChanged = settingsRepository::setSoundEnabled,
                    onHapticsChanged = settingsRepository::setHapticsEnabled,
                    onThemeChanged = settingsRepository::setTheme,
                    initialDestination = initialDestination,
                    pendingDestination = pendingDestination,
                    onDestinationHandled = { router.consume() },
                    retention = retention,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                )
            }
        }
    }
}
