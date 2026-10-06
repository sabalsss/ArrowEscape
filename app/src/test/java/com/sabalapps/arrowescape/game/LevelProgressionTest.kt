package com.sabalapps.arrowescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelProgressionTest {

    @Test
    fun `progression is the catalogue, in order`() {
        assertEquals(Levels.ALL, LevelProgression.all)
        assertEquals(30, LevelProgression.count)
        assertEquals(1, LevelProgression.first.id)
        assertEquals(30, LevelProgression.last.id)
    }

    @Test
    fun `first level is level one`() {
        assertEquals(Levels.FIRST.name, LevelProgression.first.name)
    }

    @Test
    fun `next walks the whole catalogue one level at a time`() {
        var level = LevelProgression.first
        (2..30).forEach { expected ->
            level = LevelProgression.next(level)
            assertEquals(expected, level.id)
        }
    }

    @Test
    fun `the last level repeats instead of falling off the end`() {
        val last = LevelProgression.all.last()
        assertEquals(last.name, LevelProgression.next(last).name)
        assertTrue(LevelProgression.isLast(last))
    }

    @Test
    fun `only the last level reports as last`() {
        LevelProgression.all.dropLast(1).forEach {
            assertFalse("${it.name} should not be the last level", LevelProgression.isLast(it))
        }
    }

    @Test
    fun `an unknown level falls back to the first one`() {
        val stray = Level("not in the list", rows = 1, columns = 1, arrows = emptyList())
        assertEquals(LevelProgression.first.name, LevelProgression.next(stray).name)
    }

    @Test
    fun `levelOrFirst resolves ids and falls back for nonsense`() {
        assertEquals(7, LevelProgression.levelOrFirst(7).id)
        assertEquals(1, LevelProgression.levelOrFirst(0).id)
        assertEquals(1, LevelProgression.levelOrFirst(999).id)
        assertEquals(1, LevelProgression.levelOrFirst(-3).id)
    }
}
