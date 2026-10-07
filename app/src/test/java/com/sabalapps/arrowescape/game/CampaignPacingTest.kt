package com.sabalapps.arrowescape.game

import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import com.sabalapps.arrowescape.ui.world.GameWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Campaign's rhythm, as opposed to its solvability: each world climbs, takes
 * one breath on its fifth level, and ends on its hardest board — and none of the
 * levels that were re-authored for pacing is a single-file shuffle in disguise.
 *
 * These pin what the engagement pass chose, so a later layout edit that flattens a
 * breather or reintroduces a forced stretch fails here with a name on it. They do
 * not claim every level meets the pacing bar: several earlier ones do not, which
 * is why the bar is applied only to [REAUTHORED].
 */
class CampaignPacingTest {

    private val effort = Levels.ALL.associate { it.id to ShapeAuthoring.scanningEffort(it) }

    private fun worldLevels(world: GameWorld): List<Int> =
        CampaignDiscoveries.forWorld(world).map { it.levelId }

    @Test
    fun `each world's fifth level is a breather, and nothing else dips`() {
        for (world in GameWorld.PROGRESSION) {
            val ids = worldLevels(world)
            val breather = ids[4]
            assertTrue("level $breather is not in $BREATHERS", breather in BREATHERS)
            for (id in ids.drop(1)) {
                val growth = effort.getValue(id) / effort.getValue(id - 1)
                if (id == breather) assertTrue("level $id should dip, was $growth", growth < 1.0)
                else assertTrue("level $id should not dip, was $growth", growth >= 1.0)
            }
        }
    }

    @Test
    fun `a breather offers at least as many opening choices as the level before it`() {
        for (id in BREATHERS) {
            val before = LevelSolver.initiallyValidMoves(Levels.byId(id - 1)!!)
            val here = LevelSolver.initiallyValidMoves(Levels.byId(id)!!)
            assertTrue("level $id opens with $here, level ${id - 1} with $before", here >= before)
        }
    }

    @Test
    fun `a world ends on its hardest board`() {
        for (world in GameWorld.PROGRESSION) {
            val ids = worldLevels(world)
            val finale = effort.getValue(ids.last())
            for (id in ids.dropLast(1)) {
                assertTrue("level $id is harder than its world's finale", effort.getValue(id) < finale)
            }
        }
    }

    @Test
    fun `the breathers sit well under the finale that follows them`() {
        // The point of the rhythm: the breath is followed by a visible rise.
        for (id in BREATHERS) {
            assertTrue(
                "level ${id + 1} is not clearly harder than breather $id",
                effort.getValue(id + 1) >= effort.getValue(id) * 1.05
            )
        }
    }

    @Test
    fun `the re-authored levels pass the structural bar and the pacing bar`() {
        for (id in REAUTHORED) {
            val level = Levels.byId(id)!!
            val m = ShapeAuthoring.measure(level)
            assertTrue("level $id fails the authoring bar", ShapeAuthoring.passesBar(m, id))
            assertTrue(
                "level $id has a single-file stretch ${m.layerSizes} or too many forced moves",
                ShapeAuthoring.passesPacing(m, ShapeAuthoring.forcedShare(level))
            )
        }
    }

    @Test
    fun `no re-authored level ends in a forced chain or has one in its middle`() {
        for (id in REAUTHORED) {
            val layers = ShapeAuthoring.measure(Levels.byId(id)!!).layerSizes
            assertTrue("level $id layers $layers", ShapeAuthoring.singleStreak(layers) <= 2)
            assertTrue("level $id layers $layers", layers.takeLastWhile { it == 1 }.size <= 2)
        }
    }

    @Test
    fun `the world finales are not forced shuffles`() {
        // Level 12 was the finding this pass fixed; the rest are checked so it stays fixed.
        for (id in listOf(6, 12, 18, 24, 30)) {
            val share = ShapeAuthoring.forcedShare(Levels.byId(id)!!)
            assertTrue("level $id has ${(share * 100).toInt()}% forced moves", share <= 0.24)
        }
        assertEquals(3, ShapeAuthoring.measure(Levels.byId(12)!!).layerSizes.take(3).count { it == 3 })
    }

    private companion object {
        val BREATHERS = setOf(5, 11, 17, 23, 29)
        val REAUTHORED = listOf(4, 5, 6, 9, 11, 12, 17, 23, 29)
    }
}
