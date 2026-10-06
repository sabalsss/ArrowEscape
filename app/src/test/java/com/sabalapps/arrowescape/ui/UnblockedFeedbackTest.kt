package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.MoveValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transient "your move opened these up" state: raised only by a successful
 * removal, never by a blocked tap, and gone whenever the board underneath it
 * changes. Nothing here is persisted and nothing here is a hint.
 */
class UnblockedFeedbackTest {

    private fun freeArrowId(vm: GameViewModel): Int =
        vm.state.value.arrows.first { MoveValidator.canEscape(it, vm.state.value.arrows) }.id

    private fun blockedArrowId(vm: GameViewModel): Int =
        vm.state.value.arrows.first { !MoveValidator.canEscape(it, vm.state.value.arrows) }.id

    /**
     * The arrows taking [id] off the board would open up: blocked now, free once
     * it is gone. Worked out from the board with the real rules, so these tests
     * describe *behaviour* rather than the ids of any one layout.
     */
    private fun freedBy(vm: GameViewModel, id: Int): Set<Int> {
        val board = vm.state.value.arrows
        val without = board.filterNot { it.id == id }
        return without
            .filter { MoveValidator.canEscape(it, without) && !MoveValidator.canEscape(it, board) }
            .map { it.id }
            .toSet()
    }

    /** A free arrow whose removal opens up at least one other. */
    private fun releasingArrowId(vm: GameViewModel): Int {
        val board = vm.state.value.arrows
        return board
            .filter { MoveValidator.canEscape(it, board) }
            .first { freedBy(vm, it.id).isNotEmpty() }
            .id
    }

    @Test
    fun `a successful removal reports the arrows it freed`() {
        val vm = GameViewModel()
        // Level 1 opens with free arrows that are holding others back; take one
        // and the pulse must name exactly the arrows it released.
        val before = vm.state.value.arrows
        val tapped = releasingArrowId(vm)
        val expected = freedBy(vm, tapped)

        vm.onArrowTapped(tapped)

        val pulse = requireNotNull(vm.unblocked.value)
        assertEquals(expected, pulse.tileIds)
        // Both were genuinely blocked a moment ago and are genuinely free now.
        for (id in pulse.tileIds) {
            assertTrue(!MoveValidator.canEscape(before.first { it.id == id }, before))
            val now = vm.state.value.arrows
            assertTrue(MoveValidator.canEscape(now.first { it.id == id }, now))
        }
    }

    @Test
    fun `a removal that frees nothing reports nothing`() {
        val vm = GameViewModel()
        // Play the board the honest way until a free arrow turns up that nobody
        // is waiting on — one that is in nobody's path — and take that one.
        var guard = 0
        while (true) {
            check(guard++ < 100) { "no arrow that frees nothing turned up" }
            val board = vm.state.value.arrows
            val free = board.filter { MoveValidator.canEscape(it, board) }
            val idle = free.firstOrNull { freedBy(vm, it.id).isEmpty() }
            if (idle != null) {
                vm.onArrowTapped(idle.id)
                break
            }
            vm.onArrowTapped(free.first().id)
        }

        assertNull(vm.unblocked.value)
    }

    @Test
    fun `a blocked tap raises no reaction`() {
        val vm = GameViewModel()

        vm.onArrowTapped(blockedArrowId(vm))

        assertNull(vm.unblocked.value)
        assertNotNull(vm.blocked.value)
    }

    @Test
    fun `a blocked tap clears the previous reaction`() {
        val vm = GameViewModel()
        vm.onArrowTapped(releasingArrowId(vm))
        assertNotNull(vm.unblocked.value)

        vm.onArrowTapped(blockedArrowId(vm))

        assertNull(vm.unblocked.value)
    }

    @Test
    fun `restart clears the reaction`() {
        val vm = GameViewModel()
        vm.onArrowTapped(releasingArrowId(vm))
        assertNotNull(vm.unblocked.value)

        vm.restart()

        assertNull(vm.unblocked.value)
    }

    @Test
    fun `changing mode clears the reaction`() {
        val vm = GameViewModel()
        vm.onArrowTapped(releasingArrowId(vm))
        assertNotNull(vm.unblocked.value)

        vm.startTutorial()

        assertNull(vm.unblocked.value)
        assertEquals(GameMode.Tutorial, vm.mode.value)
    }

    @Test
    fun `opening a generated board clears the reaction`() {
        val vm = GameViewModel()
        vm.onArrowTapped(releasingArrowId(vm))
        assertNotNull(vm.unblocked.value)

        vm.startEndless()

        assertNull(vm.unblocked.value)
    }

    @Test
    fun `a stale finish callback cannot clear a live reaction`() {
        val vm = GameViewModel()
        vm.onArrowTapped(releasingArrowId(vm))
        val first = requireNotNull(vm.unblocked.value).nonce

        // A second removal replaces the reaction; the first one's timer then
        // arrives late and must not take the new one down with it.
        vm.onEscapeAnimationFinished(0L)
        vm.onArrowTapped(releasingArrowId(vm))
        val second = requireNotNull(vm.unblocked.value).nonce
        assertTrue(second != first)

        vm.onUnblockedFeedbackFinished(first)
        assertNotNull(vm.unblocked.value)

        vm.onUnblockedFeedbackFinished(second)
        assertNull(vm.unblocked.value)
    }

    @Test
    fun `winning the board leaves no reaction behind`() {
        val vm = GameViewModel()
        var guard = 0
        while (vm.state.value.arrows.isNotEmpty()) {
            check(guard++ < 100)
            vm.onArrowTapped(freeArrowId(vm))
        }

        assertNull(vm.unblocked.value)
    }
}
