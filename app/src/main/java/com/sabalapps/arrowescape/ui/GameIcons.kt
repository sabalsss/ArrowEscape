package com.sabalapps.arrowescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The game's own glyphs.
 *
 * `material-icons-core` has no chart, speaker, book, infinity sign or flame, and
 * the extended set would add a few thousand vector assets to the APK for a
 * dozen glyphs. These are drawn on a Canvas instead — authored once in a 24-unit
 * square and scaled to whatever box they are given — in the same spirit as
 * [ArrowGlyph] and [BulbGlyph]: crisp at every density, no assets, and one weight
 * and corner style across all of them, which is what makes a set of icons look
 * like a set.
 *
 * They are decorative: whatever carries one labels itself, so none of these
 * contributes semantics.
 */

private const val GRID = 24f
private const val STROKE = 1.9f

/** Runs [block] in a 24×24 coordinate space scaled to fit the canvas. */
private inline fun DrawScope.onGrid(block: DrawScope.() -> Unit) {
    val k = min(size.width, size.height) / GRID
    scale(scale = k, pivot = Offset.Zero) { block() }
}

private val RoundStroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Three rising bars: statistics. */
@Composable
fun BarChartGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val r = CornerRadius(1.6f)
            drawRoundRect(color, Offset(3.5f, 12f), Size(4.5f, 8.5f), r)
            drawRoundRect(color, Offset(9.75f, 6.5f), Size(4.5f, 14f), r)
            drawRoundRect(color, Offset(16f, 3f), Size(4.5f, 17.5f), r)
        }
    }
}

/** A speaker with two sound waves: sound effects. */
@Composable
fun SpeakerGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val body = Path().apply {
                moveTo(3.5f, 9.5f)
                lineTo(7.5f, 9.5f)
                lineTo(12.5f, 5f)
                lineTo(12.5f, 19f)
                lineTo(7.5f, 14.5f)
                lineTo(3.5f, 14.5f)
                close()
            }
            drawPath(body, color)
            drawPath(body, color, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
            drawArc(
                color = color, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(8f, 7.5f), size = Size(9f, 9f), style = RoundStroke
            )
            drawArc(
                color = color, startAngle = -50f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(4.5f, 4f), size = Size(16f, 16f), style = RoundStroke
            )
        }
    }
}

/** A phone with shake lines either side: haptics. */
@Composable
fun VibrateGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            drawRoundRect(
                color = color, topLeft = Offset(8f, 3f), size = Size(8f, 18f),
                cornerRadius = CornerRadius(2.2f), style = Stroke(width = STROKE)
            )
            drawLine(color, Offset(4.8f, 8.5f), Offset(4.8f, 15.5f), STROKE, StrokeCap.Round)
            drawLine(color, Offset(2.2f, 10.5f), Offset(2.2f, 13.5f), STROKE, StrokeCap.Round)
            drawLine(color, Offset(19.2f, 8.5f), Offset(19.2f, 15.5f), STROKE, StrokeCap.Round)
            drawLine(color, Offset(21.8f, 10.5f), Offset(21.8f, 13.5f), STROKE, StrokeCap.Round)
        }
    }
}

/** A disc with eight rays: the light theme. */
@Composable
fun SunGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            drawCircle(color, radius = 4.3f, center = Offset(12f, 12f))
            for (i in 0 until 8) {
                val a = i * (PI / 4).toFloat()
                drawLine(
                    color = color,
                    start = Offset(12f + 7.4f * cos(a), 12f + 7.4f * sin(a)),
                    end = Offset(12f + 9.8f * cos(a), 12f + 9.8f * sin(a)),
                    strokeWidth = STROKE,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** A crescent: the dark theme. */
@Composable
fun MoonGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val disc = Path().apply { addOval(Rect(Offset(11.5f, 12.5f), 8.6f)) }
            val bite = Path().apply { addOval(Rect(Offset(16.2f, 9.2f), 7.3f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), color)
        }
    }
}

/** A circle half filled: follow the system theme. */
@Composable
fun SystemThemeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            drawCircle(color, radius = 8.2f, center = Offset(12f, 12f), style = Stroke(width = STROKE))
            drawArc(
                color = color, startAngle = 90f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(3.8f, 3.8f), size = Size(16.4f, 16.4f)
            )
        }
    }
}

/** An open book: help and the tutorial. */
@Composable
fun BookGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val book = Path().apply {
                moveTo(12f, 7f)
                cubicTo(10f, 5.2f, 6.5f, 4.8f, 3.2f, 5.6f)
                lineTo(3.2f, 18.2f)
                cubicTo(6.5f, 17.4f, 10f, 17.8f, 12f, 19.6f)
                cubicTo(14f, 17.8f, 17.5f, 17.4f, 20.8f, 18.2f)
                lineTo(20.8f, 5.6f)
                cubicTo(17.5f, 4.8f, 14f, 5.2f, 12f, 7f)
                lineTo(12f, 19.6f)
            }
            drawPath(book, color, style = RoundStroke)
        }
    }
}

/** A sideways figure eight: Endless Mode. */
@Composable
fun InfinityGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val loop = Path().apply {
                moveTo(12f, 12f)
                cubicTo(14f, 8.6f, 19.6f, 8.2f, 19.6f, 12f)
                cubicTo(19.6f, 15.8f, 14f, 15.4f, 12f, 12f)
                cubicTo(10f, 8.6f, 4.4f, 8.2f, 4.4f, 12f)
                cubicTo(4.4f, 15.8f, 10f, 15.4f, 12f, 12f)
            }
            drawPath(loop, color, style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Two peaks: the Campaign. */
@Composable
fun MountainGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val back = Path().apply {
                moveTo(1.8f, 19.5f)
                lineTo(9f, 6f)
                lineTo(16.2f, 19.5f)
                close()
            }
            val front = Path().apply {
                moveTo(10.6f, 19.5f)
                lineTo(16.4f, 10.2f)
                lineTo(22.2f, 19.5f)
                close()
            }
            drawPath(back, color)
            drawPath(back, color, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
            drawPath(front, color.copy(alpha = color.alpha * 0.72f))
            drawPath(front, color.copy(alpha = color.alpha * 0.72f), style = Stroke(width = 1.6f, join = StrokeJoin.Round))
        }
    }
}

/** Four rounded squares: the level grid. */
@Composable
fun GridGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val r = CornerRadius(2.2f)
            val s = Size(7.2f, 7.2f)
            drawRoundRect(color, Offset(3.6f, 3.6f), s, r)
            drawRoundRect(color, Offset(13.2f, 3.6f), s, r)
            drawRoundRect(color, Offset(3.6f, 13.2f), s, r)
            drawRoundRect(color, Offset(13.2f, 13.2f), s, r)
        }
    }
}

/**
 * The streak flame, in its own colours: a streak is always orange, whatever card
 * it sits on, so this ignores the surrounding tint.
 */
@Composable
fun FlameGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val flame = Path().apply {
                moveTo(12f, 2.2f)
                cubicTo(13.5f, 5.2f, 18.8f, 8.4f, 18.8f, 14.2f)
                cubicTo(18.8f, 18.3f, 15.8f, 21.5f, 12f, 21.5f)
                cubicTo(8.2f, 21.5f, 5.2f, 18.3f, 5.2f, 14.2f)
                cubicTo(5.2f, 11.6f, 6.6f, 9.6f, 8.2f, 8f)
                cubicTo(8.4f, 9.8f, 9.2f, 10.8f, 10.2f, 11f)
                cubicTo(9.6f, 8f, 10.6f, 4.6f, 12f, 2.2f)
                close()
            }
            drawPath(
                flame,
                Brush.verticalGradient(
                    listOf(Color(0xFFFFB531), Color(0xFFFF5A1F)),
                    startY = 2f,
                    endY = 21.5f
                )
            )
            val core = Path().apply {
                moveTo(12f, 11.6f)
                cubicTo(13.4f, 13.4f, 15f, 14.8f, 15f, 16.8f)
                cubicTo(15f, 18.7f, 13.7f, 20f, 12f, 20f)
                cubicTo(10.3f, 20f, 9f, 18.7f, 9f, 16.8f)
                cubicTo(9f, 15f, 10.6f, 13.6f, 12f, 11.6f)
                close()
            }
            drawPath(core, Color(0xFFFFE8A3))
        }
    }
}

/** A circle with a slash: a blocked tap. */
@Composable
fun NoEntryGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            drawCircle(color, radius = 8.2f, center = Offset(12f, 12f), style = Stroke(width = 2.1f))
            drawLine(color, Offset(6.4f, 6.4f), Offset(17.6f, 17.6f), 2.1f, StrokeCap.Round)
        }
    }
}

/** A rounded five-pointed star filled with [fill], optionally edged with [outline]. */
@Composable
fun StarGlyph(fill: Brush, modifier: Modifier = Modifier, outline: Color? = null) {
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val path = starPath(center.x, center.y + r * 0.07f, r * 0.94f, r * 0.43f)
        drawPath(path, fill)
        // A same-colour round-joined stroke turns the star's sharp points into
        // soft ones, which is what makes it read as game art rather than a polygon.
        drawPath(path, fill, style = Stroke(width = r * 0.18f, join = StrokeJoin.Round))
        if (outline != null) {
            drawPath(path, outline, style = Stroke(width = r * 0.10f, join = StrokeJoin.Round))
        }
    }
}

/** [StarGlyph] in one flat colour. */
@Composable
fun StarGlyph(color: Color, modifier: Modifier = Modifier, outline: Color? = null) {
    StarGlyph(fill = SolidColor(color), modifier = modifier, outline = outline)
}

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = (-PI / 2 + i * PI / 5).toFloat()
        val x = cx + radius * cos(angle)
        val y = cy + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** A big and a small four-pointed twinkle: Discoveries. */
@Composable
fun SparkleGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            fun twinkle(cx: Float, cy: Float, r: Float): Path {
                val p = r * 0.22f
                return Path().apply {
                    moveTo(cx, cy - r)
                    quadraticTo(cx + p, cy - p, cx + r, cy)
                    quadraticTo(cx + p, cy + p, cx, cy + r)
                    quadraticTo(cx - p, cy + p, cx - r, cy)
                    quadraticTo(cx - p, cy - p, cx, cy - r)
                    close()
                }
            }
            val big = twinkle(10.5f, 13.5f, 8.2f)
            val small = twinkle(18.4f, 5.6f, 4.4f)
            drawPath(big, color)
            drawPath(big, color, style = Stroke(width = 1.4f, join = StrokeJoin.Round))
            drawPath(small, color)
            drawPath(small, color, style = Stroke(width = 1.1f, join = StrokeJoin.Round))
        }
    }
}
