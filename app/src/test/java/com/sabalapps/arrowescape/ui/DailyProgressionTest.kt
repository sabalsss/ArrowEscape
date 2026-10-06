package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.daily.DailyProgress
import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.BoardAnalysis
import com.sabalapps.arrowescape.endless.EndlessProgress
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Daily Challenge end to end, driven through the ViewModel the way a player
 * drives it: open the card, clear the board, replay it, let the day roll over,
 * and have the process die mid-puzzle.
 *
 * The clock is a [FixedDateProvider] throughout, so "and then it was tomorrow"
 * is a line of test code rather than a thing that has to be waited for. A
 * relaunch is modelled by handing the same stores to a brand new ViewModel —
 * which is exactly what Android killing the app and the player coming back looks
 * like from in here.
 *
 * The daily store is deliberately a *different* store from the campaign and
 * endless one, mirroring the separate preferences file the app uses, so an
 * isolation claim proven here is the same claim that holds on device.
 */
class DailyProgressionTest {

    private val mainStore: ProgressStore = InMemoryProgressStore()
    private val dailyStore: ProgressStore = InMemoryProgressStore()
    private val clock = FixedDateProvider(GameDate(2026, 10, 4))
    private var nextEndlessSeed = 2_000L

    private fun relaunch(): GameViewModel = GameViewModel(
        progress = ProgressRepository(mainStore),
        endless = EndlessRepository(mainStore, newSeed = { nextEndlessSeed++ }),
        daily = DailyRepository(dailyStore, clock)
    )

    private fun GameViewModel.dailyMode(): GameMode.Daily =
        mode.value as? GameMode.Daily ?: error("expected Daily mode, was ${mode.value}")

    /** Clears the board the honest way: only ever taps an arrow that can leave. */
    private fun GameViewModel.clearBoard() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            val free = state.value.arrows.firstOrNull { MoveValidator.canEscape(it, state.value.arrows) }
                ?: error("board stalled with ${state.value.arrows.size} arrows left")
            onArrowTapped(free.id)
            escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
            check(guard++ < 500) { "clearing the board did not terminate" }
        }
    }

    private fun GameViewModel.removeOne() {
        val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
        onArrowTapped(free.id)
        escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
    }

    /** Spends every life on a blocked arrow, which is the only way to lose. */
    private fun GameViewModel.loseBoard() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            val target = state.value.arrows.firstOrNull { !MoveValidator.canEscape(it, state.value.arrows) }
                ?: error("no blocked arrow to lose on")
            onArrowTapped(target.id)
            blocked.value?.let { onBlockedFeedbackFinished(it.nonce) }
            check(guard++ < 50) { "losing the board did not terminate" }
        }
    }

    private fun layout(level: Level): List<String> {
        val grid = Array(level.rows) { CharArray(level.columns) { '.' } }
        for (arrow in level.arrows) {
            grid[arrow.row][arrow.col] = when (arrow.direction) {
                Direction.UP -> '^'
                Direction.DOWN -> 'v'
                Direction.LEFT -> '<'
                Direction.RIGHT -> '>'
            }
        }
        return grid.map { String(it) }
    }

    /** Plays a whole day: open the daily, clear it, move the clock on. */
    private fun GameViewModel.playToday() {
        startDaily()
        clearBoard()
    }

    // ---- opening the board ---------------------------------------------------

    @Test
    fun `the daily card opens today's puzzle`() {
        val viewModel = relaunch()
        viewModel.startDaily()

        val mode = viewModel.dailyMode()
        assertEquals(GameDate(2026, 10, 4), mode.date)
        assertEquals(DailyChallenge.seedFor(mode.date), mode.seed)
        assertEquals(DailyChallenge.tierFor(mode.date), mode.tier)
        assertEquals(GameStatus.PLAYING, viewModel.state.value.status)
        assertEquals(GameState.STARTING_LIVES, viewModel.state.value.lives)
    }

    @Test
    fun `today's board is the board the date derives, and it is solvable`() {
        val viewModel = relaunch()
        viewModel.startDaily()

        val expected = DailyChallenge.generate(GameDate(2026, 10, 4))
        assertEquals(layout(expected.level), layout(viewModel.state.value.level))
        assertTrue(
            "the witness for today's board is not a legal removal order",
            BoardAnalysis.verifyOrder(viewModel.state.value.arrows, expected.solution)
        )
    }

    @Test
    fun `opening the daily twice on the same day is the same board`() {
        val first = relaunch().also { it.startDaily() }
        val second = relaunch().also { it.startDaily() }
        assertEquals(layout(first.state.value.level), layout(second.state.value.level))
    }

    @Test
    fun `the board is identified as the daily challenge rather than as a level`() {
        val viewModel = relaunch()
        viewModel.startDaily()
        assertEquals("Daily Challenge", viewModel.dailyMode().title)
        assertEquals("Daily Challenge", viewModel.state.value.level.name)
    }

    @Test
    fun `the daily is Medium or Hard, never Expert`() {
        for (day in 0L until 90L) {
            clock.date = GameDate(2026, 1, 1).plusDays(day)
            val viewModel = relaunch()
            viewModel.startDaily()
            val tier = viewModel.dailyMode().tier
            assertTrue(
                "${clock.date.iso} was tiered $tier",
                tier == EndlessTier.MEDIUM || tier == EndlessTier.HARD
            )
        }
    }

    // ---- completion ----------------------------------------------------------

    @Test
    fun `clearing today's board records the day once`() {
        val viewModel = relaunch()
        viewModel.playToday()

        assertEquals(GameStatus.WON, viewModel.state.value.status)
        val progress = viewModel.dailyProgress.value
        assertEquals(1, progress.totalCompleted)
        assertEquals(1, progress.currentStreak)
        assertEquals(1, progress.bestStreak)
        assertEquals(GameDate(2026, 10, 4), progress.lastCompletedDate)
    }

    @Test
    fun `completing the daily clears the saved board`() {
        val viewModel = relaunch()
        viewModel.playToday()
        assertNull(DailyRepository(dailyStore, clock).loadInProgress())
    }

    @Test
    fun `the first clear of a day reports as a day that counted`() {
        // The result card reads this to decide between "that is today done" and
        // "you have already had today". Getting it backwards would tell every
        // first-time winner their streak had not moved.
        val viewModel = relaunch()
        viewModel.startDaily()
        assertFalse(
            "a day being played for the first time is not already cleared",
            viewModel.dailyMode().alreadyCleared
        )

        viewModel.clearBoard()
        assertEquals(GameStatus.WON, viewModel.state.value.status)
        assertFalse(
            "winning a day must not relabel it as a replay",
            viewModel.dailyMode().alreadyCleared
        )
        assertEquals(1, viewModel.dailyProgress.value.currentStreak)
    }

    @Test
    fun `replaying a won day reports as a day already counted`() {
        val viewModel = relaunch()
        viewModel.playToday()

        viewModel.replayCurrent()
        assertTrue(
            "a replay after a win is a day already in the books",
            viewModel.dailyMode().alreadyCleared
        )
        viewModel.clearBoard()
        assertTrue(viewModel.dailyMode().alreadyCleared)
        assertEquals(1, viewModel.dailyProgress.value.currentStreak)
    }

    @Test
    fun `retrying a lost day still reports as a day that counted`() {
        val viewModel = relaunch()
        viewModel.startDaily()
        viewModel.loseBoard()

        viewModel.replayCurrent()
        assertFalse(
            "losing does not put the day in the books",
            viewModel.dailyMode().alreadyCleared
        )
        viewModel.clearBoard()
        assertFalse(viewModel.dailyMode().alreadyCleared)
        assertEquals(1, viewModel.dailyProgress.value.currentStreak)
    }

    @Test
    fun `each new day starts out not already cleared`() {
        val viewModel = relaunch()
        repeat(4) {
            viewModel.startDaily()
            assertFalse(
                "${clock.date.iso} opened as already cleared",
                viewModel.dailyMode().alreadyCleared
            )
            viewModel.clearBoard()
            clock.advance()
        }
        assertEquals(4, viewModel.dailyProgress.value.currentStreak)
    }

    @Test
    fun `a completed daily is marked as cleared when reopened the same day`() {
        relaunch().playToday()

        val reopened = relaunch()
        reopened.startDaily()
        assertTrue(
            "reopening a cleared day must know it is a replay",
            reopened.dailyMode().alreadyCleared
        )
    }

    // ---- replay --------------------------------------------------------------

    @Test
    fun `replaying the daily does not increment the streak`() {
        val viewModel = relaunch()
        viewModel.playToday()
        val afterFirst = viewModel.dailyProgress.value

        repeat(3) {
            viewModel.replayCurrent()
            viewModel.clearBoard()
        }

        assertEquals("a replay must not move any counter", afterFirst, viewModel.dailyProgress.value)
        assertEquals(1, viewModel.dailyProgress.value.totalCompleted)
        assertEquals(1, viewModel.dailyProgress.value.currentStreak)
    }

    @Test
    fun `replaying the daily is the same board`() {
        val viewModel = relaunch()
        viewModel.startDaily()
        val original = layout(viewModel.state.value.level)

        viewModel.clearBoard()
        viewModel.replayCurrent()

        assertEquals(original, layout(viewModel.state.value.level))
        assertEquals(GameState.STARTING_LIVES, viewModel.state.value.lives)
    }

    @Test
    fun `replaying after a relaunch still does not increment the streak`() {
        relaunch().playToday()

        val reopened = relaunch()
        reopened.startDaily()
        reopened.clearBoard()

        assertEquals(1, reopened.dailyProgress.value.totalCompleted)
        assertEquals(1, reopened.dailyProgress.value.currentStreak)
    }

    @Test
    fun `a lost daily does not break the streak and can be retried`() {
        val viewModel = relaunch()
        repeat(2) {
            viewModel.playToday()
            clock.advance()
        }
        assertEquals(2, viewModel.dailyProgress.value.currentStreak)

        viewModel.startDaily()
        viewModel.loseBoard()
        assertEquals(GameStatus.LOST, viewModel.state.value.status)
        assertEquals(
            "running out of lives is not a missed day",
            2,
            viewModel.dailyProgress.value.currentStreak
        )

        // The day's puzzle is still today's, so the retry can still earn it.
        viewModel.replayCurrent()
        viewModel.clearBoard()
        assertEquals(3, viewModel.dailyProgress.value.currentStreak)
    }

    // ---- the streak across days ---------------------------------------------

    @Test
    fun `a consecutive day increments the streak`() {
        val viewModel = relaunch()
        viewModel.playToday()
        clock.advance()
        viewModel.playToday()

        assertEquals(2, viewModel.dailyProgress.value.currentStreak)
        assertEquals(2, viewModel.dailyProgress.value.totalCompleted)
    }

    @Test
    fun `a week of consecutive days builds a streak of seven`() {
        val viewModel = relaunch()
        repeat(7) {
            viewModel.playToday()
            clock.advance()
        }
        assertEquals(7, viewModel.dailyProgress.value.currentStreak)
        assertEquals(7, viewModel.dailyProgress.value.bestStreak)
        assertEquals(7, viewModel.dailyProgress.value.totalCompleted)
    }

    @Test
    fun `a skipped day resets the streak to one and keeps the best`() {
        val viewModel = relaunch()
        repeat(5) {
            viewModel.playToday()
            clock.advance()
        }
        clock.advance() // the missed day
        viewModel.playToday()

        assertEquals(1, viewModel.dailyProgress.value.currentStreak)
        assertEquals(5, viewModel.dailyProgress.value.bestStreak)
        assertEquals(6, viewModel.dailyProgress.value.totalCompleted)
    }

    @Test
    fun `each day is a different board`() {
        val layouts = (0L until 20L).map { day ->
            clock.date = GameDate(2026, 10, 4).plusDays(day)
            val viewModel = relaunch()
            viewModel.startDaily()
            layout(viewModel.state.value.level)
        }
        assertEquals("two days handed out the same board", layouts.size, layouts.toSet().size)
    }

    @Test
    fun `the streak and the best survive a relaunch`() {
        val first = relaunch()
        repeat(4) {
            first.playToday()
            clock.advance()
        }
        clock.advance()
        first.playToday()

        val reopened = relaunch()
        assertEquals(1, reopened.dailyProgress.value.currentStreak)
        assertEquals(4, reopened.dailyProgress.value.bestStreak)
        assertEquals(5, reopened.dailyProgress.value.totalCompleted)
    }

    // ---- process death -------------------------------------------------------

    @Test
    fun `a killed app comes back to the same half-cleared daily board`() {
        val first = relaunch()
        first.startDaily()
        val original = layout(first.state.value.level)
        repeat(3) { first.removeOne() }
        val survivors = first.state.value.arrows.map { it.id }.toSet()
        val lives = first.state.value.lives

        // The process dies here. Nothing else is saved.
        val reopened = relaunch()
        reopened.startDaily()

        assertEquals("the board was rebuilt differently", original, layout(reopened.state.value.level))
        assertEquals("the surviving arrows differ", survivors, reopened.state.value.arrows.map { it.id }.toSet())
        assertEquals(lives, reopened.state.value.lives)
        assertEquals(GameDate(2026, 10, 4), reopened.dailyMode().date)
    }

    @Test
    fun `a killed app comes back with the lives that were spent`() {
        val first = relaunch()
        first.startDaily()
        val blockedArrow = first.state.value.arrows
            .first { !MoveValidator.canEscape(it, first.state.value.arrows) }
        first.onArrowTapped(blockedArrow.id)
        first.blocked.value?.let { first.onBlockedFeedbackFinished(it.nonce) }
        assertEquals(GameState.STARTING_LIVES - 1, first.state.value.lives)

        val reopened = relaunch()
        reopened.startDaily()
        assertEquals(GameState.STARTING_LIVES - 1, reopened.state.value.lives)
    }

    @Test
    fun `restarting a daily throws the half-cleared board away`() {
        val first = relaunch()
        first.startDaily()
        repeat(2) { first.removeOne() }
        first.restart()

        assertEquals(
            "restart should be the full board again",
            first.state.value.level.arrows.size,
            first.state.value.arrows.size
        )
        val reopened = relaunch()
        reopened.startDaily()
        assertEquals(reopened.state.value.level.arrows.size, reopened.state.value.arrows.size)
    }

    // ---- yesterday's board ---------------------------------------------------

    @Test
    fun `yesterday's unfinished board does not replace today's challenge`() {
        val first = relaunch()
        first.startDaily()
        repeat(4) { first.removeOne() }
        val yesterdayLayout = layout(first.state.value.level)
        val yesterdaySurvivors = first.state.value.arrows.size

        clock.advance()

        val today = relaunch()
        today.startDaily()

        assertEquals("today's mode should carry today's date", GameDate(2026, 10, 5), today.dailyMode().date)
        assertNotEquals(
            "today was handed yesterday's board",
            yesterdayLayout,
            layout(today.state.value.level)
        )
        assertEquals(
            "today's board should be whole",
            today.state.value.level.arrows.size,
            today.state.value.arrows.size
        )
        assertNotEquals(yesterdaySurvivors, today.state.value.arrows.size)
        assertEquals(GameState.STARTING_LIVES, today.state.value.lives)
    }

    @Test
    fun `an unfinished day is not counted as completed when the day rolls over`() {
        val first = relaunch()
        first.startDaily()
        repeat(3) { first.removeOne() }
        clock.advance()

        val today = relaunch()
        today.startDaily()
        assertEquals(
            "a day left unfinished must not count",
            DailyProgress(),
            today.dailyProgress.value
        )
    }

    @Test
    fun `clearing today after abandoning yesterday starts the streak at one`() {
        val first = relaunch()
        first.startDaily()
        repeat(2) { first.removeOne() }
        clock.advance()

        val today = relaunch()
        today.playToday()
        assertEquals(1, today.dailyProgress.value.currentStreak)
        assertEquals(1, today.dailyProgress.value.totalCompleted)
    }

    // ---- isolation -----------------------------------------------------------

    @Test
    fun `playing the daily leaves campaign progress untouched`() {
        val viewModel = relaunch()
        val before = viewModel.playerProgress.value
        val savedBefore = viewModel.savedGame.value

        repeat(3) {
            viewModel.playToday()
            clock.advance()
        }

        assertEquals("the daily moved campaign progress", before, viewModel.playerProgress.value)
        assertEquals(savedBefore, viewModel.savedGame.value)
        assertEquals(PlayerProgress(), viewModel.playerProgress.value)
    }

    @Test
    fun `playing the daily leaves endless progress untouched`() {
        val viewModel = relaunch()
        val before = viewModel.endlessProgress.value

        repeat(3) {
            viewModel.playToday()
            clock.advance()
        }

        assertEquals("the daily moved endless progress", before, viewModel.endlessProgress.value)
        assertEquals(EndlessProgress(), viewModel.endlessProgress.value)
        assertNull(viewModel.endlessSavedGame.value)
    }

    @Test
    fun `a daily in progress and a campaign level in progress coexist`() {
        val viewModel = relaunch()
        // Leave a campaign level half-done...
        viewModel.continueGame()
        repeat(2) { viewModel.removeOne() }
        val campaignSurvivors = viewModel.state.value.arrows.map { it.id }.toSet()
        val campaignLevel = viewModel.state.value.level.id

        // ...then a daily half-done on top of it.
        viewModel.startDaily()
        repeat(2) { viewModel.removeOne() }
        val dailySurvivors = viewModel.state.value.arrows.map { it.id }.toSet()

        // Both come back, independently, after a relaunch.
        val reopened = relaunch()
        reopened.startDaily()
        assertEquals(dailySurvivors, reopened.state.value.arrows.map { it.id }.toSet())

        val again = relaunch()
        again.continueGame()
        assertEquals(campaignLevel, again.state.value.level.id)
        assertEquals(campaignSurvivors, again.state.value.arrows.map { it.id }.toSet())
    }

    @Test
    fun `a corrupt daily save resets the daily only`() {
        // Build real campaign and endless state first...
        val viewModel = relaunch()
        viewModel.continueGame()
        viewModel.clearBoard()
        viewModel.startEndless()
        viewModel.clearBoard()
        val campaignAfter = viewModel.playerProgress.value
        val endlessAfter = viewModel.endlessProgress.value
        assertTrue("the fixture needs real campaign progress", campaignAfter.completedLevels.isNotEmpty())
        assertEquals(1, endlessAfter.totalCompleted)

        // ...and a real daily streak on top.
        viewModel.playToday()
        assertEquals(1, viewModel.dailyProgress.value.totalCompleted)

        // Now corrupt the daily store and relaunch.
        dailyStore.putString("daily_progress", "not a progress record")
        dailyStore.putString("daily_game", "not a board either")
        val reopened = relaunch()

        assertEquals("the daily should have reset", DailyProgress(), reopened.dailyProgress.value)
        assertEquals("campaign was damaged by a daily reset", campaignAfter, reopened.playerProgress.value)
        assertEquals("endless was damaged by a daily reset", endlessAfter, reopened.endlessProgress.value)

        // And the campaign and endless boards still open.
        reopened.continueGame()
        assertNotNull(reopened.state.value.level)
        reopened.startDaily()
        assertEquals(GameStatus.PLAYING, reopened.state.value.status)
    }

    @Test
    fun `a corrupt campaign save leaves the daily streak alone`() {
        // The reverse direction, which matters just as much.
        val viewModel = relaunch()
        repeat(3) {
            viewModel.playToday()
            clock.advance()
        }
        val dailyAfter = viewModel.dailyProgress.value
        assertEquals(3, dailyAfter.currentStreak)

        mainStore.putString("player_progress", "garbage")
        mainStore.putString("endless_progress", "garbage")

        val reopened = relaunch()
        assertEquals("a campaign reset damaged the daily streak", dailyAfter, reopened.dailyProgress.value)
        assertEquals(PlayerProgress(), reopened.playerProgress.value)
        assertEquals(EndlessProgress(), reopened.endlessProgress.value)
    }
}
