package com.sabalapps.arrowescape.retention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreActionsTest {

    @Test
    fun `the store links name this game's package`() {
        assertEquals("com.sabalapps.arrowescape", StoreLinks.PACKAGE_NAME)
        assertEquals("market://details?id=com.sabalapps.arrowescape", StoreLinks.MARKET_URI)
        assertEquals("https://play.google.com/store/apps/details?id=com.sabalapps.arrowescape", StoreLinks.WEB_URL)
    }

    @Test
    fun `the share text says what the game is, carries the link, and spoils no shape`() {
        val text = StoreLinks.SHARE_TEXT
        assertTrue(text.contains("Arrow Escape"))
        assertTrue(text.contains(StoreLinks.WEB_URL))
        assertTrue(text.contains("solve mystery arrow shapes"))
        // Not one discovery or mystery-shape name rides along in a share.
        for (discovery in com.sabalapps.arrowescape.ui.world.CampaignDiscoveries.all) {
            val word = Regex("\\b${Regex.escape(discovery.name)}\\b", RegexOption.IGNORE_CASE)
            assertFalse("share text names ${discovery.name}", word.containsMatchIn(text))
        }
    }

    @Test
    fun `a review flow that could not start opens the listing`() {
        assertTrue(ReviewOutcome.shouldOpenListing(launchSucceeded = false, elapsedMs = 5_000))
    }

    @Test
    fun `a review flow that finished too quickly to have been on screen opens the listing`() {
        assertTrue(ReviewOutcome.shouldOpenListing(launchSucceeded = true, elapsedMs = 0))
        assertTrue(ReviewOutcome.shouldOpenListing(launchSucceeded = true, elapsedMs = ReviewOutcome.MIN_VISIBLE_MS - 1))
    }

    @Test
    fun `a review flow the player spent time in is left strictly alone`() {
        assertFalse(ReviewOutcome.shouldOpenListing(launchSucceeded = true, elapsedMs = ReviewOutcome.MIN_VISIBLE_MS))
        assertFalse(ReviewOutcome.shouldOpenListing(launchSucceeded = true, elapsedMs = 30_000))
    }
}
