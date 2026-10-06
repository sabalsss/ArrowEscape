package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.progress.PlayerProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Gold on the level grid means one thing: a best of three stars. Every other
 * completed state — one star, two stars, no star on record — is the plain completed
 * tile.
 */
class LevelTileStateTest {

    private val everyLevel = LevelProgression.all.map { it.id }

    /** All levels cleared, with [stars] as the best on [levelId] (absent when [StarRating.NONE]). */
    private fun cleared(levelId: Int, stars: Int) = PlayerProgress(
        highestUnlockedLevel = everyLevel.last(),
        currentLevel = everyLevel.last(),
        completedLevels = everyLevel.toSet(),
        bestStars = if (stars == StarRating.NONE) emptyMap() else mapOf(levelId to stars)
    )

    @Test
    fun `a three star best is the gold tile`() {
        assertEquals(TileState.PERFECT, tileStateFor(cleared(7, StarRating.MAX), 7))
    }

    @Test
    fun `one and two star bests are completed, never gold`() {
        assertEquals(TileState.COMPLETED, tileStateFor(cleared(7, 1), 7))
        assertEquals(TileState.COMPLETED, tileStateFor(cleared(7, 2), 7))
    }

    @Test
    fun `a clear with no star on record is completed, never gold`() {
        assertEquals(TileState.COMPLETED, tileStateFor(cleared(7, StarRating.NONE), 7))
    }

    @Test
    fun `gold is exactly three stars on every level of the catalogue`() {
        for (id in everyLevel) {
            for (stars in StarRating.NONE..StarRating.MAX) {
                val state = tileStateFor(cleared(id, stars), id)
                if (stars == StarRating.MAX) {
                    assertEquals("level $id, $stars stars", TileState.PERFECT, state)
                } else {
                    assertNotEquals("level $id, $stars stars", TileState.PERFECT, state)
                }
            }
        }
    }

    @Test
    fun `an uncleared level is never gold, whatever the record says`() {
        val progress = PlayerProgress(
            highestUnlockedLevel = 5,
            currentLevel = 5,
            completedLevels = setOf(1, 2, 3, 4),
            bestStars = mapOf(5 to StarRating.MAX)
        )
        assertEquals(TileState.UNLOCKED, tileStateFor(progress, 5))
    }

    @Test
    fun `locked and open levels keep their own states`() {
        val progress = PlayerProgress(
            highestUnlockedLevel = 3,
            currentLevel = 3,
            completedLevels = setOf(1, 2)
        )
        assertEquals(TileState.LOCKED, tileStateFor(progress, 4))
        assertEquals(TileState.UNLOCKED, tileStateFor(progress, 3))
        assertEquals(TileState.COMPLETED, tileStateFor(progress, 2))
    }
}
