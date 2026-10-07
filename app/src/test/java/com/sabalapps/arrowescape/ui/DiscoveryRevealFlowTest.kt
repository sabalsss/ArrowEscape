package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.ui.world.GameWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reveal as the ViewModel hands it to the screen: a Campaign win publishes what
 * the level was hiding, whether that is new, and a collection count that already
 * includes the clear — and does so *after* the win has been saved, so the
 * presentation can never be what a completion depends on.
 *
 * Boards are cleared the honest way: only ever tapping an arrow the real
 * [MoveValidator] says can leave.
 */
class DiscoveryRevealFlowTest {

    private var store: ProgressStore = InMemoryProgressStore()

    /** A ViewModel over the same bytes — the app after a cold start. */
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

    private fun GameViewModel.tapBlocked() {
        val stuck = state.value.arrows.first { !MoveValidator.canEscape(it, state.value.arrows) }
        onArrowTapped(stuck.id)
        blocked.value?.let { onBlockedFeedbackFinished(it.nonce) }
    }

    /** Marks levels 1..[through] cleared on disk, as if the player had played them. */
    private fun clearedThrough(through: Int) {
        val repo = ProgressRepository(store)
        (1..through).forEach { repo.markCompleted(it, 2) }
    }

    // ---- B. first clear versus replay ----------------------------------------------

    @Test
    fun `the first clear of a level is a new discovery`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()

        val result = vm.campaignDiscovery.value
        assertNotNull(result)
        assertEquals("Heart", result!!.discovery.name)
        assertTrue(result.isFirstClear)
        assertEquals("NEW DISCOVERY!", result.headline)
    }

    @Test
    fun `replaying a cleared level is a discovery found, never new again`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()
        assertTrue(vm.campaignDiscovery.value!!.isFirstClear)

        vm.replayCurrent()
        assertNull("a fresh run starts with no result", vm.campaignDiscovery.value)
        vm.clearLevel()

        val again = vm.campaignDiscovery.value
        assertNotNull(again)
        assertFalse(again!!.isFirstClear)
        assertEquals("DISCOVERY FOUND", again.headline)
        assertEquals("Heart", again.discovery.name)
    }

    @Test
    fun `revisiting a level cleared in an earlier session is not new either`() {
        clearedThrough(5)
        val vm = relaunch()
        vm.startLevel(3)
        vm.clearLevel()

        assertFalse(vm.campaignDiscovery.value!!.isFirstClear)
    }

    @Test
    fun `losing a level reveals nothing, and the win after the retry is still the first discovery`() {
        val vm = relaunch()
        vm.startLevel(1)
        repeat(3) { vm.tapBlocked() }
        assertEquals(GameStatus.LOST, vm.state.value.status)
        assertNull(vm.campaignDiscovery.value)

        vm.replayCurrent()
        vm.clearLevel()

        assertTrue(vm.campaignDiscovery.value!!.isFirstClear)
    }

    @Test
    fun `the discovery does not depend on how well the level was solved`() {
        val perfect = relaunch().also { it.startLevel(1); it.clearLevel() }
        assertEquals(3, perfect.campaignStars.value!!.earned)
        assertEquals("Heart", perfect.campaignDiscovery.value!!.discovery.name)

        store = InMemoryProgressStore()
        val sloppy = relaunch()
        sloppy.startLevel(1)
        sloppy.tapBlocked()
        sloppy.tapBlocked()
        sloppy.clearLevel()
        assertEquals(1, sloppy.campaignStars.value!!.earned)
        assertEquals("Heart", sloppy.campaignDiscovery.value!!.discovery.name)
        assertTrue(sloppy.campaignDiscovery.value!!.isFirstClear)
    }

    // ---- the win is saved first ----------------------------------------------------

    @Test
    fun `the win is on disk the instant the last arrow goes, before any animation finishes`() {
        val vm = relaunch()
        vm.startLevel(1)
        while (vm.state.value.status == GameStatus.PLAYING) {
            val free = vm.state.value.arrows.first { MoveValidator.canEscape(it, vm.state.value.arrows) }
            vm.onArrowTapped(free.id) // no onEscapeAnimationFinished: nothing waits for a flight
        }

        assertEquals(GameStatus.WON, vm.state.value.status)
        // A cold start right now — mid-reveal, as if the process were killed — still
        // has the completion, the stars and the next level.
        val afterKill = relaunch().playerProgress.value
        assertTrue(afterKill.isCompleted(1))
        assertEquals(3, afterKill.starsFor(1))
        assertEquals(2, afterKill.highestUnlockedLevel)
    }

    @Test
    fun `the discovery is published only after the completion was written`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()

        assertNotNull(vm.campaignDiscovery.value)
        assertTrue(vm.playerProgress.value.isCompleted(1))
    }

    // ---- H. the count includes the clear ---------------------------------------------

    @Test
    fun `the result's collection count already includes the level just cleared`() {
        clearedThrough(9) // Forest holds 3 of 6 (levels 7, 8, 9)
        val vm = relaunch()
        vm.startLevel(10)
        vm.clearLevel()

        val result = vm.campaignDiscovery.value!!
        assertEquals("Butterfly", result.discovery.name)
        assertEquals(GameWorld.FOREST, result.world)
        assertEquals(4, result.worldCollected)
        assertEquals(6, result.worldTotal)
        assertEquals(10, result.totalCollected)
        assertEquals("4 / 6 DISCOVERED", result.progressLabel)
    }

    @Test
    fun `a replay does not count the level a second time`() {
        clearedThrough(10)
        val vm = relaunch()
        vm.startLevel(10)
        vm.clearLevel()

        val result = vm.campaignDiscovery.value!!
        assertFalse(result.isFirstClear)
        assertEquals(4, result.worldCollected)
        assertEquals(10, result.totalCollected)
    }

    // ---- G. the last level ------------------------------------------------------------

    @Test
    fun `clearing the last level completes the album`() {
        clearedThrough(29)
        val vm = relaunch()
        vm.startLevel(30)
        vm.clearLevel()

        val result = vm.campaignDiscovery.value!!
        assertEquals("Galaxy", result.discovery.name)
        assertTrue(result.isFirstClear)
        assertTrue(result.worldComplete)
        assertTrue(result.allFound)
        assertEquals(30, result.totalCollected)
    }

    // ---- the run's result goes when the run does ---------------------------------------

    @Test
    fun `opening another board clears the previous result`() {
        val vm = relaunch()
        vm.startLevel(1)
        vm.clearLevel()
        assertNotNull(vm.campaignDiscovery.value)

        vm.continueToNextLevel()

        assertNull(vm.campaignDiscovery.value)
        assertNull(vm.campaignStars.value)
    }

    // ---- other modes have no discoveries -------------------------------------------------

    @Test
    fun `endless and daily wins reveal nothing`() {
        val endless = relaunch()
        endless.startEndless()
        endless.clearLevel()
        assertEquals(GameStatus.WON, endless.state.value.status)
        assertNull(endless.campaignDiscovery.value)

        val daily = relaunch()
        daily.startDaily()
        daily.clearLevel()
        assertEquals(GameStatus.WON, daily.state.value.status)
        assertNull(daily.campaignDiscovery.value)
    }

    @Test
    fun `the replayed tutorial reveals nothing and discovers nothing`() {
        val vm = relaunch()
        vm.startTutorial()
        vm.clearLevel()

        assertEquals(GameStatus.WON, vm.state.value.status)
        assertNull(vm.campaignDiscovery.value)
        assertTrue(vm.playerProgress.value.completedLevels.isEmpty())
    }

    @Test
    fun `the very first run of the tutorial is a real Campaign Level 1 and does discover the Heart`() {
        val vm = relaunch()
        vm.continueGame() // first launch: Level 1 with the lesson over it
        vm.clearLevel()

        assertEquals("Heart", vm.campaignDiscovery.value!!.discovery.name)
        assertTrue(vm.campaignDiscovery.value!!.isFirstClear)
    }
}
