package com.sabalapps.arrowescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard on the level catalogue. Every check here runs over all 30 levels,
 * so a level added or edited later cannot ship broken: an unsolvable layout,
 * an arrow off the board, two arrows in one cell or a duplicate id all fail
 * the build rather than reaching a player.
 */
class LevelCatalogueTest {

    private val levels = Levels.ALL

    @Test
    fun `the catalogue has thirty levels, numbered one to thirty in order`() {
        assertEquals(30, levels.size)
        assertEquals((1..30).toList(), levels.map { it.id })
        levels.forEach { assertEquals("Level ${it.id}", it.name) }
    }

    @Test
    fun `every level is solvable, and the solution is replayed to prove it`() {
        levels.forEach { level ->
            when (val outcome = LevelSolver.solve(level)) {
                is LevelSolver.Outcome.Solved ->
                    assertTrue(
                        "${level.name}: the solver returned an order that is not legal",
                        LevelSolver.verify(level, outcome.order)
                    )

                LevelSolver.Outcome.Unsolvable ->
                    throw AssertionError("${level.name} cannot be cleared - no removal order exists")

                LevelSolver.Outcome.Exhausted ->
                    throw AssertionError(
                        "${level.name} exhausted the solver's node budget; it is too tangled to " +
                            "prove solvable and should be redesigned"
                    )
            }
        }
    }

    @Test
    fun `every level has at least one arrow`() {
        levels.forEach {
            assertTrue("${it.name} is empty", it.arrows.isNotEmpty())
        }
    }

    @Test
    fun `arrow ids are unique within a level`() {
        levels.forEach { level ->
            val ids = level.arrows.map { it.id }
            assertEquals("${level.name} has duplicate arrow ids", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `no two arrows share a cell`() {
        levels.forEach { level ->
            val cells = level.arrows.map { it.row to it.col }
            assertEquals("${level.name} stacks two arrows in one cell", cells.size, cells.toSet().size)
        }
    }

    @Test
    fun `every arrow is inside the board`() {
        levels.forEach { level ->
            level.arrows.forEach { arrow ->
                assertTrue(
                    "${level.name}: arrow ${arrow.id} at (${arrow.row}, ${arrow.col}) is off the board",
                    arrow.row in 0 until level.rows && arrow.col in 0 until level.columns
                )
            }
        }
    }

    /**
     * Six columns is the touch-target budget, not a style choice: on a 320dp-wide
     * phone the board gets 292dp of width, so a seventh column would push cells
     * under the 48dp minimum tap target. Difficulty goes into rows instead.
     */
    @Test
    fun `board dimensions are sane and playable on a phone`() {
        levels.forEach { level ->
            assertTrue("${level.name} has a non-positive size", level.rows > 0 && level.columns > 0)
            assertTrue(
                "${level.name} has ${level.columns} columns; tap targets need at most $MAX_COLUMNS",
                level.columns <= MAX_COLUMNS
            )
            assertTrue(
                "${level.name} has ${level.rows} rows; the board stops fitting past $MAX_ROWS",
                level.rows <= MAX_ROWS
            )
            assertTrue(
                "${level.name} has more arrows than cells",
                level.arrows.size <= level.rows * level.columns
            )
        }
    }

    @Test
    fun `every level opens with at least one legal move`() {
        levels.forEach { level ->
            assertTrue(
                "${level.name} starts deadlocked",
                LevelSolver.initiallyValidMoves(level) >= 1
            )
        }
    }

    /**
     * A level where most arrows are already free is a tapping exercise, not a
     * puzzle. The first two levels are deliberately gentle, so they get a looser
     * bound than the rest.
     */
    @Test
    fun `no level is trivial - most arrows start blocked`() {
        levels.forEach { level ->
            val free = LevelSolver.initiallyValidMoves(level)
            val ceiling = if (level.id <= 2) 0.5 else 0.45
            assertTrue(
                "${level.name}: $free of ${level.arrows.size} arrows are free at the start",
                free <= level.arrows.size * ceiling
            )
        }
    }

    @Test
    fun `all four directions appear across the catalogue`() {
        val used = levels.flatMap { level -> level.arrows.map { it.direction } }.toSet()
        assertEquals(Direction.entries.toSet(), used)
    }

    /**
     * One band per world, from the Discovery Phase 2 brief. The levels are
     * silhouettes now, so counts are what each picture needs rather than a number
     * chosen first; the bands are the brief's targets, with one deliberate
     * allowance — Level 6 (the Star) is 12, one over, because its tall tip is
     * what stops it reading as the Heart.
     */
    @Test
    fun `arrow counts follow the intended difficulty bands`() {
        fun count(id: Int) = Levels.byId(id)!!.arrows.size
        (1..6).forEach { assertTrue("Level $it", count(it) in 5..12) }
        (7..12).forEach { assertTrue("Level $it", count(it) in 10..16) }
        (13..18).forEach { assertTrue("Level $it", count(it) in 15..22) }
        (19..24).forEach { assertTrue("Level $it", count(it) in 20..28) }
        (25..30).forEach { assertTrue("Level $it", count(it) in 26..35) }
    }

    /** Silhouettes make flat boards easy to build; this keeps the opening a puzzle. */
    @Test
    fun `a level opens with several choices but never most of the board`() {
        levels.filter { it.id >= 3 }.forEach { level ->
            val free = LevelSolver.initiallyValidMoves(level)
            assertTrue("${level.name} opens with only $free free arrow(s)", free >= 2)
        }
    }

    @Test
    fun `arrow counts never go down as the game goes on`() {
        levels.zipWithNext { a, b ->
            assertTrue(
                "${b.name} has fewer arrows than ${a.name}",
                b.arrows.size >= a.arrows.size
            )
        }
    }

    @Test
    fun `byId finds every level and nothing else`() {
        (1..30).forEach { assertNotNull(Levels.byId(it)) }
        assertEquals(null, Levels.byId(0))
        assertEquals(null, Levels.byId(31))
    }

    private companion object {
        /** Widest board that still leaves 48dp tap targets on a 320dp phone. */
        const val MAX_COLUMNS = 6

        /** Tallest board that still fits above the hint line on a short phone. */
        const val MAX_ROWS = 9
    }
}
