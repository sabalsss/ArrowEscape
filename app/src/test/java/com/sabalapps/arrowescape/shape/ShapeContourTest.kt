package com.sabalapps.arrowescape.shape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeContourTest {

    private fun trace(vararg rows: String) = GridContourTracer.trace(ShapeMask.parse(rows.toList()))

    @Test
    fun `an empty mask has no outline`() {
        assertTrue(trace("...", "...").isEmpty)
    }

    @Test
    fun `a single cell is one four-cornered loop`() {
        val contour = trace("#")
        assertEquals(1, contour.loops.size)
        val loop = contour.loops.single()
        assertEquals(4, loop.points.size)
        assertEquals(4, loop.perimeter)
        assertEquals(1, loop.area)
        assertFalse(loop.isHole)
    }

    @Test
    fun `straight runs collapse so a 2 by 3 block still has four corners`() {
        val loop = trace("###", "###").loops.single()
        assertEquals(4, loop.points.size)
        assertEquals(10, loop.perimeter)
        assertEquals(6, loop.area)
    }

    @Test
    fun `an L has six corners`() {
        val contour = trace("#.", "#.", "##")
        assertEquals(6, contour.loops.single().points.size)
        assertEquals(4, contour.area)
    }

    @Test
    fun `outer boundaries run clockwise and holes the other way`() {
        val contour = trace("###", "#.#", "###")
        assertEquals(1, contour.outer.size)
        assertEquals(1, contour.holes.size)
        assertTrue(contour.outer.single().signedArea2 > 0)
        assertTrue(contour.holes.single().signedArea2 < 0)
        // The hole's area is subtracted: 9 - 1 enclosed.
        assertEquals(8, contour.area)
        assertEquals(12, contour.outer.single().perimeter)
        assertEquals(4, contour.holes.single().perimeter)
    }

    @Test
    fun `cells that only touch at a corner stay two separate outlines`() {
        val contour = trace("#.", ".#")
        assertEquals(2, contour.loops.size)
        assertTrue(contour.loops.all { it.points.size == 4 && !it.isHole })
        assertEquals(2, contour.area)
    }

    @Test
    fun `an eye diagonally beside an outer notch is still a hole`() {
        // The fish's eye and the balloon's shine touch the outside only at a corner.
        for (rows in listOf(
            listOf("#.###.", "####.#", "######", "######", "#.###."),
            listOf(".###.", "#.###", "#####", "#####", ".###.", "..#..")
        )) {
            val contour = GridContourTracer.trace(ShapeMask.parse(rows))
            assertEquals(1, contour.outer.size)
            assertEquals("holes of\n${rows.joinToString("\n")}", 1, contour.holes.size)
            assertEquals(ShapeMask.parse(rows).cellCount, contour.area)
        }
    }

    @Test
    fun `a wing tip touching its body at a corner is its own outline`() {
        val contour = trace("#....#", ".#..#.", ".####.")
        assertEquals(3, contour.outer.size)
        assertEquals(0, contour.holes.size)
    }

    @Test
    fun `two separate parts each get an outer loop`() {
        val contour = trace("#.#", "#.#")
        assertEquals(2, contour.outer.size)
        assertEquals(0, contour.holes.size)
    }

    @Test
    fun `a C whose tips touch at a corner traces without losing area`() {
        // The two tips of the C meet diagonally, closing the mouth at a single point.
        val contour = trace(
            "##.",
            "#.#",
            "##."
        )
        assertEquals(6, ShapeMask.parse(listOf("##.", "#.#", "##.")).cellCount)
        assertEquals(6, contour.area)
    }

    @Test
    fun `the traced area always equals the mask's cell count`() {
        val masks = listOf(
            listOf(".#.", "###", ".#."),
            listOf("#####", "#...#", "#.#.#", "#...#", "#####"),
            listOf("#.#.#", ".#.#.", "#.#.#"),
            listOf("##..##", "#....#", "..##..", "#....#", "##..##")
        )
        for (rows in masks) {
            val mask = ShapeMask.parse(rows)
            val contour = GridContourTracer.trace(mask)
            assertEquals("area of\n${mask.ascii()}", mask.cellCount, contour.area)
        }
    }

    @Test
    fun `the trace is deterministic`() {
        val mask = ShapeMask.parse(listOf("..##..", ".####.", "######", "#.##.#"))
        val a = GridContourTracer.trace(mask)
        val b = GridContourTracer.trace(mask)
        assertEquals(
            a.loops.map { it.points to it.isHole },
            b.loops.map { it.points to it.isHole }
        )
    }

    @Test
    fun `every corner of a loop lies on the grid of the mask`() {
        val mask = ShapeMask.parse(listOf("..##..", ".####.", "######", "#.##.#"))
        for (loop in GridContourTracer.trace(mask).loops) {
            for (p in loop.points) {
                assertTrue(p.x in 0..mask.columns)
                assertTrue(p.y in 0..mask.rows)
            }
        }
    }

    @Test
    fun `consecutive corners differ along exactly one axis`() {
        val mask = ShapeMask.parse(listOf("..##..", ".####.", "######", "#.##.#"))
        for (loop in GridContourTracer.trace(mask).loops) {
            for (i in loop.points.indices) {
                val a = loop.points[i]
                val b = loop.points[(i + 1) % loop.points.size]
                assertTrue("edge $a -> $b", (a.x == b.x) != (a.y == b.y))
            }
        }
    }

    @Test
    fun `smoothing keeps a loop inside its own corners' bounds and closed`() {
        val loop = trace("..##..", ".####.", "######").loops.single()
        val smooth = loop.smoothed(2)
        val minX = loop.points.minOf { it.x }
        val maxX = loop.points.maxOf { it.x }
        val minY = loop.points.minOf { it.y }
        val maxY = loop.points.maxOf { it.y }
        for (p in smooth) {
            assertTrue(p.x in minX.toFloat()..maxX.toFloat())
            assertTrue(p.y in minY.toFloat()..maxY.toFloat())
        }
        // Every unit of perimeter becomes four points after two iterations.
        assertEquals(loop.perimeter * 4, smooth.size)
    }

    @Test
    fun `smoothing zero times just walks the edge cells`() {
        val loop = trace("##", "##").loops.single()
        assertEquals(loop.perimeter, loop.smoothed(0).size)
    }

    // ---- ShapeMask ----------------------------------------------------------

    @Test
    fun `parse reads hashes as occupied and anything else as empty`() {
        val mask = ShapeMask.parse(listOf("#.#", " # "))
        assertEquals(2, mask.rows)
        assertEquals(3, mask.columns)
        assertEquals(3, mask.cellCount)
        assertTrue(mask[0, 0])
        assertFalse(mask[0, 1])
        assertTrue(mask[1, 1])
        assertFalse(mask[5, 5])
        assertFalse(mask[-1, 0])
    }

    @Test
    fun `trimmed drops the empty margin`() {
        val mask = ShapeMask.parse(listOf(".....", ".##..", "..#..", "....."))
        val trimmed = mask.trimmed()
        assertEquals(2, trimmed.rows)
        assertEquals(2, trimmed.columns)
        assertEquals(mask.cellCount, trimmed.cellCount)
        assertEquals("##\n.#", trimmed.ascii())
    }

    @Test
    fun `mirroring twice is the identity and rotating four times is too`() {
        val mask = ShapeMask.parse(listOf("##.", "#..", "###"))
        assertEquals(mask, mask.mirrored().mirrored())
        assertEquals(mask, mask.rotated(4))
        assertEquals(mask.rotated(1), mask.rotated(-3))
        assertEquals(mask.cellCount, mask.rotated(1).cellCount)
        assertEquals(mask.columns, mask.rotated(1).rows)
    }

    @Test
    fun `components counts parts that touch along an edge`() {
        assertEquals(1, ShapeMask.parse(listOf("##", "#.")).components)
        assertEquals(2, ShapeMask.parse(listOf("#.#")).components)
        // Diagonal contact is not a connection.
        assertEquals(2, ShapeMask.parse(listOf("#.", ".#")).components)
    }

    @Test
    fun `a mask built from arrows matches the cells they stand on`() {
        val level = com.sabalapps.arrowescape.game.Levels.FIRST
        val mask = ShapeMask.of(level)
        assertEquals(level.arrows.size, mask.cellCount)
        for (arrow in level.arrows) assertTrue(mask[arrow.row, arrow.col])
    }
}
