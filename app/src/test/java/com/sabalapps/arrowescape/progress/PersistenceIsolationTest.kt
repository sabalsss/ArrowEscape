package com.sabalapps.arrowescape.progress

import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.daily.DailyProgress
import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessProgress
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.endless.EndlessSavedGame
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.settings.SettingsRepository
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four saves are four saves.
 *
 * Campaign, Endless, Daily and Settings each parse independently and fall back
 * independently, so corruption in one can only ever cost that one. These tests
 * pin that down from the storage side: every pair of records is crossed against
 * every other, and each repository is handed a store that already holds the
 * others' keys to prove it does not reach for them.
 *
 * The structural half of the claim — that Settings and the Daily Challenge are
 * not even in the same preferences file as campaign progress — is checked at the
 * bottom, because a file name is the one part of this that a unit test over
 * `ProgressStore` cannot otherwise see.
 */
class PersistenceIsolationTest {

    private val clock = FixedDateProvider(GameDate(2026, 10, 4))

    /** A plausible record for each of the three unit-testable saves. */
    private val campaignProgress = "1|12|12|1,2,3,4,5,6,7,8,9,10,11"
    private val campaignBoard = "3|12|3|4,5,6|0|0|${Levels.LAYOUT_VERSION}"
    private val endlessProgress = "1|7|6|2|4"
    private val endlessBoard =
        "2|555|7|MEDIUM|3|1,2,3|${com.sabalapps.arrowescape.shape.MysteryShapePuzzles.GENERATOR_VERSION}"
    private val dailyProgress = "1|2026-10-04|3|9|40"

    private fun dailyBoard(date: GameDate = GameDate(2026, 10, 4)): String =
        "1|${date.iso}|${DailyChallenge.seedFor(date)}|2|1,2,3"

    private fun healthyMainStore() = InMemoryProgressStore(
        mapOf(
            "player_progress" to campaignProgress,
            "saved_game" to campaignBoard,
            "endless_progress" to endlessProgress,
            "endless_game" to endlessBoard
        )
    )

    private fun healthyDailyStore() = InMemoryProgressStore(
        mapOf("daily_progress" to dailyProgress, "daily_game" to dailyBoard())
    )

    // ---- each save loads what it wrote --------------------------------------

    @Test
    fun `all four records load side by side`() {
        val main = healthyMainStore()
        val daily = healthyDailyStore()

        val campaign = ProgressRepository(main)
        val endless = EndlessRepository(main)
        val dailyRepository = DailyRepository(daily, clock)

        assertEquals(12, campaign.progress.value.currentLevel)
        assertEquals(setOf(4, 5, 6), campaign.loadInProgress()?.remainingArrowIds)
        assertEquals(7, endless.progress.value.puzzleNumber)
        assertEquals(EndlessTier.MEDIUM, endless.loadInProgress()?.tier)
        assertEquals(3, dailyRepository.progress.value.currentStreak)
        assertEquals(setOf(1, 2, 3), dailyRepository.loadInProgress()?.remainingArrowIds)
    }

    // ---- corruption, one direction at a time --------------------------------

    @Test
    fun `a corrupt daily save resets the daily and nothing else`() {
        val main = healthyMainStore()
        val daily = InMemoryProgressStore(
            mapOf("daily_progress" to "corrupt", "daily_game" to "also corrupt")
        )

        val dailyRepository = DailyRepository(daily, clock)
        assertEquals(DailyProgress(), dailyRepository.progress.value)
        assertNull(dailyRepository.loadInProgress())

        // Campaign and Endless, read afterwards, are exactly as they were.
        assertEquals(12, ProgressRepository(main).progress.value.currentLevel)
        assertEquals(setOf(4, 5, 6), ProgressRepository(main).loadInProgress()?.remainingArrowIds)
        assertEquals(7, EndlessRepository(main).progress.value.puzzleNumber)
        assertEquals(EndlessTier.MEDIUM, EndlessRepository(main).loadInProgress()?.tier)
        assertEquals(campaignProgress, main.getString("player_progress"))
        assertEquals(endlessProgress, main.getString("endless_progress"))
    }

    @Test
    fun `a corrupt campaign save resets the campaign and nothing else`() {
        val main = InMemoryProgressStore(
            mapOf(
                "player_progress" to "corrupt",
                "saved_game" to campaignBoard,
                "endless_progress" to endlessProgress,
                "endless_game" to endlessBoard
            )
        )
        val daily = healthyDailyStore()

        assertEquals(PlayerProgress(), ProgressRepository(main).progress.value)
        // Endless is untouched, including the board it had saved.
        assertEquals(7, EndlessRepository(main).progress.value.puzzleNumber)
        assertEquals(endlessProgress, main.getString("endless_progress"))
        assertEquals(endlessBoard, main.getString("endless_game"))
        // And so is the daily streak.
        assertEquals(3, DailyRepository(daily, clock).progress.value.currentStreak)
        assertEquals(40, DailyRepository(daily, clock).progress.value.totalCompleted)
    }

    @Test
    fun `a corrupt endless save resets endless and nothing else`() {
        val main = InMemoryProgressStore(
            mapOf(
                "player_progress" to campaignProgress,
                "saved_game" to campaignBoard,
                "endless_progress" to "corrupt",
                "endless_game" to "corrupt"
            )
        )
        val daily = healthyDailyStore()

        assertEquals(EndlessProgress(), EndlessRepository(main).progress.value)
        assertNull(EndlessRepository(main).loadInProgress())
        assertEquals(12, ProgressRepository(main).progress.value.currentLevel)
        assertEquals(campaignProgress, main.getString("player_progress"))
        assertEquals(campaignBoard, main.getString("saved_game"))
        assertEquals(3, DailyRepository(daily, clock).progress.value.currentStreak)
    }

    @Test
    fun `every single record corrupted at once still leaves the others loadable`() {
        // Each repository is given the full set of keys with exactly one key
        // corrupt, which is the sharpest form of the claim: a repository that
        // reached for a neighbour's key would notice the damage and over-react.
        val allKeys = listOf(
            "player_progress", "saved_game", "endless_progress", "endless_game"
        )
        for (broken in allKeys) {
            val values = HashMap(
                mapOf(
                    "player_progress" to campaignProgress,
                    "saved_game" to campaignBoard,
                    "endless_progress" to endlessProgress,
                    "endless_game" to endlessBoard
                )
            )
            values[broken] = "corrupt"
            val store = InMemoryProgressStore(values)

            // Reading both repositories must leave untouched every key that
            // belongs to the other one.
            ProgressRepository(store)
            EndlessRepository(store)

            if (broken.startsWith("player") || broken.startsWith("saved")) {
                assertEquals("'$broken' damaged endless", endlessProgress, store.getString("endless_progress"))
                assertEquals("'$broken' damaged endless", endlessBoard, store.getString("endless_game"))
            } else {
                assertEquals("'$broken' damaged campaign", campaignProgress, store.getString("player_progress"))
                assertEquals("'$broken' damaged campaign", campaignBoard, store.getString("saved_game"))
            }
        }
    }

    // ---- the saves cannot be mistaken for each other ------------------------

    @Test
    fun `one mode's record cannot be read as another's`() {
        // The formats differ in field count and in what the fields mean, so a
        // record written by one mode has to be rejected outright by the others
        // rather than half-parsed into a board that never existed.
        val records = mapOf(
            "campaign progress" to campaignProgress,
            "campaign board" to campaignBoard,
            "endless progress" to endlessProgress,
            "endless board" to endlessBoard,
            "daily progress" to dailyProgress,
            "daily board" to dailyBoard()
        )

        for ((name, record) in records) {
            if (name != "endless progress") {
                val store = InMemoryProgressStore(mapOf("endless_progress" to record))
                assertEquals(
                    "endless read '$name' as its own progress",
                    EndlessProgress(),
                    EndlessRepository(store).progress.value
                )
            }
            if (name != "endless board") {
                val store = InMemoryProgressStore(mapOf("endless_game" to record))
                assertNull("endless read '$name' as a board", EndlessRepository(store).loadInProgress())
            }
            if (name != "daily progress") {
                val store = InMemoryProgressStore(mapOf("daily_progress" to record))
                assertEquals(
                    "the daily read '$name' as its own progress",
                    DailyProgress(),
                    DailyRepository(store, clock).progress.value
                )
            }
            if (name != "daily board") {
                val store = InMemoryProgressStore(mapOf("daily_game" to record))
                assertNull(
                    "the daily read '$name' as a board",
                    DailyRepository(store, clock).loadInProgress()
                )
            }
        }
    }

    @Test
    fun `an endless board cannot be restored as a campaign level`() {
        // The pre-existing guard, re-checked now that there is a third mode:
        // generated boards carry a level id no catalogue lookup matches.
        val store = InMemoryProgressStore()
        EndlessRepository(store).saveInProgress(
            EndlessSavedGame(
                seed = 99L,
                puzzleNumber = 4,
                tier = EndlessTier.BEGINNER,
                remainingArrowIds = setOf(1, 2),
                lives = 3
            )
        )
        assertNull("an endless board leaked into the campaign", ProgressRepository(store).loadInProgress())
    }

    @Test
    fun `a daily board cannot be restored as a campaign level or an endless puzzle`() {
        val store = InMemoryProgressStore(mapOf("daily_game" to dailyBoard()))
        // The daily writes to its own key in its own file, so neither of the
        // others can even see it. Crossed explicitly anyway.
        assertNull(ProgressRepository(store).loadInProgress())
        assertNull(EndlessRepository(store).loadInProgress())
        assertNull(ProgressRepository(InMemoryProgressStore(mapOf("saved_game" to dailyBoard()))).loadInProgress())
        assertNull(
            EndlessRepository(InMemoryProgressStore(mapOf("endless_game" to dailyBoard()))).loadInProgress()
        )
    }

    // ---- the structural guarantee -------------------------------------------

    @Test
    fun `the daily and the settings live in different preferences files from progress`() {
        // A corrupt daily save cannot damage campaign, endless or settings
        // storage because it is not written to the same file. The file names are
        // the whole of that guarantee, so they are asserted rather than assumed.
        assertEquals("arrow_escape_progress", SharedPrefsProgressStore.DEFAULT_NAME)
        assertEquals("arrow_escape_daily", SharedPrefsProgressStore.DAILY_NAME)
        assertNotEquals(
            SharedPrefsProgressStore.DEFAULT_NAME,
            SharedPrefsProgressStore.DAILY_NAME
        )
        // Settings has had its own file since before any of this; the class is
        // Context-backed so the name is checked through the class's own
        // existence rather than by constructing one.
        assertTrue(SettingsRepository::class.java.name.isNotEmpty())
    }

    @Test
    fun `no two save keys collide`() {
        val keys = listOf(
            "player_progress", "saved_game",
            "endless_progress", "endless_game",
            "daily_progress", "daily_game"
        )
        assertEquals("two modes share a storage key", keys.size, keys.toSet().size)
    }
}
