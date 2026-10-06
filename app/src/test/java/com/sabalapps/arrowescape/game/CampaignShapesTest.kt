package com.sabalapps.arrowescape.game

import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what each Campaign level *looks like*: its occupied cells are its
 * discovery's silhouette, and stay that way.
 *
 * The directions in [Levels.ALL] are free to change — `CampaignRegressionTest`
 * guards them separately — but the picture a level hides is part of the product,
 * and a layout edit that moved or dropped a cell would redraw it without any
 * solvability test noticing. The blueprints in [CampaignShapes] are the record of
 * what was intended; this is what holds the catalogue to it.
 *
 * It also writes the shape review table to `app/build/reports/levels/` — a
 * development aid for the later phase that stops drawing the empty grid cells.
 */
class CampaignShapesTest {

    private val levels = Levels.ALL

    @Test
    fun `there is one blueprint per campaign level, in order`() {
        assertEquals(30, CampaignShapes.ALL.size)
        assertEquals((1..30).toList(), CampaignShapes.ALL.map { it.levelId })
        assertEquals(levels.map { it.id }, CampaignShapes.ALL.map { it.levelId })
    }

    @Test
    fun `each blueprint is named for the discovery its level hides`() {
        CampaignShapes.ALL.forEach { shape ->
            assertEquals(
                "Level ${shape.levelId}",
                CampaignDiscoveries.forLevel(shape.levelId)?.name,
                shape.discovery
            )
        }
    }

    @Test
    fun `blueprints are rectangular and inside the board limits`() {
        CampaignShapes.ALL.forEach { shape ->
            val name = "${shape.discovery} (level ${shape.levelId})"
            assertTrue("$name has no rows", shape.rows.isNotEmpty())
            assertTrue(
                "$name has rows of different widths",
                shape.rows.all { it.length == shape.width }
            )
            assertTrue(
                "$name uses characters other than # and .",
                shape.rows.all { row -> row.all { it == '#' || it == '.' } }
            )
            assertTrue("$name is ${shape.width} columns wide; the limit is 6", shape.width <= 6)
            assertTrue("$name is ${shape.height} rows tall; the limit is 9", shape.height <= 9)
            assertTrue("$name is empty", shape.size > 0)
        }
    }

    /** The point of the phase: occupied cells draw the discovery. */
    @Test
    fun `every level's occupied cells are exactly its blueprint`() {
        for (shape in CampaignShapes.ALL) {
            val level = requireNotNull(Levels.byId(shape.levelId))
            val name = "${shape.discovery} (level ${shape.levelId})"

            assertEquals("$name: board height", shape.height, level.rows)
            assertEquals("$name: board width", shape.width, level.columns)

            val occupied = level.arrows.map { it.row to it.col }.toSet()
            val expected = shape.cells.toSet()
            assertEquals(
                "$name: cells in the layout that the silhouette does not have: ${occupied - expected}; " +
                    "cells the silhouette has that the layout does not: ${expected - occupied}",
                expected,
                occupied
            )
            assertEquals("$name: arrow count", shape.size, level.arrows.size)
        }
    }

    @Test
    fun `a board is not a full rectangle - empty cells are part of every picture`() {
        // Phase 3 stops drawing the empty cells; a shape that filled its whole
        // frame would have nothing for them to be missing from.
        levels.forEach { level ->
            assertTrue(
                "${level.name} fills its whole rectangle",
                level.arrows.size < level.rows * level.columns
            )
        }
    }

    /**
     * Level 1 is the tutorial board. The lesson is three beats — tap a free
     * arrow, learn that something can be in the way, see that taking one away
     * frees another — and each needs the board to supply it.
     */
    @Test
    fun `the Cloud still carries the whole tutorial lesson`() {
        val board = Levels.FIRST.arrows
        val free = board.filter { MoveValidator.canEscape(it, board) }
        val blocked = board.filter { !MoveValidator.canEscape(it, board) }

        assertTrue("something must be free to tap first", free.isNotEmpty())
        assertTrue("something must be blocked to demonstrate blocking", blocked.isNotEmpty())
        assertTrue("the opening should stay gentle", free.size <= 3)

        // The arrow the tutorial spotlights is the one the Hint button would pick.
        val spotlight = requireNotNull(HintEngine.hint(board))
        val without = board.filterNot { it.id == spotlight.id }
        val freed = without.filter {
            MoveValidator.canEscape(it, without) && !MoveValidator.canEscape(it, board)
        }
        assertTrue("tapping the spotlighted arrow must free another one", freed.isNotEmpty())

        // And it is a short chain, not a long one.
        val depth = requireNotNull(com.sabalapps.arrowescape.endless.BoardAnalysis.peel(board)).size
        assertTrue("level 1 should clear in at most four passes, was $depth", depth <= 4)
    }

    // ---- the review table ------------------------------------------------------

    @Test
    fun `the shape review table is written for every level`() {
        val rows = levels.map { level ->
            val shape = requireNotNull(CampaignShapes.forLevel(level.id))
            val discovery = requireNotNull(CampaignDiscoveries.forLevel(level.id))
            val outcome = LevelSolver.solve(level)
            val solved = outcome is LevelSolver.Outcome.Solved &&
                LevelSolver.verify(level, outcome.order)
            ShapeRow(
                level = level.id,
                world = discovery.world.displayName,
                discovery = discovery.name,
                size = "${level.rows}×${level.columns}",
                arrows = level.arrows.size,
                description = shape.description,
                free = LevelSolver.initiallyValidMoves(level),
                solvable = solved
            )
        }

        val report = buildString {
            appendLine("# Arrow Escape — Campaign shape review")
            appendLine()
            appendLine("Generated by `CampaignShapesTest`. Development only — not shown in the app.")
            appendLine()
            appendLine("| Level | World | Discovery | Rows × columns | Arrows | Silhouette | Opening free | Solvable |")
            appendLine("|------:|:------|:----------|:--------------:|-------:|:-----------|-------------:|:--------:|")
            rows.forEach {
                appendLine(
                    "| ${it.level} | ${it.world} | ${it.discovery} | ${it.size} | ${it.arrows} " +
                        "| ${it.description} | ${it.free} | ${if (it.solvable) "yes" else "**NO**"} |"
                )
            }
            appendLine()
            appendLine("## Silhouettes (`#` = an arrow, `.` = empty)")
            CampaignShapes.ALL.forEach { shape ->
                appendLine()
                appendLine("**${shape.levelId} · ${shape.discovery}**")
                appendLine("```")
                shape.rows.forEach { appendLine(it) }
                appendLine("```")
            }
        }

        val out = File("build/reports/levels/campaign-shapes.md")
        out.parentFile?.mkdirs()
        out.writeText(report)
        println(report)

        assertNotNull(rows)
        rows.forEach { assertTrue("level ${it.level} is not solvable", it.solvable) }
    }

    private data class ShapeRow(
        val level: Int,
        val world: String,
        val discovery: String,
        val size: String,
        val arrows: Int,
        val description: String,
        val free: Int,
        val solvable: Boolean
    )
}
