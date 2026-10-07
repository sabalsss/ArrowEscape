package com.sabalapps.arrowescape.progress

import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence rules, exercised against an in-memory store. The store is the
 * only Android-shaped thing the repository touches, so every rule here —
 * unlocking, selection, save round-trips, and the fallbacks for corrupt data —
 * is covered without an emulator.
 */
class ProgressRepositoryTest {

    private val store = InMemoryProgressStore()
    private fun repo(s: ProgressStore = store) = ProgressRepository(s)

    /** A repository built over the bytes another one left behind. */
    private fun reopen() = ProgressRepository(store)

    /**
     * A version 3 board record, as the repository writes it: version, level,
     * lives, surviving ids, blocked taps, hint flag, layout version. Each test
     * below breaks exactly one field of an otherwise valid record.
     */
    private fun board(
        levelId: Int = 1,
        lives: Any = 3,
        ids: String = "0,1",
        layout: Any = Levels.LAYOUT_VERSION
    ) = "3|$levelId|$lives|$ids|0|0|$layout"

    // ---- fresh install ------------------------------------------------------

    @Test
    fun `a fresh install has only level one unlocked and nothing completed`() {
        val repo = repo()
        val progress = repo.progress.value

        assertEquals(1, progress.highestUnlockedLevel)
        assertEquals(1, progress.currentLevel)
        assertTrue(progress.completedLevels.isEmpty())
        assertTrue(repo.isUnlocked(1))
        (2..30).forEach { assertFalse("level $it should be locked", repo.isUnlocked(it)) }
        assertNull(repo.loadInProgress())
        assertEquals(1, repo.currentLevel().id)
    }

    // ---- unlocking ----------------------------------------------------------

    @Test
    fun `completing a level unlocks the next one and marks it done`() {
        val repo = repo()
        repo.markCompleted(1)

        assertTrue(repo.isUnlocked(2))
        assertFalse(repo.isUnlocked(3))
        assertEquals(setOf(1), repo.progress.value.completedLevels)
        assertTrue(repo.progress.value.isCompleted(1))
    }

    @Test
    fun `replaying an earlier level does not lock later ones again`() {
        val repo = repo()
        (1..4).forEach { repo.markCompleted(it) }
        assertEquals(5, repo.progress.value.highestUnlockedLevel)

        repo.markCompleted(2)

        assertEquals(5, repo.progress.value.highestUnlockedLevel)
        assertEquals(setOf(1, 2, 3, 4), repo.progress.value.completedLevels)
    }

    @Test
    fun `completing the last level does not unlock past the catalogue`() {
        val repo = repo()
        (1..30).forEach { repo.markCompleted(it) }

        assertEquals(30, repo.progress.value.highestUnlockedLevel)
        assertFalse(repo.isUnlocked(31))
    }

    @Test
    fun `completing a level that does not exist is ignored`() {
        val repo = repo()
        repo.markCompleted(99)

        assertEquals(1, repo.progress.value.highestUnlockedLevel)
        assertTrue(repo.progress.value.completedLevels.isEmpty())
    }

    // ---- selection ----------------------------------------------------------

    @Test
    fun `selecting an unlocked level sticks, selecting a locked one does nothing`() {
        val repo = repo()
        repo.markCompleted(1)
        repo.markCompleted(2)

        repo.selectLevel(2)
        assertEquals(2, repo.progress.value.currentLevel)

        repo.selectLevel(9)
        assertEquals(2, repo.progress.value.currentLevel)
    }

    @Test
    fun `the selected level survives a reopen`() {
        repo().apply {
            markCompleted(1)
            markCompleted(2)
            selectLevel(2)
        }

        assertEquals(2, reopen().progress.value.currentLevel)
        assertEquals(2, reopen().currentLevel().id)
    }

    @Test
    fun `completed levels survive a reopen`() {
        repo().apply {
            markCompleted(1)
            markCompleted(2)
            markCompleted(3)
        }

        val reopened = reopen()
        assertEquals(setOf(1, 2, 3), reopened.progress.value.completedLevels)
        assertEquals(4, reopened.progress.value.highestUnlockedLevel)
        assertTrue(reopened.isUnlocked(4))
        assertFalse(reopened.isUnlocked(5))
    }

    // ---- the unfinished board -----------------------------------------------

    @Test
    fun `an in-progress board round-trips through storage`() {
        val level = Levels.byId(8)!!
        val remaining = level.arrows.drop(4).map { it.id }.toSet()
        repo().saveInProgress(SavedGame(levelId = 8, remainingArrowIds = remaining, lives = 2))

        val restored = reopen().loadInProgress()

        assertEquals(SavedGame(8, remaining, 2), restored)
    }

    @Test
    fun `clearing the board leaves nothing to resume`() {
        val repo = repo()
        repo.saveInProgress(SavedGame(3, setOf(0, 1), lives = 3))
        repo.clearInProgress()

        assertNull(repo.loadInProgress())
        assertNull(reopen().loadInProgress())
    }

    @Test
    fun `the saved board is exposed as state, not re-read from storage`() {
        val repo = repo()
        assertNull(repo.savedGame.value)

        val game = SavedGame(5, setOf(0, 2, 4), lives = 1)
        repo.saveInProgress(game)
        assertEquals(game, repo.savedGame.value)

        repo.clearInProgress()
        assertNull(repo.savedGame.value)
    }

    // ---- corrupt data --------------------------------------------------------

    @Test
    fun `a save for a level that no longer exists is discarded`() {
        store.putString("saved_game", board(levelId = 99, ids = "0,1,2"))

        assertNull(repo().loadInProgress())
        assertNull(store.getString("saved_game"))
    }

    @Test
    fun `a save naming arrows that are not in the level is discarded`() {
        store.putString("saved_game", board(ids = "0,1,900"))
        assertNull(repo().loadInProgress())
    }

    @Test
    fun `a save with an impossible life count is discarded`() {
        store.putString("saved_game", board(lives = 0))
        assertNull(repo().loadInProgress())

        store.putString("saved_game", board(lives = GameState.STARTING_LIVES + 1))
        assertNull(repo().loadInProgress())
    }

    @Test
    fun `a save with an already empty board is discarded - that level is finished`() {
        store.putString("saved_game", board(ids = ""))
        assertNull(repo().loadInProgress())
    }

    @Test
    fun `garbage in the saved board is discarded`() {
        listOf(
            "",
            "nonsense",
            "3|1|3|0,1|0|0",         // no layout version
            "3|1|3|0,1|0|0|2|9",     // too many fields
            "4|1|3|0,1|0|0|2",       // a version from the future
            "3|abc|3|0,1|0|0|2",     // level id is not a number
            "3|1|three|0,1|0|0|2",   // lives is not a number
            "3|1|3|0,x,2|0|0|2",     // an arrow id is not a number
            "3|1|3|0,1|0|0|two"      // the layout version is not a number
        ).forEach { raw ->
            store.putString("saved_game", raw)
            assertNull("\"$raw\" should not restore", repo().loadInProgress())
        }
    }

    // ---- layouts changed: boards saved against the old ones --------------------

    @Test
    fun `a board saved by this layout version restores, and the record says which version it is`() {
        val saved = SavedGame(levelId = 7, remainingArrowIds = setOf(0, 3, 5), lives = 2)
        repo().saveInProgress(saved)

        assertTrue(
            "the record should end with the layout version",
            store.getString("saved_game")!!.endsWith("|${Levels.LAYOUT_VERSION}")
        )
        assertEquals(saved, reopen().loadInProgress())
    }

    @Test
    fun `a board saved against different layouts is discarded and wiped`() {
        // Same level, same ids, all in range — the only thing wrong is that the
        // ids were positions in a layout that no longer exists.
        val stale = listOf(
            "1|2|2|0,1",                                    // before stars: no tally, no layout field
            "2|2|2|0,1|1|1",                                // before layout versions
            board(levelId = 2, ids = "0,1", layout = 1),    // current format, the original layouts
            board(levelId = 2, ids = "0,1", layout = 4),    // the layouts before the Sky Garden onboarding order
            board(levelId = 2, ids = "0,1", layout = Levels.LAYOUT_VERSION + 1) // or a future set
        )
        for (raw in stale) {
            store.putString("saved_game", raw)
            assertNull("restored <$raw>", repo().loadInProgress())
            assertNull("left <$raw> on disk", store.getString("saved_game"))
        }
    }

    @Test
    fun `discarding a stale board leaves every completion, star and unlock alone`() {
        store.putString("player_progress", "2|9|4|1,2,3,4,5,6,7,8|1:3,2:2,5:1")
        store.putString("saved_game", "2|4|2|0,1,2|1|0")

        val repo = repo()

        assertNull(repo.loadInProgress())
        val progress = repo.progress.value
        assertEquals(9, progress.highestUnlockedLevel)
        assertEquals(4, progress.currentLevel)
        assertEquals((1..8).toSet(), progress.completedLevels)
        assertEquals(mapOf(1 to 3, 2 to 2, 5 to 1), progress.bestStars)
        assertEquals(
            "the progress record must not be rewritten by a board discard",
            "2|9|4|1,2,3,4,5,6,7,8|1:3,2:2,5:1",
            store.getString("player_progress")
        )
    }

    @Test
    fun `corrupt progress falls back to a fresh install and drops the stale board`() {
        store.putString("player_progress", "1|not-a-number|1|")
        store.putString("saved_game", board())

        val repo = repo()

        assertEquals(PlayerProgress(), repo.progress.value)
        assertNull(repo.loadInProgress())
        assertFalse(repo.isUnlocked(2))
    }

    @Test
    fun `progress pointing past the catalogue is rejected rather than clamped open`() {
        store.putString("player_progress", "1|999|999|")
        assertEquals(PlayerProgress(), repo().progress.value)
    }

    @Test
    fun `a selected level above the unlocked ceiling is pulled back`() {
        store.putString("player_progress", "1|3|9|1,2")

        val progress = repo().progress.value

        assertEquals(3, progress.highestUnlockedLevel)
        assertEquals(3, progress.currentLevel)
    }

    @Test
    fun `a completed list containing a level that does not exist is rejected`() {
        store.putString("player_progress", "1|2|1|1,77")
        assertEquals(PlayerProgress(), repo().progress.value)
    }
}
