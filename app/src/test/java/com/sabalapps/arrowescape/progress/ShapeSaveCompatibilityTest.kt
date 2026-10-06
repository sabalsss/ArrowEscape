package com.sabalapps.arrowescape.progress

import com.sabalapps.arrowescape.daily.DailyChallenge
import com.sabalapps.arrowescape.daily.DailyRepository
import com.sabalapps.arrowescape.endless.EndlessRepository
import com.sabalapps.arrowescape.endless.EndlessSavedGame
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.SeededRandom
import com.sabalapps.arrowescape.shape.MysteryShapePuzzles
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What happens to a board saved by the generator that came before the shape system: it is dropped —
 * it describes arrows this build can no longer produce — and **nothing else is touched**. Progress,
 * streaks and totals keep their own records and their own versions.
 */
class ShapeSaveCompatibilityTest {

    private val today = GameDate(2026, 10, 4)

    // ---- Endless ----------------------------------------------------------------

    @Test
    fun `an endless board from the abstract generator is discarded and the counters survive`() {
        val store = InMemoryProgressStore()
        store.putString("endless_progress", "1|9|8|2|5")
        store.putString("endless_game", "1|555|7|MEDIUM|3|1,2,3") // six fields, version 1: the old record

        val repo = EndlessRepository(store)

        assertNull(repo.loadInProgress())
        assertNull("the stale record is wiped, not left to be re-read", store.getString("endless_game"))
        val progress = repo.progress.value
        assertEquals(9, progress.puzzleNumber)
        assertEquals(8, progress.totalCompleted)
        assertEquals(2, progress.currentStreak)
        assertEquals(5, progress.bestStreak)
    }

    @Test
    fun `an endless board naming another generator version is discarded`() {
        val store = InMemoryProgressStore()
        val other = MysteryShapePuzzles.GENERATOR_VERSION + 1
        store.putString("endless_game", "2|555|7|MEDIUM|3|1,2,3|$other")
        assertNull(EndlessRepository(store).loadInProgress())
        store.putString("endless_game", "2|555|7|MEDIUM|3|1,2,3|0")
        assertNull(EndlessRepository(store).loadInProgress())
    }

    @Test
    fun `an endless board from the current generator round trips`() {
        val store = InMemoryProgressStore()
        val saved = EndlessSavedGame(
            seed = 123456789L, puzzleNumber = 12, tier = EndlessTier.EASY,
            remainingArrowIds = setOf(1, 4, 9), lives = 2
        )
        EndlessRepository(store).saveInProgress(saved)
        assertEquals(
            "2|123456789|12|EASY|2|1,4,9|${MysteryShapePuzzles.GENERATOR_VERSION}",
            store.getString("endless_game")
        )
        assertEquals(saved, EndlessRepository(store).loadInProgress())
    }

    @Test
    fun `a truncated or padded endless board record is discarded`() {
        val store = InMemoryProgressStore()
        store.putString("endless_game", "2|555|7|MEDIUM|3|1,2,3")
        assertNull(EndlessRepository(store).loadInProgress())
        store.putString("endless_game", "2|555|7|MEDIUM|3|1,2,3|1|extra")
        assertNull(EndlessRepository(store).loadInProgress())
    }

    @Test
    fun `writing a progress counter does not disturb a saved board`() {
        val store = InMemoryProgressStore()
        val repo = EndlessRepository(store)
        repo.saveInProgress(
            EndlessSavedGame(seed = 9L, puzzleNumber = 1, tier = EndlessTier.BEGINNER, remainingArrowIds = setOf(2), lives = 3)
        )
        repo.markCompleted()
        assertNotNull(EndlessRepository(store).loadInProgress())
        assertEquals(2, EndlessRepository(store).progress.value.puzzleNumber)
    }

    // ---- Daily ------------------------------------------------------------------

    private fun oldSchemeSeed(date: GameDate): Long =
        SeededRandom.derive(DailyChallenge.stableHash("daily${date.iso}v1"))

    @Test
    fun `a daily board saved under the abstract scheme is discarded and the streak survives`() {
        val store = InMemoryProgressStore()
        store.putString("daily_progress", "1|2026-10-03|4|6|10")
        store.putString("daily_game", "1|${today.iso}|${oldSchemeSeed(today)}|2|1,2,3")

        val repo = DailyRepository(store, FixedDateProvider(today))

        assertNull(repo.loadInProgress())
        assertNull("the stale board is wiped", store.getString("daily_game"))
        val progress = repo.progress.value
        assertEquals(4, progress.currentStreak)
        assertEquals(6, progress.bestStreak)
        assertEquals(10, progress.totalCompleted)
        assertEquals(GameDate(2026, 10, 3), progress.lastCompletedDate)
    }

    @Test
    fun `a daily board from the current scheme is kept`() {
        val store = InMemoryProgressStore()
        store.putString("daily_game", "1|${today.iso}|${DailyChallenge.seedFor(today)}|2|1,2,3")
        assertNotNull(DailyRepository(store, FixedDateProvider(today)).loadInProgress())
    }

    @Test
    fun `the old and new daily schemes derive different seeds for the same day`() {
        for (day in 0 until 30) {
            val date = GameDate(2026, 10, 1).plusDays(day.toLong())
            assert(oldSchemeSeed(date) != DailyChallenge.seedFor(date))
        }
    }
}
