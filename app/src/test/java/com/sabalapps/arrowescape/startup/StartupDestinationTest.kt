package com.sabalapps.arrowescape.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a launch goes, and that it goes there once. */
class StartupDestinationTest {

    // ---- the intent contract --------------------------------------------------

    @Test
    fun `the reminder's extra means the daily challenge`() {
        assertEquals(StartupDestination.Daily, StartupDestination.fromExtra(StartupDestination.DAILY_EXTRA_VALUE))
        assertEquals("daily", StartupDestination.DAILY_EXTRA_VALUE)
    }

    @Test
    fun `an ordinary launch, or anything unrecognised, is home`() {
        assertEquals(StartupDestination.Home, StartupDestination.fromExtra(null))
        assertEquals(StartupDestination.Home, StartupDestination.fromExtra(""))
        assertEquals(StartupDestination.Home, StartupDestination.fromExtra("home"))
        assertEquals(StartupDestination.Home, StartupDestination.fromExtra("DAILY"))
        assertEquals(StartupDestination.Home, StartupDestination.fromExtra("../../daily"))
    }

    @Test
    fun `the extra is namespaced to this app`() {
        assertEquals("com.sabalapps.arrowescape.extra.DESTINATION", LaunchIntents.EXTRA_DESTINATION)
    }

    // ---- navigate once --------------------------------------------------------

    @Test
    fun `a daily request is handed over exactly once`() {
        val router = LaunchRouter()
        router.offer(StartupDestination.Daily)
        assertEquals(StartupDestination.Daily, router.pending.value)

        assertEquals(StartupDestination.Daily, router.consume())
        assertNull("a second consume finds nothing", router.consume())
        assertNull(router.pending.value)
    }

    @Test
    fun `a normal launch has nothing to navigate to`() {
        val router = LaunchRouter()
        router.offer(StartupDestination.Home)
        assertNull(router.pending.value)
        assertNull(router.consume())
    }

    @Test
    fun `home never wipes a daily request that is still waiting`() {
        val router = LaunchRouter()
        router.offer(StartupDestination.Daily)
        router.offer(StartupDestination.Home) // e.g. the launcher icon tapped during the loading screen
        assertEquals(StartupDestination.Daily, router.consume())
    }

    @Test
    fun `a second reminder tap after the first was handled is a new request`() {
        val router = LaunchRouter()
        router.offer(StartupDestination.Daily)
        router.consume()
        router.offer(StartupDestination.Daily)
        assertEquals(StartupDestination.Daily, router.consume())
        assertNull(router.consume())
    }

    @Test
    fun `repeated offers before it is handled still navigate once`() {
        val router = LaunchRouter()
        repeat(3) { router.offer(StartupDestination.Daily) }
        assertEquals(StartupDestination.Daily, router.consume())
        assertNull(router.consume())
    }
}
