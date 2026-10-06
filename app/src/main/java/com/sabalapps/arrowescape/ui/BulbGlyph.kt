package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/**
 * The Hint button's bulb, drawn rather than shipped.
 *
 * A bulb is not in `material-icons-core` and pulling in the extended icon set
 * for one 18dp glyph would add a few thousand vector assets to the APK for it.
 * Twelve lines of Canvas, in the same style as [ArrowGlyph], costs nothing and
 * stays crisp at every density.
 */
@Composable
fun BulbGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val stroke = Stroke(width = side * 0.11f, cap = StrokeCap.Round)

        // The glass: a circle sitting in the top two thirds.
        drawCircle(
            color = color,
            radius = side * 0.27f,
            center = Offset(left + side * 0.5f, top + side * 0.38f),
            style = stroke
        )
        // The neck and the base, as two short rules under it.
        drawLine(
            color = color,
            start = Offset(left + side * 0.36f, top + side * 0.73f),
            end = Offset(left + side * 0.64f, top + side * 0.73f),
            strokeWidth = side * 0.11f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(left + side * 0.42f, top + side * 0.88f),
            end = Offset(left + side * 0.58f, top + side * 0.88f),
            strokeWidth = side * 0.11f,
            cap = StrokeCap.Round
        )
    }
}
