package com.sabalapps.arrowescape.endless

import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import com.sabalapps.arrowescape.progress.ProgressRepository
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.progress.SavedGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Endless persistence, and in particular the two properties that matter most
 * when the save is damaged:
 *
 *  * **Nothing corrupt is ever believed.** Every field is range checked on the
 *    way back in, and a save that fails any check is discarded rather than
 *    guessed at, so the mode falls back to a fresh puzzle.
 *  * **Campaign progress is unreachable from here.** The two repositories write
 *    different keys and parse independently; a corrupt endless save must not be
 *    able to cost the player a single campaign level.
 */
class EndlessRepositoryTest {

    private fun repository(
        store: ProgressStore = InMemoryProgressStore(),
        seed: Long = 1234L
    ) = EndlessRepository(store, newSeed = { seed })

    private fun savedGame(
        seed: Long = 777L,
        puzzleNumber: Int = 3,
        tier: EndlessTier = EndlessTier.BEGINNER,
        ids: Set<Int> = setOf(0, 2, 5),
        lives: Int = 2
    ) = EndlessSavedGame(seed, puzzleNumber, tier, ids, lives)

    // ---- a fresh install ----------------------------------------------------

    @Test
    fun `a fresh install starts at puzzle one with nothing saved`() {
        val repository = repository()
        assertEquals(EndlessProgress(), repository.progress.value)
        assertEquals(EndlessProgress.FIRST_PUZZLE, repository.progress.value.puzzleNumber)
        assertEquals(EndlessTier.BEGINNER, repository.progress.value.tier)
        assertNull(repository.loadInProgress())
    }

    @Test
    fun `the seed source is what new puzzles are drawn from`() {
        assertEquals(42L, repository(seed = 42L).nextSeed())
    }

    // ---- progression --------------------------------------------------------

    @Test
    fun `completing a puzzle advances the number, the total and the streak`() {
        val repository = repository()
        repository.markCompleted()

        val progress = repository.progress.value
        assertEquals(2, progress.puzzleNumber)
        assertEquals(1, progress.totalCompleted)
        assertEquals(1, progress.currentStreak)
        assertEquals(1, progress.bestStreak)
    }

    @Test
    fun `the tier climbs as puzzles are completed`() {
        val repository = repository()
        assertEquals(EndlessTier.BEGINNER, repository.progress.value.tier)
        repeat(5) { repository.markCompleted() }
        assertEquals(EndlessTier.EASY, repository.progress.value.tier)
        repeat(10) { repository.markCompleted() }
        assertEquals(EndlessTier.MEDIUM, repository.progress.value.tier)
        repeat(15) { repository.markCompleted() }
        assertEquals(EndlessTier.HARD, repository.progress.value.tier)
        repeat(20) { repository.markCompleted() }
        assertEquals(EndlessTier.EXPERT, repository.progress.value.tier)
    }

    @Test
    fun `failing ends the streak but does not skip the puzzle`() {
        val repository = repository()
        repeat(3) { repository.markCompleted() }
        assertEquals(3, repository.progress.value.currentStreak)

        repository.markFailed()

        val progress = repository.progress.value
        assertEquals("the player gets another go at the same puzzle", 4, progress.puzzleNumber)
        assertEquals(0, progress.currentStreak)
        assertEquals("the best streak is a record, not a counter", 3, progress.bestStreak)
        assertEquals(3, progress.totalCompleted)
    }

    @Test
    fun `the best streak survives being beaten and re-beaten`() {
        val repository = repository()
        repeat(4) { repository.markCompleted() }
        repository.markFailed()
        repeat(2) { repository.markCompleted() }

        assertEquals(2, repository.progress.value.currentStreak)
        assertEquals(4, repository.progress.value.bestStreak)

        repeat(5) { repository.markCompleted() }
        assertEquals(7, repository.progress.value.bestStreak)
    }

    @Test
    fun `progress survives a relaunch`() {
        val store = InMemoryProgressStore()
        repository(store).apply {
            repeat(7) { markCompleted() }
            markFailed()
            repeat(2) { markCompleted() }
        }

        // A new repository over the same store is exactly what a relaunch is.
        val restored = repository(store).progress.value
        assertEquals(10, restored.puzzleNumber)
        assertEquals(9, restored.totalCompleted)
        assertEquals(2, restored.currentStreak)
        assertEquals(7, restored.bestStreak)
    }

    // ---- the unfinished board ------------------------------------------------

    @Test
    fun `an in-progress board round-trips exactly`() {
        val store = InMemoryProgressStore()
        val game = savedGame(seed = -98765L, puzzleNumber = 37, tier = EndlessTier.HARD, ids = setOf(3, 1, 9), lives = 1)
        repository(store).saveInProgress(game)

        assertEquals(game, repository(store).loadInProgress())
    }

    @Test
    fun `a negative seed round-trips`() {
        // Seeds come from a 64-bit mixer, so half of them are negative. A
        // parser that only accepted digits would lose half the puzzles.
        val store = InMemoryProgressStore()
        repository(store).saveInProgress(savedGame(seed = Long.MIN_VALUE))
        assertEquals(Long.MIN_VALUE, repository(store).loadInProgress()?.seed)
    }

    @Test
    fun `clearing removes the board and leaves progress alone`() {
        val store = InMemoryProgressStore()
        val repository = repository(store)
        repository.markCompleted()
        repository.saveInProgress(savedGame())
        repository.clearInProgress()

        assertNull(repository.loadInProgress())
        assertNull(repository.savedGame.value)
        assertEquals("progress is not a casualty of clearing the board", 1, repository.progress.value.totalCompleted)
        assertEquals(1, repository(store).progress.value.totalCompleted)
    }

    @Test
    fun `the saved-game flow tracks what is stored`() {
        val repository = repository()
        assertNull(repository.savedGame.value)

        val game = savedGame()
        repository.saveInProgress(game)
        assertEquals(game, repository.savedGame.value)

        repository.clearInProgress()
        assertNull(repository.savedGame.value)
    }

    // ---- corruption ----------------------------------------------------------

    @Test
    fun `a corrupt board save is discarded rather than guessed at`() {
        val corrupt = listOf(
            "",
            "garbage",
            "1|2|3",                                  // too few fields
            "1|2|3|BEGINNER|2|0,1|extra",             // too many fields
            "99|777|3|BEGINNER|2|0,1",                // a version this build cannot read
            "1|not-a-number|3|BEGINNER|2|0,1",        // unparseable seed
            "1|777|0|BEGINNER|2|0,1",                 // puzzle number below the first
            "1|777|-4|BEGINNER|2|0,1",                // negative puzzle number
            "1|777|99999999|BEGINNER|2|0,1",          // puzzle number past the ceiling
            "1|777|3|NIGHTMARE|2|0,1",                // a tier this build does not have
            "1|777|3|BEGINNER|0|0,1",                 // no lives left, so nothing to resume
            "1|777|3|BEGINNER|9|0,1",                 // more lives than the game allows
            "1|777|3|BEGINNER|-1|0,1",                // negative lives
            "1|777|3|BEGINNER|2|",                    // an empty board is not in progress
            "1|777|3|BEGINNER|2|0,oops"               // an id that is not a number
        )

        for (raw in corrupt) {
            val store = InMemoryProgressStore(mapOf("endless_game" to raw))
            val repository = repository(store)
            assertNull("'$raw' should not have parsed", repository.loadInProgress())
            // And the bad value is scrubbed, so it is not re-parsed every launch.
            assertNull("'$raw' should have been cleared from the store", store.getString("endless_game"))
        }
    }

    @Test
    fun `a corrupt progress save restarts endless and drops the board with it`() {
        val corrupt = listOf(
            "garbage",
            "1|2|3",                   // too few fields
            "99|2|1|1|1",              // unreadable version
            "1|abc|1|1|1",             // unparseable puzzle number
            "1|0|1|1|1",               // puzzle number below the first
            "1|2|-1|1|1",              // negative total
            "1|2|1|-1|1",              // negative streak
            "1|2|1|1|-1"               // negative best
        )

        for (raw in corrupt) {
            val store = InMemoryProgressStore(
                mapOf(
                    "endless_progress" to raw,
                    "endless_game" to "1|777|3|BEGINNER|2|0,1"
                )
            )
            val repository = repository(store)

            assertEquals("'$raw' should have reset endless", EndlessProgress(), repository.progress.value)
            // The board was saved against progress that is now gone, so it goes
            // too rather than being resumed at a puzzle number that no longer
            // exists.
            assertNull("'$raw' should have dropped the board", store.getString("endless_game"))
            assertNull(repository.loadInProgress())
        }
    }

    @Test
    fun `a best streak below the live streak is corrected rather than reported`() {
        val store = InMemoryProgressStore(mapOf("endless_progress" to "1|9|8|5|2"))
        val progress = repository(store).progress.value
        assertEquals(5, progress.currentStreak)
        assertEquals("a best the player has already beaten is not a best", 5, progress.bestStreak)
    }

    @Test
    fun `after a corrupt save the mode still works`() {
        // The point of the fallback: the player gets a puzzle, not an error.
        val store = InMemoryProgressStore(
            mapOf("endless_progress" to "junk", "endless_game" to "junk")
        )
        val repository = repository(store, seed = 555L)

        val progress = repository.progress.value
        val puzzle = PuzzleGenerator.generate(repository.nextSeed(), progress.tier)
        assertTrue(puzzle.level.arrows.isNotEmpty())
        assertTrue(BoardAnalysis.verifyOrder(puzzle.level.arrows, puzzle.solution))

        repository.markCompleted()
        assertEquals(2, repository.progress.value.puzzleNumber)
    }

    // ---- separation from the campaign ---------------------------------------

    @Test
    fun `endless and campaign share a store without touching each other`() {
        val store = InMemoryProgressStore()
        val campaign = ProgressRepository(store)
        val endless = repository(store)

        campaign.markCompleted(1)
        campaign.markCompleted(2)
        campaign.selectLevel(3)
        campaign.saveInProgress(SavedGame(levelId = 3, remainingArrowIds = setOf(1, 2), lives = 2))

        endless.markCompleted()
        endless.saveInProgress(savedGame())
        endless.clearInProgress()
        endless.markFailed()

        // Campaign state, read back fresh from the same store.
        val restored = ProgressRepository(store)
        assertEquals(setOf(1, 2), restored.progress.value.completedLevels)
        assertEquals(3, restored.progress.value.currentLevel)
        assertEquals(3, restored.loadInProgress()?.levelId)
        assertEquals(2, restored.loadInProgress()?.lives)
    }

    @Test
    fun `a corrupt endless save cannot cost the player campaign progress`() {
        val store = InMemoryProgressStore()
        ProgressRepository(store).apply {
            markCompleted(1)
            markCompleted(2)
            markCompleted(3)
            selectLevel(4)
            saveInProgress(SavedGame(levelId = 4, remainingArrowIds = setOf(0, 1, 2), lives = 3))
        }

        // Now wreck everything endless owns, and have endless read it back.
        store.putString("endless_progress", "\u0000 not even text")
        store.putString("endless_game", "0|0|0|0|0|0")
        val endless = repository(store)
        assertEquals(EndlessProgress(), endless.progress.value)
        assertNull(endless.loadInProgress())

        val campaign = ProgressRepository(store)
        assertEquals(setOf(1, 2, 3), campaign.progress.value.completedLevels)
        assertEquals(4, campaign.progress.value.currentLevel)
        assertNotNull("the campaign's board should be untouched", campaign.loadInProgress())
        assertEquals(4, campaign.loadInProgress()?.levelId)
    }

    @Test
    fun `a corrupt campaign save cannot cost the player endless progress`() {
        val store = InMemoryProgressStore()
        repository(store).apply {
            repeat(12) { markCompleted() }
            saveInProgress(savedGame(puzzleNumber = 13, tier = EndlessTier.EASY))
        }

        store.putString("player_progress", "wrecked")
        store.putString("saved_game", "wrecked")
        ProgressRepository(store)

        val endless = repository(store)
        assertEquals(13, endless.progress.value.puzzleNumber)
        assertEquals(12, endless.progress.value.totalCompleted)
        assertEquals(13, endless.loadInProgress()?.puzzleNumber)
    }

    @Test
    fun `lives are only ever stored within the range the game allows`() {
        val store = InMemoryProgressStore()
        for (lives in 1..GameState.STARTING_LIVES) {
            repository(store).saveInProgress(savedGame(lives = lives))
            assertEquals(lives, repository(store).loadInProgress()?.lives)
        }
    }
}
