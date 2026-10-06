package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.endless.BoardAnalysis
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.MoveValidator
import com.sabalapps.arrowescape.game.ShapeAuthoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapePuzzleGeneratorTest {

    private val fish = MysteryShapes.byId("fish")!!
    private val house = MysteryShapes.byId("house")!!
    private val profile = ShapeTierProfile.of(EndlessTier.MEDIUM)

    private fun layout(board: GeneratedShapeBoard) =
        board.level.arrows.map { Triple(it.row, it.col, it.direction) }

    @Test
    fun `the picture is exactly the mask, whatever the seed`() {
        for (seed in 1L..40L) {
            val board = ShapePuzzleGenerator.generate(fish.mask, seed, profile, "Daily Challenge")
            assertEquals(fish.mask, ShapeMask.of(board.level))
            assertEquals(fish.mask.rows, board.level.rows)
            assertEquals(fish.mask.columns, board.level.columns)
            assertEquals(fish.cellCount, board.level.arrows.size)
        }
    }

    @Test
    fun `generation is a pure function of mask, seed and profile`() {
        for (seed in listOf(1L, 99L, 123456789L)) {
            val a = ShapePuzzleGenerator.generate(house.mask, seed, profile, "x")
            val b = ShapePuzzleGenerator.generate(house.mask, seed, profile, "x")
            assertEquals(layout(a), layout(b))
            assertEquals(a.solution, b.solution)
            assertEquals(a.attempts, b.attempts)
        }
    }

    @Test
    fun `different seeds give different arrows for the same picture`() {
        val layouts = (1L..30L).map { layout(ShapePuzzleGenerator.generate(fish.mask, it, profile, "x")) }.toSet()
        assertTrue("only ${layouts.size} distinct boards from 30 seeds", layouts.size >= 25)
    }

    @Test
    fun `the witness is a legal clearing order under the real rules`() {
        for (t in MysteryShapes.all.take(10)) {
            val board = ShapePuzzleGenerator.generate(t.mask, 77, profile, "x")
            assertTrue(BoardAnalysis.verifyOrder(board.level.arrows, board.solution))
            // And greedily, one tap at a time, MoveValidator agrees the board clears.
            var left = board.level.arrows
            var guard = 0
            while (left.isNotEmpty()) {
                val free = left.first { MoveValidator.canEscape(it, left) }
                left = left.filterNot { it.id == free.id }
                check(guard++ < 100)
            }
        }
    }

    @Test
    fun `ids are reading order, like every other board`() {
        val board = ShapePuzzleGenerator.generate(fish.mask, 5, profile, "x")
        val ids = board.level.arrows.map { it.id }
        assertEquals((0 until fish.cellCount).toList(), ids)
        val order = board.level.arrows.map { it.row * 10 + it.col }
        assertEquals(order.sorted(), order)
        assertEquals(ShapePuzzleGenerator.GENERATED_LEVEL_ID, board.level.id)
    }

    @Test
    fun `every glyph is in play and no stripe or single glyph dominates`() {
        for (seed in 1L..20L) {
            val board = ShapePuzzleGenerator.generate(fish.mask, seed, profile, "x")
            assertTrue(Direction.entries.size >= board.metrics.distinctDirections && board.metrics.distinctDirections >= profile.minDirections)
            assertTrue(board.metrics.dominantShare <= profile.maxDominant)
            assertTrue(board.metrics.longestRun <= profile.maxRun)
        }
    }

    @Test
    fun `a harder tier is harder on the same picture`() {
        // The point of separating shape complexity from puzzle logic: one Fish, five difficulties.
        val easy = ShapeTierProfile.of(EndlessTier.EASY)
        val hard = ShapeTierProfile.of(EndlessTier.HARD)
        fun mean(profile: ShapeTierProfile, f: (GeneratedShapeBoard) -> Double) =
            (1L..24L).map { f(ShapePuzzleGenerator.generate(fish.mask, it * 13, profile, "x")) }.average()
        assertTrue(mean(hard) { it.metrics.freeRatio } < mean(easy) { it.metrics.freeRatio })
        assertTrue(mean(hard) { it.metrics.depth.toDouble() } > mean(easy) { it.metrics.depth.toDouble() })
        assertTrue(
            mean(hard) { ShapeAuthoring.scanningEffort(it.level, samples = 20) } >
                mean(easy) { ShapeAuthoring.scanningEffort(it.level, samples = 20) }
        )
    }

    @Test
    fun `effort climbs tier by tier over the catalogue`() {
        val means = EndlessTier.entries.map { tier ->
            val p = ShapeTierProfile.of(tier)
            val boards = MysteryShapes.forTier(tier).flatMap { t ->
                (1L..3L).map { ShapePuzzleGenerator.generate(t.mask, it * 7, p, "x") }
            }
            boards.map { ShapeAuthoring.scanningEffort(it.level, samples = 15) }.average()
        }
        for (i in 1 until means.size) assertTrue("effort by tier: $means", means[i] > means[i - 1])
    }

    @Test
    fun `a one cell picture and a straight line still generate`() {
        val dot = ShapePuzzleGenerator.generate(ShapeMask.parse(listOf("#")), 1, profile, "x")
        assertEquals(1, dot.level.arrows.size)
        assertFalse(dot.onProfile) // a single arrow cannot meet a tier's rules — and still ships solvable
        assertTrue(BoardAnalysis.isSolvable(dot.level.arrows))

        val line = ShapePuzzleGenerator.generate(ShapeMask.parse(listOf("######")), 2, profile, "x")
        assertTrue(BoardAnalysis.verifyOrder(line.level.arrows, line.solution))
    }

    @Test
    fun `an impossible picture is refused rather than guessed at`() {
        val tooWide = ShapeMask.parse(listOf("#######"))
        val threw = runCatching { ShapePuzzleGenerator.generate(tooWide, 1, profile, "x") }.isFailure
        assertTrue(threw)
    }

    @Test
    fun `generating is fast enough to do on a tap`() {
        // Worst tier, biggest pictures: well under what a player could notice.
        val expert = ShapeTierProfile.of(EndlessTier.EXPERT)
        val start = System.nanoTime()
        var boards = 0
        for (t in MysteryShapes.forTier(EndlessTier.EXPERT)) for (seed in 1L..4L) {
            ShapePuzzleGenerator.generate(t.mask, seed, expert, "x")
            boards++
        }
        val avgMs = (System.nanoTime() - start) / 1e6 / boards
        assertTrue("averaged $avgMs ms a board", avgMs < 60.0)
    }

    @Test
    fun `fallback boards are never needed for the catalogue but are always solvable`() {
        // Force the cap: a profile no board can meet.
        val impossible = ShapeTierProfile(
            cells = 1..99, free = 0.9..1.0, minFree = 40, minDepth = 40, minDirections = 4,
            maxDominant = 0.1, maxRun = 1, maxSingleStreak = 0,
            steering = ShapeTierProfile.Steering(1.0..2.0, 1.0..2.0, 0.5..1.0)
        )
        val board = ShapePuzzleGenerator.generate(house.mask, 3, impossible, "x")
        assertFalse(board.onProfile)
        assertEquals(house.mask, ShapeMask.of(board.level))
        assertTrue(BoardAnalysis.verifyOrder(board.level.arrows, board.solution))
    }
}
