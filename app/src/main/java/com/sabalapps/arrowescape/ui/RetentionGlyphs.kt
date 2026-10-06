package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.min

/*
 * The two glyphs the reminder and Share rows need, in the same hand as the rest of
 * `GameIcons.kt`: authored once in a 24-unit square, drawn on a Canvas, no assets.
 */

private const val GRID = 24f

private inline fun DrawScope.onGrid(block: DrawScope.() -> Unit) {
    val k = min(size.width, size.height) / GRID
    scale(scale = k, pivot = Offset.Zero) { block() }
}

/** A bell: the daily reminder. */
@Composable
fun BellGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val body = Path().apply {
                moveTo(5.5f, 17.5f)
                cubicTo(6.8f, 16.2f, 7.2f, 14.6f, 7.2f, 12.2f)
                lineTo(7.2f, 10.5f)
                cubicTo(7.2f, 7.9f, 9.2f, 5.9f, 12f, 5.9f)
                cubicTo(14.8f, 5.9f, 16.8f, 7.9f, 16.8f, 10.5f)
                lineTo(16.8f, 12.2f)
                cubicTo(16.8f, 14.6f, 17.2f, 16.2f, 18.5f, 17.5f)
                close()
            }
            drawPath(body, color)
            drawLine(color, Offset(12f, 3.4f), Offset(12f, 5.9f), 1.9f, StrokeCap.Round)
            // The clapper.
            drawCircle(color, radius = 1.9f, center = Offset(12f, 19.6f))
        }
    }
}

/** Three linked dots: Share. */
@Composable
fun ShareGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val a = Offset(6.5f, 12f)
            val b = Offset(17.5f, 6f)
            val c = Offset(17.5f, 18f)
            val link = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(Path().apply { moveTo(b.x, b.y); lineTo(a.x, a.y); lineTo(c.x, c.y) }, color, style = link)
            drawCircle(color, radius = 2.9f, center = a)
            drawCircle(color, radius = 2.9f, center = b)
            drawCircle(color, radius = 2.9f, center = c)
        }
    }
}
