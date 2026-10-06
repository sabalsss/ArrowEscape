package com.sabalapps.arrowescape.ui.world

import com.sabalapps.arrowescape.endless.PuzzleGenerator
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.progress.PlayerProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The discovery catalogue is pure data and pure arithmetic over [PlayerProgress]. */
class CampaignDiscoveriesTest {

    private val all = CampaignDiscoveries.all

    // ---- Catalogue shape ----------------------------------------------------

    @Test
    fun `there are exactly thirty discoveries with ids one to thirty in order`() {
        assertEquals(30, all.size)
        assertEquals((1..30).toList(), all.map { it.levelId })
    }

    /** Fails if a level is ever added without deciding what it hides. */
    @Test
    fun `the catalogue covers exactly the campaign levels`() {
        assertEquals(Levels.ALL.map { it.id }, all.map { it.levelId })
    }

    @Test
    fun `every campaign level resolves to its own discovery`() {
        Levels.ALL.forEach { level ->
            val discovery = CampaignDiscoveries.forLevel(level.id)
            assertEquals(level.id, discovery?.levelId)
        }
    }

    @Test
    fun `the thirty names are the product catalogue`() {
        val expected = listOf(
            "Cloud", "Flower", "Kite", "Bird", "Heart", "Star",
            "Leaf", "Mushroom", "Tree", "Butterfly", "Fox", "Owl",
            "Sun", "Cactus", "Mountain", "Canyon Arch", "Eagle", "Treasure Chest",
            "Gem", "Crescent Moon", "Crystal", "Snowflake", "Magic Star", "Crown",
            "Comet", "Rocket", "Planet", "UFO", "Satellite", "Galaxy"
        )
        assertEquals(expected, all.map { it.name })
    }

    @Test
    fun `level names stay Level N`() {
        Levels.ALL.forEach { assertEquals("Level ${it.id}", it.name) }
    }

    // ---- Worlds -------------------------------------------------------------

    @Test
    fun `each world holds exactly six discoveries over the documented level ranges`() {
        val expected = mapOf(
            GameWorld.SKY_GARDEN to (1..6),
            GameWorld.FOREST to (7..12),
            GameWorld.SUNSET_CANYON to (13..18),
            GameWorld.CRYSTAL_NIGHT to (19..24),
            GameWorld.COSMIC to (25..30)
        )
        assertEquals(GameWorld.PROGRESSION.toSet(), expected.keys)
        expected.forEach { (world, levels) ->
            assertEquals(6, CampaignDiscoveries.totalCount(world))
            assertEquals(levels.toList(), CampaignDiscoveries.forWorld(world).map { it.levelId })
        }
    }

    @Test
    fun `a discovery is always in the world its level is played in`() {
        all.forEach { assertEquals(GameWorlds.forCampaignLevel(it.levelId), it.world) }
    }

    // ---- Art keys -----------------------------------------------------------

    @Test
    fun `art keys are unique`() {
        assertEquals(all.size, all.map { it.artKey }.toSet().size)
    }

    /**
     * Keys are permanent once shipped, so they are pinned here by name. Crystal
     * is `crystal_cluster`, not `crystal_crystal`: renamed in Discovery Phase 2,
     * while nothing yet consumed the key.
     */
    @Test
    fun `the thirty art keys are the intended ones`() {
        val expected = listOf(
            "sky_cloud", "sky_flower", "sky_kite", "sky_bird", "sky_heart", "sky_star",
            "forest_leaf", "forest_mushroom", "forest_tree", "forest_butterfly", "forest_fox", "forest_owl",
            "canyon_sun", "canyon_cactus", "canyon_mountain", "canyon_arch", "canyon_eagle",
            "canyon_treasure_chest",
            "crystal_gem", "crystal_moon", "crystal_cluster", "crystal_snowflake",
            "crystal_magic_star", "crystal_crown",
            "cosmic_comet", "cosmic_rocket", "cosmic_planet", "cosmic_ufo", "cosmic_satellite",
            "cosmic_galaxy"
        )
        assertEquals(expected, all.map { it.artKey })
        assertEquals("crystal_cluster", CampaignDiscoveries.forLevel(21)?.artKey)
        assertEquals("Crystal", CampaignDiscoveries.forLevel(21)?.name)
    }

    @Test
    fun `art keys are lowercase snake case with one prefix per world`() {
        val format = Regex("[a-z]+(_[a-z]+)+")
        all.forEach { assertTrue("bad art key ${it.artKey}", format.matches(it.artKey)) }
        GameWorld.PROGRESSION.forEach { world ->
            val prefixes = CampaignDiscoveries.forWorld(world)
                .map { it.artKey.substringBefore('_') }
                .toSet()
            assertEquals("$world should share one art prefix", 1, prefixes.size)
        }
    }

    // ---- Invalid ids --------------------------------------------------------

    @Test
    fun `ids that are not campaign levels have no discovery`() {
        listOf(0, -1, 31, 500, Int.MIN_VALUE, Int.MAX_VALUE, PuzzleGenerator.ENDLESS_LEVEL_ID)
            .forEach { assertNull("level $it", CampaignDiscoveries.forLevel(it)) }
    }

    // ---- Collected state ----------------------------------------------------

    @Test
    fun `a fresh install has collected nothing`() {
        val fresh = PlayerProgress()
        assertEquals(0, CampaignDiscoveries.collectedCount(fresh))
        assertEquals(1, CampaignDiscoveries.nextUndiscovered(fresh)?.levelId)
    }

    @Test
    fun `collected means the level is completed, not merely unlocked`() {
        val unlocked = PlayerProgress(highestUnlockedLevel = 10, currentLevel = 10)
        assertEquals(0, CampaignDiscoveries.collectedCount(unlocked))

        val bird = CampaignDiscoveries.forLevel(4)!!
        val cleared = PlayerProgress(completedLevels = setOf(4))
        assertTrue(CampaignDiscoveries.isCollected(bird, cleared))
        assertTrue(!CampaignDiscoveries.isCollected(CampaignDiscoveries.forLevel(3)!!, cleared))
    }

    @Test
    fun `counts are per world and overall`() {
        val progress = PlayerProgress(completedLevels = setOf(1, 2, 7, 30))
        assertEquals(4, CampaignDiscoveries.collectedCount(progress))
        assertEquals(2, CampaignDiscoveries.collectedCount(GameWorld.SKY_GARDEN, progress))
        assertEquals(1, CampaignDiscoveries.collectedCount(GameWorld.FOREST, progress))
        assertEquals(0, CampaignDiscoveries.collectedCount(GameWorld.SUNSET_CANYON, progress))
        assertEquals(0, CampaignDiscoveries.collectedCount(GameWorld.CRYSTAL_NIGHT, progress))
        assertEquals(1, CampaignDiscoveries.collectedCount(GameWorld.COSMIC, progress))
    }

    @Test
    fun `next undiscovered is the first gap in level order`() {
        val progress = PlayerProgress(completedLevels = setOf(1, 2, 4))
        assertEquals(3, CampaignDiscoveries.nextUndiscovered(progress)?.levelId)
    }

    @Test
    fun `nothing is left to discover once every level is cleared`() {
        val done = PlayerProgress(completedLevels = (1..30).toSet())
        assertEquals(30, CampaignDiscoveries.collectedCount(done))
        assertNull(CampaignDiscoveries.nextUndiscovered(done))
    }

    @Test
    fun `completed ids outside the catalogue never inflate the count`() {
        val progress = PlayerProgress(completedLevels = setOf(1, 31, 99, 0, -5))
        assertEquals(1, CampaignDiscoveries.collectedCount(progress))
    }
}
