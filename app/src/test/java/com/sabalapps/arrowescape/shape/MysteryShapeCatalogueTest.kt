package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.endless.BoardAnalysis
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.CampaignShapes
import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Daily / Endless catalogue: every picture is a real, connected, board-sized silhouette
 * with a hidden name of its own, none of them a Campaign discovery, and every one of them
 * turns into a solvable puzzle at every tier that may draw it. A template that cannot is
 * removed, not tolerated.
 */
class MysteryShapeCatalogueTest {

    private val all = MysteryShapes.all

    @Test
    fun `there are enough pictures to keep the modes fresh`() {
        assertTrue("only ${all.size} templates", all.size in 20..30)
    }

    @Test
    fun `ids are stable snake case and unique`() {
        val ids = all.map { it.id }
        assertEquals("duplicate ids in $ids", ids.size, ids.toSet().size)
        for (id in ids) assertTrue("'$id'", Regex("[a-z][a-z0-9_]*").matches(id))
        for (template in all) assertEquals(template, MysteryShapes.byId(template.id))
        assertEquals(null, MysteryShapes.byId("no_such_shape"))
    }

    @Test
    fun `names are present, distinct, and not Campaign discoveries`() {
        val names = all.map { it.name }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.map { it.lowercase() }.toSet().size)
        val campaign = CampaignDiscoveries.all.map { it.name.lowercase() }.toSet()
        for (template in all) {
            assertFalse(
                "${template.name} is also a Campaign discovery",
                template.name.lowercase() in campaign
            )
        }
    }

    @Test
    fun `every picture is board sized, non empty and one connected piece`() {
        for (t in all) {
            val m = t.mask
            assertTrue("${t.id} is ${m.columns} columns wide", m.columns in 1..MysteryShapes.MAX_COLUMNS)
            assertTrue("${t.id} is ${m.rows} rows tall", m.rows in 1..MysteryShapes.MAX_ROWS)
            assertTrue("${t.id} has no cells", t.cellCount > 0)
            assertEquals("${t.id} is not one connected piece\n${m.ascii()}", 1, m.components)
            // Authored tight: no empty margin row or column to push the picture off-centre.
            assertEquals("${t.id} has a margin", m, m.trimmed())
        }
    }

    @Test
    fun `sizes stay inside the range the tiers are built for`() {
        for (t in all) assertTrue("${t.id} has ${t.cellCount} cells", t.cellCount in 15..32)
    }

    @Test
    fun `every picture traces to a contour that accounts for all its cells`() {
        for (t in all) {
            val contour = GridContourTracer.trace(t.mask)
            assertFalse("${t.id} has no outline", contour.isEmpty)
            assertEquals("${t.id}", t.cellCount, contour.area)
            assertEquals("${t.id} is one part", 1, contour.outer.size)
        }
    }

    @Test
    fun `pictures with an eye or a lens keep it as a hole`() {
        fun holes(id: String) = GridContourTracer.trace(MysteryShapes.byId(id)!!.mask).holes.size
        assertEquals(1, holes("fish"))
        assertEquals(1, holes("camera"))
        assertEquals(1, holes("key"))
        assertEquals(1, holes("anchor"))
        assertEquals(1, holes("gift"))
        assertEquals(1, holes("balloon"))
        assertEquals(2, holes("ghost"))
    }

    @Test
    fun `no two pictures are the same, even flipped`() {
        for (a in all) for (b in all) {
            if (a === b) continue
            assertNotEquals("${a.id} and ${b.id} are the same picture", a.mask, b.mask)
            assertNotEquals("${a.id} is ${b.id} flipped", a.mask.mirrored(), b.mask)
        }
    }

    @Test
    fun `a template only allows a flip when the flip is a different picture`() {
        for (t in all) {
            if (t.allowMirror) {
                assertNotEquals("${t.id} is symmetric, so its flip is the same board", t.mask, t.mask.mirrored())
            }
        }
    }

    @Test
    fun `every permitted orientation still fits the board`() {
        for (t in all) for (v in t.variants) {
            assertTrue("${t.id} turned ${v.quarterTurns} is ${v.mask.columns} wide", v.mask.columns <= MysteryShapes.MAX_COLUMNS)
            assertTrue("${t.id} turned ${v.quarterTurns} is ${v.mask.rows} tall", v.mask.rows <= MysteryShapes.MAX_ROWS)
            assertEquals(t.cellCount, v.mask.cellCount)
        }
        // Nothing is rotated today: an upside-down House or a sideways Balloon is not that object.
        assertTrue(all.all { it.allowedRotations == setOf(0) })
    }

    @Test
    fun `no picture is a Campaign silhouette`() {
        val campaign = CampaignShapes.ALL.map { ShapeMask.parse(it.rows) }
        for (t in all) for (c in campaign) {
            assertNotEquals("${t.id} duplicates a Campaign silhouette", c, t.mask.trimmed())
        }
    }

    @Test
    fun `every tier has a healthy choice of pictures and daily has a pool`() {
        for (tier in EndlessTier.entries) {
            assertTrue("$tier has ${MysteryShapes.forTier(tier).size} pictures", MysteryShapes.forTier(tier).size >= 7)
        }
        assertTrue(MysteryShapes.dailyPool.size >= 18)
        assertTrue(MysteryShapes.dailyPool.all { it.cellCount >= MysteryShapes.DAILY_MIN_CELLS })
        // Bigger pictures are the harder tiers: the windows climb.
        val starts = EndlessTier.entries.map { ShapeTierProfile.of(it).cells.first }
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `every picture builds a solvable puzzle at every tier that may draw it, on profile`() {
        for (tier in EndlessTier.entries) {
            val profile = ShapeTierProfile.of(tier)
            for (t in MysteryShapes.forTier(tier)) {
                for (seed in 1L..8L) {
                    val board = ShapePuzzleGenerator.generate(t.mask, seed * 104729, profile, "x")
                    val where = "${t.id} at $tier, seed ${seed * 104729}"
                    assertEquals("$where lost its picture", t.mask, ShapeMask.of(board.level))
                    assertTrue("$where: witness is not a legal order", BoardAnalysis.verifyOrder(board.level.arrows, board.solution))
                    assertTrue("$where is not solvable", BoardAnalysis.isSolvable(board.level.arrows))
                    assertTrue("$where fell off its profile: free=${board.metrics.free} depth=${board.metrics.depth}", board.onProfile)
                }
            }
        }
    }

    @Test
    fun `every daily-sized picture also builds at its daily tier`() {
        for (t in MysteryShapes.dailyPool) {
            val tier = if (t.cellCount >= MysteryShapes.DAILY_HARD_FROM_CELLS) EndlessTier.HARD else EndlessTier.MEDIUM
            for (seed in 1L..4L) {
                val board = ShapePuzzleGenerator.generate(t.mask, seed * 31, ShapeTierProfile.of(tier), "x")
                assertEquals(t.mask, ShapeMask.of(board.level))
                assertTrue("${t.id} at $tier off profile", board.onProfile)
            }
        }
    }
}
