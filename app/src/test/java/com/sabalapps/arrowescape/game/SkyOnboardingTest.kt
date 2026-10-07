package com.sabalapps.arrowescape.game

import com.sabalapps.arrowescape.shape.GridContourTracer
import com.sabalapps.arrowescape.shape.ShapeMask
import com.sabalapps.arrowescape.tutorial.DependencyLesson
import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first three Campaign levels are the first session: Heart (the hand-guided rule), Star (one
 * removal frees another) and Kite (a little independent thinking). This pins what each is *for* —
 * not just that it solves — so a later re-author cannot quietly turn the onboarding into something
 * harder, flatter or less recognisable. Levels 4–6 carry the Sky Garden discoveries that moved out
 * of the way; their order is pinned here too because the discovery order and the layouts must agree.
 */
class SkyOnboardingTest {

    private val heart = Levels.byId(1)!!
    private val star = Levels.byId(2)!!
    private val kite = Levels.byId(3)!!

    private fun free(level: Level) = LevelSolver.initiallyValidMoves(level)
    private fun effort(level: Level) = ShapeAuthoring.scanningEffort(level)

    @Test
    fun `the Sky Garden runs Heart, Star, Kite, Bird, Cloud, Flower`() {
        assertEquals(
            listOf("Heart", "Star", "Kite", "Bird", "Cloud", "Flower"),
            (1..6).map { CampaignDiscoveries.forLevel(it)!!.name }
        )
        assertEquals(
            listOf("sky_heart", "sky_star", "sky_kite", "sky_bird", "sky_cloud", "sky_flower"),
            (1..6).map { CampaignDiscoveries.forLevel(it)!!.artKey }
        )
    }

    @Test
    fun `the first three boards are exactly the authored layouts`() {
        assertEquals(listOf(".<.<.", ">^v>v", ".^v^.", "..<.."), rows(heart))
        assertEquals(listOf("..^..", ">>^>v", ".v<<.", ".v.<."), rows(star))
        assertEquals(listOf("..v..", ".v>^.", "<v<^<", "..>..", ".>...", "^...."), rows(kite))
    }

    @Test
    fun `the first three are small, 11 to 12 arrows, and the Sky Garden never gets smaller afterwards`() {
        assertEquals(listOf(11, 11, 12), listOf(heart, star, kite).map { it.arrows.size })
        val counts = (1..7).map { Levels.byId(it)!!.arrows.size }
        assertEquals(counts.sorted(), counts)
    }

    @Test
    fun `the Heart opens with two or three obvious moves, each of which matters`() {
        assertTrue("opens with ${free(heart)}", free(heart) in 2..3)
        val m = ShapeAuthoring.measure(heart)
        assertEquals("an opener that blocks nobody is a free tap with no thought", 0, m.irrelevantOpeners)
        assertTrue("clears in at most four passes, was ${m.depth}", m.depth <= 4)
        assertTrue(ShapeAuthoring.passesBar(m, 1))
    }

    @Test
    fun `the Heart's hand lands on an arrow whose removal frees another`() {
        val board = heart.arrows
        val spotlight = requireNotNull(HintEngine.hint(board))
        val without = board.filterNot { it.id == spotlight.id }
        assertTrue(
            without.any { MoveValidator.canEscape(it, without) && !MoveValidator.canEscape(it, board) }
        )
    }

    @Test
    fun `the Star's first guided tap frees another arrow, so the dependency lesson can teach`() {
        val board = star.arrows
        val target = requireNotNull(DependencyLesson.Starting.targetId(board))
        val after = board.filterNot { it.id == target }
        val freed = after.filter { MoveValidator.canEscape(it, after) && !MoveValidator.canEscape(it, board) }
        assertTrue("the first guided move freed nothing", freed.isNotEmpty())
        val m = ShapeAuthoring.measure(star)
        assertTrue("opens with ${m.free}", m.free in 3..5)
        assertTrue("a hub: one arrow frees at least two", m.bestUnlock >= 2)
        assertTrue(ShapeAuthoring.passesBar(m, 2))
    }

    @Test
    fun `the Kite offers two to four openings and asks for a little more thought than the Star`() {
        val m = ShapeAuthoring.measure(kite)
        assertTrue("opens with ${m.free}", m.free in 2..4)
        assertTrue("two independent useful openers", m.usefulOpeners >= 2)
        assertTrue("at most one opener that blocks nobody", m.irrelevantOpeners <= 1)
        assertTrue(ShapeAuthoring.passesBar(m, 3))
    }

    @Test
    fun `the first three climb gently - each a touch harder, none a jump`() {
        val e = listOf(heart, star, kite).map(::effort)
        assertTrue("star is not harder than heart: $e", e[1] > e[0])
        assertTrue("kite is not harder than star: $e", e[2] > e[1])
        assertTrue("star jumps: $e", e[1] / e[0] <= 1.25)
        assertTrue("kite jumps: $e", e[2] / e[1] <= 1.25)
    }

    @Test
    fun `every first-session board is a real puzzle not a tapping exercise`() {
        for (level in listOf(heart, star, kite)) {
            assertTrue("${level.name} free ${free(level)}", free(level) <= level.arrows.size * 0.4)
            assertTrue(ShapeAuthoring.forcedShare(level) <= ShapeAuthoring.MAX_FORCED_SHARE)
        }
    }

    @Test
    fun `the Heart and the Star are one solid piece each and the Heart has no hole`() {
        for (id in 1..2) {
            val contour = GridContourTracer.trace(ShapeMask.of(Levels.byId(id)!!))
            assertEquals("level $id is one outline", 1, contour.outer.size)
            assertEquals("level $id has no hole", 0, contour.holes.size)
        }
    }

    private fun rows(level: Level): List<String> {
        val glyph = mapOf(
            Direction.UP to '^', Direction.DOWN to 'v', Direction.LEFT to '<', Direction.RIGHT to '>'
        )
        val grid = Array(level.rows) { CharArray(level.columns) { '.' } }
        level.arrows.forEach { grid[it.row][it.col] = glyph.getValue(it.direction) }
        return grid.map { String(it) }
    }
}
