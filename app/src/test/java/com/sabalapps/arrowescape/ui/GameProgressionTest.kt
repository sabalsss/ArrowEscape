package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Progression and restoration as the player experiences them: the ViewModel
 * driven end to end over a store that survives being handed to a brand new
 * ViewModel, which is what a process death looks like from in here.
 */
class GameProgressionTest {

    private val store: ProgressStore = InMemoryProgressStore()

    /** A ViewModel over the same bytes — i.e. the app after a cold start. */
    private fun relaunch() = GameViewModel(ProgressRepository(store))

    /** Clears [level] the honest way: only ever taps an arrow that can leave. */
    private fun GameViewModel.clearLevel() {
        while (state.value.status == GameStatus.PLAYING) {
            val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
            onArrowTapped(free.id)
            escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
        }
    }

    private fun GameViewModel.removeOne() {
        val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
        onArrowTapped(free.id)
        escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
    }

    private fun GameViewModel.blockedArrow(): ArrowTile =
        state.value.arrows.first { !MoveValidator.canEscape(it, state.value.arrows) }

    // ---- first run ----------------------------------------------------------

    @Test
    fun `a fresh install starts on level one with nothing else unlocked`() {
        val vm = relaunch()

        assertEquals(1, vm.state.value.level.id)
        assertEquals(1, vm.playerProgress.value.highestUnlockedLevel)
        assertTrue(vm.playerProgress.value.completedLevels.isEmpty())
        assertNull(vm.savedGame.value)
    }

    @Test
    fun `continue on a fresh install opens level one`() {
        val vm = relaunch()
        vm.continueGame()

        assertEquals(1, vm.state.value.level.id)
        assertEquals(Levels.byId(1)!!.arrows.size, vm.state.value.arrows.size)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
    }

    // ---- progression --------------------------------------------------------

    @Test
    fun `completing a level unlocks the next one`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()

        assertEquals(GameStatus.WON, vm.state.value.status)
        assertEquals(2, vm.playerProgress.value.highestUnlockedLevel)
        assertEquals(setOf(1), vm.playerProgress.value.completedLevels)
    }

    @Test
    fun `continue from the level complete card loads the next level`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()
        vm.continueToNextLevel()

        assertEquals(2, vm.state.value.level.id)
        assertEquals(Levels.byId(2)!!.arrows.size, vm.state.value.arrows.size)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
    }

    @Test
    fun `replay restarts the level that was just finished`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()
        vm.restart()

        assertEquals(1, vm.state.value.level.id)
        assertEquals(Levels.byId(1)!!.arrows.size, vm.state.value.arrows.size)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
    }

    @Test
    fun `retry after losing restarts the same level`() {
        val vm = relaunch()
        vm.startLevel(1)
        val target = vm.blockedArrow()
        repeat(GameState.STARTING_LIVES) {
            vm.onArrowTapped(target.id)
            vm.blocked.value?.let { vm.onBlockedFeedbackFinished(it.nonce) }
        }
        assertEquals(GameStatus.LOST, vm.state.value.status)

        vm.restart()

        assertEquals(1, vm.state.value.level.id)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertEquals(Levels.byId(1)!!.arrows.size, vm.state.value.arrows.size)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
    }

    @Test
    fun `losing does not unlock anything`() {
        val vm = relaunch()
        vm.startLevel(1)
        val target = vm.blockedArrow()
        repeat(GameState.STARTING_LIVES) {
            vm.onArrowTapped(target.id)
            vm.blocked.value?.let { vm.onBlockedFeedbackFinished(it.nonce) }
        }

        assertEquals(1, vm.playerProgress.value.highestUnlockedLevel)
        assertTrue(vm.playerProgress.value.completedLevels.isEmpty())
    }

    @Test
    fun `the last level has nowhere to advance to and replays instead`() {
        unlockEverything()
        val vm = relaunch()
        vm.startLevel(30)
        vm.clearLevel()

        assertTrue(LevelProgression.isLast(vm.state.value.level))

        vm.continueToNextLevel()
        assertEquals(30, vm.state.value.level.id)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
        assertEquals(Levels.byId(30)!!.arrows.size, vm.state.value.arrows.size)
    }

    @Test
    fun `every level can be cleared through the ViewModel and chains to the next`() {
        unlockEverything()
        val vm = relaunch()
        vm.startLevel(1)
        (1..30).forEach { expected ->
            assertEquals(expected, vm.state.value.level.id)
            vm.clearLevel()
            assertEquals(GameStatus.WON, vm.state.value.status)
            vm.continueToNextLevel()
        }
        assertEquals((1..30).toSet(), vm.playerProgress.value.completedLevels)
    }

    @Test
    fun `continue opens the next level once the current one is cleared`() {
        val first = relaunch()
        first.startLevel(1)
        first.clearLevel()

        // cold start, as if the player closed the app on the result card
        val restored = relaunch()
        restored.continueGame()

        assertEquals(2, restored.state.value.level.id)
        assertEquals(Levels.byId(2)!!.arrows.size, restored.state.value.arrows.size)
        assertEquals(GameState.STARTING_LIVES, restored.state.value.lives)
    }

    @Test
    fun `clearing the last level leaves continue on the last level`() {
        unlockEverything()
        val vm = relaunch()
        vm.startLevel(30)
        vm.clearLevel()

        val restored = relaunch()
        restored.continueGame()

        assertEquals(30, restored.state.value.level.id)
        assertEquals(30, restored.playerProgress.value.highestUnlockedLevel)
    }

    // ---- locking ------------------------------------------------------------

    @Test
    fun `a locked level cannot be opened`() {
        val vm = relaunch()
        vm.startLevel(1)

        vm.startLevel(12)

        assertEquals(1, vm.state.value.level.id)
        assertEquals(1, vm.playerProgress.value.currentLevel)
    }

    @Test
    fun `a completed level can be replayed from the grid`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()
        vm.continueToNextLevel()
        assertEquals(2, vm.state.value.level.id)

        vm.startLevel(1)

        assertEquals(1, vm.state.value.level.id)
        assertEquals(Levels.byId(1)!!.arrows.size, vm.state.value.arrows.size)
        assertTrue(vm.playerProgress.value.isCompleted(1))
        // replaying does not take the unlock away
        assertEquals(2, vm.playerProgress.value.highestUnlockedLevel)
    }

    // ---- restoring an unfinished board --------------------------------------

    @Test
    fun `an unfinished level comes back after the process is killed`() {
        unlockEverything()
        val first = relaunch()
        first.startLevel(8)
        repeat(4) { first.removeOne() }
        val expectedArrows = first.state.value.arrows.map { it.id }.toSet()
        assertTrue(expectedArrows.size < Levels.byId(8)!!.arrows.size)

        // process death: nothing survives but the store
        val restored = relaunch()
        restored.continueGame()

        assertEquals(8, restored.state.value.level.id)
        assertEquals(expectedArrows, restored.state.value.arrows.map { it.id }.toSet())
        assertEquals(GameStatus.PLAYING, restored.state.value.status)
    }

    @Test
    fun `lives come back with the board`() {
        unlockEverything()
        val first = relaunch()
        first.startLevel(8)
        val target = first.blockedArrow()
        first.onArrowTapped(target.id)
        first.blocked.value?.let { first.onBlockedFeedbackFinished(it.nonce) }
        first.removeOne()
        assertEquals(GameState.STARTING_LIVES - 1, first.state.value.lives)

        val restored = relaunch()
        restored.continueGame()

        assertEquals(GameState.STARTING_LIVES - 1, restored.state.value.lives)
    }

    @Test
    fun `restored boards carry no animation state`() {
        unlockEverything()
        val first = relaunch()
        first.startLevel(8)
        val free = first.state.value.arrows
            .first { MoveValidator.canEscape(it, first.state.value.arrows) }
        first.onArrowTapped(free.id)          // deliberately left mid-flight
        first.onArrowTapped(first.blockedArrow().id)

        val restored = relaunch()
        restored.continueGame()

        assertTrue(restored.escaping.value.isEmpty())
        assertNull(restored.blocked.value)
    }

    @Test
    fun `restart clears the restored progress and gives back the original board`() {
        unlockEverything()
        val first = relaunch()
        first.startLevel(8)
        repeat(4) { first.removeOne() }

        val restored = relaunch()
        restored.continueGame()
        restored.restart()

        assertEquals(Levels.byId(8)!!.arrows.size, restored.state.value.arrows.size)
        assertEquals(GameState.STARTING_LIVES, restored.state.value.lives)
        assertNull(restored.savedGame.value)

        // and it stays cleared across another relaunch
        val again = relaunch()
        again.continueGame()
        assertEquals(Levels.byId(8)!!.arrows.size, again.state.value.arrows.size)
    }

    @Test
    fun `completing a level throws away its in-progress board`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.removeOne()
        assertNotNull(vm.savedGame.value)

        vm.clearLevel()

        assertNull(vm.savedGame.value)
        assertNull(relaunch().savedGame.value)
    }

    @Test
    fun `losing throws away the in-progress board`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.removeOne()
        val target = vm.blockedArrow()
        repeat(GameState.STARTING_LIVES) {
            vm.onArrowTapped(target.id)
            vm.blocked.value?.let { vm.onBlockedFeedbackFinished(it.nonce) }
        }
        assertEquals(GameStatus.LOST, vm.state.value.status)

        assertNull(vm.savedGame.value)
    }

    @Test
    fun `opening a level from the grid drops another level's saved board`() {
        unlockEverything()
        val vm = relaunch()
        vm.startLevel(8)
        repeat(3) { vm.removeOne() }

        vm.startLevel(3)

        assertNull(vm.savedGame.value)
        assertEquals(Levels.byId(3)!!.arrows.size, vm.state.value.arrows.size)
    }

    @Test
    fun `a corrupt save falls back to a clean copy of that level`() {
        unlockEverything()
        store.putString("saved_game", "3|8|3|0,1,99999|0|0|${Levels.LAYOUT_VERSION}")

        val vm = relaunch()
        vm.continueGame()

        assertEquals(GameStatus.PLAYING, vm.state.value.status)
        assertTrue(vm.state.value.arrows.isNotEmpty())
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertEquals(vm.state.value.level.arrows.size, vm.state.value.arrows.size)
    }

    /**
     * The Campaign layouts were redrawn, so a board saved against the old ones
     * (a version 2 record, which carries no layout version) must not be restored
     * — its ids would still be "in range" and would silently name different
     * arrows. The level starts clean; everything the player has earned stays.
     */
    @Test
    fun `a board saved against the old layouts restarts clean and keeps all progress`() {
        store.putString("player_progress", "2|9|8|1,2,3,4,5,6,7|1:3,2:2,5:1")
        // Level 8, three arrows left, ids that exist in the new Level 8 too.
        store.putString("saved_game", "2|8|2|0,1,2|1|0")

        val vm = relaunch()
        assertNull("an old-layout board must not be offered", vm.savedGame.value)

        vm.continueGame()

        assertEquals(8, vm.state.value.level.id)
        assertEquals(
            "the level should start with its full board",
            Levels.byId(8)!!.arrows.size,
            vm.state.value.arrows.size
        )
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)

        val progress = vm.playerProgress.value
        assertEquals(9, progress.highestUnlockedLevel)
        assertEquals((1..7).toSet(), progress.completedLevels)
        assertEquals(3, progress.starsFor(1))
        assertEquals(2, progress.starsFor(2))
        assertEquals(1, progress.starsFor(5))
    }

    @Test
    fun `a board saved against the current layouts is resumed exactly`() {
        unlockEverything()
        val first = relaunch()
        first.startLevel(8)
        repeat(3) { first.removeOne() }
        val remaining = first.state.value.arrows.map { it.id }.toSet()

        val restored = relaunch()
        restored.continueGame()

        assertEquals(8, restored.state.value.level.id)
        assertEquals(remaining, restored.state.value.arrows.map { it.id }.toSet())
    }

    @Test
    fun `continue resumes the saved level even when another one is selected`() {
        unlockEverything()
        val first = relaunch()
        first.startLevel(8)
        repeat(3) { first.removeOne() }
        val expected = first.state.value.arrows.map { it.id }.toSet()

        val restored = relaunch()
        restored.continueGame()

        assertEquals(8, restored.state.value.level.id)
        assertEquals(8, restored.playerProgress.value.currentLevel)
        assertEquals(expected, restored.state.value.arrows.map { it.id }.toSet())
    }

    @Test
    fun `an unknown level in state is not something the catalogue will resume`() {
        val stray = Level("stray", rows = 2, columns = 2, arrows = emptyList())
        assertEquals(1, LevelProgression.next(stray).id)
    }

    /** Marks every level complete so the tests below can open any of them. */
    private fun unlockEverything() {
        val repo = ProgressRepository(store)
        (1..30).forEach { repo.markCompleted(it) }
        repo.clearInProgress()
    }
}
