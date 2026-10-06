package com.sabalapps.arrowescape.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's reading order: the collection is what is particular to the game, Daily is secondary. */
class HomeOrderTest {

    @Test
    fun `home is continue, discoveries, daily, then the game modes`() {
        assertEquals(
            listOf(
                HomeSection.Continue,
                HomeSection.Discoveries,
                HomeSection.Daily,
                HomeSection.Modes
            ),
            HomeContentOrder
        )
    }

    @Test
    fun `discoveries sits above the daily challenge`() {
        assertTrue(HomeContentOrder.indexOf(HomeSection.Discoveries) < HomeContentOrder.indexOf(HomeSection.Daily))
    }

    @Test
    fun `every section is shown exactly once`() {
        assertEquals(HomeSection.entries.toSet(), HomeContentOrder.toSet())
        assertEquals(HomeSection.entries.size, HomeContentOrder.size)
    }
}
