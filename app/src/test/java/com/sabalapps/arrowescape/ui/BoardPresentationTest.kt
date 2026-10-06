package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a board is presented: which mode gets which presentation, what part of a
 * Campaign layout is on screen, and how big a cell is. Pure arithmetic over the
 * real catalogue — nothing here draws.
 */
class BoardPresentationTest {

    @Test
    fun `campaign and the replayed tutorial are shapes`() {
        assertEquals(BoardPresentation.Shape, boardPresentationFor(GameMode.Campaign))
        assertEquals(BoardPresentation.Shape, boardPresentationFor(GameMode.Tutorial))
    }

    @Test
    fun `endless and daily stay traditional grids`() {
        val endless = GameMode.Endless(seed = 1L, puzzleNumber = 1, tier = EndlessTier.BEGINNER)
        val daily = GameMode.Daily(
            date = GameDate(2026, 10, 6),
            seed = 1L,
            tier = EndlessTier.MEDIUM
        )
        assertEquals(BoardPresentation.Shape, boardPresentationFor(endless))
        assertEquals(BoardPresentation.Shape, boardPresentationFor(daily))
    }

    @Test
    fun `occupied bounds are the tightest box around every arrow of every level`() {
        for (level in Levels.ALL) {
            val bounds = checkNotNull(OccupiedBounds.of(level.arrows)) { "${level.name} has no arrows" }
            assertTrue(level.arrows.all { it.row in bounds.minRow..bounds.maxRow })
            assertTrue(level.arrows.all { it.col in bounds.minCol..bounds.maxCol })
            // Tight: an arrow touches each of the four edges.
            assertTrue(level.arrows.any { it.row == bounds.minRow })
            assertTrue(level.arrows.any { it.row == bounds.maxRow })
            assertTrue(level.arrows.any { it.col == bounds.minCol })
            assertTrue(level.arrows.any { it.col == bounds.maxCol })
            // And never larger than the board the level was authored on.
            assertTrue(bounds.rows <= level.rows && bounds.columns <= level.columns)
        }
    }

    @Test
    fun `level 1 loses its empty margin rows and nothing else`() {
        val level = Levels.ALL.first { it.id == 1 }
        val bounds = checkNotNull(OccupiedBounds.of(level.arrows))
        assertEquals(4, level.rows)
        assertEquals(2, bounds.rows)
        assertEquals(level.columns, bounds.columns)
    }

    @Test
    fun `a level with no arrows has no bounds`() {
        assertNull(OccupiedBounds.of(emptyList()))
    }

    @Test
    fun `cells are square, fit the room, and never exceed the cap`() {
        for (level in Levels.ALL) {
            val bounds = checkNotNull(OccupiedBounds.of(level.arrows))
            val cell = ShapeFit.cellSize(
                availableWidth = 324f,
                availableHeight = 520f,
                rows = bounds.rows,
                columns = bounds.columns,
                maxCell = 60f
            )
            assertTrue(cell > 0f && cell <= 60f)
            assertTrue(cell * bounds.columns <= 324f + 0.001f)
            assertTrue(cell * bounds.rows <= 520f + 0.001f)
        }
    }

    @Test
    fun `a small shape is capped rather than blown up to fill the screen`() {
        // Level 2, the Flower: three columns wide. Uncapped it would be 108dp a side.
        val bounds = checkNotNull(OccupiedBounds.of(Levels.ALL.first { it.id == 2 }.arrows))
        val cell = ShapeFit.cellSize(324f, 520f, bounds.rows, bounds.columns, maxCell = 60f)
        assertEquals(60f, cell, 0f)
    }

    @Test
    fun `no level drops below a 48dp cell across a 320dp phone`() {
        // 320dp less the tight 8dp screen padding either side. Width is the binding
        // limit for any six-column shape; the touch-target floor must survive it.
        for (level in Levels.ALL) {
            val bounds = checkNotNull(OccupiedBounds.of(level.arrows))
            val cell = ShapeFit.cellSize(304f, 10_000f, bounds.rows, bounds.columns, maxCell = 60f)
            assertTrue("${level.name} would be $cell dp wide-limited", cell >= 48f)
        }
    }

    @Test
    fun `the tallest shape is height-limited, not width-limited, when the room is short`() {
        val bounds = checkNotNull(OccupiedBounds.of(Levels.ALL.maxBy { it.rows }.arrows))
        val cell = ShapeFit.cellSize(324f, 360f, bounds.rows, bounds.columns, maxCell = 60f)
        assertEquals(360f / bounds.rows, cell, 0.001f)
    }

    // ---- the closing build-up ---------------------------------------------------

    @Test
    fun `the build-up begins at the third-to-last arrow and not before`() {
        assertEquals(0, ShapeAnticipation.stage(35))
        assertEquals(0, ShapeAnticipation.stage(5))
        assertEquals(0, ShapeAnticipation.stage(4))
        assertEquals(1, ShapeAnticipation.stage(3))
        assertEquals(2, ShapeAnticipation.stage(2))
        assertEquals(3, ShapeAnticipation.stage(1))
    }

    @Test
    fun `the build-up stands down the moment the last arrow has gone`() {
        // The halo then settles, which is the beat before the discovery arrives.
        assertEquals(0, ShapeAnticipation.stage(0))
    }

    @Test
    fun `the build-up depends only on how many arrows are left, never on which`() {
        // A stage is an Int of a count: there is no arrow id anywhere in it to leak
        // which one is free, so the last move is still the player's to find.
        assertEquals(ShapeAnticipation.STAGES, ShapeAnticipation.stage(1))
        for (left in 0..40) {
            assertTrue(ShapeAnticipation.stage(left) in 0..ShapeAnticipation.STAGES)
        }
    }

    @Test
    fun `every campaign level is long enough that the build-up is a tail and not the whole puzzle`() {
        for (level in Levels.ALL) {
            assertTrue("${level.name} is too short", level.arrows.size > 2 * ShapeAnticipation.STAGES)
        }
    }
}
