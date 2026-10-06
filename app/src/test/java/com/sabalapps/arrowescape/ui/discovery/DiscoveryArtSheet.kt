package com.sabalapps.arrowescape.ui.discovery

import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import java.io.File

/**
 * Writes the discovery artwork out as an SVG review sheet — development only, in
 * the same spirit as the Campaign shape table. It draws the same layers the same
 * way `DiscoveryArtwork` does (ring, ink, fill, then details), so what is read off
 * the sheet is what the app draws, to within the difference between SVG and Skia.
 */
internal object DiscoveryArtSheet {

    private fun hex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

    private fun alphaOf(argb: Long): Float = ((argb shr 24) and 0xFF) / 255f

    /** One art as an SVG `<g>` in a 100 × 100 box at the origin. */
    fun group(spec: DiscoveryArtSpec, idPrefix: String): String {
        val defs = StringBuilder()
        val body = StringBuilder()
        var gradient = 0
        fun fillRef(fill: ArtFill): String {
            if (fill.top == fill.bottom) return hex(fill.top)
            val id = "$idPrefix-g${gradient++}"
            defs.append(
                """<linearGradient id="$id" x1="0" y1="0" x2="0" y2="1">""" +
                    """<stop offset="0" stop-color="${hex(fill.top)}" stop-opacity="${alphaOf(fill.top)}"/>""" +
                    """<stop offset="1" stop-color="${hex(fill.bottom)}" stop-opacity="${alphaOf(fill.bottom)}"/></linearGradient>"""
            )
            return "url(#$id)"
        }
        layersLoop@ for (layer in spec.layers) {
            when (layer) {
                is BodyLayer -> {
                    val rule = if (layer.evenOdd) "evenodd" else "nonzero"
                    val ref = fillRef(layer.fill)
                    if (layer.ring) {
                        body.append(
                            """<path d="${layer.d}" fill="#FFFFFF" stroke="#FFFFFF" stroke-linejoin="round" """ +
                                """stroke-width="${ART_SOFT + 2 * ART_INK + 2 * ART_RING}" fill-rule="$rule"/>"""
                        )
                    }
                    body.append(
                        """<path d="${layer.d}" fill="${hex(Ink)}" stroke="${hex(Ink)}" stroke-linejoin="round" """ +
                            """stroke-width="${ART_SOFT + 2 * ART_INK}" fill-rule="$rule"/>"""
                    )
                    body.append(
                        """<path d="${layer.d}" fill="$ref" stroke="$ref" stroke-linejoin="round" """ +
                            """stroke-width="$ART_SOFT" fill-rule="$rule"/>"""
                    )
                }
                is DetailLayer -> for (s in layer.shapes) {
                    val rule = if (s.evenOdd) "evenodd" else "nonzero"
                    val fill = s.fill?.let { fillRef(it) } ?: "none"
                    // A softened fill is drawn first, then its edge (if it has one) over it.
                    if (s.soften && s.fill != null) {
                        body.append(
                            """<path d="${s.d}" fill="$fill" stroke="$fill" stroke-width="$ART_SOFT" """ +
                                """stroke-linecap="round" stroke-linejoin="round" opacity="${s.alpha}" fill-rule="$rule"/>"""
                        )
                    } else if (s.fill != null) {
                        body.append(
                            """<path d="${s.d}" fill="$fill" stroke="none" opacity="${s.alpha}" fill-rule="$rule"/>"""
                        )
                    }
                    if (s.stroke != null) {
                        body.append(
                            """<path d="${s.d}" fill="none" stroke="${hex(s.stroke)}" stroke-width="${s.strokeWidth}" """ +
                                """stroke-linecap="round" stroke-linejoin="round" opacity="${s.alpha}" fill-rule="$rule"/>"""
                        )
                    }
                }
            }
        }
        return "<defs>$defs</defs>$body"
    }

    /** A sheet of every catalogue discovery on [background]. */
    fun sheet(background: String, label: String): String {
        val cell = 240
        val cols = 6
        val rows = 5
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${cols * cell}" height="${rows * (cell + 20)}">""")
        sb.append("""<rect width="100%" height="100%" fill="$background"/>""")
        CampaignDiscoveries.all.forEachIndexed { i, d ->
            val spec = DiscoveryArtRegistry.specFor(d.artKey) ?: return@forEachIndexed
            val x = (i % cols) * cell
            val y = (i / cols) * (cell + 20)
            sb.append("""<g transform="translate(${x + 20} ${y + 10}) scale(2)">""")
            sb.append(group(spec, "a$i"))
            sb.append("</g>")
            sb.append("""<text x="${x + cell / 2}" y="${y + cell + 8}" text-anchor="middle" font-family="sans-serif" font-size="14" fill="$label">${d.name}</text>""")
        }
        sb.append("</svg>")
        return sb.toString()
    }

    fun write(dir: File) {
        dir.mkdirs()
        File(dir, "discovery-art-dark.svg").writeText(sheet("#14194A", "#FFFFFF"))
        File(dir, "discovery-art-light.svg").writeText(sheet("#F2F5FF", "#222244"))
        File(dir, "discovery-art-blue.svg").writeText(sheet("#3E5BD8", "#FFFFFF"))
    }
}
