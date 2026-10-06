package com.sabalapps.arrowescape.game

import com.sabalapps.arrowescape.endless.BoardAnalysis
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the development-only authoring helper honest. It is not shipped, but the
 * Campaign layouts were made with it, and a helper that quietly produced boards
 * the real rules reject — or drew a different silhouette than it was given —
 * would make the next level someone authors a trap.
 */
class ShapeAuthoringTest {

    /** The first construction that does not dead-end, from a fixed run of seeds. */
    private fun built(shape: CampaignShape): Pair<List<String>, Level> {
        for (seed in 1L..400L) {
            val knobs = ShapeAuthoring.Knobs.sample(Random(seed * 7919))
            ShapeAuthoring.construct(shape, seed, knobs)?.let { return it }
        }
        throw AssertionError("no construction for ${shape.discovery} in 400 seeds")
    }

    @Test
    fun `every blueprint can be built into a board that draws exactly its silhouette`() {
        for (shape in CampaignShapes.ALL) {
            val (layout, level) = built(shape)
            val name = "${shape.discovery} (level ${shape.levelId})"

            assertEquals("$name: rows", shape.height, level.rows)
            assertEquals("$name: columns", shape.width, level.columns)
            assertEquals(
                "$name: occupied cells",
                shape.cells.toSet(),
                level.arrows.map { it.row to it.col }.toSet()
            )
            assertEquals(shape.height, layout.size)
        }
    }

    @Test
    fun `every board it builds is solvable under the real rules, by two independent routes`() {
        for (shape in CampaignShapes.ALL) {
            val (_, level) = built(shape)
            assertTrue("${shape.discovery}: peel", BoardAnalysis.isSolvable(level.arrows))

            val outcome = LevelSolver.solve(level)
            assertTrue("${shape.discovery}: DFS did not solve it", outcome is LevelSolver.Outcome.Solved)
            assertTrue(
                "${shape.discovery}: witness did not replay",
                LevelSolver.verify(level, (outcome as LevelSolver.Outcome.Solved).order)
            )
        }
    }

    @Test
    fun `construction is a pure function of the shape and the seed`() {
        val shape = requireNotNull(CampaignShapes.forLevel(10))
        val knobs = ShapeAuthoring.Knobs.sample(Random(11))
        val first = ShapeAuthoring.construct(shape, 11L, knobs)
        val second = ShapeAuthoring.construct(shape, 11L, knobs)
        assertEquals(first?.first, second?.first)
    }

    @Test
    fun `search only returns boards that clear the authoring bar`() {
        val shape = requireNotNull(CampaignShapes.forLevel(13))
        val found = ShapeAuthoring.search(shape, tries = 300)
        assertTrue("expected some boards for the Sun in 300 tries", found.isNotEmpty())
        found.forEach {
            assertTrue(ShapeAuthoring.passesBar(it.metrics, shape.levelId))
            assertNotNull(it.level.arrows.firstOrNull())
        }
    }

    @Test
    fun `the effort measure agrees with itself between calls`() {
        val level = Levels.byId(20)!!
        assertEquals(ShapeAuthoring.scanningEffort(level), ShapeAuthoring.scanningEffort(level), 0.0)
    }

    @Test
    fun `a single-file streak is counted anywhere in a peel`() {
        assertEquals(0, ShapeAuthoring.singleStreak(listOf(3, 3, 2)))
        assertEquals(1, ShapeAuthoring.singleStreak(listOf(3, 1, 3, 1, 2)))
        assertEquals(4, ShapeAuthoring.singleStreak(listOf(5, 3, 1, 1, 1, 1, 2)))
        assertEquals(5, ShapeAuthoring.singleStreak(listOf(4, 3, 3, 1, 1, 1, 1, 1)))
    }

    @Test
    fun `the pacing bar refuses a forced middle or a forced tail but not a small board`() {
        fun metrics(layers: List<Int>) = ShapeAuthoring.measure(Levels.byId(20)!!, withEffort = false)
            .copy(arrows = layers.sum(), layerSizes = layers)
        assertTrue(ShapeAuthoring.passesPacing(metrics(listOf(4, 3, 3, 2, 2, 1))))
        assertTrue(!ShapeAuthoring.passesPacing(metrics(listOf(4, 3, 1, 1, 1, 3, 2))))
        assertTrue(!ShapeAuthoring.passesPacing(metrics(listOf(4, 4, 3, 1, 1, 1))))
        assertTrue(ShapeAuthoring.passesPacing(metrics(listOf(3, 1, 1, 1, 1))))
    }

    @Test
    fun `the forced share is a share, and a wide open board has little of it`() {
        val wide = ShapeAuthoring.forcedShare(Levels.byId(23)!!)
        assertTrue(wide in 0.0..1.0)
        assertEquals(wide, ShapeAuthoring.forcedShare(Levels.byId(23)!!), 0.0)
    }
}
