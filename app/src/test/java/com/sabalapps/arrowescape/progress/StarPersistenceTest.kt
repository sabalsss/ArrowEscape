package com.sabalapps.arrowescape.progress

import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.game.StarRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The star record on disk: that a best is a best, that it survives a relaunch,
 * and that a save written before stars existed still loads.
 *
 * A relaunch here is a second repository over the same store, which is exactly
 * what a cold start looks like from in here.
 */
class StarPersistenceTest {

    private val store: ProgressStore = InMemoryProgressStore()

    private fun relaunch() = ProgressRepository(store)

    // ---- best, not last -----------------------------------------------------

    @Test
    fun `a first clear records its stars`() {
        val repo = relaunch()
        repo.markCompleted(1, 2)

        assertEquals(2, repo.starsFor(1))
        assertEquals(2, repo.progress.value.starsFor(1))
    }

    @Test
    fun `a better replay raises the best`() {
        val repo = relaunch()
        repo.markCompleted(1, 1)
        repo.markCompleted(1, 3)

        assertEquals(3, repo.starsFor(1))
    }

    @Test
    fun `a worse replay does not take the best away`() {
        val repo = relaunch()
        repo.markCompleted(1, 3)
        repo.markCompleted(1, 1)

        assertEquals(3, repo.starsFor(1))
    }

    @Test
    fun `an equal replay leaves the best where it is`() {
        val repo = relaunch()
        repo.markCompleted(1, 2)
        repo.markCompleted(1, 2)

        assertEquals(2, repo.starsFor(1))
    }

    @Test
    fun `completing without a rating does not disturb a recorded best`() {
        val repo = relaunch()
        repo.markCompleted(1, 3)
        repo.markCompleted(1, StarRating.NONE)

        assertEquals(3, repo.starsFor(1))
    }

    @Test
    fun `an unrated clear has no stars`() {
        val repo = relaunch()
        repo.markCompleted(1)

        assertEquals(StarRating.NONE, repo.starsFor(1))
        assertTrue(repo.progress.value.isCompleted(1))
    }

    @Test
    fun `stars on a level outside the catalogue are not recorded`() {
        val repo = relaunch()
        repo.markCompleted(99, 3)

        assertEquals(StarRating.NONE, repo.starsFor(99))
        assertTrue(repo.progress.value.bestStars.isEmpty())
    }

    @Test
    fun `each level keeps its own best`() {
        val repo = relaunch()
        repo.markCompleted(1, 3)
        repo.markCompleted(2, 1)
        repo.markCompleted(3, 2)

        assertEquals(3, repo.starsFor(1))
        assertEquals(1, repo.starsFor(2))
        assertEquals(2, repo.starsFor(3))
        assertEquals(StarRating.NONE, repo.starsFor(4))
    }

    // ---- across a relaunch --------------------------------------------------

    @Test
    fun `stars survive a relaunch`() {
        relaunch().apply {
            markCompleted(1, 3)
            markCompleted(2, 1)
        }

        val reopened = relaunch()
        assertEquals(3, reopened.starsFor(1))
        assertEquals(1, reopened.starsFor(2))
    }

    @Test
    fun `a best cannot be downgraded by a replay after a relaunch`() {
        relaunch().markCompleted(1, 3)
        relaunch().markCompleted(1, 1)

        assertEquals(3, relaunch().starsFor(1))
    }

    // ---- compatibility with saves written before stars existed --------------

    @Test
    fun `a version one progress record still loads and simply has no stars`() {
        store.putString("player_progress", "1|5|3|1,2,3,4")

        val repo = relaunch()
        assertEquals(5, repo.progress.value.highestUnlockedLevel)
        assertEquals(3, repo.progress.value.currentLevel)
        assertEquals(setOf(1, 2, 3, 4), repo.progress.value.completedLevels)
        assertTrue(repo.progress.value.bestStars.isEmpty())
    }

    @Test
    fun `a star earned after upgrading joins a version one record`() {
        store.putString("player_progress", "1|5|3|1,2,3,4")
        relaunch().markCompleted(3, 2)

        val reopened = relaunch()
        assertEquals(2, reopened.starsFor(3))
        assertEquals(setOf(1, 2, 3, 4), reopened.progress.value.completedLevels)
        assertEquals(5, reopened.progress.value.highestUnlockedLevel)
    }

    /**
     * Until Discovery Phase 2 an older board was a perfectly good board and was
     * resumed with an empty tally. The Campaign layouts have since been redrawn,
     * and a saved board is only arrow ids — positions in a layout — so a record
     * from before the layout version existed (version 1 or 2) cannot be restored
     * onto the new layouts. It is discarded; the level starts again, and nothing
     * about progress or stars is touched.
     */
    @Test
    fun `an in-progress board from before layout versions is discarded, not resumed`() {
        for (raw in listOf("1|2|2|0,1", "2|2|2|0,1|1|1")) {
            store.putString("saved_game", raw)

            assertNull("resumed <$raw>", relaunch().loadInProgress())
            assertNull("left <$raw> on disk", store.getString("saved_game"))
        }
    }

    // ---- the in-progress tally ----------------------------------------------

    @Test
    fun `the run tally round trips through the saved board`() {
        relaunch().saveInProgress(
            SavedGame(
                levelId = 2,
                remainingArrowIds = setOf(0, 1),
                lives = 2,
                blockedTaps = 1,
                hintUsed = true
            )
        )

        val saved = relaunch().loadInProgress()
        assertNotNull(saved)
        assertEquals(1, saved!!.blockedTaps)
        assertTrue(saved.hintUsed)
    }

    // ---- corruption ---------------------------------------------------------

    @Test
    fun `a malformed stars field is rejected rather than guessed at`() {
        val bad = listOf(
            "2|5|3|1,2,3|3",          // a star with no level id
            "2|5|3|1,2,3|1:x",        // the rating is not a number
            "2|5|3|1,2,3|x:3",        // the level id is not a number
            "2|5|3|1,2,3|1:4",        // four stars does not exist
            "2|5|3|1,2,3|1:0",        // nor does a zero-star clear
            "2|5|3|1,2,3|99:3",       // outside the catalogue
            "2|5|3|1,2,3|1:3,1:2",    // the same level twice
            "2|5|3|1,2,3"             // version 2 with a field missing
        )
        for (raw in bad) {
            store.putString("player_progress", raw)
            val repo = relaunch()
            assertEquals("accepted <$raw>", PlayerProgress(), repo.progress.value)
            assertNull("left <$raw> on disk", store.getString("player_progress"))
        }
    }

    @Test
    fun `a malformed run tally is rejected rather than guessed at`() {
        val layout = Levels.LAYOUT_VERSION
        val bad = listOf(
            "3|1|3|0,1|x|0|$layout",   // blocked taps is not a number
            "3|1|3|0,1|-1|0|$layout",  // nor a negative one
            "3|1|3|0,1|0|2|$layout",   // the hint flag is not a flag
            "3|1|3|0,1|0|0",           // version 3 with the layout field missing
            "3|1|3|0,1|0|0|$layout|0", // or one too many
            "4|1|3|0,1|0|0|$layout"    // a version from the future
        )
        for (raw in bad) {
            store.putString("saved_game", raw)
            assertNull("accepted <$raw>", relaunch().loadInProgress())
            assertNull("left <$raw> on disk", store.getString("saved_game"))
        }
    }

    @Test
    fun `a star against a level that was never cleared is dropped`() {
        store.putString("player_progress", "2|5|3|1,2|4:3")

        val repo = relaunch()
        assertEquals(setOf(1, 2), repo.progress.value.completedLevels)
        assertEquals(StarRating.NONE, repo.starsFor(4))
    }
}
