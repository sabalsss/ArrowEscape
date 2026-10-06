package com.sabalapps.arrowescape.game

import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.PuzzleGenerator
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A hint has one hard rule — it must never point at an arrow the player cannot
 * remove — and one soft one: the same board should always produce the same
 * suggestion. Both are checked here on hand-written fixtures and then at scale
 * across every board the game can actually hand a player.
 */
class HintEngineTest {

    private fun board(vararg layout: String): List<ArrowTile> =
        Levels.fromLayout(name = "fixture", layout = layout.toList()).arrows

    private fun hintOn(vararg layout: String): ArrowTile? = HintEngine.hint(board(*layout))

    // ---- the hard rule -------------------------------------------------------

    @Test
    fun `a hint is always an arrow that can leave right now`() {
        val board = board("^...", "^...", "^...", ">>.<")
        val hint = requireNotNull(HintEngine.hint(board))
        assertTrue(
            "the hint at (${hint.row}, ${hint.col}) is blocked",
            MoveValidator.canEscape(hint, board)
        )
    }

    @Test
    fun `a hint never points at a blocked arrow on any campaign level`() {
        // Every position reachable by playing every level the honest way: after
        // each removal, the hint has to still be a legal move.
        for (level in Levels.ALL) {
            var live = level.arrows
            var guard = 0
            while (live.isNotEmpty()) {
                val hint = requireNotNull(HintEngine.hint(live)) {
                    "Level ${level.id} stalled with ${live.size} arrows and no hint"
                }
                assertTrue(
                    "Level ${level.id}: hinted a blocked arrow (id ${hint.id})",
                    MoveValidator.canEscape(hint, live)
                )
                live = live.filterNot { it.id == hint.id }
                check(guard++ < 200) { "Level ${level.id} did not terminate" }
            }
        }
    }

    @Test
    fun `playing only the hinted arrow clears every campaign level`() {
        // A consequence of the rule above plus the board being solvable, but
        // worth stating directly: a player who only ever taps Hint and then taps
        // the arrow it points at never gets stuck.
        for (level in Levels.ALL) {
            var state = GameState.newGame(level)
            var guard = 0
            while (state.status == GameStatus.PLAYING) {
                val hint = requireNotNull(HintEngine.hint(state.arrows))
                val (next, result) = state.onArrowTapped(hint.id)
                assertTrue(
                    "Level ${level.id}: following the hint cost a life",
                    result is TapResult.Escaped
                )
                state = next
                check(guard++ < 200) { "Level ${level.id} did not terminate" }
            }
            assertEquals("Level ${level.id} was not cleared", GameStatus.WON, state.status)
            assertEquals(GameState.STARTING_LIVES, state.lives)
        }
    }

    @Test
    fun `a hint never points at a blocked arrow on a generated board`() {
        for (tier in EndlessTier.entries) {
            for (seed in 1L..30L) {
                var live = PuzzleGenerator.generate(seed, tier).level.arrows
                var guard = 0
                while (live.isNotEmpty()) {
                    val hint = requireNotNull(HintEngine.hint(live)) {
                        "tier=$tier seed=$seed stalled with ${live.size} arrows"
                    }
                    assertTrue(
                        "tier=$tier seed=$seed: hinted a blocked arrow",
                        MoveValidator.canEscape(hint, live)
                    )
                    live = live.filterNot { it.id == hint.id }
                    check(guard++ < 200) { "tier=$tier seed=$seed did not terminate" }
                }
            }
        }
    }

    @Test
    fun `a hint never points at a blocked arrow on a daily board`() {
        for (day in 0L until 60L) {
            val date = GameDate(2026, 1, 1).plusDays(day)
            var live = DailyChallenge.generate(date).level.arrows
            var guard = 0
            while (live.isNotEmpty()) {
                val hint = requireNotNull(HintEngine.hint(live)) {
                    "${date.iso} stalled with ${live.size} arrows"
                }
                assertTrue("${date.iso}: hinted a blocked arrow", MoveValidator.canEscape(hint, live))
                live = live.filterNot { it.id == hint.id }
                check(guard++ < 200) { "${date.iso} did not terminate" }
            }
        }
    }

    // ---- determinism ---------------------------------------------------------

    @Test
    fun `the same board always produces the same hint`() {
        val board = board(">..v>>", "......", ">.v.v.", ">..v..", "^.>v>.", "<.<<>v")
        val first = HintEngine.hint(board)
        repeat(20) { assertEquals(first, HintEngine.hint(board)) }
    }

    @Test
    fun `the hint does not depend on the order the board is listed in`() {
        // The board is a list, but it describes a set of positions. A hint that
        // changed when the list was shuffled would not be a function of the
        // board at all.
        val board = board("^<..<", "....>", ".....", ".^..<", "vvv<.", "vv>.^")
        val expected = HintEngine.hint(board)
        assertEquals(expected, HintEngine.hint(board.reversed()))
        assertEquals(expected, HintEngine.hint(board.sortedBy { it.col }))
        assertEquals(expected, HintEngine.hint(board.sortedByDescending { it.id }))
    }

    // ---- which free arrow ----------------------------------------------------

    @Test
    fun `the hint prefers the arrow that unblocks the most others`() {
        // Column 0 is a stack of four arrows pointing up: removing the top one
        // frees exactly one more. The arrow at the right of row 0 points off the
        // edge and frees nothing. The useful move is the one that is picked.
        val hint = requireNotNull(hintOn("^..>", "^...", "^...", "^..."))
        assertEquals("the top of the stack is the arrow that frees something", 0, hint.row)
        assertEquals(0, hint.col)
    }

    @Test
    fun `the lowest id breaks a tie between equally useful arrows`() {
        // Four arrows all free, none blocking anything: nothing to choose
        // between them but the id, and ids run in reading order.
        val hint = requireNotNull(hintOn("^^^^"))
        assertEquals(0, hint.id)
    }

    @Test
    fun `a board with exactly one legal move is hinted at that move`() {
        // A column of five arrows pointing up, each blocked by the one above,
        // under a single arrow pointing out to the left. That left arrow is the
        // only thing on the board that can move, so there is nothing for the
        // choice to get wrong — which is what makes it worth checking.
        val board = board("<.....", "^.....", "^.....", "^.....", "^.....", "^.....")
        assertEquals(
            "only one arrow is free",
            1,
            board.count { MoveValidator.canEscape(it, board) }
        )

        val hint = requireNotNull(HintEngine.hint(board))
        assertEquals(Direction.LEFT, hint.direction)
        assertEquals(0, hint.row)
        assertEquals(0, hint.col)
    }

    // ---- the edges -----------------------------------------------------------

    @Test
    fun `an empty board has nothing to hint at`() {
        assertNull(HintEngine.hint(emptyList()))
    }

    @Test
    fun `a single arrow is its own hint`() {
        val hint = requireNotNull(hintOn("..^."))
        assertEquals(0, hint.id)
    }

    @Test
    fun `a board with no free arrow is handled rather than crashing`() {
        // Unreachable in play — nothing the game ships can get here — but a hint
        // must never be able to throw.
        assertNull(HintEngine.hint(board("><..")))
        assertNull(HintEngine.hint(board("v...", "^...")))
    }

    @Test
    fun `every partially cleared campaign board still has a hint`() {
        for (level in Levels.ALL) {
            var live = level.arrows
            while (live.isNotEmpty()) {
                assertNotNull("Level ${level.id} with ${live.size} arrows had no hint", HintEngine.hint(live))
                val next = requireNotNull(HintEngine.hint(live))
                live = live.filterNot { it.id == next.id }
            }
        }
    }
}
