package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.sabalapps.arrowescape.game.Direction
import kotlin.math.min

/**
 * The arrow itself, drawn with Compose rather than shipped as an image so it
 * stays crisp at every screen density and needs no assets.
 *
 * The shape is authored once pointing UP inside a unit square and the points are
 * rotated per direction, which keeps all four directions pixel-identical apart
 * from their orientation. The drop shadow stays offset downwards in screen space
 * so the light always comes from the same place.
 */
@Composable
fun ArrowGlyph(
    direction: Direction,
    color: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val size = min(this.size.width, this.size.height)
        val left = (this.size.width - size) / 2f
        val top = (this.size.height - size) / 2f
        val stroke = Stroke(
            width = size * STROKE_FRACTION,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val path = arrowPath(direction, size, left, top)

        translate(top = size * SHADOW_OFFSET_FRACTION) {
            drawPath(path, shadowColor, style = stroke)
        }
        drawPath(path, color, style = stroke)
    }
}

private const val STROKE_FRACTION = 0.145f
private const val SHADOW_OFFSET_FRACTION = 0.045f

/** Unit-square outline of an UP arrow: the shaft, then the two head strokes. */
private val UP_SHAFT = listOf(0.5f to 0.80f, 0.5f to 0.30f)
private val UP_HEAD = listOf(0.24f to 0.545f, 0.5f to 0.265f, 0.76f to 0.545f)

private fun arrowPath(direction: Direction, size: Float, left: Float, top: Float): Path {
    fun place(point: Pair<Float, Float>): Offset {
        val (x, y) = rotate(point.first, point.second, direction)
        return Offset(left + x * size, top + y * size)
    }
    return Path().apply {
        val shaft = UP_SHAFT.map(::place)
        moveTo(shaft[0].x, shaft[0].y)
        lineTo(shaft[1].x, shaft[1].y)

        val head = UP_HEAD.map(::place)
        moveTo(head[0].x, head[0].y)
        lineTo(head[1].x, head[1].y)
        lineTo(head[2].x, head[2].y)
    }
}

/** Rotates a point inside the unit square so the UP outline faces [direction]. */
private fun rotate(x: Float, y: Float, direction: Direction): Pair<Float, Float> =
    when (direction) {
        Direction.UP -> x to y
        Direction.RIGHT -> (1f - y) to x
        Direction.DOWN -> (1f - x) to (1f - y)
        Direction.LEFT -> y to (1f - x)
    }
