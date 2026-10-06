package com.sabalapps.arrowescape.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabalapps.arrowescape.settings.GameSettings
import com.sabalapps.arrowescape.settings.ThemeOption
import com.sabalapps.arrowescape.startup.StartupDestination
import com.sabalapps.arrowescape.ui.world.GameWorlds

/**
 * The places the player can be. A plain enum in a `rememberSaveable` back stack
 * rather than a navigation library: a handful of destinations, no arguments, no
 * deep links, and the stack is never more than three deep.
 *
 * New destinations are only ever appended, because the stack is saved as ordinals:
 * a saved state from an older build must still mean the same screens.
 *
 *  - [SETTINGS] is a screen of its own, reached from Home and from the game's HUD,
 *    and back returns to whichever it came from.
 *  - [DAILY] is the completed-today page: Home's Daily card opens it once the day
 *    is done, instead of dropping the player back into a finished board.
 *  - [DISCOVERIES] is the album of what the Campaign has uncovered. Home opens it,
 *    and so does the last level's result; back is Home either way, and a mystery in
 *    it can open its level, which is then a push — back from the board returns to
 *    the album.
 */
enum class Screen { HOME, GAME, LEVEL_SELECT, STATS, SETTINGS, DAILY, DISCOVERIES }

/** Enums are not Bundle-friendly, so the stack is saved as ordinals. */
private val ScreenStackSaver = listSaver<List<Screen>, Int>(
    save = { stack -> stack.map { it.ordinal } },
    restore = { saved -> saved.map { Screen.entries[it] } }
)

/**
 * Root of the app: owns navigation, and nothing else. Every screen below is a
 * plain function of state, and the single [GameViewModel] is passed down so the
 * board survives moving between Home, the level grid and the game.
 *
 * ## Where it opens
 *
 * [initialDestination] is what the *launch* asked for — Home, or today's Daily Challenge when
 * the daily reminder was tapped — and it becomes the stack the app **starts with**, so the
 * player lands on it directly rather than passing through Home. It is only read when there is
 * no saved stack to restore. [pendingDestination] is the same request arriving while the app is
 * already open; it is acknowledged through [onDestinationHandled] the moment it is acted on, so
 * it is acted on once.
 */
@Composable
fun ArrowEscapeApp(
    viewModel: GameViewModel,
    settings: GameSettings,
    onSoundChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onThemeChanged: (ThemeOption) -> Unit,
    retention: RetentionUi,
    modifier: Modifier = Modifier,
    initialDestination: StartupDestination? = null,
    pendingDestination: StartupDestination? = null,
    onDestinationHandled: () -> Unit = {}
) {
    var stack by rememberSaveable(stateSaver = ScreenStackSaver) {
        mutableStateOf(openingStack(initialDestination, viewModel))
    }
    // Which way the last move went, read by the transition so a push slides in from
    // the right and a pop slides back. Not saved: it only matters mid-animation.
    var forward by remember { mutableStateOf(true) }
    val progress by viewModel.playerProgress.collectAsStateWithLifecycle()
    val savedGame by viewModel.savedGame.collectAsStateWithLifecycle()
    val endlessProgress by viewModel.endlessProgress.collectAsStateWithLifecycle()
    val endlessSavedGame by viewModel.endlessSavedGame.collectAsStateWithLifecycle()
    val dailyProgress by viewModel.dailyProgress.collectAsStateWithLifecycle()
    // Read through the ViewModel's injected clock rather than from a direct call
    // to the clock here, so a test that says "it is tomorrow" is believed by the
    // UI as well as by the repository.
    val today = remember(dailyProgress) { viewModel.today() }
    val reducedMotion = rememberReducedMotion()
    val reminderPreference by retention.reminderPreference.collectAsStateWithLifecycle()

    val current = stack.last()

    // The world the player is in, which the menus without a board of their own
    // (Home, Stats, Settings) are dressed in. Derived exactly as the game screen
    // derives it, so a menu is always the scene the next board is played in.
    val resumeLevelId = savedGame?.levelId ?: progress.currentLevel
    val menuWorld = GameWorlds.forCampaignLevel(resumeLevelId)

    fun goTo(screen: Screen) {
        forward = true
        stack = stack + screen
    }

    fun back() {
        if (stack.size > 1) {
            forward = false
            stack = stack.dropLast(1)
        }
    }

    /** Replaces the whole stack, so a destination is reached the same way from any depth. */
    fun reset(vararg screens: Screen) {
        forward = screens.size > stack.size
        stack = screens.toList()
    }

    // Back is only intercepted away from Home; on Home the system default runs,
    // which is what leaves the app. Leaving the game this way keeps the saved
    // board — the ViewModel has already written every move to disk — so back
    // out of a level and Continue picks it straight back up.
    BackHandler(enabled = current != Screen.HOME) { back() }

    // The reminder tapped while the game is open. Acknowledged first, so a recomposition
    // while this runs cannot act on it twice; then the player is taken to the Daily
    // Challenge with Home beneath it, exactly as if they had pressed the card.
    LaunchedEffect(pendingDestination) {
        val destination = pendingDestination ?: return@LaunchedEffect
        onDestinationHandled()
        if (destination == StartupDestination.Daily) {
            reset(*openingStack(destination, viewModel).toTypedArray())
        }
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = { screenTransition(initialState, targetState, forward, reducedMotion) },
        label = "screen"
    ) { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                progress = progress,
                resumeLevelId = resumeLevelId,
                hasSavedGame = savedGame != null,
                endlessProgress = endlessProgress,
                hasEndlessSavedGame = endlessSavedGame != null,
                dailyProgress = dailyProgress,
                today = today,
                onContinue = {
                    viewModel.continueGame()
                    goTo(Screen.GAME)
                },
                // Every mode reuses Screen.GAME: the board, the HUD, the
                // animations and the feedback are the same screen, and only the
                // puzzle source behind it differs. A day that is already done
                // opens its summary first, with Play Again one tap away.
                onDaily = {
                    if (dailyProgress.isCompletedOn(today)) {
                        goTo(Screen.DAILY)
                    } else {
                        viewModel.startDaily()
                        goTo(Screen.GAME)
                    }
                },
                onLevelSelect = { goTo(Screen.LEVEL_SELECT) },
                onEndless = {
                    viewModel.startEndless()
                    goTo(Screen.GAME)
                },
                onDiscoveries = { goTo(Screen.DISCOVERIES) },
                onSettings = { goTo(Screen.SETTINGS) },
                onStats = { goTo(Screen.STATS) },
                modifier = modifier
            )

            Screen.LEVEL_SELECT -> LevelSelectScreen(
                progress = progress,
                onLevelChosen = { levelId ->
                    viewModel.startLevel(levelId)
                    goTo(Screen.GAME)
                },
                onBack = { back() },
                modifier = modifier,
                onOpenDiscoveries = { goTo(Screen.DISCOVERIES) }
            )

            Screen.STATS -> StatsScreen(
                stats = PlayerStats.from(
                    campaign = progress,
                    endless = endlessProgress,
                    daily = dailyProgress
                ),
                dailyDoneToday = dailyProgress.isCompletedOn(today),
                world = menuWorld,
                onBack = { back() },
                modifier = modifier
            )

            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                world = menuWorld,
                onSoundChanged = onSoundChanged,
                onHapticsChanged = onHapticsChanged,
                onThemeChanged = onThemeChanged,
                reminder = ReminderRowState.of(
                    wanted = reminderPreference.enabled,
                    allowed = retention.notificationsAllowed
                ),
                onReminderChanged = retention::setReminderEnabled,
                onRate = retention::rate,
                onShare = retention::share,
                // From either place the lesson needs a screen to be on, so this
                // both loads the board and navigates. The stack is replaced rather
                // than pushed: back out of the tutorial goes Home, not back into
                // Settings.
                onReplayTutorial = {
                    viewModel.startTutorial()
                    reset(Screen.HOME, Screen.GAME)
                },
                onBack = { back() },
                modifier = modifier
            )

            Screen.DAILY ->
                if (dailyProgress.isCompletedOn(today)) {
                    DailyScreen(
                        date = today,
                        dailyProgress = dailyProgress,
                        onPlayAgain = {
                            viewModel.startDaily()
                            reset(Screen.HOME, Screen.GAME)
                        },
                        onHome = { reset(Screen.HOME) },
                        onBack = { back() },
                        modifier = modifier
                    )
                } else {
                    // A back stack restored on a later day: this page only ever
                    // describes a day that is done, so it must not describe a new
                    // one. Home is where that day's Daily card lives.
                    LaunchedEffect(Unit) { reset(Screen.HOME) }
                }

            Screen.DISCOVERIES -> DiscoveriesScreen(
                progress = progress,
                world = menuWorld,
                onPlayLevel = { levelId ->
                    viewModel.startLevel(levelId)
                    goTo(Screen.GAME)
                },
                onBack = { back() },
                modifier = modifier
            )

            Screen.GAME -> GameScreen(
                settings = settings,
                onBack = { back() },
                onOpenSettings = { goTo(Screen.SETTINGS) },
                // From the result screen: drop back to the grid, replacing the
                // game rather than stacking on top of it, so back from there
                // still goes Home.
                onLevelSelect = { reset(Screen.HOME, Screen.LEVEL_SELECT) },
                // Endless has no level grid, so its result offers Home instead.
                // Resetting the stack rather than popping it means Home is
                // reached the same way from any depth.
                onHome = { reset(Screen.HOME) },
                // Clearing the last level offers the album instead of a "next".
                onOpenDiscoveries = { reset(Screen.HOME, Screen.DISCOVERIES) },
                modifier = modifier,
                viewModel = viewModel
            )
        }
    }
}

/**
 * The stack the app opens with. Home for an ordinary launch; for the daily reminder, today's
 * Daily Challenge on top of Home — the board if it is still to be solved, the "completed
 * today" page if it is not — which is also what the Daily card on Home does.
 *
 * Opening the board loads it into [viewModel], so this is only for the moment of *choosing*
 * the stack: once at launch, or once when a request arrives.
 */
private fun openingStack(destination: StartupDestination?, viewModel: GameViewModel): List<Screen> =
    when (destination) {
        StartupDestination.Daily ->
            if (viewModel.dailyProgress.value.isCompletedOn(viewModel.today())) {
                listOf(Screen.HOME, Screen.DAILY)
            } else {
                viewModel.startDaily()
                listOf(Screen.HOME, Screen.GAME)
            }

        StartupDestination.Home, null -> listOf(Screen.HOME)
    }

/**
 * How one screen gives way to the next. Short on purpose — 150 to 300ms — because
 * a menu is a thing you pass through, not a scene.
 *
 *  - into a board: the screen zooms up from slightly small, as if stepping into it;
 *  - out of a board: it breathes out again;
 *  - between menus: a slide, in the direction of travel;
 *  - with animations turned off, a quick cross-fade and nothing more.
 */
private fun screenTransition(
    from: Screen,
    to: Screen,
    forward: Boolean,
    reducedMotion: Boolean
): ContentTransform = when {
    reducedMotion -> fadeIn(tween(120)) togetherWith fadeOut(tween(100))

    to == Screen.GAME ->
        (fadeIn(tween(240)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.94f)) togetherWith
            fadeOut(tween(160))

    from == Screen.GAME ->
        fadeIn(tween(220)) togetherWith
            (fadeOut(tween(180)) + scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 1.04f))

    forward ->
        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(160)))

    else ->
        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 10 } + fadeOut(tween(160)))
}
