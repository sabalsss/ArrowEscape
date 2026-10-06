package com.sabalapps.arrowescape.ui.discovery

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Cosmic: Comet · Rocket · Planet · UFO · Satellite · Galaxy. */
internal object CosmicDiscoveryArt {

    val specs: Map<String, DiscoveryArtSpec> by lazy {
        mapOf(
            "cosmic_comet" to cometArt(),
            "cosmic_rocket" to rocketArt(),
            "cosmic_planet" to planetArt(),
            "cosmic_ufo" to ufoArt(),
            "cosmic_satellite" to satelliteArt(),
            "cosmic_galaxy" to galaxyArt()
        )
    }

    private fun cometArt() = art {
        val tail = "M80 50 C62 34 38 20 12 12 C24 36 34 60 52 78 Z"
        body(rgb(0xFFD36B), rgb(0xFF8A3D)) { +tail; +circle(66f, 64f, 21f) }
        body(rgb(0xFFFFFF), rgb(0xFFE08A)) { +circle(66f, 64f, 20f) }
        details {
            line("M66 50 C54 38 36 26 18 17", White, 2.6f, 0.75f)
            line("M58 62 C48 52 38 42 26 32", White, 1.8f, 0.55f)
            gloss(ellipse(58f, 54f, 6f, 2.6f, -40f), 0.7f)
            face(66f, 66f, 0.98f)
            twinkle(86f, 20f, 6f, White)
            twinkle(16f, 70f, 5f, rgb(0xFFE56E))
            twinkle(40f, 90f, 4f, rgb(0x8FE6FF))
        }
    }

    private fun rocketArt() = art {
        val flame = "M38 68 C38 80 45 88 50 92 C55 88 62 80 62 68 Z"
        val finL = "M33 52 C20 56 13 68 13 82 L35 72 Z"
        val finR = "M67 52 C80 56 87 68 87 82 L65 72 Z"
        val hull = "M50 8 C68 21 74 44 70 68 L30 68 C26 44 32 21 50 8 Z"
        body(rgb(0xFFFFFF), rgb(0xC9D6FF)) { +flame; +finL; +finR; +hull }
        body(rgb(0xFFE680), rgb(0xFF7A2E)) { +flame }
        body(rgb(0xFF9494), rgb(0xE6455A)) { +finL; +finR }
        body(rgb(0xFFFFFF), rgb(0xC9D6FF)) { +hull }
        body(rgb(0xFF9494), rgb(0xE6455A)) {
            +"M50 8 C60 15 66 24 68.5 33 L31.5 33 C34 24 40 15 50 8 Z"
        }
        body(rgb(0xB5ECFF), rgb(0x3E9BEF)) { +circle(50f, 49f, 10.5f) }
        details {
            line("M32 33 L68 33", Ink, 1.4f, 0.35f)
            gloss(ellipse(40f, 24f, 2.6f, 7f, 22f), 0.5f)
            gloss(ellipse(38f, 54f, 2.4f, 9f, 6f), 0.4f)
            fill(ellipse(50f, 80f, 4.2f, 7f), White, alpha = 0.6f)
            face(50f, 50f, 0.52f, blush = false)
            twinkle(86f, 18f, 5.5f, White)
            twinkle(14f, 36f, 4.5f, rgb(0xFFE56E))
        }
    }

    /** One half of the ring's annulus, as a polyline: [lower] is the half in front. */
    private fun ringHalf(
        cx: Float,
        cy: Float,
        rxOuter: Float,
        ryOuter: Float,
        rxInner: Float,
        ryInner: Float,
        rotDeg: Float,
        lower: Boolean
    ): String {
        val steps = 28
        val rot = rotDeg * PI.toFloat() / 180f
        val cr = cos(rot)
        val sr = sin(rot)
        fun pt(rx: Float, ry: Float, t: Float): String {
            val x = rx * cos(t)
            val y = ry * sin(t)
            return "${n(cx + x * cr - y * sr)} ${n(cy + x * sr + y * cr)}"
        }
        // The front half of the ring sweeps t from 0 to π (down, in screen space);
        // the back half from π to 2π. Out along the outer edge, back along the inner.
        val t0 = if (lower) 0f else PI.toFloat()
        val sb = StringBuilder()
        for (i in 0..steps) {
            val t = t0 + PI.toFloat() * i / steps
            sb.append(if (i == 0) "M" else "L").append(pt(rxOuter, ryOuter, t)).append(' ')
        }
        for (i in steps downTo 0) {
            val t = t0 + PI.toFloat() * i / steps
            sb.append("L").append(pt(rxInner, ryInner, t)).append(' ')
        }
        return sb.append('Z').toString()
    }

    private fun planetArt() = art {
        val cx = 50f
        val cy = 52f
        val rot = -20f
        val whole = ringHalf(cx, cy, 46f, 15f, 33f, 8.5f, rot, lower = true) + " " +
            ringHalf(cx, cy, 46f, 15f, 33f, 8.5f, rot, lower = false)
        // the ring behind the planet…
        body(rgb(0xFFE79A), rgb(0xFFB347)) { +whole }
        // …the planet…
        body(rgb(0xC2A9FF), rgb(0x6A4BE0)) { +circle(cx, cy, 27f) }
        details {
            line("M30 33 Q50 28 70 33", White, 3.2f, 0.28f)
            line("M26 69 Q50 78 74 69", Ink, 3.2f, 0.16f)
            gloss(ellipse(38f, 36f, 3.6f, 9f, 40f), 0.5f)
            dot(70f, 60f, 3.2f, White, 0.2f)
            dot(33f, 62f, 2.4f, White, 0.2f)
        }
        // …and the near half of the ring crossing in front of it.
        body(rgb(0xFFE79A), rgb(0xFFB347), ring = false) {
            +ringHalf(cx, cy, 46f, 15f, 33f, 8.5f, rot, lower = true)
        }
        details {
            face(cx, cy - 10f, 0.9f)
            twinkle(86f, 18f, 5.5f, White)
            twinkle(12f, 28f, 4.5f, rgb(0xFFE56E))
        }
    }

    private fun ufoArt() = art {
        details {
            fill("M34 70 L66 70 L82 94 L18 94 Z", 0x66FFF2A6, 0x00FFF2A6)
        }
        val dome = "M28 54 C28 32 38 22 50 22 C62 22 72 32 72 54 Z"
        val saucer = ellipse(50f, 58f, 42f, 13f)
        val bump = ellipse(50f, 70f, 17f, 6f)
        body(rgb(0xD7DBFF), rgb(0x8F93E6)) { +dome; +saucer; +bump }
        body(rgb(0xDDF8FF), rgb(0x8FDCF5)) { +dome }
        details {
            gloss(ellipse(38f, 34f, 3f, 8f, 25f), 0.55f)
            // the pilot
            fill(circle(50f, 38f, 9f), rgb(0xA8F5A0), rgb(0x4FC864), edge = Ink, edgeWidth = 1.5f)
            fill(ellipse(46.4f, 38.5f, 2f, 3f, 20f), Ink)
            fill(ellipse(53.6f, 38.5f, 2f, 3f, -20f), Ink)
            dot(46.9f, 37.4f, 0.8f, White)
            dot(54.1f, 37.4f, 0.8f, White)
            line("M48 43 Q50 45 52 43", Ink, 1.2f)
        }
        body(rgb(0xD7DBFF), rgb(0x8F93E6)) { +saucer }
        details {
            gloss(ellipse(30f, 53f, 9f, 2.6f, -12f), 0.5f)
            dot(22f, 60f, 3.4f, rgb(0xFFE56E))
            dot(36f, 64f, 3.4f, rgb(0xFF8FB8))
            dot(50f, 66f, 3.4f, rgb(0x8FE6FF))
            dot(64f, 64f, 3.4f, rgb(0xFF8FB8))
            dot(78f, 60f, 3.4f, rgb(0xFFE56E))
            twinkle(88f, 14f, 5.5f, White)
            twinkle(12f, 22f, 4.5f, rgb(0xFFE56E))
        }
    }

    private fun satelliteArt() = art {
        val panelL = roundRect(5f, 38f, 25f, 24f, 3f)
        val panelR = roundRect(70f, 38f, 25f, 24f, 3f)
        val box = roundRect(35f, 32f, 30f, 34f, 7f)
        body(rgb(0xFFE98A), rgb(0xF2A81E)) {
            +panelL; +panelR; +box
            +roundRect(29f, 46f, 8f, 6f, 2f); +roundRect(63f, 46f, 8f, 6f, 2f)
            +circle(50f, 17f, 4f); +roundRect(48.5f, 18f, 3f, 16f, 1.5f)
        }
        body(rgb(0x9FC8FF), rgb(0x3E6FE0)) { +panelL; +panelR }
        body(rgb(0xFFE98A), rgb(0xF2A81E)) { +box }
        details {
            for (x in listOf(13.3f, 21.6f, 78.3f, 86.6f)) {
                line("M${n(x)} 40 L${n(x)} 60", White, 1.2f, 0.5f)
            }
            line("M7 50 L28 50", White, 1.2f, 0.5f)
            line("M72 50 L93 50", White, 1.2f, 0.5f)
            gloss(ellipse(10f, 43f, 3.6f, 1.6f, -20f), 0.5f)
            gloss(ellipse(75f, 43f, 3.6f, 1.6f, -20f), 0.5f)
            dot(50f, 17f, 4f, rgb(0xFF7A8A))
            gloss(ellipse(48.8f, 15.8f, 1.1f, 1.1f), 0.8f)
            // the dish
            fill("M36 30 C38 20 62 20 64 30 Z", White, rgb(0xC9D6FF), edge = Ink, edgeWidth = 1.5f)
            face(50f, 51f, 0.74f)
            twinkle(88f, 14f, 5.5f, White)
            twinkle(14f, 82f, 4.5f, rgb(0xFFE56E))
            twinkle(80f, 86f, 4f, rgb(0x8FE6FF))
        }
    }

    private fun galaxyArt() = art {
        val cx = 50f
        val cy = 50f
        body(rgb(0x7A5BE8), rgb(0x2B1E88)) { +circle(cx, cy, 40f) }
        details {
            // two spiral arms, each a soft wide stroke with a bright thread on it
            for (arm in 0 until 2) {
                val pts = ArrayList<Pair<Float, Float>>()
                var theta = 0f
                while (theta <= 3.6f) {
                    val r = 7f + 29f * (theta / 3.6f)
                    val a = theta + arm * PI.toFloat() + 0.2f
                    pts += (cx + r * cos(a)) to (cy + r * sin(a))
                    theta += 0.22f
                }
                val d = pts.mapIndexed { i, (x, y) -> (if (i == 0) "M" else "L") + n(x) + " " + n(y) }.joinToString(" ")
                line(d, if (arm == 0) rgb(0xFF8FD0) else rgb(0x7FE3FF), 7.5f, 0.85f)
                line(d, White, 2.2f, 0.7f)
            }
            dot(cx, cy, 13f, rgb(0xFFF6C8), 0.35f)
        }
        body(rgb(0xFFFBE0), rgb(0xFFD36B), ring = false) { +circle(cx, cy, 10.5f) }
        details {
            face(cx, cy + 0.5f, 0.5f, gap = 8f)
            for ((x, y, r) in listOf(
                Triple(20f, 30f, 1.8f), Triple(78f, 24f, 1.6f), Triple(84f, 62f, 1.9f),
                Triple(30f, 80f, 1.6f), Triple(68f, 84f, 1.4f), Triple(14f, 56f, 1.4f)
            )) {
                dot(x, y, r, White, 0.95f)
            }
            twinkle(86f, 16f, 5.5f, White)
            twinkle(14f, 84f, 4.5f, rgb(0xFFE56E))
        }
    }
}
