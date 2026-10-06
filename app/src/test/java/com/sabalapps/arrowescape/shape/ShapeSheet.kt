package com.sabalapps.arrowescape.shape

import java.io.File
import kotlin.math.hypot

/**
 * Dev-only SVG review sheets for silhouettes — the way the Campaign shapes and the
 * Daily / Endless catalogue were audited. Each shape is drawn three ways, left to
 * right: the pieces as the board lays them out, the confirmation outline traced from
 * the cells (with its ghost fill), and the polished silhouette the Daily / Endless
 * reveal draws. Render one with headless Chrome (`--screenshot`) to look at it.
 *
 * Test source set, so it cannot reach the app, and nothing here asserts anything.
 */
internal object ShapeSheet {

    class Entry(val label: String, val mask: ShapeMask, val note: String = "")

    private const val CELL = 22
    private const val PAD = 14
    private const val PANEL_W = 6 * CELL
    private const val PANEL_H = 9 * CELL
    private const val GAP = 10

    fun svg(title: String, entries: List<Entry>, perRow: Int = 3, background: String = "#1C2447"): String {
        val blockW = PANEL_W * 3 + GAP * 2
        val blockH = PANEL_H + 30
        val rows = (entries.size + perRow - 1) / perRow
        val width = perRow * (blockW + PAD * 2) + PAD
        val height = rows * (blockH + PAD) + 52
        val out = StringBuilder()
        out.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height" font-family="Helvetica, Arial, sans-serif">""")
        out.append("""<rect width="$width" height="$height" fill="$background"/>""")
        out.append("""<text x="$PAD" y="30" font-size="20" font-weight="700" fill="#FFFFFF">$title</text>""")
        entries.forEachIndexed { index, entry ->
            val col = index % perRow
            val row = index / perRow
            val x0 = PAD + col * (blockW + PAD * 2)
            val y0 = 52 + row * (blockH + PAD)
            out.append("""<text x="$x0" y="${y0 + 14}" font-size="13" font-weight="700" fill="#FFE28A">${esc(entry.label)}</text>""")
            if (entry.note.isNotEmpty()) {
                out.append("""<text x="${x0 + blockW}" y="${y0 + 14}" font-size="11" text-anchor="end" fill="#9AA6D8">${esc(entry.note)}</text>""")
            }
            val top = y0 + 22
            panel(out, entry.mask, x0, top, PANEL_W, PANEL_H, mode = 0)
            panel(out, entry.mask, x0 + PANEL_W + GAP, top, PANEL_W, PANEL_H, mode = 1)
            panel(out, entry.mask, x0 + (PANEL_W + GAP) * 2, top, PANEL_W, PANEL_H, mode = 2)
        }
        out.append("</svg>")
        return out.toString()
    }

    private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;")

    private fun panel(out: StringBuilder, mask: ShapeMask, x: Int, y: Int, w: Int, h: Int, mode: Int) {
        out.append("""<rect x="$x" y="$y" width="$w" height="$h" rx="8" fill="#FFFFFF" fill-opacity="0.05"/>""")
        val box = mask.bounds ?: return
        // Centre the occupied box in the panel, exactly as the board does.
        val ox = x + (w - box.columns * CELL) / 2.0 - box.minCol * CELL
        val oy = y + (h - box.rows * CELL) / 2.0 - box.minRow * CELL
        val contour = GridContourTracer.trace(mask)
        when (mode) {
            0 -> for (c in mask.cells) {
                out.append(
                    """<rect x="${ox + c.col * CELL + 1.5}" y="${oy + c.row * CELL + 1.5}" width="${CELL - 3}" height="${CELL - 3}" rx="6" fill="#3F51D5" stroke="#8EA0FF" stroke-width="1"/>"""
                )
            }
            1 -> {
                val d = contour.loops.joinToString(" ") { rounded(it, ox, oy, CELL * 0.3) }
                out.append("""<path d="$d" fill="#5BE3F0" fill-opacity="0.16" fill-rule="evenodd"/>""")
                out.append("""<path d="$d" fill="none" stroke="#5BE3F0" stroke-opacity="0.35" stroke-width="7" stroke-linejoin="round"/>""")
                out.append("""<path d="$d" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linejoin="round"/>""")
                for (c in mask.cells) {
                    out.append(
                        """<rect x="${ox + c.col * CELL + 4}" y="${oy + c.row * CELL + 4}" width="${CELL - 8}" height="${CELL - 8}" rx="4" fill="#FFFFFF" fill-opacity="0.12"/>"""
                    )
                }
            }
            else -> {
                val d = contour.loops.joinToString(" ") { loop ->
                    val pts = loop.smoothed(2)
                    "M" + pts.joinToString(" L") { "%.2f %.2f".format(ox + it.x * CELL, oy + it.y * CELL) } + " Z"
                }
                out.append("""<path d="$d" fill="#1B2A6B" stroke="#FFFFFF" stroke-width="6" stroke-linejoin="round" fill-rule="evenodd"/>""")
                out.append("""<path d="$d" fill="#1B2A6B" stroke="#1B2A6B" stroke-width="4" stroke-linejoin="round" fill-rule="evenodd"/>""")
                out.append("""<path d="$d" fill="#7FE3F2" stroke="#7FE3F2" stroke-width="1" stroke-linejoin="round" fill-rule="evenodd"/>""")
            }
        }
    }

    /** A loop with every corner rounded by up to [r], as SVG path data. */
    private fun rounded(loop: ShapeLoop, ox: Double, oy: Double, r: Double): String {
        val pts = loop.points
        val n = pts.size
        val sb = StringBuilder()
        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]
            val cur = pts[i]
            val next = pts[(i + 1) % n]
            val inLen = hypot((cur.x - prev.x).toDouble(), (cur.y - prev.y).toDouble()) * CELL
            val outLen = hypot((next.x - cur.x).toDouble(), (next.y - cur.y).toDouble()) * CELL
            val rr = minOf(r, inLen / 2, outLen / 2)
            val ax = cur.x * CELL + (prev.x - cur.x) / (inLen / CELL) * rr
            val ay = cur.y * CELL + (prev.y - cur.y) / (inLen / CELL) * rr
            val bx = cur.x * CELL + (next.x - cur.x) / (outLen / CELL) * rr
            val by = cur.y * CELL + (next.y - cur.y) / (outLen / CELL) * rr
            sb.append(if (i == 0) "M" else " L").append("%.2f %.2f".format(ox + ax, oy + ay))
            sb.append(" Q%.2f %.2f %.2f %.2f".format(ox + cur.x * CELL, oy + cur.y * CELL, ox + bx, oy + by))
        }
        sb.append(" Z")
        return sb.toString()
    }

    fun write(file: File, title: String, entries: List<Entry>, perRow: Int = 3) {
        file.parentFile?.mkdirs()
        file.writeText(svg(title, entries, perRow))
    }
}
