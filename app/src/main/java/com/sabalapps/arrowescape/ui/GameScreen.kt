package com.sabalapps.arrowescape.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.feedback.GameSound
import com.sabalapps.arrowescape.feedback.HapticEffect
import com.sabalapps.arrowescape.feedback.Haptics
import com.sabalapps.arrowescape.feedback.SoundPlayer
import com.sabalapps.arrowescape.settings.GameSettings
import com.sabalapps.arrowescape.shape.GridContourTracer
import com.sabalapps.arrowescape.shape.ShapeContour
import com.sabalapps.arrowescape.shape.ShapeMask
import com.sabalapps.arrowescape.retention.CampaignWin
import com.sabalapps.arrowescape.retention.RetentionPrompt
import com.sabalapps.arrowescape.tutorial.OnboardingCoach
import com.sabalapps.arrowescape.tutorial.OnboardingGuide
import com.sabalapps.arrowescape.ui.world.DiscoveryCompletion
import com.sabalapps.arrowescape.settings.ThemeOption
import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import com.sabalapps.arrowescape.ui.world.GameWorlds
import com.sabalapps.arrowescape.ui.world.WorldBackground
import com.sabalapps.arrowescape.ui.world.WorldStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

/** Quick press-in before the arrow launches, so the tap feels acknowledged. */
private const val LAUNCH_WINDUP_MS = 70
private const val FLIGHT_MS = 300
private const val SHAKE_STEP_MS = 55
private const val BLOCKER_PULSE_MS = 240

/** One breath of the hint glow, in each direction. */
private const val HINT_PULSE_MS = 760

/**
 * How long a hint stays up before it fades itself out.
 *
 * Long enough to find the arrow on a nine-row board without hunting, short
 * enough that a hint left on screen does not become a permanent marker the
 * player plays off instead of reading the board.
 */
private const val HINT_VISIBLE_MS = 4_200L

/**
 * The acknowledgement an arrow gets when the last removal opened its path.
 *
 * Deliberately quick and deliberately unlike the hint: a hint breathes teal for
 * four seconds because the player asked a question, while this is the board
 * noting what just changed and getting out of the way. It is also why the
 * reaction is one-shot — a marker that stayed up would be a free solver.
 */
private const val AWAKEN_RISE_MS = 120
private const val AWAKEN_FALL_MS = 180

/** Long enough for [AWAKEN_RISE_MS] + [AWAKEN_FALL_MS] to have finished. */
private const val AWAKEN_VISIBLE_MS = (AWAKEN_RISE_MS + AWAKEN_FALL_MS + 60).toLong()

/** The impact ring and sparks under a blocked tap, over the shake. */
private const val IMPACT_MS = 300

/** How much of the impact the tile's compression takes up, at the very start. */
private const val IMPACT_COMPRESSION = 0.35f

/** Material's minimum accessible touch target. */
private val MIN_TOUCH_TARGET = 48.dp

/** Largest the board is allowed to get, so it stays a board on tablets. */
private val MAX_BOARD_WIDTH = 460.dp

/** No level is wider than this, which is what keeps a cell finger-sized. */
private const val MAX_COLUMNS = 6

// Chrome comes in two sizes. On a window with room to spare the roomy set looks
// better; once it would push a cell under MIN_TOUCH_TARGET, the padding gives
// way instead of the arrows. Width and height are decided separately, because a
// narrow window and a short one squeeze different things.
private val ROOMY_SCREEN_PADDING = 18.dp
private val ROOMY_BOARD_PADDING = 12.dp
private val TIGHT_SCREEN_PADDING = 8.dp
private val TIGHT_BOARD_PADDING = 6.dp

private val ROOMY_VERTICAL_PADDING = 12.dp
private val ROOMY_GAP_ABOVE_BOARD = 18.dp
private val ROOMY_GAP_BELOW_BOARD = 14.dp
private val TIGHT_VERTICAL_PADDING = 6.dp
private val TIGHT_GAP_ABOVE_BOARD = 8.dp
private val TIGHT_GAP_BELOW_BOARD = 8.dp

/**
 * Below this width the roomy padding would squeeze a six-column board under
 * [MIN_TOUCH_TARGET]. The tight pair is a little tighter than it strictly has
 * to be, which leaves a dp of headroom for rounding on the narrowest phones.
 */
private val TIGHT_LAYOUT_BELOW =
    MIN_TOUCH_TARGET * MAX_COLUMNS + (ROOMY_SCREEN_PADDING + ROOMY_BOARD_PADDING) * 2 + 8.dp

/**
 * Below this much room above the system bars, the tallest board (nine rows) runs
 * out of height before it runs out of width, so the vertical gaps close up too.
 */
private val SHORT_LAYOUT_BELOW = 640.dp

// How the chrome sits over the world artwork.
//
// Everything is translucent rather than opaque, because the artwork being
// visible is the whole point of having it — but translucent enough to separate
// the board and the HUD from whatever is behind them, because the board is still
// what the player is reading. The numbers are the least opaque that keep
// Material's surface/onSurface pairs at their intended contrast over all five
// backdrops, the brightest (Sky Garden) and the darkest (Cosmic) included.
//
// Deliberately no blur anywhere: a runtime blur over a full-screen image costs
// more than the rest of this screen put together and buys nothing a flat
// translucent surface does not already do here.

/**
 * The board surface. Low enough that the scene still reads behind the grid, high
 * enough that the arrows on it keep their contrast over the brightest backdrop.
 */
private const val BOARD_SURFACE_ALPHA = 0.88f

/**
 * How much of the world's accent is mixed into the board surface.
 *
 * Low, but richer than it was: enough that the board belongs to the scene — a
 * faintly warm slab on Sunset Canyon, a faintly cool one in Crystal Night — rather
 * than being a white rectangle dropped on it; not enough to tint the arrows' own
 * indigo or to make the five worlds need five arrow styles.
 */
private const val BOARD_TINT = 0.12f

/** A hairline edge, so a translucent surface still has a defined boundary. */
private val CHROME_BORDER = 1.dp

// A Campaign shape is sized from what it occupies rather than from the window; see
// `ShapeFit`. The cap is what keeps the smallest boards (3 columns, 8 arrows) from
// being blown up to fill a screen built for 35: with it, every level's pieces land
// between roughly 50 and 60dp on a phone, so the bigger the picture the bigger it
// is on screen, and no level looks accidentally huge or accidentally tiny.
private val SHAPE_MAX_CELL = 60.dp

/**
 * Room kept above and below a Campaign shape inside its stage. The roomy value
 * gives the formation air; the tight one gives a 9-row board every dp it can have
 * on a short window, where its cells are already near the 48dp touch target.
 */
private val ROOMY_SHAPE_MARGIN = 10.dp
private val TIGHT_SHAPE_MARGIN = 4.dp

/** How far past the formation's own edge the halo reaches, in cells. */
private const val SHAPE_HALO_SPREAD_CELLS = 0.9f

/**
 * How much of the halo is left when the last arrow has gone. It dims as the shape
 * is taken apart — the formation it was supporting is leaving — but never to
 * nothing, so the world does not snap bare under the final arrow.
 */
private const val SHAPE_HALO_FLOOR = 0.45f
private const val SHAPE_HALO_EASE_MS = 400

/** The closing build-up eases in more slowly than the dimming: it should be felt, not seen. */
private const val SHAPE_ANTICIPATION_EASE_MS = 520

@Composable
fun GameScreen(
    settings: GameSettings,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onLevelSelect: () -> Unit,
    onHome: () -> Unit,
    /** The last Campaign level's result offers the album in place of "next". */
    onOpenDiscoveries: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val escaping by viewModel.escaping.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val unblocked by viewModel.unblocked.collectAsStateWithLifecycle()
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val tutorial by viewModel.tutorial.collectAsStateWithLifecycle()
    val dependency by viewModel.dependencyLesson.collectAsStateWithLifecycle()
    val dailyProgress by viewModel.dailyProgress.collectAsStateWithLifecycle()
    // The stars the Campaign run just earned, or null in every other mode and
    // on every loss. Set on the frame the board resolves, so it is already here
    // when the celebration and then the card read it.
    val campaignStars by viewModel.campaignStars.collectAsStateWithLifecycle()
    // What that clear uncovered, set on the same frame and by the same win.
    val campaignDiscovery by viewModel.campaignDiscovery.collectAsStateWithLifecycle()
    // What a solved Daily / Endless board was hiding. Null for the whole of a board in play.
    val shapeReveal by viewModel.shapeReveal.collectAsStateWithLifecycle()
    val playerProgress by viewModel.playerProgress.collectAsStateWithLifecycle()

    // Sound and haptics are built here rather than inside GameFeedbackEffect
    // because the win stinger is no longer played when the event arrives: the
    // celebration holds it back to its own peak (§12b).
    val feedbackView = LocalView.current
    val feedbackContext = LocalContext.current
    val haptics = remember(feedbackView) { Haptics(feedbackView) }
    val soundPlayer = remember(feedbackContext) { SoundPlayer(feedbackContext) }
    DisposableEffect(soundPlayer) { onDispose { soundPlayer.release() } }

    GameFeedbackEffect(
        events = viewModel.events,
        settings = settings,
        soundPlayer = soundPlayer,
        haptics = haptics
    )

    val currentSettings by rememberUpdatedState(settings)
    val reducedMotion = rememberReducedMotion()
    // What finishing turns into. Derived from the mode and the level — never from a result having
    // arrived — so the choice cannot race the win that makes it.
    val kind = completionKindFor(mode, hidesDiscovery = CampaignDiscoveries.forLevel(state.level.id) != null)
    val celebration = rememberWinCelebration(
        status = state.status,
        kind = kind,
        reducedMotion = reducedMotion,
        // The outline has closed: one very small confirmation, not a second celebration.
        onLock = {
            soundPlayer.play(GameSound.SHAPE_CONFIRM, currentSettings.soundEnabled)
            haptics.perform(HapticEffect.SHAPE_LOCK, currentSettings.hapticsEnabled)
        },
        onPeak = {
            soundPlayer.play(GameSound.LEVEL_COMPLETE, currentSettings.soundEnabled)
            haptics.perform(HapticEffect.CELEBRATION, currentSettings.hapticsEnabled)
        }
    )

    // A hint fades itself out rather than staying up until the next tap, so the
    // board never ends up with a permanent marker on it. Keyed on the nonce, so
    // tapping Hint again restarts the clock instead of inheriting the old one.
    LaunchedEffect(hint?.nonce) {
        val showing = hint ?: return@LaunchedEffect
        delay(HINT_VISIBLE_MS)
        viewModel.onHintFinished(showing.nonce)
    }

    // The newly-freed reaction is retired centrally rather than by whichever
    // cell happens to finish last: one pulse can light several arrows, and one
    // timer per pulse is both simpler and the only version where "the reaction
    // has played" means the same thing for all of them. Nonce-keyed, so a second
    // removal restarts the clock instead of inheriting the first one's.
    LaunchedEffect(unblocked?.nonce) {
        val pulse = unblocked ?: return@LaunchedEffect
        delay(AWAKEN_VISIBLE_MS)
        viewModel.onUnblockedFeedbackFinished(pulse.nonce)
    }

    // What the lessons point at — Level 1's free arrow, Level 2's "clear one to free
    // another" — resolved from the real board by the engines the Hint button already uses
    // (`OnboardingGuide`), so a lesson and a hint can never disagree about what a good
    // move looks like and nothing here restates the blocking rule.
    val guide = remember(tutorial, dependency, state.arrows) {
        OnboardingGuide.resolve(tutorial, dependency, state.arrows)
    }
    val tutorialSpotlight = guide.spotlightId?.let {
        HintHighlight(tileId = it, nonce = TUTORIAL_SPOTLIGHT_NONCE)
    }
    // An explicit hint wins: the player asked for it, and the tutorial's own
    // emphasis is already pointing at the arrow the engine would pick anyway.
    val spotlight = hint ?: tutorialSpotlight
    val hintAvailable = remember(state) { viewModel.canHint() }

    // Which world this board is played in. Derived, never stored — see
    // `GameWorlds` — so the same board always comes back to the same place,
    // including after process death. The level id is only read in Campaign; the
    // other modes answer from their own identity.
    val world = remember(mode, state.level.id) { GameWorlds.forMode(mode, state.level.id) }
    val worldStyle = remember(world) { WorldStyle.of(world) }
    // The Daily Challenge reuses one of the five worlds and is told apart by a
    // restrained golden layer over it: a wash in the background, a hairline edge
    // on the board, and the tier in gold rather than white. No sixth asset, and
    // no second GameScreen.
    val dailyAccent = if (mode is GameMode.Daily) MaterialTheme.colorScheme.tertiary else null
    // Every mode's board is a picture made of arrows and is drawn as one — the arrows are the
    // clue. Decided once, here, so nothing below asks which mode it is — it asks how the board is
    // being presented.
    val presentation = boardPresentationFor(mode)

    // A win waits for the celebration; a loss shows immediately, because
    // nothing is being celebrated.
    val resultShowing = isResultShowing(state.status, celebration.resultVisible)

    // ---- what follows a Campaign win, once the reward has been given ------------------
    //
    // Strictly *after* the result: the last arrow, the outline, the reveal and every beat of
    // it belong to the discovery, and nothing here starts until the reveal has finished
    // arriving and had a moment to be looked at. Then, in order: a small nod for the first
    // clear of Level 1 or 2, then — if the policy allows one — a single optional prompt.
    // Losses, Daily, Endless and the replayed tutorial never get here.
    val retention = LocalRetentionUi.current
    var coachLine by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf<RetentionPrompt?>(null) }
    val latestDiscovery by rememberUpdatedState(campaignDiscovery)
    val latestProgress by rememberUpdatedState(playerProgress)
    val campaignWinSettling = resultShowing && state.status == GameStatus.WON && mode == GameMode.Campaign
    LaunchedEffect(campaignWinSettling, state.level.id) {
        coachLine = null
        if (!campaignWinSettling) {
            prompt = null
            return@LaunchedEffect
        }
        delay(PromptTiming.settleDelayMs(reducedMotion))
        val discovery = latestDiscovery
        OnboardingCoach.lineForWin(state.level.id, discovery?.isFirstClear == true)?.let { line ->
            coachLine = line
            delay(COACH_VISIBLE_MS)
            coachLine = null
            delay(COACH_TO_PROMPT_GAP_MS)
        }
        prompt = retention?.coordinator?.onCampaignResultSettled(
            CampaignWin(
                completedCount = latestProgress.completedLevels.size,
                finishesASet = discovery != null && discovery.completion != DiscoveryCompletion.NONE
            )
        )
    }
    // The outline of the puzzle just played, traced from its own occupied cells. Cached per level: a
    // board is a fixed picture, so this is computed once — not per frame, and not per arrow.
    val contour = remember(state.level) { GridContourTracer.trace(ShapeMask.of(state.level)) }
    val won = state.status == GameStatus.WON

    // Where the Campaign shape was — the middle of the stage, in this screen's own
    // coordinates — so the discovery can start its reveal there and settle into the
    // result's layout. Measured, never assumed: until it is known the reveal simply
    // fades in where it will end up.
    var stageCentre by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // The world stays under everything, result included: the result is a moment
        // in the world the player was just playing in, not a different place.
        WorldBackground(world = world, daily = dailyAccent != null)
        // The game itself. While a result is up the controls leave: the result's
        // scrim is translucent so the world shows through it, and a HUD, a hint
        // pill or a progress bar left underneath would show through it too, as a
        // faded ghost. They fade out over the span the scrim fades in over, so the
        // bare world is never exposed in between, and then leave composition — and
        // with it touch and the accessibility tree, which is also what a screen
        // reader should have: the result, not the finished board. Only presentation:
        // the board's state is in the ViewModel and the chrome comes straight back,
        // with no entrance of its own, when the result goes.
        AnimatedVisibility(
            visible = gameplayChromeVisible(resultShowing),
            enter = EnterTransition.None,
            exit = fadeOut(
                tween(resultEnterMs(kind, reducedMotion), easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .fillMaxSize()
                .then(if (resultShowing) Modifier.clearAndSetSemantics {} else Modifier)
        ) {
            // `modifier` carries the system-bar insets, so it belongs here: the
            // constraints read below have to be the space the board actually gets.
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val narrow = maxWidth < TIGHT_LAYOUT_BELOW
                val short = maxHeight < SHORT_LAYOUT_BELOW
                val livesInTopRow = maxWidth >= HUD_LIVES_IN_TOP_ROW_FROM
                val screenPadding = if (narrow) TIGHT_SCREEN_PADDING else ROOMY_SCREEN_PADDING
                val boardPadding = if (narrow) TIGHT_BOARD_PADDING else ROOMY_BOARD_PADDING
                val verticalPadding = if (short) TIGHT_VERTICAL_PADDING else ROOMY_VERTICAL_PADDING
                val gapAboveBoard = if (short) TIGHT_GAP_ABOVE_BOARD else ROOMY_GAP_ABOVE_BOARD
                val gapBelowBoard = if (short) TIGHT_GAP_BELOW_BOARD else ROOMY_GAP_BELOW_BOARD

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = screenPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GameHud(
                        // All a mode changes about the HUD is the name: a
                        // generated board has no catalogue name, so it is named
                        // by what it is and badged with its difficulty instead.
                        title = hudTitle(mode, state.level.name),
                        subtitle = hudSubtitle(mode, world.displayName),
                        description = headerTitle(mode, state.level.name),
                        goldSubtitle = mode is GameMode.Daily,
                        remaining = state.arrows.size,
                        total = state.level.arrows.size,
                        lives = state.lives,
                        livesInTopRow = livesInTopRow,
                        onBack = onBack,
                        onRestart = viewModel::restart,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.widthIn(max = MAX_BOARD_WIDTH)
                    )
                    Spacer(Modifier.height(gapAboveBoard))
                    Box(
                        modifier = when (presentation) {
                            // fill = false lets a tall board shrink to the height it
                            // has left rather than pushing the hint line off the
                            // screen; the grid still sizes itself by aspect ratio.
                            BoardPresentation.Grid -> Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = MAX_BOARD_WIDTH)

                            // A shape has no container to size, so its wrapper is the
                            // *stage*: all the room between the HUD and the footer,
                            // which the shape is then centred and fitted within.
                            BoardPresentation.Shape -> Modifier
                                .weight(1f)
                                .widthIn(max = MAX_BOARD_WIDTH)
                                .fillMaxWidth()
                        }.onGloballyPositioned { stage ->
                            val origin = stage.positionInRoot()
                            stageCentre = Offset(
                                origin.x + stage.size.width / 2f,
                                origin.y + stage.size.height / 2f
                            )
                        }
                    ) {
                        Board(
                            presentation = presentation,
                            state = state,
                            escaping = escaping,
                            blocked = blocked,
                            unblocked = unblocked,
                            spotlight = spotlight,
                            handTargetId = guide.handTargetId.takeIf { state.status == GameStatus.PLAYING },
                            contentPadding = boardPadding,
                            shapeMargin = if (short) TIGHT_SHAPE_MARGIN else ROOMY_SHAPE_MARGIN,
                            // The last three arrows lean the halo in, in every mode but the lesson.
                            anticipate = mode != GameMode.Tutorial,
                            worldAccent = worldStyle.accent,
                            accent = dailyAccent,
                            // Only a *won* board draws the outline, and only from the clock that the
                            // win started — a board in play or a lost one never confirms a shape.
                            confirm = if (won) {
                                ShapeConfirmSpec(
                                    contour = contour,
                                    schedule = celebration.schedule,
                                    clock = { celebration.clock.value },
                                    accent = dailyAccent?.let { lerp(worldStyle.accent, it, 0.5f) } ?: worldStyle.accent
                                )
                            } else {
                                null
                            },
                            isTappable = viewModel::isTappable,
                            onArrowTapped = viewModel::onArrowTapped,
                            onEscapeFinished = viewModel::onEscapeAnimationFinished,
                            onBlockedFinished = viewModel::onBlockedFeedbackFinished,
                            modifier = Modifier
                        )
                    }
                    Spacer(Modifier.height(gapBelowBoard))
                    GameFooter(
                        // While the tutorial is running its caption is the line that
                        // matters, so it takes the one line there is rather than
                        // being stacked under a sentence that says the same thing.
                        message = guide.caption
                            ?: mysteryLine(mode, state.level.id, playerProgress.isCompleted(state.level.id)),
                        emphasised = guide.caption != null,
                        hintEnabled = hintAvailable,
                        onHint = viewModel::requestHint,
                        // The board is done: the line and the hint have nothing left to say, and the
                        // outline is what the eye should be on.
                        modifier = Modifier
                            .widthIn(max = MAX_BOARD_WIDTH)
                            .alpha(if (won) 0f else 1f)
                    )
                }
            }
        }

        if (resultShowing) {
            val arrowsTotal = state.level.arrows.size
            val tierName = tierLabel(mode)
            ResultScreen(
                won = state.status == GameStatus.WON,
                livesLeft = state.lives,
                arrowsCleared = (arrowsTotal - state.arrows.size).coerceIn(0, arrowsTotal),
                arrowsTotal = arrowsTotal,
                // Only Campaign persists the hint flag, so only Campaign can say.
                hintUsed = if (mode == GameMode.Campaign) viewModel.hintUsedOnBoard else null,
                world = world,
                heading = headerTitle(mode, state.level.name),
                subheading = if (tierName != null) {
                    "$tierName \u00b7 ${world.displayName}"
                } else {
                    world.displayName
                },
                dailyDate = (mode as? GameMode.Daily)?.date?.friendly(),
                context = when (val current = mode) {
                    GameMode.Campaign -> ResultContext.Campaign(
                        isLastLevel = LevelProgression.isLast(state.level),
                        // Null on a loss, which is what leaves a loss unrated.
                        stars = campaignStars?.earned ?: StarRating.NONE,
                        bestStars = campaignStars?.best ?: StarRating.NONE,
                        isNewBest = campaignStars?.isNewBest == true,
                        // Null on a loss, which keeps a loss the plain card.
                        discovery = campaignDiscovery
                    )

                    is GameMode.Endless -> ResultContext.Endless(
                        puzzleNumber = current.puzzleNumber,
                        tierLabel = current.tier.label
                    )

                    is GameMode.Daily -> ResultContext.Daily(
                        tierLabel = current.tier.label,
                        streak = dailyProgress.currentStreak,
                        bestStreak = dailyProgress.bestStreak,
                        // A replay of a day already cleared says so, rather than
                        // congratulating the player on a streak that did not move.
                        alreadyCountedToday = current.alreadyCleared
                    )

                    GameMode.Tutorial -> ResultContext.Tutorial
                },
                onContinue = viewModel::continueAfterWin,
                onReplay = viewModel::replayCurrent,
                onLevelSelect = onLevelSelect,
                onHome = onHome,
                modifier = modifier,
                onViewDiscoveries = onOpenDiscoveries,
                revealFrom = stageCentre,
                kind = kind,
                // The name arrives only here, once the board is won; before that there is none.
                shape = shapeReveal?.let { ShapeResult(it.name, contour) }
            )
        }

        // The nod, over the result, near the top where the discovery is not.
        Box(modifier = modifier.fillMaxSize()) {
            CoachToast(
                text = coachLine,
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            )
        }

        // The one optional prompt, over everything — answered before the result is reachable again.
        RetentionPromptLayer(
            prompt = prompt,
            reducedMotion = reducedMotion,
            onRate = {
                prompt = null
                retention?.rate()
            },
            onShare = {
                prompt = null
                retention?.share()
            },
            onEnableReminder = {
                prompt = null
                retention?.setReminderEnabled(true)
            },
            onDismiss = { prompt = null }
        )
    }
}

/** How long the small nod stays up, and the quiet gap between it and a prompt. */
private const val COACH_VISIBLE_MS = 2_600L
private const val COACH_TO_PROMPT_GAP_MS = 500L

/**
 * The heading over the board. A campaign level has a name of its own; the other
 * three boards are named by what they are, because a generated board has no
 * name and the tutorial is not a level.
 */
private fun headerTitle(mode: GameMode, levelName: String): String = when (mode) {
    GameMode.Campaign -> levelName
    is GameMode.Endless -> mode.title
    is GameMode.Daily -> mode.title
    GameMode.Tutorial -> "How to play"
}

/**
 * The difficulty badge under the heading, or null for no badge.
 *
 * Always a name — Beginner through Expert — and never the numeric difficulty
 * score. The score is a generator-tuning number: it is what the quality filter
 * compares against, it moves whenever the metric is re-cut, and "67.5" tells a
 * player nothing they can act on. It stays in the debug log.
 *
 * A campaign level carries its difficulty in its level number, and the tutorial
 * has no difficulty worth naming, so neither gets one.
 */
private fun tierLabel(mode: GameMode): String? = when (mode) {
    is GameMode.Endless -> mode.tier.label
    is GameMode.Daily -> mode.tier.label
    GameMode.Campaign, GameMode.Tutorial -> null
}

/**
 * The HUD pill's first line. Abbreviated where the full name would not fit
 * beside four controls at 320dp — the full name goes to TalkBack.
 */
private fun hudTitle(mode: GameMode, levelName: String): String = when (mode) {
    GameMode.Campaign -> levelName
    is GameMode.Endless -> mode.title
    is GameMode.Daily -> "Daily"
    GameMode.Tutorial -> "How to play"
}

/** The HUD pill's second line: the tier where there is one, else the world. */
private fun hudSubtitle(mode: GameMode, worldName: String): String =
    tierLabel(mode) ?: worldName

/**
 * Below this width the hearts leave the top row and sit beside the progress bar,
 * so the level name keeps room for "Endless #51" at 320dp.
 */
private val HUD_LIVES_IN_TOP_ROW_FROM = 400.dp

/**
 * The nonce the tutorial's emphasis uses. A constant rather than a counter,
 * because the emphasis is a steady "look here" that should survive arrows
 * leaving the board, while a hint is a one-shot that should restart its glow
 * every time it is asked for.
 */
private const val TUTORIAL_SPOTLIGHT_NONCE = -1

/**
 * Turns game events into sound and haptics in one place, so every outcome has a
 * consistent feel and the ViewModel stays free of Android types.
 */
@Composable
private fun GameFeedbackEffect(
    events: SharedFlow<GameEvent>,
    settings: GameSettings,
    soundPlayer: SoundPlayer,
    haptics: Haptics
) {
    val currentSettings by rememberUpdatedState(settings)

    LaunchedEffect(events) {
        events.collect { event ->
            val sound = currentSettings.soundEnabled
            val haptic = currentSettings.hapticsEnabled
            when (event) {
                GameEvent.ArrowEscaped -> {
                    soundPlayer.play(GameSound.ESCAPE, sound)
                    haptics.perform(HapticEffect.LIGHT_SUCCESS, haptic)
                }
                GameEvent.MoveBlocked -> {
                    soundPlayer.play(GameSound.BLOCKED, sound)
                    haptics.perform(HapticEffect.REJECTION, haptic)
                }
                // LevelComplete is deliberately absent: the win stinger and
                // its haptic belong to the celebration's peak, not to the
                // frame the board resolved on (§12b).
                GameEvent.LevelComplete -> Unit
                GameEvent.GameOver -> {
                    soundPlayer.play(GameSound.GAME_OVER, sound)
                    haptics.perform(HapticEffect.FAILURE, haptic)
                }
            }
        }
    }
}

/**
 * The board, in whichever presentation its mode calls for.
 *
 * Both presentations place the same [ArrowCell]s on the same logical grid through
 * [BoardPieces]; what differs is only what is drawn around them and how the grid is
 * fitted to the window. [contentPadding] and [accent] belong to the grid, and
 * [shapeMargin] to the shape.
 */
@Composable
private fun Board(
    presentation: BoardPresentation,
    state: GameState,
    escaping: List<EscapingArrow>,
    blocked: BlockedFeedback?,
    /** The arrows the last successful removal freed, if it freed any. */
    unblocked: UnblockedPulse?,
    /** The one arrow a hint or the tutorial is pointing at, if any. */
    spotlight: HintHighlight?,
    /** The arrow the animated tutorial hand taps, if a lesson is running. */
    handTargetId: Int?,
    contentPadding: Dp,
    shapeMargin: Dp,
    /** A Campaign shape leans into its last three arrows; the replayed tutorial does not. */
    anticipate: Boolean,
    /** The world's own colour, mixed into the board surface at [BOARD_TINT]. */
    worldAccent: Color,
    /** A warm board edge for the Daily Challenge, or null for every other mode. */
    accent: Color?,
    /** The solved-shape outline, while a won board confirms its shape; null otherwise. */
    confirm: ShapeConfirmSpec?,
    isTappable: (Int) -> Boolean,
    onArrowTapped: (Int) -> Unit,
    onEscapeFinished: (Long) -> Unit,
    onBlockedFinished: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (presentation) {
        BoardPresentation.Grid -> GridBoard(
            state = state,
            escaping = escaping,
            blocked = blocked,
            unblocked = unblocked,
            spotlight = spotlight,
            handTargetId = handTargetId,
            contentPadding = contentPadding,
            worldAccent = worldAccent,
            accent = accent,
            isTappable = isTappable,
            onArrowTapped = onArrowTapped,
            onEscapeFinished = onEscapeFinished,
            onBlockedFinished = onBlockedFinished,
            modifier = modifier
        )

        BoardPresentation.Shape -> ShapeBoard(
            state = state,
            escaping = escaping,
            blocked = blocked,
            unblocked = unblocked,
            spotlight = spotlight,
            handTargetId = handTargetId,
            margin = shapeMargin,
            anticipate = anticipate,
            worldAccent = worldAccent,
            haloAccent = accent ?: worldAccent,
            confirm = confirm,
            isTappable = isTappable,
            onArrowTapped = onArrowTapped,
            onEscapeFinished = onEscapeFinished,
            onBlockedFinished = onBlockedFinished,
            modifier = modifier
        )
    }
}

/**
 * The traditional board: a rounded translucent surface with a slot drawn for every
 * cell. Endless and Daily.
 */
@Composable
private fun GridBoard(
    state: GameState,
    escaping: List<EscapingArrow>,
    blocked: BlockedFeedback?,
    unblocked: UnblockedPulse?,
    spotlight: HintHighlight?,
    handTargetId: Int?,
    contentPadding: Dp,
    worldAccent: Color,
    accent: Color?,
    isTappable: (Int) -> Boolean,
    onArrowTapped: (Int) -> Unit,
    onEscapeFinished: (Long) -> Unit,
    onBlockedFinished: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // The board surface is a sibling Box rather than a Card wrapping the tiles:
    // a Card clips its children, which would cut escaping arrows off at the edge
    // instead of letting them fly clear of the board.
    // No fillMaxWidth: aspectRatio then takes the full width when the height
    // allows it and falls back to a height-limited size when it does not.
    Box(
        modifier = modifier
            .aspectRatio(state.columns.toFloat() / state.rows.toFloat())
    ) {
        // The board surface, over the world artwork. Translucent, tinted towards
        // the world's own colour, and edged: a bright rim along the top that
        // settles into the world's accent below it, a faint inner keyline, and a
        // broad soft shadow for depth. The border draws after the fill so it reads
        // as an edge rather than being washed out by it.
        val dark = isDarkScheme()
        Box(
            modifier = Modifier
                .matchParentSize()
                .softShadow(14.dp, BOARD_SHAPE)
                .clip(BOARD_SHAPE)
                .background(
                    lerp(MaterialTheme.colorScheme.surface, worldAccent, BOARD_TINT)
                        .copy(alpha = BOARD_SURFACE_ALPHA)
                )
                .border(
                    width = 1.5.dp,
                    brush = if (accent != null) {
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.40f))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.32f else 0.85f),
                                worldAccent.copy(alpha = 0.45f)
                            )
                        )
                    },
                    shape = BOARD_SHAPE
                )
        ) {
            // Light from above, fading out well before the grid begins.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (dark) 0.07f else 0.30f),
                            0.30f to Color.Transparent
                        )
                    )
            )
            // The inner keyline, so the playing field reads as set into a frame.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp)
                    .border(
                        width = 1.dp,
                        color = (accent ?: worldAccent).copy(alpha = 0.26f),
                        shape = RoundedCornerShape(BOARD_CORNER - 4.dp)
                    )
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            val density = LocalDensity.current
            // One logical cell = one full touch target. The visible tile is inset
            // inside it, so arrows keep their spacing while taps stay forgiving.
            val cellWidth = maxWidth / state.columns
            val cellHeight = maxHeight / state.rows
            val metrics = BoardMetrics(
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                cellWidthPx = with(density) { cellWidth.toPx() },
                cellHeightPx = with(density) { cellHeight.toPx() },
                widthPx = with(density) { maxWidth.toPx() },
                heightPx = with(density) { maxHeight.toPx() }
            )

            EmptyGrid(
                rows = state.rows,
                columns = state.columns,
                cellWidth = metrics.cellWidth,
                cellHeight = metrics.cellHeight,
                cellWidthPx = metrics.cellWidthPx,
                cellHeightPx = metrics.cellHeightPx
            )

            BoardPieces(
                state = state,
                escaping = escaping,
                blocked = blocked,
                unblocked = unblocked,
                spotlight = spotlight,
                handTargetId = handTargetId,
                metrics = metrics,
                style = GridPiece,
                worldAccent = worldAccent,
                isTappable = isTappable,
                onArrowTapped = onArrowTapped,
                onEscapeFinished = onEscapeFinished,
                onBlockedFinished = onBlockedFinished
            )
        }
    }
}

/**
 * The Campaign board: the arrows *are* the picture, floating on the world.
 *
 * There is no surface, no border, no keyline and no slot for an empty cell — an
 * empty cell is simply nothing, whether it is an unused margin, the gap between two
 * parts of the silhouette or a meaningful hole (an Owl's eye, a Crown's jewel). The
 * only thing drawn that is not an arrow is [CampaignShapeHalo], a soft glow with no
 * edge.
 *
 * ## Which cells
 *
 * The level keeps its own coordinates. This only decides what part of that grid
 * is on screen and how big a cell is: the rectangle that holds the level's arrows
 * ([OccupiedBounds]) is fitted to the stage with square cells ([ShapeFit]) and
 * centred in it, so the *picture* is centred and a layout's unused margin rows
 * cannot push it off-centre. An arrow's position on screen is `(row - minRow,
 * col - minCol)` cells from that rectangle's corner; its row, column and id are
 * untouched.
 *
 * The bounds come from the level's full arrow list, not from what is left on the
 * board. As arrows leave, the rest stay exactly where they are — the shape comes
 * apart, it does not reflow, and it does not re-centre or re-scale.
 *
 * ## Touch
 *
 * Only an arrow is composed, so only an arrow can be tapped. There is no invisible
 * target over an empty cell.
 */
@Composable
private fun ShapeBoard(
    state: GameState,
    escaping: List<EscapingArrow>,
    blocked: BlockedFeedback?,
    unblocked: UnblockedPulse?,
    spotlight: HintHighlight?,
    handTargetId: Int?,
    margin: Dp,
    anticipate: Boolean,
    worldAccent: Color,
    /** What the halo is lit with: the world's colour, warmed to gold for the Daily. */
    haloAccent: Color,
    confirm: ShapeConfirmSpec?,
    isTappable: (Int) -> Boolean,
    onArrowTapped: (Int) -> Unit,
    onEscapeFinished: (Long) -> Unit,
    onBlockedFinished: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bounds = remember(state.level) {
        OccupiedBounds.of(state.level.arrows) ?: OccupiedBounds.whole(state.rows, state.columns)
    }

    // The halo dims as the formation is taken apart, and eases doing it unless
    // animations are off, in which case it simply steps. Read in the halo's draw
    // phase, so an ease repaints one Canvas and recomposes nothing.
    val reducedMotion = rememberReducedMotion()
    val total = state.level.arrows.size
    val remainingFraction = if (total == 0) 0f else state.arrows.size.toFloat() / total
    val haloStrength = animateFloatAsState(
        targetValue = SHAPE_HALO_FLOOR + (1f - SHAPE_HALO_FLOOR) * remainingFraction,
        animationSpec = if (reducedMotion) snap() else tween(SHAPE_HALO_EASE_MS),
        label = "shapeHalo"
    )
    // The last three arrows lean the halo in (see `ShapeAnticipation`). It is a
    // function of how many arrows are left and nothing else: nothing here knows
    // which arrow is free, so the last move is still the player's to find. When the
    // last one goes the stage drops to 0 and the halo settles, which is the beat
    // between the arrow leaving and the discovery arriving.
    val anticipation = animateFloatAsState(
        targetValue = if (anticipate) {
            ShapeAnticipation.stage(state.arrows.size).toFloat() / ShapeAnticipation.STAGES
        } else {
            0f
        },
        animationSpec = if (reducedMotion) snap() else tween(SHAPE_ANTICIPATION_EASE_MS),
        label = "shapeAnticipation"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val cell = ShapeFit.cellSize(
            availableWidth = maxWidth.value,
            availableHeight = (maxHeight - margin * 2).value,
            rows = bounds.rows,
            columns = bounds.columns,
            maxCell = SHAPE_MAX_CELL.value
        ).dp
        val shapeWidth = cell * bounds.columns
        val shapeHeight = cell * bounds.rows

        CampaignShapeHalo(
            shapeWidth = shapeWidth,
            shapeHeight = shapeHeight,
            spread = cell * SHAPE_HALO_SPREAD_CELLS,
            accent = haloAccent,
            strength = haloStrength,
            anticipation = anticipation,
            modifier = Modifier.matchParentSize()
        )

        // The formation's own box. It is the "board" for everything that needs one —
        // where an arrow's flight ends and where the edge accent lands — but nothing
        // is drawn on it and nothing clips it, so a flying arrow is never cut off.
        Box(modifier = Modifier.size(shapeWidth, shapeHeight)) {
            BoardPieces(
                state = state,
                escaping = escaping,
                blocked = blocked,
                unblocked = unblocked,
                spotlight = spotlight,
                handTargetId = handTargetId,
                metrics = BoardMetrics(
                    cellWidth = cell,
                    cellHeight = cell,
                    cellWidthPx = with(density) { cell.toPx() },
                    cellHeightPx = with(density) { cell.toPx() },
                    widthPx = with(density) { shapeWidth.toPx() },
                    heightPx = with(density) { shapeHeight.toPx() },
                    originRow = bounds.minRow,
                    originCol = bounds.minCol
                ),
                style = ShapePiece,
                worldAccent = worldAccent,
                isTappable = isTappable,
                onArrowTapped = onArrowTapped,
                onEscapeFinished = onEscapeFinished,
                onBlockedFinished = onBlockedFinished
            )
            // The solved shape's outline, in the same box and at the same cell size the arrows
            // were in: the shape does not move, resize or reflow — it is simply outlined where it
            // stood. It is not composed unless the board has been won.
            if (confirm != null) {
                ShapeConfirmOverlay(
                    contour = confirm.contour,
                    cellWidth = with(density) { cell.toPx() },
                    cellHeight = with(density) { cell.toPx() },
                    originRow = bounds.minRow,
                    originCol = bounds.minCol,
                    schedule = confirm.schedule,
                    accent = confirm.accent,
                    clock = confirm.clock,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

/** What a won board needs to draw its confirmation outline. */
@Immutable
internal class ShapeConfirmSpec(
    val contour: ShapeContour,
    val schedule: ShapeConfirmSchedule,
    val clock: () -> Float,
    val accent: Color
)

/**
 * The line under the board. It does not name the picture and does not explain the rules (the
 * tutorial and the pulse on a blocking arrow do that); it asks the question the whole mode is
 * about. The very first board of a new player gets the one sentence that says what the game *is*.
 */
private fun mysteryLine(mode: GameMode, levelId: Int, levelCleared: Boolean): String = when (mode) {
    GameMode.Campaign ->
        if (levelId == 1 && !levelCleared) "Clear the arrows to reveal the hidden shape."
        else "What might it be? Solve the shape to find out."
    is GameMode.Daily -> "What might today's shape be?"
    is GameMode.Endless -> "Mystery shape. Solve it to reveal it."
    GameMode.Tutorial -> "Clear the arrows to reveal the hidden shape."
}

/**
 * Where one logical cell is on screen, and how big the board it belongs to is.
 *
 * [widthPx] and [heightPx] are the board an escaping arrow flies off: the whole
 * grid for a [BoardPresentation.Grid], the occupied rectangle for a shape.
 * [originRow] and [originCol] are the level row and column drawn at that board's
 * top-left corner — zero for the grid, the occupied bounds' minimum for a shape.
 */
@Immutable
private data class BoardMetrics(
    val cellWidth: Dp,
    val cellHeight: Dp,
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val originRow: Int = 0,
    val originCol: Int = 0
)

/** The arrows still on the board, then the ones still in flight, both in place. */
@Composable
private fun BoardPieces(
    state: GameState,
    escaping: List<EscapingArrow>,
    blocked: BlockedFeedback?,
    unblocked: UnblockedPulse?,
    spotlight: HintHighlight?,
    handTargetId: Int?,
    metrics: BoardMetrics,
    style: PieceStyle,
    worldAccent: Color,
    isTappable: (Int) -> Boolean,
    onArrowTapped: (Int) -> Unit,
    onEscapeFinished: (Long) -> Unit,
    onBlockedFinished: (Int) -> Unit
) {
    state.arrows.forEach { tile ->
        key(tile.id) {
            ArrowCell(
                tile = tile,
                metrics = metrics,
                style = style,
                worldAccent = worldAccent,
                escaping = false,
                enabled = isTappable(tile.id),
                blocked = blocked?.takeIf { it.tileId == tile.id },
                isBlockerOf = blocked?.takeIf { it.blockerId == tile.id },
                awakened = unblocked?.takeIf { tile.id in it.tileIds },
                spotlit = spotlight?.takeIf { it.tileId == tile.id },
                onClick = { onArrowTapped(tile.id) },
                onEscapeFinished = {},
                onBlockedFinished = onBlockedFinished
            )
        }
    }

    // The lesson's hand, over the arrow it points at. Placed from the same metrics the
    // arrows are, so it lands on the real cell whatever the board's scale.
    if (handTargetId != null) {
        val target = state.arrows.firstOrNull { it.id == handTargetId }
        if (target != null) {
            val cx = (target.col - metrics.originCol + 0.5f) * metrics.cellWidthPx
            TutorialHand(
                centreX = cx,
                centreY = (target.row - metrics.originRow + 0.5f) * metrics.cellHeightPx,
                cellPx = minOf(metrics.cellWidthPx, metrics.cellHeightPx),
                // Near the right-hand edge the hand trails to the left, so it stays on screen.
                mirrored = cx > metrics.widthPx * 0.58f,
                reducedMotion = rememberReducedMotion()
            )
        }
    }

    // Arrows that already left the game state but are still in flight.
    escaping.forEach { flight ->
        key("escape-${flight.flightId}") {
            ArrowCell(
                tile = flight.tile,
                metrics = metrics,
                style = style,
                worldAccent = worldAccent,
                escaping = true,
                enabled = false,
                blocked = null,
                isBlockerOf = null,
                awakened = null,
                spotlit = null,
                onClick = {},
                onEscapeFinished = { onEscapeFinished(flight.flightId) },
                onBlockedFinished = {}
            )
        }
    }
}

private val BOARD_CORNER = 30.dp
private val BOARD_SHAPE = RoundedCornerShape(BOARD_CORNER)

@Composable
private fun EmptyGrid(
    rows: Int,
    columns: Int,
    cellWidth: Dp,
    cellHeight: Dp,
    cellWidthPx: Float,
    cellHeightPx: Float
) {
    val slotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)
    val slotEdge = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    for (row in 0 until rows) {
        for (col in 0 until columns) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((col * cellWidthPx).toInt(), (row * cellHeightPx).toInt())
                    }
                    .size(cellWidth, cellHeight)
                    .padding(TILE_INSET)
                    .clip(RoundedCornerShape(TILE_CORNER))
                    .background(slotColor)
                    .border(1.dp, slotEdge, RoundedCornerShape(TILE_CORNER))
            )
        }
    }
}

private val TILE_INSET = 5.dp
private val TILE_CORNER = 16.dp
private val GLYPH_INSET = 9.dp

/**
 * How one arrow piece is built.
 *
 * [GridPiece] is the arrow tile the game has always had, and is left exactly as it
 * was for Endless and Daily. [ShapePiece] is the Campaign's refinement of it: the
 * same rounded-square tile with its glyph, packed a little tighter so a silhouette
 * reads as one object, and finished as a physical piece — opaque electric-blue
 * body, a top highlight, a small inner border, a navy-tinted soft shadow and a
 * white glyph. The blue is the same on every world; only a faint rim reflects it.
 */
@Immutable
private class PieceStyle(
    /** Gap between a cell's edge and its visible tile, inside the touch target. */
    val inset: Dp,
    val corner: Dp,
    val glyphInset: Dp,
    /** The Campaign finish: fixed blue body, sheen, rim, soft shadow, white glyph. */
    val finished: Boolean
)

private val GridPiece = PieceStyle(
    inset = TILE_INSET,
    corner = TILE_CORNER,
    glyphInset = GLYPH_INSET,
    finished = false
)

private val ShapePiece = PieceStyle(
    inset = 3.5.dp,
    corner = 14.dp,
    glyphInset = 8.dp,
    finished = true
)

@Composable
private fun ArrowCell(
    tile: ArrowTile,
    metrics: BoardMetrics,
    style: PieceStyle,
    /** Tints the rim of a [PieceStyle.finished] piece; unused by the grid's tile. */
    worldAccent: Color,
    escaping: Boolean,
    enabled: Boolean,
    blocked: BlockedFeedback?,
    isBlockerOf: BlockedFeedback?,
    /**
     * Non-null when the last removal opened this arrow's path. Presentation
     * only: it never reaches the semantics below, because the board does not say
     * which arrows can escape.
     */
    awakened: UnblockedPulse?,
    /** Non-null when this is the arrow a hint or the tutorial is pointing at. */
    spotlit: HintHighlight?,
    onClick: () -> Unit,
    onEscapeFinished: () -> Unit,
    onBlockedFinished: (Int) -> Unit
) {
    val cellWidth = metrics.cellWidth
    val cellHeight = metrics.cellHeight
    val cellWidthPx = metrics.cellWidthPx
    val cellHeightPx = metrics.cellHeightPx
    val boardWidthPx = metrics.widthPx
    val boardHeightPx = metrics.heightPx
    // Where the cell is on screen. The tile keeps its own row and column — the id,
    // the semantics and every rule use those — and the board's origin is only
    // subtracted here, so a shape trimmed to its occupied bounds lands each arrow
    // on its original coordinates relative to its neighbours.
    val baseX = (tile.col - metrics.originCol) * cellWidthPx
    val baseY = (tile.row - metrics.originRow) * cellHeightPx

    val flight = remember { Animatable(0f) }
    val launch = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val blockFlash = remember { Animatable(0f) }
    val blockerPulse = remember { Animatable(0f) }
    val spotlight = remember { Animatable(0f) }
    val awaken = remember { Animatable(0f) }
    val impact = remember { Animatable(0f) }
    val reducedMotion = rememberReducedMotion()

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = tween(90),
        label = "pressScale"
    )

    // Distance that carries the arrow fully off the board, plus a cell of margin.
    val travel = when (tile.direction) {
        Direction.UP -> baseY + cellHeightPx * 1.6f
        Direction.DOWN -> boardHeightPx - baseY + cellHeightPx * 0.6f
        Direction.LEFT -> baseX + cellWidthPx * 1.6f
        Direction.RIGHT -> boardWidthPx - baseX + cellWidthPx * 0.6f
    }

    if (escaping) {
        LaunchedEffect(tile.id) {
            // Wind up against the direction of travel, then launch.
            launch.animateTo(1f, tween(LAUNCH_WINDUP_MS, easing = FastOutSlowInEasing))
            flight.animateTo(1f, tween(FLIGHT_MS, easing = LinearOutSlowInEasing))
            onEscapeFinished()
        }
    }

    LaunchedEffect(blocked?.nonce) {
        val feedback = blocked
        if (feedback == null) {
            // Feedback was cleared or handed to another arrow: never leave this
            // one stranded mid-shake.
            shake.snapTo(0f)
            blockFlash.snapTo(0f)
            return@LaunchedEffect
        }
        val amplitude = cellWidthPx * 0.13f
        blockFlash.snapTo(1f)
        repeat(3) {
            shake.animateTo(amplitude, tween(SHAKE_STEP_MS))
            shake.animateTo(-amplitude, tween(SHAKE_STEP_MS))
        }
        shake.animateTo(0f, tween(SHAKE_STEP_MS))
        blockFlash.animateTo(0f, tween(180))
        onBlockedFinished(feedback.nonce)
    }

    // The impact ring, sparks and compression, on their own clock so they land
    // with the first jolt of the shake rather than queueing behind it. This is
    // the decorative half of the blocked feedback and the half that stands down
    // when animations are off: the shake, the red flash and the blocker's pulse
    // are how the board explains the rule, and those always play.
    LaunchedEffect(blocked?.nonce, reducedMotion) {
        if (blocked == null || reducedMotion) {
            impact.snapTo(0f)
            return@LaunchedEffect
        }
        impact.snapTo(0f)
        impact.animateTo(1f, tween(IMPACT_MS, easing = LinearOutSlowInEasing))
    }

    // The arrow that did the blocking pulses too, so the rule reads clearly
    // without ever marking which arrows happen to be free.
    LaunchedEffect(isBlockerOf?.nonce) {
        if (isBlockerOf == null) return@LaunchedEffect
        blockerPulse.snapTo(0f)
        blockerPulse.animateTo(1f, tween(BLOCKER_PULSE_MS, easing = FastOutSlowInEasing))
        blockerPulse.animateTo(0f, tween(BLOCKER_PULSE_MS * 2, easing = FastOutSlowInEasing))
    }

    // "Your move opened this one up." One shot, roughly 300ms, then gone: a lift
    // in scale and brightness plus a soft halo, and pointedly *not* a border,
    // because the breathing teal border is the hint's and has to stay the one
    // thing that means "here is a move".
    //
    // Purely decorative — the arrow was already playable and nothing about the
    // board changed — so it is skipped outright when animations are off.
    LaunchedEffect(awakened?.nonce) {
        if (awakened == null || reducedMotion) {
            awaken.snapTo(0f)
            return@LaunchedEffect
        }
        awaken.snapTo(0f)
        awaken.animateTo(1f, tween(AWAKEN_RISE_MS, easing = FastOutSlowInEasing))
        awaken.animateTo(0f, tween(AWAKEN_FALL_MS, easing = FastOutSlowInEasing))
    }

    // A hint breathes rather than flashes. The loop is unbounded on purpose:
    // what ends a hint is the game — a tap, a board change or the fade-out timer
    // — and when that happens this effect is cancelled and the glow is reset
    // below, so there is no duration here that could disagree with the state.
    LaunchedEffect(spotlit?.nonce) {
        if (spotlit == null) {
            spotlight.snapTo(0f)
            return@LaunchedEffect
        }
        if (reducedMotion) {
            // Animations are off, so the arrow is marked rather than animated.
            // The glow still has to be visible — it is the whole hint.
            spotlight.snapTo(1f)
            return@LaunchedEffect
        }
        spotlight.snapTo(SPOTLIGHT_TROUGH)
        while (true) {
            spotlight.animateTo(1f, tween(HINT_PULSE_MS, easing = FastOutSlowInEasing))
            spotlight.animateTo(SPOTLIGHT_TROUGH, tween(HINT_PULSE_MS, easing = FastOutSlowInEasing))
        }
    }

    val progress = flight.value
    val windUp = launch.value * (1f - progress)
    val flightX = tile.direction.dCol * (travel * progress - cellWidthPx * 0.12f * windUp)
    val flightY = tile.direction.dRow * (travel * progress - cellHeightPx * 0.12f * windUp)

    val scheme = MaterialTheme.colorScheme
    val wake = awaken.value
    val hit = impact.value
    // The tile lifts towards white for a moment rather than towards any accent:
    // a brightness change is read as "this one just changed" without borrowing
    // the teal that means hint or the red that means blocked.
    //
    // The grid's tile is the theme's primary, its lower half a little see-through
    // over the board surface. A finished piece has no surface under it, so it is
    // the game's fixed blue and fully opaque: that is what keeps the artwork from
    // showing through an arrow and what keeps the blue identical on every world.
    val bodyTop = if (style.finished) ShapePieceTop else scheme.primary
    val bodyBottom = if (style.finished) ShapePieceBottom else scheme.primary
    val bottomAlpha = if (style.finished) 1f else 0.82f
    val topColor = lerp(
        lerp(bodyTop, Color.White, 0.22f * wake),
        scheme.error,
        blockFlash.value * 0.85f
    )
    val bottomColor = lerp(
        lerp(bodyBottom, Color.White, 0.22f * wake).copy(alpha = bottomAlpha),
        scheme.error.copy(alpha = bottomAlpha),
        blockFlash.value * 0.85f
    )
    val pulse = blockerPulse.value
    val glow = spotlight.value
    // A quick squash at the moment of impact, over almost at once, so a blocked
    // tap has some weight without delaying anything.
    val compression = if (hit > 0f && hit < 1f) {
        (1f - hit / IMPACT_COMPRESSION).coerceIn(0f, 1f)
    } else {
        0f
    }
    val elevation = (5.dp + (6.dp * pulse) + (5.dp * glow) + (3.dp * wake)) *
        (if (pressed) 0.4f else 1f)
    // A blocked tap is the louder signal and owns the red border, so the two
    // never argue over the same edge: the glow stands down while a shake runs.
    //
    // The glow is `secondary` — the theme's teal — rather than `tertiary`, which
    // this theme does not define and which therefore falls back to Material's
    // default muted brown: unreadable against a blue tile and not a colour this
    // app uses anywhere else. Teal is the one accent that is neither the tile's
    // own blue nor the error red a blocked tap owns.
    val borderWidth = if (pulse > 0f) 3.dp * pulse else 3.dp * glow
    val borderColor = if (pulse > 0f) {
        scheme.error.copy(alpha = pulse)
    } else {
        scheme.secondary.copy(alpha = glow)
    }

    // The effect colours are the same in every world and every mode. A world
    // tints the board it is played on; the interaction itself has to feel like
    // one game everywhere, and the Daily Challenge's gold belongs to its chrome
    // rather than to its arrows — the puzzle is the same puzzle.
    //
    // Teal into white is the one accent that is neither the tile's own indigo
    // nor the error red a blocked tap owns, and it reads over all five
    // backdrops, Sky Garden's bright sky and Cosmic's near-black alike.
    val trailHalo = scheme.secondary
    val trailCore = lerp(scheme.secondary, Color.White, 0.65f)
    val impactSpark = lerp(scheme.error, scheme.tertiary, 0.40f)

    val density = LocalDensity.current
    val insetPx = with(density) { style.inset.toPx() }
    val cornerPx = with(density) { style.corner.toPx() }
    val tileShape = remember(style) { RoundedCornerShape(style.corner) }
    // Where in the flight this arrow crosses the board boundary. Static for a
    // given arrow, so the edge accent needs no timer of its own.
    val exitAt = remember(tile.direction, baseX, baseY, travel) {
        exitFraction(
            direction = tile.direction,
            baseX = baseX,
            baseY = baseY,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            boardWidthPx = boardWidthPx,
            boardHeightPx = boardHeightPx,
            travel = travel
        )
    }
    val travelExtent = if (tile.direction.dCol == 0) cellHeightPx else cellWidthPx

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (baseX + flightX + shake.value).toInt(),
                    (baseY + flightY).toInt()
                )
            }
            .size(cellWidth, cellHeight)
            // Padding lives inside the touch target: the whole cell stays tappable.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClickLabel = "Remove arrow",
                role = Role.Button,
                onClick = onClick
            )
            // Direction and position only: never leak whether the path is clear.
            // The one exception is an arrow the player has explicitly asked about
            // — a hint they cannot hear is not a hint.
            .semantics {
                contentDescription = buildString {
                    append("${tile.direction.spokenName} arrow, ")
                    append("row ${tile.row + 1}, column ${tile.col + 1}")
                    if (spotlit != null) append(", suggested")
                }
            }
            .padding(style.inset)
            // Everything decorative is drawn here, before the clip and before
            // the tile's own alpha and scale: the trail has to reach outside the
            // cell, the edge accent has to land on the board boundary rather
            // than on the tile, and neither should inherit the squash the tile
            // is doing. Nothing above clips — the board surface is a sibling Box
            // precisely so an escaping arrow is not cut off at the edge — so
            // drawing past these bounds is already the established behaviour.
            //
            // These are draw-phase reads of values the cell is already
            // recomposing on, so the effects cost a handful of `drawCircle` and
            // `drawRoundRect` calls per frame and nothing else. Each one is a
            // pure function of a progress value that ends, so when the flight or
            // the feedback is over there is nothing left running and nothing to
            // dispose.
            .drawBehind {
                if (reducedMotion) return@drawBehind
                if (escaping) {
                    drawEscapeTrail(
                        direction = tile.direction,
                        progress = progress,
                        travelled = travel * progress,
                        extent = travelExtent,
                        halo = trailHalo,
                        core = trailCore
                    )
                    // The flight offset is subtracted back out so the burst
                    // stays on the cell the arrow left instead of riding along.
                    drawLaunchSparks(
                        origin = Offset(size.width / 2f - flightX, size.height / 2f - flightY),
                        tileId = tile.id,
                        progress = progress,
                        glow = trailHalo,
                        spark = trailCore
                    )
                    if (progress >= exitAt) {
                        drawExitAccent(
                            at = when (tile.direction) {
                                Direction.UP ->
                                    Offset(size.width / 2f, -(baseY + flightY + insetPx))
                                Direction.DOWN ->
                                    Offset(size.width / 2f, boardHeightPx - baseY - flightY - insetPx)
                                Direction.LEFT ->
                                    Offset(-(baseX + flightX + insetPx), size.height / 2f)
                                Direction.RIGHT ->
                                    Offset(boardWidthPx - baseX - flightX - insetPx, size.height / 2f)
                            },
                            reached = (progress - exitAt) / EXIT_WINDOW,
                            color = trailCore
                        )
                    }
                }
                if (hit > 0f) drawBlockedImpact(hit, scheme.error, impactSpark)
                drawAwakenHalo(wake, cornerPx, Color.White)
            }
            .scale(
                pressScale *
                    (1f - 0.06f * windUp) *
                    (1f + 0.11f * pulse + 0.06f * glow + 0.05f * wake) *
                    (1f - 0.05f * compression)
            )
            .alpha(if (escaping) fadeOut(progress) else 1f)
            .then(
                if (style.finished) {
                    Modifier.softShadow(elevation, tileShape)
                } else {
                    Modifier.shadow(elevation, tileShape, clip = false)
                }
            )
            .clip(tileShape)
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
            .then(
                if (style.finished) {
                    Modifier.arrowPieceFinish(style.corner, worldAccent)
                } else {
                    Modifier
                }
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = tileShape
            ),
        contentAlignment = Alignment.Center
    ) {
        ArrowGlyph(
            direction = tile.direction,
            // White on the finished piece's fixed blue; the grid's tile keeps the
            // theme pairing (primary / onPrimary), which flips in the dark theme.
            color = if (style.finished) Color.White else scheme.onPrimary,
            shadowColor = Color.Black.copy(alpha = 0.22f),
            modifier = Modifier
                .fillMaxSize()
                .padding(style.glyphInset)
        )
    }
}

/** How far the hint glow fades back between breaths. Never all the way out. */
private const val SPOTLIGHT_TROUGH = 0.35f

/** Stays solid for most of the flight, then fades as it clears the board. */
private fun fadeOut(progress: Float): Float =
    ((1f - progress) / 0.45f).coerceIn(0f, 1f)

/** Spoken direction for TalkBack, e.g. "Right pointing". */
private val Direction.spokenName: String
    get() = when (this) {
        Direction.UP -> "Up pointing"
        Direction.DOWN -> "Down pointing"
        Direction.LEFT -> "Left pointing"
        Direction.RIGHT -> "Right pointing"
    }
