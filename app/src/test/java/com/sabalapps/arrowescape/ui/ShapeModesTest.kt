package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.shape.MysteryShapePuzzles
import com.sabalapps.arrowescape.shape.MysteryShapes
import com.sabalapps.arrowescape.shape.ShapeMask
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mystery-shape promise, through the real ViewModel: **no board in play can say what it is**,
 * and a won board names exactly the picture it was built to — plus the repeat control that keeps
 * Endless from serving the same picture back to back.
 */
class ShapeModesTest {

    private val mainStore: ProgressStore = InMemoryProgressStore()
    private val dailyStore: ProgressStore = InMemoryProgressStore()
    private val clock = FixedDateProvider(GameDate(2026, 10, 4))
    private var seedCounter = 1_000L

    private fun launch(seeds: () -> Long = { seedCounter++ * 7919 }): GameViewModel = GameViewModel(
        progress = ProgressRepository(mainStore),
        endless = EndlessRepository(mainStore, newSeed = seeds),
        daily = DailyRepository(dailyStore, clock)
    )

    private fun GameViewModel.clearBoard() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            val free = state.value.arrows.first { MoveValidator.canEscape(it, state.value.arrows) }
            onArrowTapped(free.id)
            escaping.value.forEach { onEscapeAnimationFinished(it.flightId) }
            check(guard++ < 200)
        }
    }

    private fun GameViewModel.loseBoard() {
        var guard = 0
        while (state.value.status == GameStatus.PLAYING) {
            val blocked = state.value.arrows.first { !MoveValidator.canEscape(it, state.value.arrows) }
            onArrowTapped(blocked.id)
            this.blocked.value?.let { onBlockedFeedbackFinished(it.nonce) }
            check(guard++ < 20)
        }
    }

    private fun GameViewModel.everythingShownDuringPlay(): String = listOf(
        mode.value.toString(),
        state.value.level.name,
        state.value.toString(),
        hint.value.toString(),
        blocked.value.toString(),
        unblocked.value.toString(),
        escaping.value.toString(),
        tutorial.value.toString(),
        campaignStars.value.toString(),
        campaignDiscovery.value.toString()
    ).joinToString("\n")

    private val allNames = MysteryShapes.all.map { it.name }

    // ---- Daily ----------------------------------------------------------------

    @Test
    fun `an unsolved daily says nothing about what it is`() {
        val vm = launch()
        vm.startDaily()
        assertNull(vm.shapeReveal.value)
        val shown = vm.everythingShownDuringPlay()
        for (name in allNames) assertFalse("'$name' leaked into: $shown", shown.contains(name, ignoreCase = true))
        // Still nothing half way through, or after asking for a hint.
        repeat(5) {
            val free = vm.state.value.arrows.first { MoveValidator.canEscape(it, vm.state.value.arrows) }
            vm.onArrowTapped(free.id)
            vm.escaping.value.forEach { vm.onEscapeAnimationFinished(it.flightId) }
        }
        vm.requestHint()
        assertNull(vm.shapeReveal.value)
        for (name in allNames) assertFalse(vm.everythingShownDuringPlay().contains(name, ignoreCase = true))
    }

    @Test
    fun `a solved daily names the day's picture, and only then`() {
        val vm = launch()
        vm.startDaily()
        val expected = DailyChallenge.shapeFor(clock.today()).template
        assertNull(vm.shapeReveal.value)
        vm.clearBoard()
        val reveal = assertNotNull(vm.shapeReveal.value).let { vm.shapeReveal.value!! }
        assertEquals(expected.name, reveal.name)
        assertEquals(expected.id, reveal.templateId)
    }

    @Test
    fun `a lost daily reveals nothing`() {
        val vm = launch()
        vm.startDaily()
        vm.loseBoard()
        assertEquals(GameStatus.LOST, vm.state.value.status)
        assertNull(vm.shapeReveal.value)
    }

    @Test
    fun `retrying a lost daily starts hidden again, and a replay after a win hides the name until it is won again`() {
        val vm = launch()
        vm.startDaily()
        vm.loseBoard()
        vm.replayCurrent()
        assertNull(vm.shapeReveal.value)
        vm.clearBoard()
        assertNotNull(vm.shapeReveal.value)
        vm.replayCurrent()
        assertNull("the name must hide again when the board is replayed", vm.shapeReveal.value)
        vm.clearBoard()
        assertNotNull(vm.shapeReveal.value)
    }

    @Test
    fun `the daily board is the same board after the process dies`() {
        val first = launch()
        first.startDaily()
        val before = first.state.value.arrows.map { Triple(it.row, it.col, it.direction) }
        val second = launch()
        second.startDaily()
        assertEquals(before, second.state.value.arrows.map { Triple(it.row, it.col, it.direction) })
        assertEquals(DailyChallenge.shapeFor(clock.today()).mask, ShapeMask.of(second.state.value.level))
    }

    @Test
    fun `solving the daily moves the streak once and a replay leaves it alone`() {
        val vm = launch()
        vm.startDaily()
        vm.clearBoard()
        assertEquals(1, vm.dailyProgress.value.currentStreak)
        vm.replayCurrent()
        vm.clearBoard()
        assertEquals(1, vm.dailyProgress.value.currentStreak)
        assertEquals(1, vm.dailyProgress.value.totalCompleted)
        assertTrue((vm.mode.value as GameMode.Daily).alreadyCleared)
    }

    // ---- Endless --------------------------------------------------------------

    @Test
    fun `an unsolved endless puzzle says nothing about what it is`() {
        val vm = launch()
        vm.startEndless()
        assertNull(vm.shapeReveal.value)
        val shown = vm.everythingShownDuringPlay()
        for (name in allNames) assertFalse("'$name' leaked into: $shown", shown.contains(name, ignoreCase = true))
    }

    @Test
    fun `a solved endless puzzle names the picture its seed chose`() {
        val vm = launch()
        vm.startEndless()
        val mode = vm.mode.value as GameMode.Endless
        val expected = MysteryShapePuzzles.choose(mode.seed, mode.tier).template
        vm.clearBoard()
        assertEquals(expected.name, vm.shapeReveal.value?.name)
        assertEquals(expected.mask.cellCount, vm.state.value.level.arrows.size.also { })
    }

    @Test
    fun `the next endless puzzle hides the name again`() {
        val vm = launch()
        vm.startEndless()
        vm.clearBoard()
        assertNotNull(vm.shapeReveal.value)
        vm.nextEndlessPuzzle()
        assertNull(vm.shapeReveal.value)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
    }

    @Test
    fun `a lost endless puzzle reveals nothing`() {
        val vm = launch()
        vm.startEndless()
        vm.loseBoard()
        assertNull(vm.shapeReveal.value)
    }

    @Test
    fun `endless never serves the same picture twice running`() {
        val vm = launch()
        vm.startEndless()
        var previous = (vm.mode.value as GameMode.Endless).let { MysteryShapePuzzles.templateIdFor(it.seed, it.tier) }
        repeat(160) {
            vm.clearBoard()
            vm.nextEndlessPuzzle()
            val mode = vm.mode.value as GameMode.Endless
            val id = MysteryShapePuzzles.templateIdFor(mode.seed, mode.tier)
            assertTrue("puzzle ${mode.puzzleNumber} repeated $id", id != previous)
            previous = id
        }
    }

    @Test
    fun `endless keeps a few pictures between repeats`() {
        val vm = launch()
        vm.startEndless()
        val seen = ArrayList<String>()
        repeat(120) {
            val mode = vm.mode.value as GameMode.Endless
            seen += MysteryShapePuzzles.templateIdFor(mode.seed, mode.tier)
            vm.clearBoard()
            vm.nextEndlessPuzzle()
        }
        // Within any window of four consecutive puzzles, no picture twice.
        for (window in seen.windowed(4)) {
            assertEquals("a picture came back within four puzzles: $window", window.size, window.toSet().size)
        }
    }

    @Test
    fun `a seed source that cannot vary still terminates`() {
        val vm = launch(seeds = { 42L })
        vm.startEndless()
        vm.clearBoard()
        vm.nextEndlessPuzzle() // the repeat control gives up after a bounded number of tries
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
    }

    @Test
    fun `a resumed endless board is the board that was left`() {
        val first = launch()
        first.startEndless()
        val seed = (first.mode.value as GameMode.Endless).seed
        val removed = first.state.value.arrows.first { MoveValidator.canEscape(it, first.state.value.arrows) }
        first.onArrowTapped(removed.id)
        first.escaping.value.forEach { first.onEscapeAnimationFinished(it.flightId) }
        val second = launch()
        second.startEndless()
        assertEquals(seed, (second.mode.value as GameMode.Endless).seed)
        assertEquals(first.state.value.arrows.map { it.id }.toSet(), second.state.value.arrows.map { it.id }.toSet())
        assertNull(second.shapeReveal.value)
    }

    @Test
    fun `every tier builds a shape board through the viewmodel path`() {
        for (tier in EndlessTier.entries) {
            val seeds = (1L..6L).map { it * 7919 }
            for (seed in seeds) {
                val puzzle = MysteryShapePuzzles.generate(seed, tier)
                assertNotNull(puzzle.shape)
                assertTrue(puzzle.onTier)
                assertEquals(MysteryShapePuzzles.choose(seed, tier).mask, ShapeMask.of(puzzle.level))
            }
        }
    }

    // ---- the other modes publish no shape ---------------------------------------

    @Test
    fun `campaign and the tutorial never publish a mystery shape`() {
        val vm = launch()
        vm.startLevel(1)
        vm.clearBoard()
        assertNull(vm.shapeReveal.value)
        assertNotNull(vm.campaignDiscovery.value)
        vm.startTutorial()
        vm.clearBoard()
        assertNull(vm.shapeReveal.value)
    }

    // ---- spoilers in the Campaign, and no double writes ---------------------------------

    @Test
    fun `a campaign board in play says nothing about what it is`() {
        val names = com.sabalapps.arrowescape.ui.world.CampaignDiscoveries.all.flatMap { listOf(it.name, it.artKey) }
        for (level in listOf(1, 1)) {
            val vm = launch()
            vm.startLevel(level)
            assertNull(vm.campaignDiscovery.value)
            val shown = vm.everythingShownDuringPlay()
            for (name in names) assertFalse("'$name' leaked into: $shown", shown.contains(name, ignoreCase = true))
        }
        // Part way through a level, after a hint, after a blocked tap: still nothing.
        val vm = launch()
        vm.startLevel(1)
        vm.requestHint()
        val blocked = vm.state.value.arrows.first { !MoveValidator.canEscape(it, vm.state.value.arrows) }
        vm.onArrowTapped(blocked.id)
        val shown = vm.everythingShownDuringPlay()
        for (name in names) assertFalse(shown.contains(name, ignoreCase = true))
        assertNull(vm.campaignDiscovery.value)
    }

    @Test
    fun `a lost campaign board reveals no discovery`() {
        val vm = launch()
        vm.startLevel(1)
        vm.loseBoard()
        assertNull(vm.campaignDiscovery.value)
        assertNull(vm.shapeReveal.value)
    }

    @Test
    fun `after a win nothing more can be tapped, so nothing is written twice`() {
        val vm = launch()
        vm.startDaily()
        vm.clearBoard()
        val total = vm.dailyProgress.value.totalCompleted
        val streak = vm.dailyProgress.value.currentStreak
        val reveal = vm.shapeReveal.value
        for (id in 0..40) vm.onArrowTapped(id)
        assertEquals(total, vm.dailyProgress.value.totalCompleted)
        assertEquals(streak, vm.dailyProgress.value.currentStreak)
        assertEquals(reveal, vm.shapeReveal.value)
        assertEquals(GameStatus.WON, vm.state.value.status)

        val endless = launch()
        endless.startEndless()
        endless.clearBoard()
        val done = endless.endlessProgress.value.totalCompleted
        for (id in 0..40) endless.onArrowTapped(id)
        assertEquals(done, endless.endlessProgress.value.totalCompleted)
    }
}
