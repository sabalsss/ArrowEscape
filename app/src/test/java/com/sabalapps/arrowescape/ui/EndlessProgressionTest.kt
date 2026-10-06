package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.endless.BoardAnalysis
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.PuzzleGenerator
import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Endless Mode end to end, driven through the ViewModel the way a player drives
 * it: start the mode, clear a puzzle, take the next one, replay one, and have
 * the process die mid-board.
 *
 * As in [GameProgressionTest], a relaunch is modelled by handing the same store
 * to a brand new ViewModel — which is exactly what Android killing the app and
 * the player coming back looks like from in here.
 */
class EndlessProgressionTest {

    private val store: ProgressStore = InMemoryProgressStore()

    /**
     * Seeds are handed out in a fixed sequence rather than drawn from the clock,
     * so "the next puzzle" is a specific, reproducible board in these tests.
     */
    private var nextSeed = 1_000L

    private fun relaunch(): GameViewModel = GameViewModel(
        progress = ProgressRepository(store),
        endless = EndlessRepository(store, newSeed = { nextSeed++ })
    )

    private fun GameViewModel.endlessMode(): GameMode.Endless =
        mode.value as? GameMode.Endless ?: error("expected Endless mode, was ${mode.value}")

    /** Clears the board the honest way: only ever taps an arrow that can leave. */
    private fun GameViewModel.clearBoard() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            val free = state.value.arrows.firstOrNull { MoveValidator.canEscape(it, state.value.arrows) }
                ?: error("board stalled with ${state.value.arrows.size} arrows left — it was not solvable")
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
            // The blocked arrow is locked until its animation ends, so the
            // next tap needs that callback first.
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

    // ---- starting out -------------------------------------------------------

    @Test
    fun `endless starts at puzzle one on the beginner tier`() {
        val vm = relaunch()
        vm.startEndless()

        val mode = vm.endlessMode()
        assertEquals(1, mode.puzzleNumber)
        assertEquals(EndlessTier.BEGINNER, mode.tier)
        assertEquals("Endless #1", mode.title)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertTrue(vm.state.value.arrows.isNotEmpty())
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
    }

    @Test
    fun `the board endless puts on screen is solvable`() {
        val vm = relaunch()
        vm.startEndless()

        assertNotNull(
            "endless #1 was not solvable",
            BoardAnalysis.solutionOrder(vm.state.value.arrows)
        )
        // And clearing it never stalls, which is the same claim from the
        // player's side.
        vm.clearBoard()
        assertEquals(GameStatus.WON, vm.state.value.status)
    }

    @Test
    fun `an endless board is never a campaign level`() {
        val vm = relaunch()
        vm.startEndless()
        assertEquals(PuzzleGenerator.ENDLESS_LEVEL_ID, vm.state.value.level.id)
        assertNull("endless wrote a campaign save", ProgressRepository(store).loadInProgress())
    }

    // ---- completing and advancing -------------------------------------------

    @Test
    fun `clearing endless one brings endless two into view`() {
        val vm = relaunch()
        vm.startEndless()
        val first = layout(vm.state.value.level)

        vm.clearBoard()

        assertEquals(GameStatus.WON, vm.state.value.status)
        assertEquals("the card still names the puzzle just cleared", 1, vm.endlessMode().puzzleNumber)
        assertEquals(1, vm.endlessProgress.value.totalCompleted)
        assertEquals(2, vm.endlessProgress.value.puzzleNumber)
        assertEquals(1, vm.endlessProgress.value.currentStreak)

        vm.continueAfterWin()

        assertEquals(2, vm.endlessMode().puzzleNumber)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
        assertNotEquals("endless #2 is the same board as #1", first, layout(vm.state.value.level))
    }

    @Test
    fun `there is no last endless puzzle`() {
        val vm = relaunch()
        vm.startEndless()
        repeat(8) {
            vm.clearBoard()
            assertEquals(GameStatus.WON, vm.state.value.status)
            vm.continueAfterWin()
            assertEquals(GameStatus.PLAYING, vm.state.value.status)
            assertTrue(vm.state.value.arrows.isNotEmpty())
        }
        assertEquals(9, vm.endlessMode().puzzleNumber)
        assertEquals(8, vm.endlessProgress.value.totalCompleted)
    }

    @Test
    fun `the tier climbs with the puzzle number`() {
        val vm = relaunch()
        vm.startEndless()
        assertEquals(EndlessTier.BEGINNER, vm.endlessMode().tier)

        // Five clears is the end of the Beginner band.
        repeat(5) {
            vm.clearBoard()
            vm.continueAfterWin()
        }
        assertEquals(6, vm.endlessMode().puzzleNumber)
        assertEquals(EndlessTier.EASY, vm.endlessMode().tier)
    }

    @Test
    fun `losing keeps the player on the same puzzle number`() {
        val vm = relaunch()
        vm.startEndless()
        vm.clearBoard()
        vm.continueAfterWin()
        assertEquals(1, vm.endlessProgress.value.currentStreak)

        vm.loseBoard()

        assertEquals(GameStatus.LOST, vm.state.value.status)
        assertEquals("a lost puzzle is retried, not skipped", 2, vm.endlessProgress.value.puzzleNumber)
        assertEquals(1, vm.endlessProgress.value.totalCompleted)
        assertEquals(0, vm.endlessProgress.value.currentStreak)
        assertEquals("the best streak is a record", 1, vm.endlessProgress.value.bestStreak)
        assertNull("a finished board is not worth resuming", vm.endlessSavedGame.value)
    }

    @Test
    fun `a puzzle lost and then won still counts`() {
        val vm = relaunch()
        vm.startEndless()
        vm.loseBoard()
        assertEquals(0, vm.endlessProgress.value.totalCompleted)

        vm.replayCurrent()
        vm.clearBoard()

        assertEquals(1, vm.endlessProgress.value.totalCompleted)
        assertEquals(2, vm.endlessProgress.value.puzzleNumber)
    }

    // ---- replay -------------------------------------------------------------

    @Test
    fun `replay rebuilds the identical board from the same seed`() {
        val vm = relaunch()
        vm.startEndless()
        val before = layout(vm.state.value.level)
        val seed = vm.endlessMode().seed

        vm.removeOne()
        vm.replayCurrent()

        assertEquals("replay changed the layout", before, layout(vm.state.value.level))
        assertEquals("replay changed the seed", seed, vm.endlessMode().seed)
        assertEquals("replay did not restore the full board", before.sumOf { row -> row.count { it != '.' } }, vm.state.value.arrows.size)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertEquals(1, vm.endlessMode().puzzleNumber)
    }

    @Test
    fun `replaying a puzzle already cleared does not advance the counters again`() {
        val vm = relaunch()
        vm.startEndless()
        vm.clearBoard()
        assertEquals(1, vm.endlessProgress.value.totalCompleted)

        vm.replayCurrent()
        vm.clearBoard()

        assertEquals("a replay is not another puzzle", 1, vm.endlessProgress.value.totalCompleted)
        assertEquals(2, vm.endlessProgress.value.puzzleNumber)
        assertEquals(1, vm.endlessProgress.value.currentStreak)
    }

    @Test
    fun `restart keeps the same endless puzzle and drops its save`() {
        val vm = relaunch()
        vm.startEndless()
        val before = layout(vm.state.value.level)

        vm.removeOne()
        assertNotNull(vm.endlessSavedGame.value)

        vm.restart()

        assertEquals(before, layout(vm.state.value.level))
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertNull("restart is a clean slate, so there is nothing to resume", vm.endlessSavedGame.value)
    }

    // ---- surviving a process death ------------------------------------------

    @Test
    fun `a part-cleared endless board is restored exactly after a relaunch`() {
        val first = relaunch()
        first.startEndless()
        val seed = first.endlessMode().seed
        val tier = first.endlessMode().tier
        val board = layout(first.state.value.level)

        first.removeOne()
        first.removeOne()
        val survivingIds = first.state.value.arrows.map { it.id }.sorted()
        val arrowsLeft = survivingIds.size

        // Android kills the app here. Everything in memory is gone.
        val restored = relaunch()
        restored.startEndless()

        assertEquals("the restored board is a different layout", board, layout(restored.state.value.level))
        assertEquals(seed, restored.endlessMode().seed)
        assertEquals(tier, restored.endlessMode().tier)
        assertEquals(1, restored.endlessMode().puzzleNumber)
        assertEquals(arrowsLeft, restored.state.value.arrows.size)
        assertEquals(
            "the wrong arrows came back",
            survivingIds,
            restored.state.value.arrows.map { it.id }.sorted()
        )
        assertEquals(GameStatus.PLAYING, restored.state.value.status)
    }

    @Test
    fun `lives are restored along with the board`() {
        val first = relaunch()
        first.startEndless()
        val blocked = first.state.value.arrows.first { !MoveValidator.canEscape(it, first.state.value.arrows) }
        first.onArrowTapped(blocked.id)
        first.blocked.value?.let { first.onBlockedFeedbackFinished(it.nonce) }
        assertEquals(GameState.STARTING_LIVES - 1, first.state.value.lives)
        first.removeOne()

        val restored = relaunch()
        restored.startEndless()

        assertEquals(GameState.STARTING_LIVES - 1, restored.state.value.lives)
    }

    @Test
    fun `a relaunch mid-board does not re-seed the puzzle`() {
        val first = relaunch()
        first.startEndless()
        first.removeOne()
        val seedBefore = nextSeed

        relaunch().startEndless()

        assertEquals("resuming should not have drawn a new seed", seedBefore, nextSeed)
    }

    @Test
    fun `after a win a relaunch opens the next puzzle rather than the cleared one`() {
        val first = relaunch()
        first.startEndless()
        val cleared = layout(first.state.value.level)
        first.clearBoard()

        val restored = relaunch()
        restored.startEndless()

        assertEquals(2, restored.endlessMode().puzzleNumber)
        assertNotEquals("the cleared board came back", cleared, layout(restored.state.value.level))
        assertEquals(GameStatus.PLAYING, restored.state.value.status)
    }

    // ---- corrupt and stale saves --------------------------------------------

    @Test
    fun `a corrupt endless save falls back to a fresh valid puzzle`() {
        store.putString("endless_game", "1|not-a-seed|3|BEGINNER|2|0,1")
        store.putString("endless_progress", "total nonsense")

        val vm = relaunch()
        vm.startEndless()

        assertEquals(1, vm.endlessMode().puzzleNumber)
        assertTrue(vm.state.value.arrows.isNotEmpty())
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertNotNull(
            "the fallback puzzle is not solvable",
            BoardAnalysis.solutionOrder(vm.state.value.arrows)
        )
        vm.clearBoard()
        assertEquals(GameStatus.WON, vm.state.value.status)
    }

    @Test
    fun `a save whose ids do not belong to its board is discarded`() {
        // A plausible-looking save that names arrows the regenerated board does
        // not have — a hand-edited preference, or a seed from a future
        // generator. It must not produce a board the player cannot finish.
        store.putString("endless_game", "1|1000|1|BEGINNER|2|0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15")

        val vm = relaunch()
        vm.startEndless()

        assertEquals(GameStatus.PLAYING, vm.state.value.status)
        assertNotNull(
            "the replacement puzzle is not solvable",
            BoardAnalysis.solutionOrder(vm.state.value.arrows)
        )
        vm.clearBoard()
        assertEquals(GameStatus.WON, vm.state.value.status)
    }

    // ---- separation from the campaign ---------------------------------------

    @Test
    fun `playing endless leaves campaign progress and its board untouched`() {
        val vm = relaunch()

        // Get the campaign to a specific, checkable state first.
        vm.continueGame()
        vm.removeOne()
        vm.removeOne()
        val campaignLevel = vm.state.value.level.id
        val campaignArrows = vm.state.value.arrows.map { it.id }.sorted()
        val campaignProgress = vm.playerProgress.value

        // Now play a run of endless, including a loss.
        vm.startEndless()
        vm.clearBoard()
        vm.continueAfterWin()
        vm.removeOne()
        vm.loseBoard()
        vm.startEndless()
        vm.removeOne()

        assertEquals("endless moved campaign progress", campaignProgress, vm.playerProgress.value)
        assertEquals(campaignLevel, vm.savedGame.value?.levelId)
        assertEquals(
            "endless overwrote the campaign's board",
            campaignArrows,
            vm.savedGame.value?.remainingArrowIds?.sorted()
        )

        // And the campaign picks straight back up where it was left.
        vm.continueGame()
        assertEquals(campaignLevel, vm.state.value.level.id)
        assertEquals(campaignArrows, vm.state.value.arrows.map { it.id }.sorted())
    }

    @Test
    fun `switching between the modes does not blur the two boards`() {
        val vm = relaunch()

        vm.continueGame()
        vm.removeOne()
        val campaignBoard = vm.state.value.arrows.map { it.id }.sorted()

        vm.startEndless()
        vm.removeOne()
        val endlessBoard = vm.state.value.arrows.map { it.id }.sorted()
        assertEquals(GameMode.Endless::class, vm.mode.value::class)

        vm.continueGame()
        assertEquals(GameMode.Campaign, vm.mode.value)
        assertEquals(campaignBoard, vm.state.value.arrows.map { it.id }.sorted())

        vm.startEndless()
        assertEquals(endlessBoard, vm.state.value.arrows.map { it.id }.sorted())
    }

    @Test
    fun `continue after a win does the right thing in each mode`() {
        val vm = relaunch()

        // Campaign: advance through the catalogue.
        vm.continueGame()
        val startingLevel = vm.state.value.level.id
        vm.clearBoard()
        vm.continueAfterWin()
        assertEquals(GameMode.Campaign, vm.mode.value)
        assertEquals(startingLevel + 1, vm.state.value.level.id)

        // Endless: generate the next puzzle.
        vm.startEndless()
        vm.clearBoard()
        vm.continueAfterWin()
        assertEquals(2, vm.endlessMode().puzzleNumber)
    }

    @Test
    fun `endless progress survives a relaunch`() {
        val vm = relaunch()
        vm.startEndless()
        repeat(3) {
            vm.clearBoard()
            vm.continueAfterWin()
        }
        vm.loseBoard()

        val restored = relaunch()
        assertEquals(4, restored.endlessProgress.value.puzzleNumber)
        assertEquals(3, restored.endlessProgress.value.totalCompleted)
        assertEquals(0, restored.endlessProgress.value.currentStreak)
        assertEquals(3, restored.endlessProgress.value.bestStreak)
    }

    // ---- the hardest boards the mode can produce ----------------------------

    @Test
    fun `an expert board is playable and clearable`() {
        // The Expert tier is only reached after 50 puzzles, so it is forced here
        // rather than played to: a 6x9, 36-arrow board is the worst case for
        // both the layout and the solver.
        store.putString("endless_progress", "1|51|50|0|12")
        val forced = relaunch()
        forced.startEndless()

        assertEquals(EndlessTier.EXPERT, forced.endlessMode().tier)
        assertEquals(51, forced.endlessMode().puzzleNumber)
        assertTrue(
            "an Expert board should be wider than a Beginner one",
            forced.state.value.arrows.size >= EndlessTier.EXPERT.arrows.first - EndlessTier.ARROW_SLACK
        )
        assertTrue(forced.state.value.columns <= 6)

        forced.clearBoard()
        assertEquals(GameStatus.WON, forced.state.value.status)
        assertEquals(52, forced.endlessProgress.value.puzzleNumber)
        assertTrue(
            "50 endless puzzles should not have unlocked a campaign level",
            forced.playerProgress.value.completedLevels.isEmpty()
        )
    }

    @Test
    fun `every tier produces a board the player can actually finish`() {
        for (tier in EndlessTier.entries) {
            val target = when (tier) {
                EndlessTier.BEGINNER -> 1
                EndlessTier.EASY -> 6
                EndlessTier.MEDIUM -> 16
                EndlessTier.HARD -> 31
                EndlessTier.EXPERT -> 51
            }
            val freshStore: ProgressStore = InMemoryProgressStore(
                mapOf("endless_progress" to "1|$target|${target - 1}|0|0")
            )
            val vm = GameViewModel(
                progress = ProgressRepository(freshStore),
                endless = EndlessRepository(freshStore, newSeed = { 4242L })
            )
            vm.startEndless()

            assertEquals(tier, vm.endlessMode().tier)
            assertTrue("$tier board exceeds the column cap", vm.state.value.columns <= 6)
            vm.clearBoard()
            assertEquals("$tier board could not be cleared", GameStatus.WON, vm.state.value.status)
        }
    }

    /** Guards the helper above: a board that is not solvable must fail loudly. */
    @Test
    fun `the clearing helper reports a stalled board rather than looping`() {
        val standoff = listOf(
            ArrowTile(0, 0, 0, Direction.RIGHT),
            ArrowTile(1, 0, 1, Direction.LEFT)
        )
        assertNull("this fixture is supposed to be unsolvable", BoardAnalysis.solutionOrder(standoff))
    }
}
