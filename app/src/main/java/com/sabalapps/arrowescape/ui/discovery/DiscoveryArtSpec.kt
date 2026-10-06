package com.sabalapps.arrowescape.ui.discovery

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The data model for the discovery illustrations, and the few primitives they are
 * built from.
 *
 * A discovery is drawn in a 100 × 100 space as a short stack of layers of SVG path
 * data. This file is pure Kotlin — no Compose, no Android — so the catalogue of
 * artwork can be checked on a plain JVM (every art key resolves, every path parses)
 * and written out as a review sheet. `DiscoveryArtwork.kt` is the only thing that
 * draws it.
 *
 * ## The house style
 *
 * Thirty items have to read as one set, so they share a finish rather than each
 * being drawn its own way:
 *
 *  - a **body** is a fill with a vertical two-tone gradient, a deep indigo ink
 *    edge, and a thin white "sticker" ring outside that — the ink holds on a light
 *    card, the ring holds on a dark scrim, and a body therefore reads on all five
 *    worlds and every tile;
 *  - everything inside the edge (shine, spots, faces, sparkles) is a **detail**;
 *  - shapes are chubby and round, corners are softened, and anything that has a face
 *    gets the same small dot eyes, smile and blush.
 */

/** A vertical fill: [top] easing to [bottom]. Equal colours make it flat. */
class ArtFill(val top: Long, val bottom: Long = top)

/**
 * One drawn shape inside a [DetailLayer]. [fill] and [stroke] are independent; a
 * part with neither draws nothing.
 *
 * @param soften stroke the fill's own colour at [ART_SOFT] with round joins, which
 *   turns sharp polygon corners into soft ones. Bodies always do; a detail opts in.
 */
class ArtShape(
    val d: String,
    val fill: ArtFill? = null,
    val stroke: Long? = null,
    val strokeWidth: Float = 0f,
    val alpha: Float = 1f,
    val soften: Boolean = false,
    val evenOdd: Boolean = false
)

/** A layer of the illustration, drawn in the order the spec lists them. */
sealed interface ArtLayer

/**
 * A silhouette: every subpath of [d] is one shape, filled with [fill] across the
 * whole thing's bounds and edged *once* around the union — so overlapping
 * circles read as one cloud, not as circles.
 *
 * @param ring draw the white sticker ring. Only the outermost body wants it; a
 *   second body drawn on top (an eagle's head over its wings) is edged in ink only.
 */
class BodyLayer(
    val d: String,
    val fill: ArtFill,
    val ring: Boolean = true,
    val evenOdd: Boolean = false
) : ArtLayer

/** Anything drawn as it is: shine, markings, faces, sparkles. */
class DetailLayer(val shapes: List<ArtShape>) : ArtLayer

/** The whole of one discovery's drawing. */
class DiscoveryArtSpec(val layers: List<ArtLayer>)

// ---- the shared finish (all in 100-unit art space) ----------------------------

/** Width of the same-colour round-joined stroke that softens a body's corners. */
const val ART_SOFT = 3.0f

/** Thickness of the ink edge, measured outside the softened fill. */
const val ART_INK = 2.4f

/** Thickness of the white sticker ring, measured outside the ink. */
const val ART_RING = 2.2f

/** The one ink every outline, eye and line is drawn in. */
const val Ink: Long = 0xFF2B2A66
const val White: Long = 0xFFFFFFFF
const val Blush: Long = 0xFFFF8FA8

/** `0xRRGGBB` as an opaque colour. */
fun rgb(hex: Int): Long = 0xFF000000L or (hex.toLong() and 0xFFFFFF)

// ---- the DSL the artwork is written in ----------------------------------------

/** Collects the subpaths of one body. */
class PathBuilder {
    private val sb = StringBuilder()
    operator fun String.unaryPlus() {
        sb.append(this).append(' ')
    }

    fun build(): String = sb.toString().trim()
}

/** Collects the details of one [DetailLayer]. */
class DetailBuilder {
    private val shapes = ArrayList<ArtShape>()

    /**
     * A filled shape. [edge] gives it an outline of its own (a jewel, a pupil's
     * ring); without one it is just colour.
     */
    fun fill(
        d: String,
        top: Long,
        bottom: Long = top,
        alpha: Float = 1f,
        soften: Boolean = false,
        evenOdd: Boolean = false,
        edge: Long? = null,
        edgeWidth: Float = 1.6f
    ) {
        shapes += ArtShape(
            d = d,
            fill = ArtFill(top, bottom),
            stroke = edge,
            strokeWidth = if (edge != null) edgeWidth else 0f,
            alpha = alpha,
            soften = soften,
            evenOdd = evenOdd
        )
    }

    fun line(d: String, color: Long, width: Float, alpha: Float = 1f) {
        shapes += ArtShape(d = d, stroke = color, strokeWidth = width, alpha = alpha)
    }

    /** A soft white shine: the highlight every glossy body carries. */
    fun gloss(d: String, alpha: Float = 0.55f) = fill(d, White, alpha = alpha)

    fun dot(cx: Float, cy: Float, r: Float, color: Long, alpha: Float = 1f) =
        fill(circle(cx, cy, r), color, alpha = alpha)

    /** A four-pointed twinkle — the "slightly magical" note. */
    fun twinkle(cx: Float, cy: Float, r: Float, color: Long = White, alpha: Float = 1f) =
        fill(sparkle4(cx, cy, r), color, alpha = alpha, soften = true)

    /**
     * The shared face: two dot eyes with a shine, a small smile and a blush. Every
     * item that has one is drawn through here so they all read as the same family.
     *
     * @param gap half the distance between the eyes.
     */
    fun face(
        cx: Float,
        cy: Float,
        s: Float = 1f,
        gap: Float = 8f,
        smile: Boolean = true,
        blush: Boolean = true
    ) {
        val ex = gap * s
        if (blush) {
            fill(ellipse(cx - ex * 1.7f, cy + 5.4f * s, 3.7f * s, 2.4f * s), Blush, alpha = 0.62f)
            fill(ellipse(cx + ex * 1.7f, cy + 5.4f * s, 3.7f * s, 2.4f * s), Blush, alpha = 0.62f)
        }
        fill(ellipse(cx - ex, cy, 2.6f * s, 3.4f * s), Ink)
        fill(ellipse(cx + ex, cy, 2.6f * s, 3.4f * s), Ink)
        dot(cx - ex + 0.9f * s, cy - 1.3f * s, 1.05f * s, White)
        dot(cx + ex + 0.9f * s, cy - 1.3f * s, 1.05f * s, White)
        if (smile) {
            line(
                "M${n(cx - 3.2f * s)} ${n(cy + 5f * s)} Q${n(cx)} ${n(cy + 9.2f * s)} ${n(cx + 3.2f * s)} ${n(cy + 5f * s)}",
                Ink,
                1.7f * s
            )
        }
    }

    /** A pair of closed, sleepy eyes and a smile — the moon's face. */
    fun sleepyFace(cx: Float, cy: Float, s: Float = 1f, gap: Float = 7.5f) {
        val ex = gap * s
        fill(ellipse(cx - ex * 1.75f, cy + 5.2f * s, 3.5f * s, 2.3f * s), Blush, alpha = 0.62f)
        fill(ellipse(cx + ex * 1.75f, cy + 5.2f * s, 3.5f * s, 2.3f * s), Blush, alpha = 0.62f)
        line("M${n(cx - ex - 3f * s)} ${n(cy)} Q${n(cx - ex)} ${n(cy + 3.6f * s)} ${n(cx - ex + 3f * s)} ${n(cy)}", Ink, 1.8f * s)
        line("M${n(cx + ex - 3f * s)} ${n(cy)} Q${n(cx + ex)} ${n(cy + 3.6f * s)} ${n(cx + ex + 3f * s)} ${n(cy)}", Ink, 1.8f * s)
        line("M${n(cx - 2.6f * s)} ${n(cy + 5.6f * s)} Q${n(cx)} ${n(cy + 8.2f * s)} ${n(cx + 2.6f * s)} ${n(cy + 5.6f * s)}", Ink, 1.6f * s)
    }

    internal fun build(): DetailLayer = DetailLayer(shapes.toList())
}

/** Builds one [DiscoveryArtSpec]. */
class ArtBuilder {
    private val layers = ArrayList<ArtLayer>()

    /** A silhouette; the first one carries the sticker ring, later ones do not. */
    fun body(
        top: Long,
        bottom: Long = top,
        ring: Boolean = layers.none { it is BodyLayer },
        evenOdd: Boolean = false,
        paths: PathBuilder.() -> Unit
    ) {
        layers += BodyLayer(PathBuilder().apply(paths).build(), ArtFill(top, bottom), ring, evenOdd)
    }

    fun details(block: DetailBuilder.() -> Unit) {
        layers += DetailBuilder().apply(block).build()
    }

    internal fun build(): DiscoveryArtSpec = DiscoveryArtSpec(layers.toList())
}

fun art(block: ArtBuilder.() -> Unit): DiscoveryArtSpec = ArtBuilder().apply(block).build()

// ---- path primitives -----------------------------------------------------------
//
// Each returns SVG path data. They are the "reusable primitives where genuinely
// useful": circles, ellipses, rounded rectangles, polygons, stars and sparkles come
// up in nearly every item; a leaf, a wing or a flame is specific to its item and is
// written out where it is used.
//
// Every one winds clockwise (screen space). That matters: a body is several
// subpaths in one path filled non-zero, and a subpath wound the other way would
// cut a hole where it overlaps its neighbour. A `poly` is only as good as the order
// it is given — list its points clockwise.

/** A number as short path text: at most two decimals, no trailing zeros. */
internal fun n(v: Float): String {
    val r = Math.round(v * 100f) / 100f
    return if (r == r.toLong().toFloat()) r.toLong().toString() else r.toString()
}

fun circle(cx: Float, cy: Float, r: Float): String =
    "M${n(cx - r)} ${n(cy)} A${n(r)} ${n(r)} 0 1 1 ${n(cx + r)} ${n(cy)} A${n(r)} ${n(r)} 0 1 1 ${n(cx - r)} ${n(cy)} Z"

/** An ellipse, optionally turned [rotDeg] degrees about its centre. */
fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rotDeg: Float = 0f): String {
    val k = 0.5522847f
    val a = rotDeg * PI.toFloat() / 180f
    val ca = cos(a)
    val sa = sin(a)
    fun p(x: Float, y: Float): String = "${n(cx + x * ca - y * sa)} ${n(cy + x * sa + y * ca)}"
    return "M${p(rx, 0f)} " +
        "C${p(rx, k * ry)} ${p(k * rx, ry)} ${p(0f, ry)} " +
        "C${p(-k * rx, ry)} ${p(-rx, k * ry)} ${p(-rx, 0f)} " +
        "C${p(-rx, -k * ry)} ${p(-k * rx, -ry)} ${p(0f, -ry)} " +
        "C${p(k * rx, -ry)} ${p(rx, -k * ry)} ${p(rx, 0f)} Z"
}

fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val rr = minOf(r, w / 2f, h / 2f)
    return "M${n(x + rr)} ${n(y)} H${n(x + w - rr)} A${n(rr)} ${n(rr)} 0 0 1 ${n(x + w)} ${n(y + rr)} " +
        "V${n(y + h - rr)} A${n(rr)} ${n(rr)} 0 0 1 ${n(x + w - rr)} ${n(y + h)} " +
        "H${n(x + rr)} A${n(rr)} ${n(rr)} 0 0 1 ${n(x)} ${n(y + h - rr)} " +
        "V${n(y + rr)} A${n(rr)} ${n(rr)} 0 0 1 ${n(x + rr)} ${n(y)} Z"
}

/** A closed polygon through [pts] given as x, y, x, y, …. */
fun poly(vararg pts: Float): String {
    val sb = StringBuilder()
    for (i in pts.indices step 2) {
        sb.append(if (i == 0) "M" else "L").append(n(pts[i])).append(' ').append(n(pts[i + 1])).append(' ')
    }
    return sb.append('Z').toString()
}

/** A point on a circle, [deg] degrees clockwise from 3 o'clock. */
internal fun polar(cx: Float, cy: Float, r: Float, deg: Float): Pair<Float, Float> {
    val a = deg * PI.toFloat() / 180f
    return (cx + r * cos(a)) to (cy + r * sin(a))
}

/** A star of [points] points. The first point is at [startDeg] (default straight up). */
fun star(
    cx: Float,
    cy: Float,
    outer: Float,
    inner: Float,
    points: Int = 5,
    startDeg: Float = -90f
): String {
    val sb = StringBuilder()
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val (x, y) = polar(cx, cy, r, startDeg + i * 180f / points)
        sb.append(if (i == 0) "M" else "L").append(n(x)).append(' ').append(n(y)).append(' ')
    }
    return sb.append('Z').toString()
}

/** A four-pointed twinkle with concave sides. */
fun sparkle4(cx: Float, cy: Float, r: Float, pinch: Float = 0.2f): String {
    val p = pinch * r
    return "M${n(cx)} ${n(cy - r)} Q${n(cx + p)} ${n(cy - p)} ${n(cx + r)} ${n(cy)} " +
        "Q${n(cx + p)} ${n(cy + p)} ${n(cx)} ${n(cy + r)} " +
        "Q${n(cx - p)} ${n(cy + p)} ${n(cx - r)} ${n(cy)} " +
        "Q${n(cx - p)} ${n(cy - p)} ${n(cx)} ${n(cy - r)} Z"
}

/** A thick rounded bar from (x1, y1) to (x2, y2), [w] wide, with round ends. */
fun capsule(x1: Float, y1: Float, x2: Float, y2: Float, w: Float): String {
    val dx = x2 - x1
    val dy = y2 - y1
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    val r = w / 2f
    val nx = -dy / len * r
    val ny = dx / len * r
    return "M${n(x1 - nx)} ${n(y1 - ny)} L${n(x2 - nx)} ${n(y2 - ny)} " +
        "A${n(r)} ${n(r)} 0 0 1 ${n(x2 + nx)} ${n(y2 + ny)} " +
        "L${n(x1 + nx)} ${n(y1 + ny)} A${n(r)} ${n(r)} 0 0 1 ${n(x1 - nx)} ${n(y1 - ny)} Z"
}

/** A classic heart centred on (cx, cy), about [s] units wide. */
fun heart(cx: Float, cy: Float, s: Float): String {
    val k = s / 100f
    fun x(v: Float) = n(cx + (v - 50f) * k)
    fun y(v: Float) = n(cy + (v - 50f) * k)
    return "M${x(50f)} ${y(86f)} C${x(18f)} ${y(62f)} ${x(8f)} ${y(40f)} ${x(24f)} ${y(26f)} " +
        "C${x(36f)} ${y(15f)} ${x(48f)} ${y(22f)} ${x(50f)} ${y(32f)} " +
        "C${x(52f)} ${y(22f)} ${x(64f)} ${y(15f)} ${x(76f)} ${y(26f)} " +
        "C${x(92f)} ${y(40f)} ${x(82f)} ${y(62f)} ${x(50f)} ${y(86f)} Z"
}
