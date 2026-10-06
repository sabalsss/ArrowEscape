package com.sabalapps.arrowescape.daily

import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.time.FixedDateProvider
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streak rules and the daily save, driven through a clock the test owns.
 *
 * Not one of these reads the machine's real date. Every "and then it was
 * tomorrow" is [FixedDateProvider.advance], which is the entire reason
 * `DateProvider` exists — a streak test that depended on the wall clock could
 * only ever check one day's worth of behaviour, and would do it differently
 * depending on when it ran.
 *
 * A relaunch is modelled by handing the same store to a brand new repository,
 * which is what Android killing the app and the player coming back looks like
 * from in here.
 */
class DailyRepositoryTest {

    private val store: ProgressStore = InMemoryProgressStore()
    private val clock = FixedDateProvider(GameDate(2026, 10, 4))

    private fun relaunch(): DailyRepository = DailyRepository(store, clock)

    private fun repository(): DailyRepository = relaunch()

    private fun save(repository: DailyRepository, lives: Int = 3, ids: Set<Int> = setOf(1, 2, 3)) {
        val date = repository.today()
        repository.saveInProgress(
            DailySavedGame(
                date = date,
                seed = DailyChallenge.seedFor(date),
                remainingArrowIds = ids,
                lives = lives
            )
        )
    }

    // ---- a fresh install -----------------------------------------------------

    @Test
    fun `a fresh install has no streak and nothing in progress`() {
        val repository = repository()
        assertEquals(DailyProgress(), repository.progress.value)
        assertNull(repository.loadInProgress())
        assertFalse(repository.isTodayCompleted())
        assertEquals(0, repository.progress.value.totalCompleted)
    }

    @Test
    fun `today is read through the injected clock`() {
        val repository = repository()
        assertEquals(GameDate(2026, 10, 4), repository.today())
        clock.advance()
        assertEquals(GameDate(2026, 10, 5), repository.today())
    }

    // ---- the streak rules ----------------------------------------------------

    @Test
    fun `the first completion starts the streak at one`() {
        val repository = repository()
        repository.markCompleted()

        val progress = repository.progress.value
        assertEquals(1, progress.currentStreak)
        assertEquals(1, progress.bestStreak)
        assertEquals(1, progress.totalCompleted)
        assertEquals(GameDate(2026, 10, 4), progress.lastCompletedDate)
        assertTrue(repository.isTodayCompleted())
    }

    @Test
    fun `completing consecutive days extends the streak`() {
        val repository = repository()
        repeat(5) {
            repository.markCompleted()
            clock.advance()
        }
        val progress = repository.progress.value
        assertEquals(5, progress.currentStreak)
        assertEquals(5, progress.bestStreak)
        assertEquals(5, progress.totalCompleted)
    }

    @Test
    fun `a skipped day resets the streak to one`() {
        val repository = repository()
        repeat(4) {
            repository.markCompleted()
            clock.advance()
        }
        assertEquals(4, repository.progress.value.currentStreak)

        // Miss a day entirely, then come back.
        clock.advance()
        repository.markCompleted()

        val progress = repository.progress.value
        assertEquals("a gap starts a new streak", 1, progress.currentStreak)
        assertEquals("the best is not forgotten", 4, progress.bestStreak)
        assertEquals("the total still counts the day", 5, progress.totalCompleted)
    }

    @Test
    fun `a long gap resets the streak to one`() {
        val repository = repository()
        repository.markCompleted()
        clock.advance(days = 400)
        repository.markCompleted()

        assertEquals(1, repository.progress.value.currentStreak)
        assertEquals(2, repository.progress.value.totalCompleted)
    }

    @Test
    fun `completing the same day again changes nothing at all`() {
        val repository = repository()
        repository.markCompleted()
        val afterFirst = repository.progress.value

        repeat(5) { repository.markCompleted() }

        assertEquals("a replay must not move any counter", afterFirst, repository.progress.value)
        assertEquals(1, repository.progress.value.currentStreak)
        assertEquals(1, repository.progress.value.totalCompleted)
    }

    @Test
    fun `a replay in the middle of a streak does not extend it`() {
        val repository = repository()
        repeat(3) {
            repository.markCompleted()
            clock.advance()
        }
        // Back on day three, replay it half a dozen times.
        clock.advance(days = -1)
        repeat(6) { repository.markCompleted() }

        assertEquals(3, repository.progress.value.currentStreak)
        assertEquals(3, repository.progress.value.totalCompleted)
    }

    @Test
    fun `the best streak survives the current one being broken`() {
        val repository = repository()
        repeat(7) {
            repository.markCompleted()
            clock.advance()
        }
        clock.advance(days = 3)
        repository.markCompleted()

        assertEquals(1, repository.progress.value.currentStreak)
        assertEquals(7, repository.progress.value.bestStreak)
    }

    @Test
    fun `a clock moved backwards starts a new streak rather than crediting one`() {
        // Documented behaviour rather than a guarantee: the app is offline, so
        // the date is whatever the device says. What matters is that nothing
        // breaks and that going backwards is never worth more than going
        // forwards — see DateProvider.
        val repository = repository()
        repeat(3) {
            repository.markCompleted()
            clock.advance()
        }
        clock.advance(days = -30)
        repository.markCompleted()

        assertEquals(1, repository.progress.value.currentStreak)
        assertEquals(3, repository.progress.value.bestStreak)
    }

    // ---- persistence ---------------------------------------------------------

    @Test
    fun `the streak survives a relaunch`() {
        val first = relaunch()
        repeat(4) {
            first.markCompleted()
            clock.advance()
        }
        val expected = first.progress.value

        assertEquals(expected, relaunch().progress.value)
    }

    @Test
    fun `the total and the best survive a relaunch after a break`() {
        val first = relaunch()
        repeat(6) {
            first.markCompleted()
            clock.advance()
        }
        clock.advance(days = 5)
        first.markCompleted()

        val reopened = relaunch()
        assertEquals(1, reopened.progress.value.currentStreak)
        assertEquals(6, reopened.progress.value.bestStreak)
        assertEquals(7, reopened.progress.value.totalCompleted)
    }

    @Test
    fun `a board in progress survives a relaunch exactly`() {
        val first = relaunch()
        save(first, lives = 2, ids = setOf(3, 7, 11))

        val restored = relaunch().loadInProgress()
        assertNotNull(restored)
        assertEquals(GameDate(2026, 10, 4), restored!!.date)
        assertEquals(DailyChallenge.seedFor(GameDate(2026, 10, 4)), restored.seed)
        assertEquals(setOf(3, 7, 11), restored.remainingArrowIds)
        assertEquals(2, restored.lives)
    }

    @Test
    fun `clearing the board in progress removes it`() {
        val repository = relaunch()
        save(repository)
        repository.clearInProgress()

        assertNull(repository.loadInProgress())
        assertNull(relaunch().loadInProgress())
    }

    // ---- yesterday's board ---------------------------------------------------

    @Test
    fun `an unfinished board from yesterday is not offered today`() {
        val first = relaunch()
        save(first, lives = 1, ids = setOf(2, 5))
        assertNotNull("it should be there on the day it was saved", first.loadInProgress())

        clock.advance()

        val today = relaunch()
        assertNull("yesterday's board must not stand in for today's", today.loadInProgress())
    }

    @Test
    fun `discarding yesterday's board also wipes it from storage`() {
        val first = relaunch()
        save(first)
        clock.advance()

        // The first read discards it; a second repository must not find it again.
        assertNull(relaunch().loadInProgress())
        assertNull(relaunch().loadInProgress())
        assertNull(store.getString("daily_game"))
    }

    @Test
    fun `yesterday's unfinished board does not affect the streak`() {
        val first = relaunch()
        save(first)
        clock.advance()
        relaunch().loadInProgress()

        assertEquals("an unfinished day is not a completed day", DailyProgress(), relaunch().progress.value)
    }

    // ---- corruption ----------------------------------------------------------

    @Test
    fun `a corrupt progress record resets the daily challenge to a fresh state`() {
        for (corrupt in listOf(
            "", "garbage", "1", "1|2026-10-04", "1|2026-10-04|1|1",
            "2|2026-10-04|1|1|1", // a version from the future
            "1|not-a-date|1|1|1",
            "1|2026-02-30|1|1|1", // a date that is not a day
            "1|2026-10-04|x|1|1",
            "1|2026-10-04|-1|1|1",
            "1||3|3|3", // a streak with no day behind it
            "1|2026-10-04|0|0|0", // a day recorded with nothing counted
            "1|2026-10-04|5|5|2", // fewer days cleared than the streak needs
            "1|2026-10-04|1|1|99999999" // a count past the sanity ceiling
        )) {
            val store = InMemoryProgressStore(mapOf("daily_progress" to corrupt))
            val repository = DailyRepository(store, clock)
            assertEquals("'$corrupt' should have reset", DailyProgress(), repository.progress.value)
            assertNull("'$corrupt' should have been wiped", store.getString("daily_progress"))
        }
    }

    @Test
    fun `a corrupt saved board is treated as absent`() {
        val date = GameDate(2026, 10, 4)
        val seed = DailyChallenge.seedFor(date)
        for (corrupt in listOf(
            "", "garbage", "1|2026-10-04|$seed|3", "2|2026-10-04|$seed|3|1",
            "1|not-a-date|$seed|3|1",
            "1|2026-10-04|notanumber|3|1",
            "1|2026-10-04|$seed|0|1", // zero lives is a lost board, not one to resume
            "1|2026-10-04|$seed|4|1", // more lives than the game ever hands out
            "1|2026-10-04|$seed|3|", // an empty board is a finished one
            "1|2026-10-04|$seed|3|1,x",
            "1|2026-10-04|999|3|1" // a seed this date does not derive to
        )) {
            val store = InMemoryProgressStore(mapOf("daily_game" to corrupt))
            val repository = DailyRepository(store, clock)
            assertNull("'$corrupt' should not have loaded", repository.loadInProgress())
            assertNull("'$corrupt' should have been wiped", store.getString("daily_game"))
        }
    }

    @Test
    fun `a corrupt progress record drops the board saved against it`() {
        val date = GameDate(2026, 10, 4)
        val store = InMemoryProgressStore(
            mapOf(
                "daily_progress" to "garbage",
                "daily_game" to "1|${date.iso}|${DailyChallenge.seedFor(date)}|3|1,2"
            )
        )
        val repository = DailyRepository(store, clock)

        assertEquals(DailyProgress(), repository.progress.value)
        assertNull(repository.loadInProgress())
    }

    @Test
    fun `a corrupt daily save leaves every other mode's keys untouched`() {
        // The strong form of the isolation claim: the repository is handed a
        // store that also holds campaign, endless and settings keys, and must
        // not touch any of them while resetting itself. In the app they are not
        // even in the same preferences file.
        val others = mapOf(
            "player_progress" to "1|12|12|1,2,3",
            "saved_game" to "1|12|3|4,5",
            "endless_progress" to "1|7|6|2|4",
            "endless_game" to "1|555|7|MEDIUM|3|1,2",
            "sound_enabled" to "false"
        )
        val store = InMemoryProgressStore(others + mapOf("daily_progress" to "garbage"))
        DailyRepository(store, clock)

        for ((key, value) in others) {
            assertEquals("the daily reset damaged '$key'", value, store.getString(key))
        }
    }

    @Test
    fun `a daily record written by this version reads back identically`() {
        // The round trip through the delimited format, including the "never
        // completed" case where the date field is empty.
        val first = relaunch()
        assertEquals(DailyProgress(), relaunch().progress.value)

        repeat(3) {
            first.markCompleted()
            clock.advance()
        }
        clock.advance(days = -1)
        assertEquals(first.progress.value, relaunch().progress.value)
    }
}
