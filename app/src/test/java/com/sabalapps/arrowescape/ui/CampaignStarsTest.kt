package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.game.StarRating
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
 * Stars as the player earns them: the ViewModel driven end to end over a store
 * that survives being handed to a brand new ViewModel, which is what a process
 * death looks like from in here.
 *
 * Boards are always cleared the honest way — only ever tapping an arrow
 * [MoveValidator] says can leave — so a blocked tap in these tests is a
 * deliberate mistake and never an accident of the test.
 */
class CampaignStarsTest {

    private val store: ProgressStore = InMemoryProgressStore()

    private fun relaunch() = GameViewModel(ProgressRepository(store))

    private fun GameViewModel.clearLevel() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            check(guard++ < 500) { "level did not finish" }
            val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
            onArrowTapped(free.id)
            escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
        }
    }

    /** One deliberate mistake, with its animation stood down so the next tap lands. */
    private fun GameViewModel.tapBlocked() {
        val stuck = state.value.arrows.first { !MoveValidator.canEscape(it, state.value.arrows) }
        onArrowTapped(stuck.id)
        blocked.value?.let { onBlockedFeedbackFinished(it.nonce) }
    }

    // ---- the three tiers, earned on a real board ----------------------------

    @Test
    fun `a clean clear earns three stars and reads as a perfect escape`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()

        val result = vm.campaignStars.value
        assertNotNull(result)
        assertEquals(3, result!!.earned)
        assertTrue(result.isPerfect)
        assertTrue(result.isNewBest)
        assertEquals(3, vm.playerProgress.value.starsFor(1))
    }

    @Test
    fun `one blocked tap earns two stars`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.tapBlocked()
        vm.clearLevel()

        assertEquals(GameStatus.WON, vm.state.value.status)
        assertEquals(2, vm.campaignStars.value?.earned)
        assertFalse(vm.campaignStars.value!!.isPerfect)
        assertEquals(2, vm.playerProgress.value.starsFor(1))
    }

    @Test
    fun `two blocked taps earn one star`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.tapBlocked()
        vm.tapBlocked()
        vm.clearLevel()

        assertEquals(GameStatus.WON, vm.state.value.status)
        assertEquals(1, vm.campaignStars.value?.earned)
        assertEquals(1, vm.playerProgress.value.starsFor(1))
    }

    @Test
    fun `asking for a hint costs a star`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.requestHint()
        vm.clearLevel()

        assertEquals(2, vm.campaignStars.value?.earned)
    }

    @Test
    fun `asking for a hint twice costs no more than once`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.requestHint()
        vm.requestHint()
        vm.clearLevel()

        assertEquals(2, vm.campaignStars.value?.earned)
    }

    @Test
    fun `a hint on top of a blocked tap earns one star`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.tapBlocked()
        vm.requestHint()
        vm.clearLevel()

        assertEquals(1, vm.campaignStars.value?.earned)
    }

    // ---- best, not last -----------------------------------------------------

    @Test
    fun `replaying worse does not take a better result away`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()
        assertEquals(3, vm.playerProgress.value.starsFor(1))

        vm.replayCurrent()
        vm.tapBlocked()
        vm.tapBlocked()
        vm.clearLevel()

        assertEquals(1, vm.campaignStars.value?.earned)
        assertEquals(3, vm.campaignStars.value?.best)
        assertFalse(vm.campaignStars.value!!.isNewBest)
        assertEquals(3, vm.playerProgress.value.starsFor(1))
    }

    @Test
    fun `replaying better raises the result`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.tapBlocked()
        vm.tapBlocked()
        vm.clearLevel()
        assertEquals(1, vm.playerProgress.value.starsFor(1))

        vm.replayCurrent()
        vm.clearLevel()

        assertEquals(3, vm.campaignStars.value?.earned)
        assertTrue(vm.campaignStars.value!!.isNewBest)
        assertEquals(3, vm.playerProgress.value.starsFor(1))
    }

    @Test
    fun `stars are still there after a relaunch`() {
        relaunch().apply {
            startLevel(1)
            clearLevel()
        }

        assertEquals(3, relaunch().playerProgress.value.starsFor(1))
    }

    // ---- the run tally ------------------------------------------------------

    @Test
    fun `restarting a level starts the tally over`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.tapBlocked()
        vm.tapBlocked()
        vm.restart()
        vm.clearLevel()

        assertEquals(3, vm.campaignStars.value?.earned)
    }

    @Test
    fun `moving on to the next level starts the tally over`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.tapBlocked()
        vm.clearLevel()
        assertEquals(2, vm.campaignStars.value?.earned)

        vm.continueAfterWin()
        assertNull(vm.campaignStars.value)
        vm.clearLevel()

        assertEquals(3, vm.campaignStars.value?.earned)
        assertEquals(2, vm.playerProgress.value.starsFor(1))
        assertEquals(3, vm.playerProgress.value.starsFor(2))
    }

    @Test
    fun `a mistake survives being killed mid level`() {
        relaunch().apply {
            startLevel(1)
            tapBlocked()
            // Leave the board half-played: one arrow away and then walk off.
            val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
            onArrowTapped(free.id)
        }

        val resumed = relaunch()
        resumed.continueGame()
        resumed.clearLevel()

        // The blocked tap is still on the record, so this is not a clean run.
        assertEquals(2, resumed.campaignStars.value?.earned)
    }

    @Test
    fun `a hint survives being killed mid level`() {
        relaunch().apply {
            startLevel(1)
            requestHint()
            val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
            onArrowTapped(free.id)
        }

        val resumed = relaunch()
        resumed.continueGame()
        resumed.clearLevel()

        assertEquals(2, resumed.campaignStars.value?.earned)
    }

    // ---- nothing else is rated ----------------------------------------------

    @Test
    fun `running out of lives earns nothing`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.tapBlocked()
        vm.tapBlocked()
        vm.tapBlocked()

        assertEquals(GameStatus.LOST, vm.state.value.status)
        assertNull(vm.campaignStars.value)
        assertEquals(StarRating.NONE, vm.playerProgress.value.starsFor(1))
    }

    @Test
    fun `an endless puzzle is not rated`() {
        val vm = relaunch()
        vm.startEndless()
        vm.clearLevel()

        assertEquals(GameStatus.WON, vm.state.value.status)
        assertNull(vm.campaignStars.value)
        assertTrue(vm.playerProgress.value.bestStars.isEmpty())
    }

    @Test
    fun `the daily challenge is not rated`() {
        val vm = relaunch()
        vm.startDaily()
        vm.clearLevel()

        assertEquals(GameStatus.WON, vm.state.value.status)
        assertNull(vm.campaignStars.value)
        assertTrue(vm.playerProgress.value.bestStars.isEmpty())
    }

    @Test
    fun `replaying the tutorial from settings rates nothing and touches no level`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()
        assertEquals(3, vm.playerProgress.value.starsFor(1))

        vm.startTutorial()
        vm.tapBlocked()
        vm.clearLevel()

        // The lesson writes nothing at all, so Level 1 keeps the run it earned.
        assertNull(vm.campaignStars.value)
        assertEquals(3, vm.playerProgress.value.starsFor(1))
    }

    @Test
    fun `the first run of the tutorial is Campaign Level 1 and is rated like one`() {
        // A fresh install opens Level 1 with the lesson over it. That board is
        // genuine Campaign Level 1, so clearing it earns stars.
        val vm = relaunch()
        vm.continueGame()
        assertTrue(vm.tutorial.value.isActive)
        vm.clearLevel()

        assertEquals(3, vm.campaignStars.value?.earned)
        assertEquals(3, vm.playerProgress.value.starsFor(1))
    }
}
