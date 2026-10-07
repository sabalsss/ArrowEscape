package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.endless.BoardAnalysis
import com.sabalapps.arrowescape.game.CampaignShapes
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.ui.OccupiedBounds
import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Campaign through the shape core: each level is the mask of its discovery's silhouette, the
 * confirmation outline can be traced from it, and the picture the outline is traced from is the
 * picture the board is laid out on — so it can never describe something the player did not solve.
 */
class CampaignShapeMaskTest {

    @Test
    fun `every level has a discovery, a mask, a solvable board and a contour`() {
        assertEquals(30, Levels.ALL.size)
        for (level in Levels.ALL) {
            val name = "level ${level.id}"
            assertNotNull("$name has no discovery", CampaignDiscoveries.forLevel(level.id))
            val mask = ShapeMask.of(level)
            assertEquals("$name: one cell per arrow", level.arrows.size, mask.cellCount)
            assertEquals("$name: no two arrows share a cell", level.arrows.size, level.arrows.map { it.row to it.col }.toSet().size)
            assertTrue("$name is not solvable", BoardAnalysis.isSolvable(level.arrows))
            val contour = GridContourTracer.trace(mask)
            assertTrue("$name has no outline", !contour.isEmpty)
            assertEquals("$name: the outline does not account for every cell", mask.cellCount, contour.area)
            assertTrue("$name is ${level.columns} columns", level.columns <= MysteryShapes.MAX_COLUMNS)
            assertTrue("$name is ${level.rows} rows", level.rows <= MysteryShapes.MAX_ROWS)
        }
    }

    @Test
    fun `each level's mask is its authored blueprint`() {
        for (shape in CampaignShapes.ALL) {
            val level = Levels.byId(shape.levelId)!!
            assertEquals(
                "${shape.discovery}\n${ShapeMask.of(level).ascii()}\nvs\n${ShapeMask.parse(shape.rows).ascii()}",
                ShapeMask.parse(shape.rows),
                ShapeMask.of(level)
            )
        }
    }

    @Test
    fun `the mask is taken from the whole level, so it does not change as arrows leave`() {
        for (level in Levels.ALL) {
            val whole = ShapeMask.of(level)
            // What is left after some arrows have gone is a different (smaller) set of cells, but the
            // level's own mask — what the outline is traced from — is fixed by its full arrow list.
            val partial = ShapeMask.of(level.arrows.drop(level.arrows.size / 2), level.rows, level.columns)
            assertTrue(partial.cellCount < whole.cellCount)
            assertEquals(whole, ShapeMask.of(level))
        }
    }

    @Test
    fun `the contour lives inside the same occupied bounds the board is centred on`() {
        for (level in Levels.ALL) {
            val bounds = OccupiedBounds.of(level.arrows)!!
            val mask = ShapeMask.of(level)
            val box = mask.bounds!!
            assertEquals(bounds.minRow, box.minRow)
            assertEquals(bounds.maxRow, box.maxRow)
            assertEquals(bounds.minCol, box.minCol)
            assertEquals(bounds.maxCol, box.maxCol)
            for (loop in GridContourTracer.trace(mask).loops) for (p in loop.points) {
                assertTrue(p.x in box.minCol..(box.maxCol + 1))
                assertTrue(p.y in box.minRow..(box.maxRow + 1))
            }
        }
    }

    @Test
    fun `the holes the silhouettes were drawn with are kept`() {
        fun holes(id: Int) = GridContourTracer.trace(ShapeMask.of(Levels.byId(id)!!)).holes.size
        assertEquals("Owl's two eyes", 2, holes(12))
        assertEquals("Rocket's porthole", 1, holes(26))
        assertEquals("Canyon Arch's doorway", 0, holes(16)) // open at the bottom: a notch, not a hole
        assertEquals("Crown is solid since it was redrawn", 0, holes(24))
        // The Satellite's antenna touches its panels only at corners, so it is a separate piece with
        // its own outline and the gap beneath it is open, not a hole.
        assertEquals("Satellite has no enclosed hole", 0, holes(29))
        assertEquals("Satellite's antenna is its own outline", 2, GridContourTracer.trace(ShapeMask.of(Levels.byId(29)!!)).outer.size)
        assertEquals("a Heart is one piece", 0, holes(1))
        assertEquals("the Flower's open centre", 1, holes(6))
    }

    @Test
    fun `irregular masks never crash the tracer`() {
        for (level in Levels.ALL) {
            for (keep in listOf(1, 2, 3)) {
                val thinned = level.arrows.filterIndexed { i, _ -> i % keep == 0 }
                val mask = ShapeMask.of(thinned, level.rows, level.columns)
                assertEquals(mask.cellCount, GridContourTracer.trace(mask).area)
            }
        }
    }
}
