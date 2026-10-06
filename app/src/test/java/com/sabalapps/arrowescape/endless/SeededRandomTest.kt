package com.sabalapps.arrowescape.endless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The random source is the whole reason a saved endless puzzle is a single
 * `Long`, so the properties it has to hold are worth pinning down: identical
 * streams from identical seeds, values inside their stated bounds, and no
 * obvious bias that would skew which boards get built.
 */
class SeededRandomTest {

    @Test
    fun `the same seed replays the same stream`() {
        val a = SeededRandom(12345L)
        val b = SeededRandom(12345L)
        repeat(1_000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun `different seeds diverge immediately`() {
        // Adjacent seeds are the interesting case: a weak mixer would hand back
        // neighbouring first draws and the boards would come out alike.
        val first = SeededRandom(1L).nextLong()
        val second = SeededRandom(2L).nextLong()
        assertNotEquals(first, second)
    }

    @Test
    fun `nextInt stays inside its bound`() {
        val rng = SeededRandom(7L)
        repeat(10_000) {
            val value = rng.nextInt(6)
            assertTrue("$value outside 0..5", value in 0..5)
        }
    }

    @Test
    fun `nextInt over a range includes both ends`() {
        val rng = SeededRandom(11L)
        val seen = HashSet<Int>()
        repeat(10_000) { seen += rng.nextInt(28..36) }
        assertEquals((28..36).toSet(), seen)
    }

    @Test
    fun `nextInt rejects a non-positive bound`() {
        val rng = SeededRandom(1L)
        for (bound in listOf(0, -1, Int.MIN_VALUE)) {
            val failed = runCatching { rng.nextInt(bound) }.isFailure
            assertTrue("bound=$bound should have been rejected", failed)
        }
    }

    @Test
    fun `nextDouble stays in zero until one`() {
        val rng = SeededRandom(3L)
        repeat(10_000) {
            val value = rng.nextDouble()
            assertTrue("$value outside 0.0..<1.0", value >= 0.0 && value < 1.0)
        }
    }

    @Test
    fun `draws are spread evenly enough to pick board sizes fairly`() {
        // Not a statistical test, a sanity one: the generator picks board sizes
        // and arrow targets with these draws, so a badly skewed stream would
        // quietly stop producing whole classes of board.
        val rng = SeededRandom(99L)
        val buckets = IntArray(6)
        val draws = 60_000
        repeat(draws) { buckets[rng.nextInt(6)]++ }
        val expected = draws / 6
        for ((bucket, count) in buckets.withIndex()) {
            assertTrue(
                "bucket $bucket drew $count of $draws, expected around $expected",
                count > expected * 0.9 && count < expected * 1.1
            )
        }
    }

    @Test
    fun `pick returns a member of the list and uses all of it`() {
        val rng = SeededRandom(5L)
        val items = listOf("a", "b", "c")
        val seen = HashSet<String>()
        repeat(1_000) {
            val picked = rng.pick(items)
            assertTrue("$picked is not in $items", picked in items)
            seen += picked
        }
        assertEquals(items.toSet(), seen)
    }

    @Test
    fun `derive is a pure function of its parts`() {
        assertEquals(
            SeededRandom.derive(42L, 1L, 3L),
            SeededRandom.derive(42L, 1L, 3L)
        )
    }

    @Test
    fun `derive separates streams that differ in any one part`() {
        // This is what keeps attempt 2 of a seed from colliding with attempt 3,
        // and tier 1 from colliding with tier 2.
        val derived = buildSet {
            for (tier in 0L..4L) {
                for (attempt in 0L..23L) add(SeededRandom.derive(1_000L, tier, attempt))
            }
        }
        assertEquals("derive collided across (tier, attempt)", 5 * 24, derived.size)
    }

    @Test
    fun `derive is order sensitive`() {
        assertNotEquals(SeededRandom.derive(1L, 2L), SeededRandom.derive(2L, 1L))
    }

    @Test
    fun `extreme seeds still produce a usable stream`() {
        for (seed in listOf(0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val rng = SeededRandom(seed)
            val draws = List(500) { rng.nextInt(6) }
            assertTrue("seed=$seed collapsed to one value", draws.toSet().size > 1)
        }
    }
}
