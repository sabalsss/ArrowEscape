package com.sabalapps.arrowescape.ui.discovery

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.sabalapps.arrowescape.ui.world.CampaignDiscovery
import kotlin.math.min

/**
 * One discovery's illustration, drawn on a Canvas.
 *
 * This is the single artwork system: the reveal, the Discoveries album, the Level
 * Select tiles and Home's thumbnails all draw through it, so a discovery has one
 * identity wherever it appears. It resolves [artKey] — the stable key on
 * `CampaignDiscovery` — through [DiscoveryArtRegistry]; nothing here knows a level
 * number.
 *
 * The art is square and is fitted, centred, into the smaller side of whatever box it
 * is given. It draws no semantics: it is decoration, and whatever shows it carries
 * the words ("Butterfly, discovered, Forest").
 *
 * **Only call this for a discovery the player has found.** A mystery or a locked
 * slot has no [CampaignDiscovery] to pass (see `DiscoverySlot`), which is how the
 * artwork of something undiscovered stays out of every screen.
 */
@Composable
fun DiscoveryArtwork(artKey: String, modifier: Modifier = Modifier) {
    val art = remember(artKey) { DiscoveryArtCache.parsed(artKey) }
    Canvas(modifier = modifier) {
        if (art != null) drawDiscoveryArt(art)
    }
}

/** [DiscoveryArtwork] for a discovery the player holds. */
@Composable
fun DiscoveryArtwork(discovery: CampaignDiscovery, modifier: Modifier = Modifier) {
    DiscoveryArtwork(artKey = discovery.artKey, modifier = modifier)
}

// ---- parsed, ready-to-draw art ---------------------------------------------------

private val InkColor = Color(Ink)
private val RingStroke = ART_SOFT + 2f * ART_INK + 2f * ART_RING
private val InkStroke = ART_SOFT + 2f * ART_INK

private fun roundStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

internal sealed interface ParsedLayer

internal class ParsedBody(
    val path: Path,
    val fill: Brush,
    val ring: Boolean,
    val ringStroke: Stroke,
    val inkStroke: Stroke,
    val softStroke: Stroke
) : ParsedLayer

internal class ParsedShape(
    val path: Path,
    val fill: Brush?,
    val soften: Boolean,
    val edge: Color?,
    val edgeStroke: Stroke?,
    val softStroke: Stroke,
    val alpha: Float
)

internal class ParsedDetails(val shapes: List<ParsedShape>) : ParsedLayer

internal class ParsedArt(val layers: List<ParsedLayer>)

private fun brushFor(fill: ArtFill, path: Path): Brush {
    if (fill.top == fill.bottom) return SolidColor(Color(fill.top))
    val bounds = path.getBounds()
    return Brush.verticalGradient(
        colors = listOf(Color(fill.top), Color(fill.bottom)),
        startY = bounds.top,
        endY = bounds.bottom
    )
}

private fun parsePath(d: String, evenOdd: Boolean): Path =
    PathParser().parsePathString(d).toPath().also {
        if (evenOdd) it.fillType = PathFillType.EvenOdd
    }

private fun parse(spec: DiscoveryArtSpec): ParsedArt = ParsedArt(
    spec.layers.map { layer ->
        when (layer) {
            is BodyLayer -> {
                val path = parsePath(layer.d, layer.evenOdd)
                ParsedBody(
                    path = path,
                    fill = brushFor(layer.fill, path),
                    ring = layer.ring,
                    ringStroke = roundStroke(RingStroke),
                    inkStroke = roundStroke(InkStroke),
                    softStroke = roundStroke(ART_SOFT)
                )
            }

            is DetailLayer -> ParsedDetails(
                layer.shapes.map { shape ->
                    val path = parsePath(shape.d, shape.evenOdd)
                    ParsedShape(
                        path = path,
                        fill = shape.fill?.let { brushFor(it, path) },
                        soften = shape.soften,
                        edge = shape.stroke?.let { Color(it) },
                        edgeStroke = shape.stroke?.let { roundStroke(shape.strokeWidth) },
                        softStroke = roundStroke(ART_SOFT),
                        alpha = shape.alpha
                    )
                }
            )
        }
    }
)

/**
 * Parsed art by key, shared by everything that draws a discovery: a Level Select
 * with a dozen miniatures on screen parses each drawing once, not once per tile.
 * Compose reads this on the main thread only; the lock is just for safety.
 */
internal object DiscoveryArtCache {
    private val parsed = HashMap<String, ParsedArt>()

    fun parsed(artKey: String): ParsedArt? = synchronized(parsed) {
        parsed[artKey] ?: DiscoveryArtRegistry.specFor(artKey)?.let { parse(it) }?.also { parsed[artKey] = it }
    }
}

/**
 * Draws [art] into the largest centred square in this scope: for each body, the
 * sticker ring, then the ink edge, then the fill; then the details in order.
 */
internal fun DrawScope.drawDiscoveryArt(art: ParsedArt) {
    val side = min(size.width, size.height)
    if (side <= 0f) return
    val k = side / ART_SPACE
    withTransform({
        translate((size.width - side) / 2f, (size.height - side) / 2f)
        scale(k, k, pivot = Offset.Zero)
    }) {
        for (layer in art.layers) {
            when (layer) {
                is ParsedBody -> {
                    if (layer.ring) drawPath(layer.path, Color.White, style = layer.ringStroke)
                    drawPath(layer.path, InkColor, style = layer.inkStroke)
                    drawPath(layer.path, layer.fill)
                    drawPath(layer.path, layer.fill, style = layer.softStroke)
                }

                is ParsedDetails -> for (shape in layer.shapes) {
                    if (shape.fill != null) {
                        drawPath(shape.path, shape.fill, alpha = shape.alpha)
                        if (shape.soften) {
                            drawPath(shape.path, shape.fill, alpha = shape.alpha, style = shape.softStroke)
                        }
                    }
                    if (shape.edge != null && shape.edgeStroke != null) {
                        drawPath(shape.path, shape.edge, alpha = shape.alpha, style = shape.edgeStroke)
                    }
                }
            }
        }
    }
}

/** The art is authored in a 100 × 100 space. */
private const val ART_SPACE = 100f
