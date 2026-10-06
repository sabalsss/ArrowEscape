package com.sabalapps.arrowescape.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.sabalapps.arrowescape.shape.GridPoint
import com.sabalapps.arrowescape.shape.ShapeContour
import com.sabalapps.arrowescape.shape.ShapeLoop
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The geometry of one confirmation outline at one cell size: the rounded loops, their lengths
 * (so a trace can draw a fraction of each), the filled region, and where the twinkles sit.
 *
 * Built **once** per contour and cell size — `remember`ed by [ShapeConfirmOverlay] — and only
 * ever *drawn* per frame, so an animation costs strokes, not geometry. The coordinates are the
 * formation box's own: the corner of the grid at `(originCol, originRow)` is the top-left, which is
 * exactly where the board puts the arrow at that cell, so the outline sits on the pieces it was
 * traced from.
 */
internal class ShapeOutlinePaths private constructor(
    val loops: List<Path>,
    val measures: List<PathMeasure>,
    val lengths: FloatArray,
    /** Every loop, even-odd, so a hole (an eye, a lens) stays open when this is filled. */
    val fill: Path,
    val twinkles: List<Offset>
) {
    companion object {
        /** Corners are rounded to this fraction of a cell, which is what the pieces' own corners are. */
        private const val CORNER = 0.30f

        fun build(
            contour: ShapeContour,
            cellWidth: Float,
            cellHeight: Float,
            originRow: Int,
            originCol: Int
        ): ShapeOutlinePaths {
            fun at(p: GridPoint) = Offset((p.x - originCol) * cellWidth, (p.y - originRow) * cellHeight)

            val loops = contour.loops.map { rounded(it, ::at, CORNER * min(cellWidth, cellHeight)) }
            val measures = loops.map { path -> PathMeasure().also { it.setPath(path, true) } }
            val lengths = FloatArray(loops.size) { measures[it].length }
            val fill = Path().apply {
                fillType = PathFillType.EvenOdd
                loops.forEach { addPath(it) }
            }
            return ShapeOutlinePaths(loops, measures, lengths, fill, twinklesOf(contour, ::at))
        }

        /** A loop with every corner rounded by up to [radius]; a closed path. */
        private fun rounded(loop: ShapeLoop, at: (GridPoint) -> Offset, radius: Float): Path {
            val pts = loop.points.map(at)
            val n = pts.size
            val path = Path()
            for (i in 0 until n) {
                val previous = pts[(i - 1 + n) % n]
                val here = pts[i]
                val next = pts[(i + 1) % n]
                val inLength = hypot(here.x - previous.x, here.y - previous.y)
                val outLength = hypot(next.x - here.x, next.y - here.y)
                val r = min(radius, min(inLength, outLength) / 2f)
                val start = Offset(here.x + (previous.x - here.x) / inLength * r, here.y + (previous.y - here.y) / inLength * r)
                val end = Offset(here.x + (next.x - here.x) / outLength * r, here.y + (next.y - here.y) / outLength * r)
                if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
                path.quadraticTo(here.x, here.y, end.x, end.y)
            }
            path.close()
            return path
        }

        /** A handful of points spread round the outer outlines, for the tiny twinkles at the lock. */
        private fun twinklesOf(contour: ShapeContour, at: (GridPoint) -> Offset): List<Offset> {
            val outer = contour.outer.maxByOrNull { it.perimeter } ?: return emptyList()
            val corners = outer.points.map(at)
            if (corners.isEmpty()) return emptyList()
            val step = (corners.size / TWINKLES.toFloat()).coerceAtLeast(1f)
            return List(min(TWINKLES, corners.size)) { corners[(it * step).toInt().coerceAtMost(corners.lastIndex)] }
        }

        private const val TWINKLES = 7
    }
}

/**
 * The shape-confirmation outline: the moment the last arrow has gone, the **silhouette of the
 * puzzle just solved** draws itself where the arrows were.
 *
 *  1. a faint ghost fill of the shape rises (the bridge from "arrows" to "outline");
 *  2. the outline traces round every loop at once — outer edge and holes together;
 *  3. the inside lights up and the line strengthens;
 *  4. the outline *locks*: one small flash and a few twinkles.
 *
 * Everything is a function of one clock ([clock], milliseconds on the launch clock) read **only in
 * the draw phase**, so the whole sequence repaints one Canvas and recomposes nothing. Under reduced
 * motion ([ShapeConfirmSchedule.animated] false) the finished outline simply fades in: it appears
 * all at once and stays still.
 *
 * It is decoration: no semantics, no touch, and it carries no name — the outline is the shape the
 * player has just solved and says nothing about what it is.
 *
 * Draw it as a sibling inside the formation's own box (the same box the pieces are placed in), so
 * its coordinates are the board's.
 */
@Composable
internal fun ShapeConfirmOverlay(
    contour: ShapeContour,
    cellWidth: Float,
    cellHeight: Float,
    originRow: Int,
    originCol: Int,
    schedule: ShapeConfirmSchedule,
    accent: Color,
    clock: () -> Float,
    modifier: Modifier = Modifier
) {
    val paths = remember(contour, cellWidth, cellHeight, originRow, originCol) {
        ShapeOutlinePaths.build(contour, cellWidth, cellHeight, originRow, originCol)
    }
    val segment = remember { Path() }
    val light = remember(accent) { lerp(accent, Color.White, 0.55f) }

    Canvas(modifier = modifier) {
        val t = clock()
        if (t < schedule.ghostStartMs && schedule.animated) return@Canvas
        val cell = min(cellWidth, cellHeight)

        val ghost = ease(window(t, schedule.ghostStartMs, schedule.ghostMs))
        val trace = if (schedule.animated) ease(window(t, schedule.traceStartMs, schedule.traceMs)) else 1f
        val fillUp = ease(window(t, schedule.fillStartMs, schedule.fillMs))
        // Reduced motion: one fade in place of the trace, the glow and the flash.
        val appear = if (schedule.animated) 1f else window(t, schedule.traceStartMs, schedule.traceMs)
        val lock = if (schedule.animated) bell(window(t, schedule.lockMs - 40, 280)) else 0f

        // 1 + 3. The inside: a soft ghost first, then a lit fill.
        val fillAlpha = (GHOST_ALPHA * ghost + FILL_ALPHA * fillUp + LOCK_FILL * lock) * appear
        if (fillAlpha > 0.001f) {
            drawPath(
                path = paths.fill,
                brush = Brush.verticalGradient(
                    listOf(light.copy(alpha = fillAlpha), accent.copy(alpha = fillAlpha * 0.55f)),
                    startY = 0f,
                    endY = size.height
                )
            )
        }

        // 2 + 4. The line: a wide soft glow, the accent, and a thin bright core.
        if (trace > 0.001f && appear > 0.001f) {
            val glow = (0.16f + 0.14f * fillUp + 0.30f * lock) * appear
            val strong = (0.50f + 0.25f * fillUp) * appear
            stroke(paths, segment, trace, accent.copy(alpha = glow), cell * (0.34f + 0.18f * lock))
            stroke(paths, segment, trace, accent.copy(alpha = strong), cell * 0.13f)
            stroke(paths, segment, trace, Color.White.copy(alpha = 0.95f * appear), cell * (0.05f + 0.02f * lock))
        }

        // 4. A few twinkles, once, as the outline locks.
        if (schedule.animated && lock > 0.02f) {
            val reach = cell * 0.26f
            paths.twinkles.forEachIndexed { index, point ->
                val phase = bell(window(t, schedule.lockMs - 120 + index * 22, 380))
                if (phase > 0.02f) twinkle(point, reach * (0.4f + 0.6f * phase), Color.White.copy(alpha = 0.9f * phase), cell)
            }
        }
    }
}

/** Draws every loop's outline up to [fraction] of its length, with one stroke style. */
private fun DrawScope.stroke(
    paths: ShapeOutlinePaths,
    segment: Path,
    fraction: Float,
    colour: Color,
    width: Float
) {
    val style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (fraction >= 0.999f) {
        paths.loops.forEach { drawPath(it, colour, style = style) }
        return
    }
    paths.loops.indices.forEach { i ->
        segment.rewind()
        paths.measures[i].getSegment(0f, paths.lengths[i] * fraction, segment, true)
        drawPath(segment, colour, style = style)
    }
}

/** A tiny four-point star: two crossed hairlines and a dot. */
private fun DrawScope.twinkle(at: Offset, reach: Float, colour: Color, cell: Float) {
    val w = cell * 0.035f
    drawLine(colour, Offset(at.x - reach, at.y), Offset(at.x + reach, at.y), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(colour, Offset(at.x, at.y - reach), Offset(at.x, at.y + reach), strokeWidth = w, cap = StrokeCap.Round)
    drawCircle(colour, radius = w * 1.4f, center = at)
}

/** 0 before [start] ms, 1 after [start] + [duration], linear between. */
internal fun window(t: Float, start: Int, duration: Int): Float =
    if (duration <= 0) {
        if (t >= start) 1f else 0f
    } else {
        ((t - start) / duration).coerceIn(0f, 1f)
    }

private fun ease(x: Float): Float = FastOutSlowInEasing.transform(x)

/** 0 → 1 → 0 across a window, smooth at both ends. */
private fun bell(x: Float): Float = sin(PI.toFloat() * x.coerceIn(0f, 1f))

private const val GHOST_ALPHA = 0.10f
private const val FILL_ALPHA = 0.18f
private const val LOCK_FILL = 0.10f
