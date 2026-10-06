package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.ArrowTile
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.game.MoveValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the input-safety rules layered on top of the (unchanged) engine:
 * one tap costs at most one life, flights and blocked animations cannot be
 * re-triggered, and nothing can leave the board permanently locked.
 */
class GameViewModelTest {

    private val level = com.sabalapps.arrowescape.game.Levels.FIRST

    /** An arrow whose path out is blocked in the starting position. */
    private fun blockedArrow(vm: GameViewModel): ArrowTile =
        vm.state.value.arrows.first { !MoveValidator.canEscape(it, vm.state.value.arrows) }

    /** An arrow that can leave the board right now. */
    private fun freeArrow(vm: GameViewModel): ArrowTile =
        vm.state.value.arrows.first { MoveValidator.canEscape(it, vm.state.value.arrows) }

    @Test
    fun `rapid taps on a blocked arrow only cost one life`() {
        val vm = GameViewModel()
        val target = blockedArrow(vm)

        repeat(10) { vm.onArrowTapped(target.id) }

        assertEquals(GameState.STARTING_LIVES - 1, vm.state.value.lives)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
    }

    @Test
    fun `the blocked arrow becomes tappable again once its animation ends`() {
        val vm = GameViewModel()
        val target = blockedArrow(vm)

        vm.onArrowTapped(target.id)
        assertFalse(vm.isTappable(target.id))

        val nonce = requireNotNull(vm.blocked.value).nonce
        vm.onBlockedFeedbackFinished(nonce)

        assertTrue(vm.isTappable(target.id))
        vm.onArrowTapped(target.id)
        assertEquals(GameState.STARTING_LIVES - 2, vm.state.value.lives)
    }

    @Test
    fun `a stale animation callback cannot unlock the wrong arrow`() {
        val vm = GameViewModel()
        val target = blockedArrow(vm)
        vm.onArrowTapped(target.id)

        vm.onBlockedFeedbackFinished(nonce = 999)

        assertFalse(vm.isTappable(target.id))
        assertNotNull(vm.blocked.value)
    }

    @Test
    fun `other arrows stay responsive while one is locked`() {
        val vm = GameViewModel()
        val blocked = blockedArrow(vm)
        val free = freeArrow(vm)

        vm.onArrowTapped(blocked.id)
        assertFalse(vm.isTappable(blocked.id))
        assertTrue(vm.isTappable(free.id))

        vm.onArrowTapped(free.id)
        assertFalse(vm.state.value.arrows.any { it.id == free.id })
    }

    @Test
    fun `a successful move clears the blocked lock`() {
        val vm = GameViewModel()
        val blocked = blockedArrow(vm)
        vm.onArrowTapped(blocked.id)
        vm.onArrowTapped(freeArrow(vm).id)

        assertNull(vm.blocked.value)
        assertTrue(vm.isTappable(blocked.id))
    }

    @Test
    fun `an arrow in flight cannot be tapped again`() {
        val vm = GameViewModel()
        val free = freeArrow(vm)

        vm.onArrowTapped(free.id)
        assertEquals(1, vm.escaping.value.size)
        assertFalse(vm.isTappable(free.id))

        repeat(5) { vm.onArrowTapped(free.id) }
        assertEquals(1, vm.escaping.value.size)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
    }

    @Test
    fun `flight ids are unique so a late callback cannot cancel another flight`() {
        val vm = GameViewModel()
        val first = freeArrow(vm)
        vm.onArrowTapped(first.id)
        val second = freeArrow(vm)
        vm.onArrowTapped(second.id)

        val ids = vm.escaping.value.map { it.flightId }
        assertEquals(2, ids.toSet().size)

        vm.onEscapeAnimationFinished(ids.first())
        assertEquals(listOf(ids.last()), vm.escaping.value.map { it.flightId })
    }

    @Test
    fun `a callback from a previous level does not touch the current one`() {
        val vm = GameViewModel()
        vm.onArrowTapped(freeArrow(vm).id)
        val staleFlight = vm.escaping.value.single().flightId

        vm.restart()
        vm.onArrowTapped(freeArrow(vm).id)
        val currentFlight = vm.escaping.value.single().flightId

        vm.onEscapeAnimationFinished(staleFlight)

        assertEquals(listOf(currentFlight), vm.escaping.value.map { it.flightId })
    }

    @Test
    fun `restart clears locks, flights and lives`() {
        val vm = GameViewModel()
        val blocked = blockedArrow(vm)
        vm.onArrowTapped(blocked.id)
        vm.onArrowTapped(freeArrow(vm).id)

        vm.restart()

        assertNull(vm.blocked.value)
        assertTrue(vm.escaping.value.isEmpty())
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertEquals(level.arrows.size, vm.state.value.arrows.size)
        assertTrue(vm.state.value.arrows.all { vm.isTappable(it.id) })
    }

    @Test
    fun `taps are ignored once the game is over`() {
        val vm = GameViewModel()
        val target = blockedArrow(vm)

        repeat(GameState.STARTING_LIVES) {
            vm.onArrowTapped(target.id)
            vm.onBlockedFeedbackFinished(requireNotNull(vm.blocked.value).nonce)
        }
        assertEquals(GameStatus.LOST, vm.state.value.status)

        val before = vm.state.value
        vm.state.value.arrows.forEach { vm.onArrowTapped(it.id) }
        assertEquals(before, vm.state.value)
        assertTrue(vm.state.value.arrows.none { vm.isTappable(it.id) })
    }

    /**
     * Phase 3 gave "continue" somewhere to go. It still has to hand back a full
     * board with full lives and no animation left over — it is just the next
     * level's board now. (Level 2 is locked on a fresh ViewModel, so continue
     * stays put; either way the board must be whole.)
     */
    @Test
    fun `continue reloads a full board`() {
        val vm = GameViewModel()
        vm.onArrowTapped(freeArrow(vm).id)
        vm.continueToNextLevel()

        val loaded = vm.state.value.level
        assertEquals(loaded.arrows.size, vm.state.value.arrows.size)
        assertEquals(GameState.STARTING_LIVES, vm.state.value.lives)
        assertEquals(GameStatus.PLAYING, vm.state.value.status)
        assertTrue(vm.escaping.value.isEmpty())
    }
}
