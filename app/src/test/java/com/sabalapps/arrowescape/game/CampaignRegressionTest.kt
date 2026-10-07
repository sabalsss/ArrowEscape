package com.sabalapps.arrowescape.game

import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.PuzzleGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * A lock on the curated campaign.
 *
 * Endless Mode shares `GameState`, `MoveValidator`, `ArrowTile`, `Direction` and
 * the whole of `GameScreen` with the campaign, which is the point — there is one
 * copy of the gameplay. The risk that comes with sharing is that work on the
 * generator quietly changes what a campaign level *is*, and the invariant tests
 * in [LevelCatalogueTest] would not all notice: a level swapped for a different
 * board of the same shape and arrow count passes every one of them.
 *
 * So this pins the catalogue exactly. The fingerprint covers every level's id,
 * dimensions and full arrow list, in order. If it fails, a curated level was
 * edited, reordered, replaced — or substituted with a generated board, which
 * must never happen. That is either a deliberate catalogue change, in which case
 * the expected value below is updated along with it **and `Levels.LAYOUT_VERSION`
 * is bumped** (an in-progress Campaign save is only valid for the layouts it was
 * written against), or a bug.
 *
 * The expected values below describe the thirty discovery-shape layouts as paced
 * in the engagement pass (`Levels.LAYOUT_VERSION` 5): the Phase 2 silhouettes with
 * Levels 5, 6, 9, 11, 12, 17, 23 and 29 re-authored for pacing (version 3), then Levels
 * 10, 17, 21, 24 and 27 redrawn as clearer pictures (version 4), then the Sky Garden's
 * first-session order (Heart, Star, Kite, Bird, Cloud, Flower — Levels 1-6, version 5).
 * Version 2 was the Phase 2 set; the earlier abstract layouts were version 1.
 */
class CampaignRegressionTest {

    /**
     * SHA-256 over the thirty shipped levels. Levels are persisted by id, so a
     * change here is a change to boards players already have progress against.
     */
    private val expectedFingerprint =
        "8e3cc50d10d6b91bd50b5b5f998b28d276cd57b29754d0036fab1ce4a586ac96"

    @Test
    fun `the thirty curated levels are byte-for-byte unchanged`() {
        assertEquals(
            "the campaign catalogue changed — update this fingerprint AND bump " +
                "Levels.LAYOUT_VERSION; see this test's documentation",
            expectedFingerprint,
            fingerprint()
        )
    }

    @Test
    fun `the layout version names the paced discovery shape layouts`() {
        // Moves together with the fingerprint above: an in-progress save written
        // against any other value is discarded rather than restored onto these.
        assertEquals(5, Levels.LAYOUT_VERSION)
    }

    @Test
    fun `the catalogue is still thirty levels numbered one to thirty`() {
        assertEquals(30, Levels.ALL.size)
        assertEquals((1..30).toList(), Levels.ALL.map { it.id })
        assertEquals(30, LevelProgression.count)
        assertEquals(1, LevelProgression.first.id)
        assertEquals(30, LevelProgression.last.id)
    }

    @Test
    fun `arrow counts and board shapes are the shipped ones`() {
        assertEquals(
            listOf(11, 11, 12, 12, 12, 12, 12, 14, 14, 15, 15, 15, 17, 17, 17, 20, 22, 22, 22, 23,
                24, 24, 28, 28, 28, 28, 32, 32, 34, 35),
            Levels.ALL.map { it.arrows.size }
        )
        // rows x columns. The silhouettes are not rectangles: a board is as tall
        // and wide as its picture (Level 1 alone adds a margin row top and bottom).
        assertEquals(
            listOf("4x5", "4x5", "6x5", "4x6", "3x6", "5x5", "5x5", "4x6", "6x5", "4x5",
                "5x5", "5x5", "5x5", "7x5", "4x6", "5x6", "6x6", "5x6", "5x6", "7x6",
                "6x6", "8x6", "8x6", "6x6", "8x6", "8x6", "8x6", "8x6", "8x6", "9x6"),
            Levels.ALL.map { "${it.rows}x${it.columns}" }
        )
    }

    @Test
    fun `level names are the ones progress and the UI were built against`() {
        for (level in Levels.ALL) {
            assertEquals("Level ${level.id}", level.name)
        }
    }

    @Test
    fun `no generated board can enter the catalogue`() {
        // Generated boards carry an id no catalogue lookup matches, which is
        // what stops a campaign save from restoring an endless board and vice
        // versa. If this id ever collided, Level Select would start offering
        // generated puzzles.
        assertNull(
            "the endless level id now collides with the catalogue",
            Levels.byId(PuzzleGenerator.ENDLESS_LEVEL_ID)
        )
        assertTrue(
            "every catalogue id must differ from the endless id",
            Levels.ALL.none { it.id == PuzzleGenerator.ENDLESS_LEVEL_ID }
        )

        for (tier in EndlessTier.entries) {
            for (seed in 1L..20L) {
                val generated = PuzzleGenerator.generate(seed, tier).level
                assertEquals(PuzzleGenerator.ENDLESS_LEVEL_ID, generated.id)
                assertNull(
                    "tier=$tier seed=$seed produced a board the catalogue would claim",
                    Levels.byId(generated.id)
                )
            }
        }
    }

    @Test
    fun `campaign progression still walks the catalogue end to end`() {
        var level = LevelProgression.first
        val visited = ArrayList<Int>()
        while (true) {
            visited += level.id
            if (LevelProgression.isLast(level)) break
            level = LevelProgression.next(level)
            check(visited.size <= 40) { "progression did not terminate" }
        }
        assertEquals((1..30).toList(), visited)
        assertEquals(
            "the last level has nothing to advance to",
            LevelProgression.last.id,
            LevelProgression.next(LevelProgression.last).id
        )
    }

    private fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (level in Levels.ALL) {
            digest.update("${level.id}:${level.rows}x${level.columns}:".toByteArray())
            for (arrow in level.arrows) {
                digest.update(
                    "${arrow.id},${arrow.row},${arrow.col},${arrow.direction.name};".toByteArray()
                )
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
