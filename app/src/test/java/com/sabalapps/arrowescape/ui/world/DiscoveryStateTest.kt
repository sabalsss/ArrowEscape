package com.sabalapps.arrowescape.ui.world

import com.sabalapps.arrowescape.progress.PlayerProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the player may know, as data: the three slot states, the world and global
 * counts, the all-thirty state, and the result a Campaign win reports.
 *
 * The spoiler tests are the load-bearing ones. Every non-collected slot is rendered
 * to every string the app could show or speak from it, and none may contain a name
 * or an art key from the catalogue.
 */
class DiscoveryStateTest {

    private val all = CampaignDiscoveries.all

    /** The player has cleared levels 1..[cleared] and has unlocked one past them. */
    private fun progressAfter(cleared: Int) = PlayerProgress(
        highestUnlockedLevel = (cleared + 1).coerceAtMost(30),
        currentLevel = (cleared + 1).coerceAtMost(30),
        completedLevels = (1..cleared).toSet(),
        bestStars = (1..cleared).associateWith { 2 }
    )

    // ---- C. the three slot states ------------------------------------------------

    @Test
    fun `a cleared level is collected, an open one is a mystery, the rest are locked`() {
        val progress = progressAfter(cleared = 10)

        all.forEach { discovery ->
            val slot = DiscoverySlot.of(discovery, progress)
            when {
                discovery.levelId <= 10 -> assertTrue("level ${discovery.levelId}", slot is DiscoverySlot.Collected)
                discovery.levelId == 11 -> assertTrue("level 11", slot is DiscoverySlot.Mystery)
                else -> assertTrue("level ${discovery.levelId}", slot is DiscoverySlot.Locked)
            }
        }
    }

    @Test
    fun `a fresh install has one mystery and twenty-nine locked, nothing collected`() {
        val slots = all.map { DiscoverySlot.of(it, PlayerProgress()) }

        assertEquals(1, slots.count { it is DiscoverySlot.Mystery })
        assertEquals(29, slots.count { it is DiscoverySlot.Locked })
        assertEquals(0, slots.count { it is DiscoverySlot.Collected })
        assertEquals(1, slots.first { it is DiscoverySlot.Mystery }.levelId)
    }

    @Test
    fun `a collected slot carries its identity and the player's best stars`() {
        val butterfly = CampaignDiscoveries.forLevel(10)!!
        val progress = progressAfter(cleared = 10).copy(bestStars = mapOf(10 to 3))

        val slot = DiscoverySlot.of(butterfly, progress) as DiscoverySlot.Collected

        assertEquals("Butterfly", slot.discovery.name)
        assertEquals(3, slot.stars)
        assertEquals("Butterfly, discovered, Forest", slot.spokenLabel)
    }

    @Test
    fun `a mystery speaks as an undiscovered item with its level and a locked slot as locked`() {
        val progress = progressAfter(cleared = 10)
        assertEquals(
            "Undiscovered item, Level 11",
            DiscoverySlot.of(CampaignDiscoveries.forLevel(11)!!, progress).spokenLabel
        )
        assertEquals(
            "Locked discovery",
            DiscoverySlot.of(CampaignDiscoveries.forLevel(20)!!, progress).spokenLabel
        )
    }

    // ---- D. spoilers --------------------------------------------------------------

    /** Every string the app could show or say for a slot that is not collected. */
    private fun leakSurface(slot: DiscoverySlot): List<String> = listOf(
        slot.toString(),
        slot.spokenLabel,
        slot.levelId.toString()
    )

    @Test
    fun `a slot that is not collected never contains a discovery name or art key`() {
        val names = all.map { it.name }
        val keys = all.map { it.artKey }
        // Every possible point in the Campaign, so the open level moves through all thirty.
        for (cleared in 0..30) {
            val progress = progressAfter(cleared)
            all.map { DiscoverySlot.of(it, progress) }
                .filterNot { it is DiscoverySlot.Collected }
                .forEach { slot ->
                    leakSurface(slot).forEach { text ->
                        names.forEach { name ->
                            assertFalse("$text leaks the name $name at $cleared cleared", text.contains(name, ignoreCase = true))
                        }
                        keys.forEach { key ->
                            assertFalse("$text leaks the key $key at $cleared cleared", text.contains(key, ignoreCase = true))
                        }
                    }
                }
        }
    }

    @Test
    fun `a mystery or locked slot is not even holding a discovery`() {
        // The structural guarantee: only Collected has a CampaignDiscovery field.
        val progress = progressAfter(cleared = 5)
        val notCollected = all.map { DiscoverySlot.of(it, progress) }.filterNot { it is DiscoverySlot.Collected }
        assertTrue(notCollected.isNotEmpty())
        notCollected.forEach { slot ->
            val holdsDiscovery = slot.javaClass.declaredFields.any { it.type == CampaignDiscovery::class.java }
            assertFalse("${slot.javaClass.simpleName} must not hold a discovery", holdsDiscovery)
        }
        val collected = DiscoverySlot.of(CampaignDiscoveries.forLevel(1)!!, progress)
        assertTrue(collected.javaClass.declaredFields.any { it.type == CampaignDiscovery::class.java })
    }

    // ---- E. world counts ----------------------------------------------------------

    @Test
    fun `each world reports how many of its six are found`() {
        val progress = PlayerProgress(
            highestUnlockedLevel = 30,
            completedLevels = setOf(1, 2, 3, 7, 8, 9, 10, 11, 12, 13, 25)
        )
        val collection = DiscoveryCollection.of(progress)

        val byWorld = collection.worlds.associate { it.world to it.collected }
        assertEquals(3, byWorld[GameWorld.SKY_GARDEN])
        assertEquals(6, byWorld[GameWorld.FOREST])
        assertEquals(1, byWorld[GameWorld.SUNSET_CANYON])
        assertEquals(0, byWorld[GameWorld.CRYSTAL_NIGHT])
        assertEquals(1, byWorld[GameWorld.COSMIC])
        collection.worlds.forEach {
            assertEquals(6, it.total)
            assertEquals(CampaignDiscoveries.collectedCount(it.world, progress), it.collected)
        }
    }

    @Test
    fun `the five worlds come in progression order with their own six slots`() {
        val collection = DiscoveryCollection.of(PlayerProgress())

        assertEquals(GameWorld.PROGRESSION, collection.worlds.map { it.world })
        collection.worlds.forEach { world ->
            assertEquals(CampaignDiscoveries.forWorld(world.world).map { it.levelId }, world.slots.map { it.levelId })
        }
    }

    @Test
    fun `a world is complete only when all six are found`() {
        val five = DiscoveryCollection.of(PlayerProgress(highestUnlockedLevel = 7, completedLevels = (1..5).toSet()))
        assertFalse(five.worlds.first().isComplete)
        assertEquals("5 / 6 DISCOVERED", five.worlds.first().progressLabel)

        val six = DiscoveryCollection.of(PlayerProgress(highestUnlockedLevel = 7, completedLevels = (1..6).toSet()))
        assertTrue(six.worlds.first().isComplete)
        assertEquals("6 / 6 DISCOVERED", six.worlds.first().progressLabel)
        assertTrue(six.worlds.first().spokenSummary.endsWith("complete"))
        assertFalse(six.worlds[1].isComplete)
    }

    // ---- F. the global count -------------------------------------------------------

    @Test
    fun `the global count and header come from the catalogue`() {
        val progress = progressAfter(cleared = 14)
        val collection = DiscoveryCollection.of(progress)

        assertEquals(14, collection.collected)
        assertEquals(30, collection.total)
        assertEquals(CampaignDiscoveries.totalCount, collection.total)
        assertEquals("14 / 30 FOUND", collection.headerLabel)
        assertEquals(CampaignDiscoveries.collectedCount(progress), collection.collected)
    }

    @Test
    fun `a stray completed id cannot inflate the album`() {
        val collection = DiscoveryCollection.of(PlayerProgress(completedLevels = setOf(1, 31, 99)))
        assertEquals(1, collection.collected)
    }

    @Test
    fun `the album can find a slot by level and knows no others`() {
        val collection = DiscoveryCollection.of(progressAfter(3))
        assertTrue(collection.slotFor(2) is DiscoverySlot.Collected)
        assertTrue(collection.slotFor(4) is DiscoverySlot.Mystery)
        assertTrue(collection.slotFor(5) is DiscoverySlot.Locked)
        assertNull(collection.slotFor(0))
        assertNull(collection.slotFor(31))
    }

    // ---- G. all thirty -------------------------------------------------------------

    @Test
    fun `all thirty found is the special state and one short is not`() {
        val done = DiscoveryCollection.of(PlayerProgress(highestUnlockedLevel = 30, completedLevels = (1..30).toSet()))
        assertTrue(done.allFound)
        assertEquals("30 / 30 FOUND", done.headerLabel)
        assertTrue(done.worlds.all { it.isComplete })

        val one = DiscoveryCollection.of(PlayerProgress(highestUnlockedLevel = 30, completedLevels = (1..29).toSet()))
        assertFalse(one.allFound)
        assertFalse(one.worlds.last().isComplete)
    }

    // ---- latest, for Home -----------------------------------------------------------

    @Test
    fun `the latest collected are the highest cleared levels, newest first`() {
        val latest = CampaignDiscoveries.latestCollected(progressAfter(9), limit = 4)
        assertEquals(listOf(9, 8, 7, 6), latest.map { it.levelId })
    }

    @Test
    fun `latest collected is only ever things already found`() {
        assertTrue(CampaignDiscoveries.latestCollected(PlayerProgress(), 4).isEmpty())
        assertEquals(2, CampaignDiscoveries.latestCollected(progressAfter(2), 4).size)
        assertTrue(CampaignDiscoveries.latestCollected(progressAfter(10), 0).isEmpty())
    }

    // ---- B and H. the result a win reports ------------------------------------------

    @Test
    fun `a first clear is a new discovery and a revisit is not`() {
        val after = progressAfter(cleared = 10)

        val first = DiscoveryResult.of(10, wasCompletedBeforeRun = false, progress = after)!!
        assertTrue(first.isFirstClear)
        assertEquals("NEW DISCOVERY!", first.headline)

        val replay = DiscoveryResult.of(10, wasCompletedBeforeRun = true, progress = after)!!
        assertFalse(replay.isFirstClear)
        assertEquals("DISCOVERY FOUND", replay.headline)
    }

    @Test
    fun `the result's counts include the level just cleared even if the record is not yet committed`() {
        // Forest is 3 / 6 before level 10; clearing the Butterfly makes it 4 / 6.
        val before = PlayerProgress(highestUnlockedLevel = 10, completedLevels = (1..9).toSet())
        assertEquals(3, CampaignDiscoveries.collectedCount(GameWorld.FOREST, before))

        val result = DiscoveryResult.of(10, wasCompletedBeforeRun = false, progress = before)!!

        assertEquals("Butterfly", result.discovery.name)
        assertEquals(4, result.worldCollected)
        assertEquals(6, result.worldTotal)
        assertEquals(10, result.totalCollected)
        assertEquals(30, result.totalCount)
        assertEquals("FOREST COLLECTION", result.collectionTitle)
        assertEquals("4 / 6 DISCOVERED", result.progressLabel)
    }

    @Test
    fun `counting the clear twice does not count it twice`() {
        val committed = progressAfter(cleared = 10)
        val result = DiscoveryResult.of(10, wasCompletedBeforeRun = false, progress = committed)!!
        assertEquals(10, result.totalCollected)
        assertEquals(4, result.worldCollected)
    }

    @Test
    fun `clearing the sixth of a world completes it, and the thirtieth completes the album`() {
        val sixth = DiscoveryResult.of(12, false, progressAfter(cleared = 12))!!
        assertTrue(sixth.worldComplete)
        assertFalse(sixth.allFound)
        assertTrue(sixth.spokenSummary.endsWith("World complete."))

        val last = DiscoveryResult.of(30, false, progressAfter(cleared = 30))!!
        assertTrue(last.worldComplete)
        assertTrue(last.allFound)
        assertTrue(last.spokenSummary.endsWith("All discoveries found."))
    }

    @Test
    fun `the spoken summary says the discovery, the collection and the count`() {
        val result = DiscoveryResult.of(10, true, progressAfter(cleared = 10))!!
        assertEquals(
            "Discovery found. Butterfly. Forest collection, 4 of 6 discovered.",
            result.spokenSummary
        )
        val first = DiscoveryResult.of(2, false, progressAfter(cleared = 2))!!
        assertTrue(first.spokenSummary.startsWith("New discovery. Flower."))
    }

    @Test
    fun `a level that hides nothing has no result`() {
        assertNull(DiscoveryResult.of(0, false, PlayerProgress()))
        assertNull(DiscoveryResult.of(31, false, PlayerProgress()))
        assertNotNull(DiscoveryResult.of(1, false, PlayerProgress()))
    }

    // ---- completion: the set a first clear finishes -------------------------------

    @Test
    fun `finishing a world on a first clear completes it, with its six discoveries and its words`() {
        val sixth = DiscoveryResult.of(6, false, progressAfter(cleared = 6))!!
        assertEquals(DiscoveryCompletion.WORLD, sixth.completion)
        assertEquals("SKY GARDEN COMPLETE!", sixth.completionTitle)
        assertEquals("6 / 6 DISCOVERED", sixth.completionCount)
        assertEquals(CampaignDiscoveries.forWorld(GameWorld.SKY_GARDEN), sixth.completionSet)
        assertEquals(6, sixth.completionSet.size)
    }

    @Test
    fun `a world that is not finished has no completion, even at five of six`() {
        val fifth = DiscoveryResult.of(5, false, progressAfter(cleared = 5))!!
        assertEquals(DiscoveryCompletion.NONE, fifth.completion)
        assertNull(fifth.completionTitle)
        assertNull(fifth.completionCount)
        assertTrue(fifth.completionSet.isEmpty())
    }

    @Test
    fun `replaying a level of a finished world does not repeat the milestone`() {
        // worldComplete is still true — the note on the result stays — but nothing was
        // *finished* by this clear.
        val replay = DiscoveryResult.of(6, true, progressAfter(cleared = 12))!!
        assertTrue(replay.worldComplete)
        assertEquals(DiscoveryCompletion.NONE, replay.completion)
        assertTrue(replay.completionSet.isEmpty())
    }

    @Test
    fun `the thirtieth discovery completes the album and shows each world by its finale`() {
        val last = DiscoveryResult.of(30, false, progressAfter(cleared = 30))!!
        assertEquals(DiscoveryCompletion.CAMPAIGN, last.completion)
        assertEquals("ALL DISCOVERIES FOUND", last.completionTitle)
        assertEquals("30 / 30", last.completionCount)
        assertEquals(
            listOf("Star", "Owl", "Treasure Chest", "Crown", "Galaxy"),
            last.completionSet.map { it.name }
        )
        // One per world, in world order.
        assertEquals(GameWorld.PROGRESSION, last.completionSet.map { it.world })
    }

    @Test
    fun `a completion set only ever holds discoveries the player has found`() {
        // Whatever a completion shows must already be collected: it exists only when
        // the whole set is, so it cannot name a discovery the player does not have.
        for (level in 1..30) {
            val progress = progressAfter(cleared = level)
            val result = DiscoveryResult.of(level, false, progress)!!
            result.completionSet.forEach { shown ->
                assertTrue(
                    "level $level shows ${shown.name}",
                    CampaignDiscoveries.isCollected(shown, progress)
                )
            }
        }
    }

    @Test
    fun `a completion says what it found in the spoken summary, not only in the artwork`() {
        val sixth = DiscoveryResult.of(6, false, progressAfter(cleared = 6))!!
        assertTrue(sixth.spokenSummary.contains("Sky Garden collection, 6 of 6 discovered"))
        assertTrue(sixth.spokenSummary.endsWith("World complete."))
    }
}
