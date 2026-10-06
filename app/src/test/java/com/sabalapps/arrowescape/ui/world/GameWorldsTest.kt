package com.sabalapps.arrowescape.ui.world

import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.ui.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * World *selection*. Nothing here touches a bitmap, a drawable or a composable:
 * what is worth testing is that a board always comes back to the same place, and
 * that is arithmetic over numbers the game already has.
 */
class GameWorldsTest {

    // ---- Campaign: the five blocks, and both ends of each boundary ----------

    @Test
    fun `campaign level 1 is sky garden`() {
        assertEquals(GameWorld.SKY_GARDEN, GameWorlds.forCampaignLevel(1))
    }

    @Test
    fun `campaign level 6 is still sky garden`() {
        assertEquals(GameWorld.SKY_GARDEN, GameWorlds.forCampaignLevel(6))
    }

    @Test
    fun `campaign level 7 is forest`() {
        assertEquals(GameWorld.FOREST, GameWorlds.forCampaignLevel(7))
    }

    @Test
    fun `campaign level 13 is sunset canyon`() {
        assertEquals(GameWorld.SUNSET_CANYON, GameWorlds.forCampaignLevel(13))
    }

    @Test
    fun `campaign level 19 is crystal night`() {
        assertEquals(GameWorld.CRYSTAL_NIGHT, GameWorlds.forCampaignLevel(19))
    }

    @Test
    fun `campaign level 25 is cosmic`() {
        assertEquals(GameWorld.COSMIC, GameWorlds.forCampaignLevel(25))
    }

    @Test
    fun `campaign level 30 is cosmic`() {
        assertEquals(GameWorld.COSMIC, GameWorlds.forCampaignLevel(30))
    }

    /**
     * The catalogue is covered exactly: every level has a world, each of the five
     * worlds gets a block, and the blocks are the documented six levels each.
     * This is the test that fails if a level is ever added without deciding
     * where it is played.
     */
    @Test
    fun `every campaign level maps into one of the five worlds, six levels each`() {
        val byWorld = Levels.ALL.groupBy { GameWorlds.forCampaignLevel(it.id) }
        assertEquals(GameWorld.PROGRESSION.toSet(), byWorld.keys)
        byWorld.forEach { (world, levels) ->
            assertEquals(
                "$world should cover ${GameWorlds.CAMPAIGN_LEVELS_PER_WORLD} levels",
                GameWorlds.CAMPAIGN_LEVELS_PER_WORLD,
                levels.size
            )
        }
    }

    /** Past the catalogue stays in the last world rather than wrapping to the first. */
    @Test
    fun `a level past the catalogue stays in the last world`() {
        assertEquals(GameWorld.COSMIC, GameWorlds.forCampaignLevel(31))
        assertEquals(GameWorld.COSMIC, GameWorlds.forCampaignLevel(500))
    }

    // ---- Endless: one world per puzzle, cycling forever --------------------

    @Test
    fun `endless rotates through the five worlds in order and wraps`() {
        assertEquals(GameWorld.SKY_GARDEN, GameWorlds.forEndlessPuzzle(1))
        assertEquals(GameWorld.FOREST, GameWorlds.forEndlessPuzzle(2))
        assertEquals(GameWorld.SUNSET_CANYON, GameWorlds.forEndlessPuzzle(3))
        assertEquals(GameWorld.CRYSTAL_NIGHT, GameWorlds.forEndlessPuzzle(4))
        assertEquals(GameWorld.COSMIC, GameWorlds.forEndlessPuzzle(5))
        assertEquals(GameWorld.SKY_GARDEN, GameWorlds.forEndlessPuzzle(6))
    }

    /**
     * The property that matters for a restored save: a puzzle number is the only
     * input, so the same puzzle is always the same world — including the one
     * rebuilt from disk after process death, which is a different `GameMode`
     * instance carrying the same number.
     */
    @Test
    fun `the same endless puzzle always gives the same world`() {
        for (number in 1..200) {
            val first = GameWorlds.forEndlessPuzzle(number)
            assertSame(first, GameWorlds.forEndlessPuzzle(number))
            // Through `forMode`, as the screen actually asks, with a different
            // seed and tier each time to prove neither is consulted.
            val reopened = GameWorlds.forMode(
                GameMode.Endless(
                    seed = number * 7_919L,
                    puzzleNumber = number,
                    tier = EndlessTier.entries[number % EndlessTier.entries.size]
                ),
                levelId = 0
            )
            assertSame(first, reopened)
        }
    }

    // ---- Daily: derived from the seed the day already has ------------------

    /**
     * A daily's world is a function of its seed, which is a function of its
     * date, so the day's backdrop is as fixed as the day's board. Checked over a
     * year, and across separate `GameMode.Daily` instances for the same date —
     * which is what a save reopened the next hour looks like.
     */
    @Test
    fun `the same daily date always gives the same world`() {
        var date = GameDate(2026, 1, 1)
        repeat(365) {
            val seed = DailyChallenge.seedFor(date)
            val expected = GameWorlds.forDailySeed(seed)
            assertSame(expected, GameWorlds.forDailySeed(DailyChallenge.seedFor(date)))
            assertSame(
                expected,
                GameWorlds.forMode(
                    GameMode.Daily(
                        date = date,
                        seed = seed,
                        tier = DailyChallenge.tierFor(date),
                        alreadyCleared = true
                    ),
                    levelId = 0
                )
            )
            date = date.plusDays(1L)
        }
    }

    /**
     * Daily reuses the five worlds rather than owning a sixth asset, and it
     * reaches all five rather than parking on one — which a seed-derived
     * mapping could silently do if the mixing were poor.
     */
    @Test
    fun `daily uses all five worlds over a year and never a sixth`() {
        var date = GameDate(2026, 1, 1)
        val seen = mutableSetOf<GameWorld>()
        repeat(365) {
            seen += GameWorlds.forDailySeed(DailyChallenge.seedFor(date))
            date = date.plusDays(1L)
        }
        assertEquals(GameWorld.PROGRESSION.toSet(), seen)
    }

    // ---- The tutorial, and the shape of the enum ---------------------------

    @Test
    fun `the tutorial is sky garden`() {
        assertEquals(GameWorld.SKY_GARDEN, GameWorlds.forMode(GameMode.Tutorial, levelId = 1))
    }

    /**
     * Five worlds, five pieces of artwork, stable ids. The id is what a world
     * would be written down as if a later phase ever needs to persist one, so a
     * rename is a save-format change and should fail a test rather than happen
     * quietly.
     */
    @Test
    fun `there are exactly five worlds with stable distinct ids`() {
        assertEquals(5, GameWorld.entries.size)
        assertEquals(GameWorld.entries.toList(), GameWorld.PROGRESSION)
        assertEquals(
            listOf("sky_garden", "forest", "sunset_canyon", "crystal_night", "cosmic"),
            GameWorld.PROGRESSION.map { it.id }
        )
        assertTrue(GameWorld.entries.all { it.displayName.isNotBlank() })
    }
}
