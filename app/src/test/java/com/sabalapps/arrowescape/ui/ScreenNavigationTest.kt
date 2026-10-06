package com.sabalapps.arrowescape.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The back stack is saved as enum ordinals, so a destination may only ever be
 * appended: reordering or inserting would make a saved state from an older build
 * restore as different screens. This is the tripwire.
 */
class ScreenNavigationTest {

    @Test
    fun `destinations are only ever appended`() {
        assertEquals(
            listOf("HOME", "GAME", "LEVEL_SELECT", "STATS", "SETTINGS", "DAILY", "DISCOVERIES"),
            Screen.entries.map { it.name }
        )
    }

    @Test
    fun `discoveries was appended after every existing destination`() {
        assertEquals(Screen.entries.lastIndex, Screen.DISCOVERIES.ordinal)
        assertEquals(5, Screen.DAILY.ordinal)
    }
}
