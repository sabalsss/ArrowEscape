package com.sabalapps.arrowescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The star rule, one case per tier plus the edges. The rule is a one-liner, so
 * what these tests are really pinning down is the *boundaries*: where three
 * becomes two, where two becomes one, and that nothing can fall below one.
 */
class StarRatingTest {

    // ---- three stars --------------------------------------------------------

    @Test
    fun `a clean run with no hint is three stars`() {
        assertEquals(3, StarRating.stars(blockedTaps = 0, hintUsed = false))
    }

    @Test
    fun `three stars is the perfect escape`() {
        assertTrue(StarRating.isPerfect(StarRating.stars(0, hintUsed = false)))
    }

    // ---- two stars ----------------------------------------------------------

    @Test
    fun `one blocked tap is two stars`() {
        assertEquals(2, StarRating.stars(blockedTaps = 1, hintUsed = false))
    }

    @Test
    fun `a hint alone is two stars`() {
        assertEquals(2, StarRating.stars(blockedTaps = 0, hintUsed = true))
    }

    @Test
    fun `two stars is not a perfect escape`() {
        assertFalse(StarRating.isPerfect(StarRating.stars(0, hintUsed = true)))
        assertFalse(StarRating.isPerfect(StarRating.stars(1, hintUsed = false)))
    }

    // ---- one star -----------------------------------------------------------

    @Test
    fun `two blocked taps is one star`() {
        assertEquals(1, StarRating.stars(blockedTaps = 2, hintUsed = false))
    }

    @Test
    fun `a blocked tap plus a hint is one star`() {
        assertEquals(1, StarRating.stars(blockedTaps = 1, hintUsed = true))
    }

    @Test
    fun `a clear is never worth less than one star however bad the run`() {
        assertEquals(1, StarRating.stars(blockedTaps = 2, hintUsed = true))
        assertEquals(1, StarRating.stars(blockedTaps = 50, hintUsed = true))
    }

    // ---- shape --------------------------------------------------------------

    @Test
    fun `every reachable run rates somewhere in one to three`() {
        // A winning run can only ever have 0, 1 or 2 blocked taps — the third
        // ends the board — so this is the whole input space.
        for (blocked in 0..2) {
            for (hint in listOf(false, true)) {
                val stars = StarRating.stars(blocked, hint)
                assertTrue("$blocked/$hint gave $stars", stars in StarRating.MIN..StarRating.MAX)
            }
        }
    }

    @Test
    fun `all three tiers are reachable`() {
        val reachable = (0..2).flatMap { blocked ->
            listOf(false, true).map { StarRating.stars(blocked, it) }
        }.toSet()
        assertEquals(setOf(1, 2, 3), reachable)
    }

    @Test
    fun `more mistakes never earns more stars`() {
        var previous = StarRating.MAX + 1
        for (blocked in 0..4) {
            val stars = StarRating.stars(blocked, hintUsed = false)
            assertTrue(stars <= previous)
            previous = stars
        }
    }

    @Test
    fun `only one to three are valid stored ratings`() {
        assertFalse(StarRating.isValid(StarRating.NONE))
        assertTrue(StarRating.isValid(1))
        assertTrue(StarRating.isValid(2))
        assertTrue(StarRating.isValid(3))
        assertFalse(StarRating.isValid(4))
        assertFalse(StarRating.isValid(-1))
    }
}
