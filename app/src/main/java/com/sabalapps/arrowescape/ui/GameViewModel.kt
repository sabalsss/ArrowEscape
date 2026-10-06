package com.sabalapps.arrowescape.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.daily.DailyProgress
import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.daily.DailySavedGame
import com.sabalapps.arrowescape.endless.EndlessProgress
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.endless.EndlessSavedGame
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.GeneratedPuzzle
import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.EscapeAnalysis
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.HintEngine
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.game.TapResult
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.SavedGame
import com.sabalapps.arrowescape.shape.MysteryShapePuzzles
import com.sabalapps.arrowescape.shape.ShapeIdentity
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.time.SystemDateProvider
import com.sabalapps.arrowescape.tutorial.DependencyLesson
import com.sabalapps.arrowescape.tutorial.InMemoryTutorialFlagStore
import com.sabalapps.arrowescape.tutorial.TutorialFlagStore
import com.sabalapps.arrowescape.tutorial.TutorialState
import com.sabalapps.arrowescape.ui.world.DiscoveryResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * An arrow that has left the game state but is still flying across the screen.
 *
 * [flightId] is unique per launch rather than per arrow, so a late "animation
 * finished" callback from a previous level can never affect the current one.
 */
data class EscapingArrow(val flightId: Long, val tile: ArrowTile)

/**
 * A blocked tap, with the arrow that stopped it so the board can point out *why*
 * the move failed. [nonce] increments on every blocked tap so repeating the same
 * mistake replays the animation.
 */
data class BlockedFeedback(
    val tileId: Int,
    val blockerId: Int?,
    val nonce: Int
)

/**
 * The arrow a hint is currently pointing at.
 *
 * [nonce] increments on every hint, which does two jobs: tapping Hint again
 * restarts the glow rather than doing nothing visible, and the "this hint has
 * been shown long enough" callback can be ignored when it arrives for a hint
 * that has already been replaced.
 */
data class HintHighlight(val tileId: Int, val nonce: Int)

/**
 * The arrows whose path out was opened up by the arrow that just left, so the
 * board can briefly acknowledge what the move *changed*.
 *
 * This is feedback, not a hint. It is produced only by a successful removal, it
 * only ever contains arrows that were blocked a moment ago, and it is dropped as
 * soon as its animation has played — nothing here marks the arrows that were
 * already free, and nothing here reaches accessibility semantics. The one
 * explicit move suggestion is still [com.sabalapps.arrowescape.game.HintEngine].
 *
 * [nonce] increments per pulse, so a reaction to one move cannot be cancelled by
 * a timer left over from the move before it.
 */
data class UnblockedPulse(val tileIds: Set<Int>, val nonce: Int)

/**
 * The star result of the Campaign level that has just been cleared.
 *
 * [earned] is this run; [best] is what the player now has on record, which is
 * the better of this run and whatever was there before. [isNewBest] is true only
 * when this run improved on it — a replay that does worse reports the same best
 * and no improvement, which is what the result card needs to avoid
 * congratulating a player on a star they already had.
 *
 * Null in every other mode and on every loss: stars are Campaign mastery, and
 * nothing is earned by running out of lives.
 */
data class CampaignStars(
    val earned: Int,
    val best: Int,
    val isNewBest: Boolean
) {
    /** The Perfect Escape — no blocked taps, no hint. */
    val isPerfect: Boolean get() = StarRating.isPerfect(earned)
}

/**
 * What a solved Daily or Endless board was hiding: the picture's name.
 *
 * The one place that name leaves the ViewModel, and only after the board is *won* — it is null
 * while the board is in play, after a loss, in Campaign and in the tutorial. (A board in play holds
 * its arrows and nothing else; the picture itself is the only clue.) The outline that confirms the
 * shape is not carried here at all: it is traced from the board's own occupied cells.
 */
data class ShapeReveal(val templateId: String, val name: String)

/**
 * Holds the current [GameState] plus the short-lived animation bookkeeping the
 * board needs. The rules themselves live in the game package and are untouched.
 *
 * Input safety: an arrow is only tappable when the game is running, the arrow is
 * not mid-flight, and it is not the arrow currently playing its blocked
 * animation. That makes a burst of taps on a blocked arrow cost exactly one
 * life. Nothing here can lock the board permanently — the only lock is
 * [blocked], which is cleared when its animation ends, when it is replaced by a
 * different blocked arrow, and on every restart.
 */
class GameViewModel(
    private val progress: ProgressRepository = ProgressRepository(InMemoryProgressStore()),
    private val endless: EndlessRepository = EndlessRepository(InMemoryProgressStore()),
    private val daily: DailyRepository = DailyRepository(InMemoryProgressStore(), SystemDateProvider()),
    /**
     * Whether the tutorial has been seen through. Behind an interface so this
     * ViewModel stays constructible without an Android `Context`; in the app it
     * is `SettingsRepository`.
     */
    private val tutorialFlags: TutorialFlagStore = InMemoryTutorialFlagStore(),
    /**
     * Where generator diagnostics go. A no-op by default and a no-op in release
     * builds — `MainActivity` only wires up a real sink when the running package
     * is flagged debuggable, so a seed and a difficulty score can never leak
     * into a shipped build's logcat.
     */
    private val debugSink: (String) -> Unit = {}
) : ViewModel() {

    private val _state = MutableStateFlow(GameState.newGame(progress.currentLevel()))
    val state: StateFlow<GameState> = _state.asStateFlow()

    /**
     * Which puzzle source the board on screen came from. Everything about
     * playing it is shared; this is what the HUD and the result card read to
     * know whether they are showing a level, an endless puzzle, the day's
     * challenge or the tutorial.
     */
    private val _mode = MutableStateFlow<GameMode>(GameMode.Campaign)
    val mode: StateFlow<GameMode> = _mode.asStateFlow()

    /** Unlocked / completed / selected levels, for Home and the level grid. */
    val playerProgress: StateFlow<PlayerProgress> = progress.progress

    /** The unfinished board, if there is one. Drives the Home "Resume" label. */
    val savedGame: StateFlow<SavedGame?> = progress.savedGame

    /** Endless counters, for Home. Never mixed with campaign progress. */
    val endlessProgress: StateFlow<EndlessProgress> = endless.progress

    /** The unfinished endless board, if there is one. */
    val endlessSavedGame: StateFlow<EndlessSavedGame?> = endless.savedGame

    /** Daily streak counters, for Home and Stats. Independent of the other two. */
    val dailyProgress: StateFlow<DailyProgress> = daily.progress

    private val _escaping = MutableStateFlow<List<EscapingArrow>>(emptyList())
    val escaping: StateFlow<List<EscapingArrow>> = _escaping.asStateFlow()

    private val _blocked = MutableStateFlow<BlockedFeedback?>(null)
    val blocked: StateFlow<BlockedFeedback?> = _blocked.asStateFlow()

    private val _unblocked = MutableStateFlow<UnblockedPulse?>(null)

    /**
     * The arrows freed by the latest successful removal, or null when the last
     * move freed nothing. Short-lived: the board plays one small reaction and
     * calls [onUnblockedFeedbackFinished].
     */
    val unblocked: StateFlow<UnblockedPulse?> = _unblocked.asStateFlow()

    private val _hint = MutableStateFlow<HintHighlight?>(null)

    /** The arrow a hint is pointing at, or null when no hint is showing. */
    val hint: StateFlow<HintHighlight?> = _hint.asStateFlow()

    private val _campaignStars = MutableStateFlow<CampaignStars?>(null)

    /**
     * The star result of the Campaign level just cleared, or null. Set on the
     * frame the board is won and cleared the moment another board is opened, so
     * the result card and the celebration both read the run they are reporting
     * on and never the one before it.
     */
    val campaignStars: StateFlow<CampaignStars?> = _campaignStars.asStateFlow()

    private val _campaignDiscovery = MutableStateFlow<DiscoveryResult?>(null)

    /**
     * What the Campaign level just cleared was hiding, whether this is the first
     * time it has been found, and where the collection stands *including this
     * clear* — or null: in every other mode, on every loss, and for a level that
     * hides nothing.
     *
     * Set on the same frame as [campaignStars], from the same win, and cleared with
     * it the moment another board is opened. It is presentation about a run that has
     * already been saved: [persistCampaign] has written the completion, the stars
     * and the unlock before this is published, so nothing the reveal does — or fails
     * to do — can lose a clear.
     */
    val campaignDiscovery: StateFlow<DiscoveryResult?> = _campaignDiscovery.asStateFlow()

    private val _shapeReveal = MutableStateFlow<ShapeReveal?>(null)

    /**
     * What the Daily or Endless board just solved was hiding, or null. Published on the same frame
     * as the win, after the progress write, and cleared the moment another board is opened. Null
     * for the whole of a board in play, on a loss, and in Campaign and the tutorial — a Campaign
     * win publishes [campaignDiscovery] instead.
     */
    val shapeReveal: StateFlow<ShapeReveal?> = _shapeReveal.asStateFlow()

    /** The picture the Daily / Endless board on screen was built to. Never exposed while in play. */
    private var currentShape: ShapeIdentity? = null

    /**
     * Endless: the pictures served most recently, newest last. Lightweight on purpose — memory only,
     * not persisted — and it only steers which *seed* is drawn next (a board is a pure function of its
     * seed, so repeat control cannot live inside generation). A resumed board seeds it, so a relaunch
     * does not forget the picture the player was just on.
     */
    private val recentShapes = ArrayDeque<String>()

    private val _tutorial = MutableStateFlow(TutorialState.Inactive)

    /** How far the first-run lesson has got. [TutorialState.Inactive] most of the time. */
    val tutorial: StateFlow<TutorialState> = _tutorial.asStateFlow()

    private val _dependency = MutableStateFlow(DependencyLesson.Inactive)

    /**
     * How far Level 2's lesson ("removing one arrow can free another") has got.
     * [DependencyLesson.Inactive] everywhere but the first play of Level 2.
     */
    val dependencyLesson: StateFlow<DependencyLesson> = _dependency.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var blockedNonce = 0
    private var hintNonce = 0
    private var unblockedNonce = 0
    private var nextFlightId = 0L

    /**
     * The current run's star tally — the only two facts [StarRating] reads.
     *
     * They belong to the *run*, not the level: opening, restarting or replaying
     * any board starts them over, so a level cleared on the second attempt is
     * rated on that attempt. They are counted in every mode but only ever read
     * in Campaign, which is what keeps Endless, Daily and the tutorial out of
     * the star system entirely. The first run of the tutorial is genuine
     * Campaign Level 1 and is rated like any other level; [GameMode.Tutorial] —
     * the replay from Settings — persists nothing and so rates nothing.
     *
     * They are mirrored into [SavedGame] on every move, so being killed
     * mid-level cannot launder a blocked tap into a Perfect Escape.
     */
    private var blockedTapsThisRun = 0
    private var hintUsedThisRun = false

    /**
     * Whether a hint was asked for on the board in front of the player.
     *
     * Read-only, for the result screen's "Hint used" line. It is the same flag the
     * star rule reads, exposed rather than duplicated, and it is only trustworthy
     * in Campaign — the one mode that persists it across process death.
     */
    val hintUsedOnBoard: Boolean get() = hintUsedThisRun

    /** Today, as the Daily Challenge reckons it. Read through the injected clock. */
    fun today(): GameDate = daily.today()

    /**
     * Whether a tap on [id] would currently do anything. Exposed so the board can
     * also stop reporting the arrow as clickable to accessibility services.
     */
    fun isTappable(id: Int): Boolean {
        if (_state.value.status != GameStatus.PLAYING) return false
        if (_blocked.value?.tileId == id) return false
        return _escaping.value.none { it.tile.id == id }
    }

    fun onArrowTapped(id: Int) {
        if (!isTappable(id)) return

        val boardBeforeTap = _state.value.arrows
        val (next, result) = _state.value.onArrowTapped(id)
        _state.value = next

        // Any resolved tap is a board state change or an answered question, and
        // either way the hint it was given in response to is spent. Clearing it
        // here covers both of the rules a hint has to obey — it goes away when
        // the arrow is tapped, and it goes away when the board changes — without
        // either of them needing its own special case.
        _hint.value = null

        // Likewise the last move's "these opened up" reaction: it describes the
        // move before this one, so whatever this tap turns out to be, it is
        // spent. A successful removal immediately puts its own set back.
        _unblocked.value = null

        when (result) {
            is TapResult.Escaped -> {
                _escaping.update { it + EscapingArrow(nextFlightId++, result.tile) }
                _blocked.value = null
                // What the removal changed, derived rather than tracked: the
                // free set before the tap against the free set after it, both
                // from MoveValidator. A blocked tap moves no arrow, so only this
                // branch asks.
                val freed = EscapeAnalysis.newlyFreed(boardBeforeTap, next.arrows)
                if (freed.isNotEmpty()) {
                    _unblocked.value = UnblockedPulse(freed, ++unblockedNonce)
                }
                _events.tryEmit(GameEvent.ArrowEscaped)
                advanceTutorial(TutorialState::onArrowEscaped)
                advanceDependency { it.onArrowEscaped(result.tile.id, freed) }
            }
            is TapResult.Blocked -> {
                // The run's one mistake counter. Incremented here rather than
                // derived from lives so it keeps meaning the same thing if the
                // life count ever changes.
                blockedTapsThisRun++
                val nearest = MoveValidator.blockers(result.tile, boardBeforeTap).firstOrNull()
                _blocked.value = BlockedFeedback(result.tile.id, nearest?.id, ++blockedNonce)
                _events.tryEmit(GameEvent.MoveBlocked)
                advanceTutorial(TutorialState::onMoveBlocked)
                advanceDependency(DependencyLesson::onMoveBlocked)
            }
            TapResult.Ignored -> Unit
        }

        persist(next)

        when (next.status) {
            GameStatus.WON -> {
                advanceTutorial(TutorialState::onBoardFinished)
                advanceDependency(DependencyLesson::onBoardFinished)
                _events.tryEmit(GameEvent.LevelComplete)
            }
            GameStatus.LOST -> {
                advanceTutorial(TutorialState::onBoardFinished)
                advanceDependency(DependencyLesson::onBoardFinished)
                _events.tryEmit(GameEvent.GameOver)
            }
            GameStatus.PLAYING -> Unit
        }
    }

    fun onEscapeAnimationFinished(flightId: Long) {
        _escaping.update { list -> list.filterNot { it.flightId == flightId } }
    }

    fun onBlockedFeedbackFinished(nonce: Int) {
        if (_blocked.value?.nonce == nonce) _blocked.value = null
    }

    /**
     * The "these opened up" reaction has played. Nonce-guarded, so a timer from
     * a pulse that has already been replaced cannot clear the current one.
     */
    fun onUnblockedFeedbackFinished(nonce: Int) {
        if (_unblocked.value?.nonce == nonce) _unblocked.value = null
    }

    // ---- hints ---------------------------------------------------------------

    /**
     * Points at one arrow that can leave right now. Free, in every sense: it
     * costs nothing, it removes nothing, and it reveals nothing beyond the one
     * arrow — see [HintEngine].
     *
     * A finished board has nothing to suggest, so a hint there is ignored rather
     * than highlighting an arrow on a board the player can no longer touch.
     */
    fun requestHint() {
        if (_state.value.status != GameStatus.PLAYING) return
        val suggestion = HintEngine.hint(_state.value.arrows) ?: return
        _hint.value = HintHighlight(tileId = suggestion.id, nonce = ++hintNonce)
        // Costs a star, and only once however many times it is asked for — a
        // hint is help, and the second one is not more help than the first.
        // Set only after a hint was actually produced, so a tap on the button
        // that had nothing to point at costs nothing.
        hintUsedThisRun = true
        // Asking for a hint mid-board changes what the board will be worth, so
        // the in-progress save has to learn about it now rather than on the next
        // move — otherwise a relaunch would forget it. Campaign only: it is the
        // only mode whose save has a tally to update.
        if (_mode.value == GameMode.Campaign) persistCampaign(_state.value)
    }

    /** True when there is an arrow a hint could point at. Drives the button's enabled state. */
    fun canHint(): Boolean =
        _state.value.status == GameStatus.PLAYING && HintEngine.hint(_state.value.arrows) != null

    /**
     * The glow has been showing long enough. Nonce-guarded, so a timer left over
     * from a hint that has already been replaced cannot cancel the current one.
     */
    fun onHintFinished(nonce: Int) {
        if (_hint.value?.nonce == nonce) _hint.value = null
    }

    // ---- the tutorial --------------------------------------------------------

    /**
     * Opens the lesson on its own, from Settings. [GameMode.Tutorial] rather
     * than Campaign Level 1, so a player who has been playing for a week can
     * re-read the rule without their campaign selection being dragged back to
     * the first level — see [GameMode.Tutorial].
     *
     * The persisted flag is deliberately not cleared: the tutorial has still
     * been seen, and this is the player choosing to see it again rather than the
     * game deciding to show it.
     */
    fun startTutorial() {
        _mode.value = GameMode.Tutorial
        currentShape = null
        load(LevelProgression.first)
        _tutorial.value = TutorialState.Starting
        _dependency.value = DependencyLesson.Inactive
    }

    /**
     * Moves the lesson on, and records that it has worked.
     *
     * The flag is written the moment an arrow has escaped under the lesson rather
     * than when the level ends, because those are different moments: a player who
     * was shown the free arrow, tapped it and then backed out has been taught, and
     * should not be taught again.
     */
    private fun advanceTutorial(transition: (TutorialState) -> TutorialState) {
        val current = _tutorial.value
        if (!current.isActive) return
        val next = transition(current)
        _tutorial.value = next
        if (next.step.isTaught && !current.step.isTaught) tutorialFlags.setTutorialCompleted(true)
    }

    /** The same for Level 2's lesson: recorded the moment the idea lands, not when the level ends. */
    private fun advanceDependency(transition: (DependencyLesson) -> DependencyLesson) {
        val current = _dependency.value
        if (!current.isActive) return
        val next = transition(current)
        _dependency.value = next
        if (next.step.isTaught && !current.step.isTaught) tutorialFlags.setDependencyLessonCompleted(true)
    }

    /**
     * The lesson runs on the first ever Campaign Level 1 and nowhere else. A
     * player who leaves Level 1 half-done and comes back has not been taught
     * yet, so the flag — not "has this board been opened" — is what decides.
     */
    private fun tutorialFor(level: Level): TutorialState =
        if (level.id == LevelProgression.first.id && !tutorialFlags.isTutorialCompleted()) {
            TutorialState.Starting
        } else {
            TutorialState.Inactive
        }

    /**
     * Level 2's lesson runs on the first ever play of Level 2 and nowhere else. A level
     * the player has already cleared has been taught by definition — which also covers a
     * player who updated into this lesson with Level 2 long behind them.
     */
    private fun dependencyFor(level: Level): DependencyLesson =
        if (level.id == DEPENDENCY_LESSON_LEVEL_ID &&
            !tutorialFlags.isDependencyLessonCompleted() &&
            !progress.progress.value.isCompleted(level.id)
        ) {
            DependencyLesson.Starting
        } else {
            DependencyLesson.Inactive
        }

    /**
     * Replays the board currently loaded from its original layout, and throws
     * away whatever was saved for it — Restart is the player asking for a clean
     * slate, so resuming later must not bring the half-cleared board back. A
     * generated board is rebuilt from the same seed, so an endless or daily
     * restart is the same puzzle, not a new one.
     */
    fun restart() {
        when (_mode.value) {
            GameMode.Campaign -> progress.clearInProgress()
            is GameMode.Endless -> endless.clearInProgress()
            is GameMode.Daily -> daily.clearInProgress()
            // The tutorial writes nothing, so there is nothing to throw away.
            GameMode.Tutorial -> Unit
        }
        val wasTutorial = _tutorial.value
        val wasDependency = _dependency.value
        load(_state.value.level)
        // Restarting before a lesson has worked starts it over; restarting a board
        // the player has already been taught on does not bring it back.
        _tutorial.value =
            if (wasTutorial.isActive && !wasTutorial.step.isTaught) TutorialState.Starting else TutorialState.Inactive
        _dependency.value =
            if (wasDependency.isActive && !wasDependency.step.isTaught) DependencyLesson.Starting else DependencyLesson.Inactive
    }

    /**
     * What the result card's primary action does, in every mode. The card is
     * shared between the modes, so the dispatch lives here rather than in the
     * composable: the ViewModel is the thing that knows which puzzle source is
     * on screen, and this way the progression is testable without Compose.
     */
    fun continueAfterWin() {
        when (_mode.value) {
            GameMode.Campaign -> continueToNextLevel()
            is GameMode.Endless -> nextEndlessPuzzle()
            // There is exactly one puzzle per day and the tutorial has exactly
            // one board, so neither has a "next" to go to. Their cards offer
            // Replay and Home instead, and never call this.
            is GameMode.Daily, GameMode.Tutorial -> Unit
        }
    }

    /**
     * What the result card's Replay does. A campaign level is re-read from the
     * catalogue; a generated board is rebuilt from its seed, which is what makes
     * Replay the *same* puzzle rather than something that merely looks similar.
     */
    fun replayCurrent() {
        when (_mode.value) {
            GameMode.Campaign, GameMode.Tutorial -> restart()
            is GameMode.Endless -> replayEndlessPuzzle()
            is GameMode.Daily -> replayDaily()
        }
    }

    /**
     * Loads the level after the current one. On the last level there is nothing
     * to advance to, so it replays instead — the UI shows the "all levels
     * complete" card there rather than a Next button.
     */
    fun continueToNextLevel() {
        // Only the campaign advances through the catalogue; a generated board is
        // not in it, so there is no "next" for the other modes to find here.
        if (_mode.value !is GameMode.Campaign) return
        openLevel(LevelProgression.next(_state.value.level))
    }

    /**
     * Opens [levelId] from scratch. This is the level grid's entry point, so it
     * is the one that enforces the lock: a tap on a level the player has not
     * reached does nothing at all.
     *
     * Advancing from a finished level goes through [continueToNextLevel]
     * instead, which does not re-check — winning is what unlocked the next
     * level a moment earlier.
     */
    fun startLevel(levelId: Int) {
        if (!progress.isUnlocked(levelId)) return
        openLevel(LevelProgression.levelOrFirst(levelId))
    }

    private fun openLevel(level: Level) {
        _mode.value = GameMode.Campaign
        currentShape = null
        progress.selectLevel(level.id)
        progress.clearInProgress()
        load(level)
        _tutorial.value = tutorialFor(level)
        _dependency.value = dependencyFor(level)
    }

    /**
     * What Continue does: pick up the unfinished board if there is a valid one,
     * otherwise open the player's current level fresh. A save that no longer
     * describes a real board is dropped by the repository, so the fallback here
     * is always a clean copy of the level.
     */
    fun continueGame() {
        val saved = progress.loadInProgress()
        val level = saved?.let { LevelProgression.levelOrFirst(it.levelId) } ?: progress.currentLevel()
        progress.selectLevel(level.id)

        _mode.value = GameMode.Campaign
        currentShape = null
        _escaping.value = emptyList()
        _blocked.value = null
        _hint.value = null
        _unblocked.value = null
        resetRunTally()
        // Resuming continues the run it left off, tally included — the saved
        // board and the mistakes that got it into that state are the same fact.
        if (saved != null) {
            blockedTapsThisRun = saved.blockedTaps
            hintUsedThisRun = saved.hintUsed
        }
        _state.value = if (saved != null) {
            GameState(
                level = level,
                arrows = level.arrows.filter { it.id in saved.remainingArrowIds },
                lives = saved.lives
            )
        } else {
            GameState.newGame(level)
        }
        _tutorial.value = tutorialFor(level)
        _dependency.value = dependencyFor(level)
    }

    // ---- endless mode -------------------------------------------------------

    /**
     * What Endless Mode does from Home: pick up the generated board the player
     * left, or build the next one.
     *
     * The saved board is rebuilt by regenerating from its seed and tier, then
     * filtered down to the ids that were still standing. If any saved id is not
     * on the regenerated board the save is not describing this puzzle — a seed
     * from a future generator version, or a hand-edited preference — so it is
     * dropped and a fresh puzzle is generated instead. No other mode's progress
     * is consulted and none is written here.
     */
    fun startEndless() {
        val saved = endless.loadInProgress()
        if (saved != null) {
            val puzzle = MysteryShapePuzzles.generate(saved.seed, saved.tier)
            val arrows = puzzle.level.arrows.filter { it.id in saved.remainingArrowIds }
            if (arrows.isNotEmpty() && arrows.size == saved.remainingArrowIds.size) {
                debugSink("endless resume #${saved.puzzleNumber} :: ${puzzle.debugSummary()}")
                openEndless(puzzle, saved.puzzleNumber, arrows, saved.lives)
                return
            }
            debugSink("endless save did not match seed=${saved.seed}; generating a fresh puzzle")
            endless.clearInProgress()
        }
        nextEndlessPuzzle()
    }

    /**
     * Generates the puzzle the player is now up to. There is no last endless
     * puzzle, so this is always available from the result card.
     */
    fun nextEndlessPuzzle() {
        val number = endless.progress.value.puzzleNumber
        val tier = EndlessTier.forPuzzleNumber(number)
        // Repeat control: a board is a pure function of its seed, so "not the picture you just
        // had" is done by choosing the seed — draw another until its picture is not among the last
        // few. Bounded, so a source of seeds that cannot vary (a test's fixed one) still terminates.
        var seed = endless.nextSeed()
        var rerolls = 0
        while (MysteryShapePuzzles.templateIdFor(seed, tier) in recentShapes && rerolls < MAX_SEED_REROLLS) {
            seed = endless.nextSeed()
            rerolls++
        }
        openEndless(generate(seed, tier), number)
    }

    /**
     * Replays the endless puzzle on screen. Regenerating from the same seed is
     * what makes this the same board down to the last arrow rather than
     * something that merely looks similar.
     */
    fun replayEndlessPuzzle() {
        val current = _mode.value as? GameMode.Endless ?: return nextEndlessPuzzle()
        // The mode is left exactly as it is, which is the point: the puzzle
        // number stays the same in the HUD, and `alreadyCleared` carries over so
        // a second win on a beaten puzzle does not bump the counters again.
        endless.clearInProgress()
        val puzzle = generate(current.seed, current.tier)
        currentShape = puzzle.shape
        load(puzzle.level)
    }

    private fun generate(seed: Long, tier: EndlessTier): GeneratedPuzzle =
        MysteryShapePuzzles.generate(seed, tier).also { debugSink("endless generate :: ${it.debugSummary()}") }

    /**
     * Puts a generated board on screen. [arrows] and [lives] are only passed
     * when resuming; a new puzzle starts from the full board and full lives.
     */
    private fun openEndless(
        puzzle: GeneratedPuzzle,
        puzzleNumber: Int,
        arrows: List<ArrowTile>? = null,
        lives: Int = GameState.STARTING_LIVES
    ) {
        _mode.value = GameMode.Endless(
            seed = puzzle.seed,
            puzzleNumber = puzzleNumber,
            tier = puzzle.tier
        )
        if (arrows == null) endless.clearInProgress()
        puzzle.shape?.let { remember(it.templateId) }
        openGeneratedBoard(puzzle, arrows, lives)
    }

    /** Notes the picture just served, keeping the last [RECENT_SHAPES] distinct ones. */
    private fun remember(templateId: String) {
        recentShapes.remove(templateId)
        recentShapes.addLast(templateId)
        while (recentShapes.size > RECENT_SHAPES) recentShapes.removeFirst()
    }

    // ---- daily challenge -----------------------------------------------------

    /**
     * What the Daily Challenge card does: pick up today's board where it was
     * left, or build it.
     *
     * The date is the puzzle, so there is nothing to choose here. A save from an
     * earlier day is not offered — [DailyRepository.loadInProgress] drops it —
     * so a player who left yesterday's board half-done is handed *today's*
     * puzzle rather than yesterday's under today's heading.
     *
     * A board that is already cleared today is opened as a replay: the same
     * puzzle, flagged [GameMode.Daily.alreadyCleared] so winning it again does
     * not touch the streak.
     */
    fun startDaily() {
        val date = daily.today()
        val puzzle = DailyChallenge.generate(date)
        val alreadyCleared = daily.progress.value.isCompletedOn(date)
        val saved = daily.loadInProgress()

        if (saved != null) {
            val arrows = puzzle.level.arrows.filter { it.id in saved.remainingArrowIds }
            if (arrows.isNotEmpty() && arrows.size == saved.remainingArrowIds.size) {
                debugSink("daily resume ${date.iso} :: ${puzzle.debugSummary()}")
                openDaily(puzzle, date, alreadyCleared, arrows, saved.lives)
                return
            }
            // The ids do not belong to the board this date builds, so the save
            // is describing a puzzle that no longer exists. Today's board is
            // still today's board, so it is opened fresh rather than refused.
            debugSink("daily save did not match ${date.iso}; opening today's board fresh")
            daily.clearInProgress()
        }

        debugSink("daily generate ${date.iso} :: ${puzzle.debugSummary()}")
        openDaily(puzzle, date, alreadyCleared)
    }

    /** Replays today's puzzle from the top. The same board — the date decides it. */
    fun replayDaily() {
        val current = _mode.value as? GameMode.Daily ?: return startDaily()
        daily.clearInProgress()
        // The date is kept — a replay is the same board — but `alreadyCleared`
        // is re-read rather than carried over, because what it means is "was
        // this day already in the books when this attempt started". After a win
        // it now is; after a loss it still is not, so the retry can still earn
        // the day.
        _mode.value = current.copy(
            alreadyCleared = daily.progress.value.isCompletedOn(current.date)
        )
        val puzzle = DailyChallenge.generate(current.date)
        currentShape = puzzle.shape
        load(puzzle.level)
    }

    private fun openDaily(
        puzzle: GeneratedPuzzle,
        date: GameDate,
        alreadyCleared: Boolean,
        arrows: List<ArrowTile>? = null,
        lives: Int = GameState.STARTING_LIVES
    ) {
        _mode.value = GameMode.Daily(
            date = date,
            seed = puzzle.seed,
            tier = puzzle.tier,
            alreadyCleared = alreadyCleared
        )
        if (arrows == null) daily.clearInProgress()
        openGeneratedBoard(puzzle, arrows, lives)
    }

    /** The part of opening a generated board that endless and daily share. */
    private fun openGeneratedBoard(
        puzzle: GeneratedPuzzle,
        arrows: List<ArrowTile>?,
        lives: Int
    ) {
        _escaping.value = emptyList()
        _blocked.value = null
        _hint.value = null
        _unblocked.value = null
        resetRunTally()
        currentShape = puzzle.shape
        _tutorial.value = TutorialState.Inactive
        _dependency.value = DependencyLesson.Inactive
        _state.value = GameState(
            level = puzzle.level,
            arrows = arrows ?: puzzle.level.arrows,
            lives = lives
        )
    }

    private fun load(level: Level) {
        _escaping.value = emptyList()
        _blocked.value = null
        _hint.value = null
        _unblocked.value = null
        resetRunTally()
        _state.value = GameState.newGame(level)
    }

    /**
     * A fresh board is a fresh run: the star tally starts over and the last
     * result stops being the current one. Every way of putting a board on screen
     * goes through here or sets the tally itself from a save.
     */
    private fun resetRunTally() {
        blockedTapsThisRun = 0
        hintUsedThisRun = false
        _campaignStars.value = null
        _campaignDiscovery.value = null
        _shapeReveal.value = null
    }

    /**
     * Mirrors the board to disk after every move. Only the facts needed to
     * rebuild it are written — level or seed, surviving arrow ids, lives — never
     * the in-flight animations, which are meaningless after a relaunch.
     *
     * A finished board has nothing worth resuming, so winning or losing clears
     * the save instead of writing one.
     */
    private fun persist(state: GameState) {
        when (val current = _mode.value) {
            GameMode.Campaign -> persistCampaign(state)
            is GameMode.Endless -> persistEndless(state, current)
            is GameMode.Daily -> persistDaily(state, current)
            // The tutorial is a demonstration, not a session. Writing nothing is
            // what guarantees that re-watching it cannot touch Campaign Level 1.
            GameMode.Tutorial -> Unit
        }
    }

    /**
     * Campaign is the one mode that rates a run. The stars are worked out from
     * the run's own tally, recorded as a *best* — [ProgressRepository.markCompleted]
     * refuses a downgrade — and published for the result card, which needs to
     * know both what this run earned and whether it beat what was there.
     *
     * The previous best is read before the write, because the write is what
     * changes it.
     */
    private fun persistCampaign(state: GameState) {
        when (state.status) {
            GameStatus.PLAYING -> progress.saveInProgress(
                SavedGame(
                    levelId = state.level.id,
                    remainingArrowIds = state.arrows.mapTo(HashSet()) { it.id },
                    lives = state.lives,
                    blockedTaps = blockedTapsThisRun,
                    hintUsed = hintUsedThisRun
                )
            )
            GameStatus.WON -> {
                val earned = StarRating.stars(blockedTapsThisRun, hintUsedThisRun)
                val previousBest = progress.starsFor(state.level.id)
                // Read before the write, for the same reason as the previous best:
                // the write is what makes it true. This is what separates a first
                // discovery from a revisit, and it is the whole of it — no "seen"
                // flag is kept anywhere.
                val wasCompletedBeforeRun = progress.progress.value.isCompleted(state.level.id)
                progress.markCompleted(state.level.id, earned)
                _campaignStars.value = CampaignStars(
                    earned = earned,
                    best = maxOf(earned, previousBest),
                    isNewBest = earned > previousBest
                )
                _campaignDiscovery.value = DiscoveryResult.of(
                    levelId = state.level.id,
                    wasCompletedBeforeRun = wasCompletedBeforeRun,
                    progress = progress.progress.value
                )
                progress.clearInProgress()
            }
            GameStatus.LOST -> progress.clearInProgress()
        }
    }

    /**
     * The endless mirror of [persistCampaign]. A board is stored as its seed and
     * tier plus the surviving ids, so a 36-arrow layout costs a few dozen bytes
     * and rebuilds exactly.
     *
     * Only the *first* clear of a given puzzle moves the counters: replaying a
     * seed the player has already beaten is a replay, not another puzzle.
     */
    private fun persistEndless(state: GameState, mode: GameMode.Endless) {
        when (state.status) {
            GameStatus.PLAYING -> endless.saveInProgress(
                EndlessSavedGame(
                    seed = mode.seed,
                    puzzleNumber = mode.puzzleNumber,
                    tier = mode.tier,
                    remainingArrowIds = state.arrows.mapTo(HashSet()) { it.id },
                    lives = state.lives
                )
            )
            GameStatus.WON -> {
                if (!mode.alreadyCleared) {
                    endless.markCompleted()
                    _mode.value = mode.copy(alreadyCleared = true)
                }
                endless.clearInProgress()
                publishShape()
            }
            GameStatus.LOST -> {
                endless.markFailed()
                endless.clearInProgress()
            }
        }
    }

    /**
     * The daily mirror. The board is stored as its date plus the surviving ids —
     * the seed is derived from the date, so there is nothing else to write — and
     * that is what makes a killed app come back to the same half-cleared board.
     *
     * The streak moves on the first clear of a date and never again. That rule
     * lives in [DailyProgress.completing], which is idempotent per date, rather
     * than in a flag here — so recording a win is unconditional and a replay
     * costs a no-op. [GameMode.Daily.alreadyCleared] is left exactly as it was
     * set when the board was opened, because it is what the result card reads
     * to decide whether to report a streak that moved or a day already counted;
     * flipping it here would make every first clear announce itself as a replay.
     *
     * Losing a daily does *not* break the streak: the streak counts days the
     * player turned up and finished, and running out of lives leaves the day's
     * puzzle still there to finish. The board is dropped so Retry starts clean.
     */
    private fun persistDaily(state: GameState, mode: GameMode.Daily) {
        when (state.status) {
            GameStatus.PLAYING -> daily.saveInProgress(
                DailySavedGame(
                    date = mode.date,
                    seed = mode.seed,
                    remainingArrowIds = state.arrows.mapTo(HashSet()) { it.id },
                    lives = state.lives
                )
            )
            GameStatus.WON -> {
                daily.markCompleted(mode.date)
                daily.clearInProgress()
                publishShape()
            }
            GameStatus.LOST -> daily.clearInProgress()
        }
    }

    /** The win is already on disk; now — and only now — the picture's name may be shown. */
    private fun publishShape() {
        _shapeReveal.value = currentShape?.let { ShapeReveal(it.templateId, it.name) }
    }

    private companion object {
        const val EVENT_BUFFER = 8

        /** The Campaign level that teaches "removing one arrow can free another". */
        const val DEPENDENCY_LESSON_LEVEL_ID = 2

        /** How many recent Endless pictures a new puzzle avoids. */
        const val RECENT_SHAPES = 3

        /** Seeds drawn before accepting a repeat; generous, but finite. */
        const val MAX_SEED_REROLLS = 16
    }
}

/** Supplies the ViewModel's dependencies, for `viewModel(factory = ...)`. */
class GameViewModelFactory(
    private val progress: ProgressRepository,
    private val endless: EndlessRepository,
    private val daily: DailyRepository,
    private val tutorialFlags: TutorialFlagStore,
    private val debugSink: (String) -> Unit = {}
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GameViewModel(progress, endless, daily, tutorialFlags, debugSink) as T
}
