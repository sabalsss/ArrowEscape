package com.sabalapps.arrowescape.ui.discovery

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Crystal Night: Gem · Crescent Moon · Crystal · Snowflake · Magic Star · Crown. */
internal object CrystalDiscoveryArt {

    val specs: Map<String, DiscoveryArtSpec> by lazy {
        mapOf(
            "crystal_gem" to gemArt(),
            "crystal_moon" to moonArt(),
            "crystal_cluster" to clusterArt(),
            "crystal_snowflake" to snowflakeArt(),
            "crystal_magic_star" to magicStarArt(),
            "crystal_crown" to crownArt()
        )
    }

    private fun gemArt() = art {
        body(rgb(0xA6F6FF), rgb(0x3E8FF0)) {
            +poly(28f, 18f, 72f, 18f, 92f, 40f, 50f, 88f, 8f, 40f)
        }
        details {
            // crown facets
            fill(poly(28f, 18f, 50f, 18f, 38f, 40f), White, alpha = 0.34f)
            fill(poly(50f, 18f, 72f, 18f, 62f, 40f), White, alpha = 0.12f)
            fill(poly(8f, 40f, 28f, 18f, 38f, 40f), White, alpha = 0.18f)
            fill(poly(72f, 18f, 92f, 40f, 62f, 40f), Ink, alpha = 0.10f)
            // pavilion facets
            fill(poly(8f, 40f, 38f, 40f, 50f, 88f), Ink, alpha = 0.10f)
            fill(poly(38f, 40f, 62f, 40f, 50f, 88f), White, alpha = 0.26f)
            fill(poly(62f, 40f, 92f, 40f, 50f, 88f), Ink, alpha = 0.20f)
            line("M8 40 L92 40", Ink, 1.6f, 0.40f)
            line("M28 18 L38 40 L50 18 L62 40 L72 18", Ink, 1.4f, 0.32f)
            line("M38 40 L50 88 L62 40", Ink, 1.4f, 0.30f)
            twinkle(18f, 14f, 6.5f, White)
            twinkle(90f, 20f, 5f, rgb(0xFFF08A))
            twinkle(84f, 82f, 4.5f, White)
        }
    }

    private fun moonArt() = art {
        // The crescent is the big disc with a smaller one bitten out of it; the two
        // circles' crossing points are worked out rather than eyeballed.
        val outerC = 50f to 52f
        val outerR = 38f
        val innerC = 69f to 41f
        val innerR = 29f
        body(rgb(0xFFF3A8), rgb(0xFFC443)) {
            +crescent(outerC, outerR, innerC, innerR)
        }
        details {
            gloss(ellipse(25f, 36f, 3.4f, 10f, 28f), 0.5f)
            sleepyFace(25f, 58f, 0.66f, gap = 7.2f)
            twinkle(80f, 18f, 6f, rgb(0xFFF08A))
            twinkle(88f, 70f, 5f, White)
            dot(70f, 86f, 2.4f, White, 0.9f)
            dot(90f, 46f, 2f, White, 0.9f)
        }
    }

    /** Disc ([c1], [r1]) minus disc ([c2], [r2]); the discs must overlap. */
    private fun crescent(c1: Pair<Float, Float>, r1: Float, c2: Pair<Float, Float>, r2: Float): String {
        val dx = c2.first - c1.first
        val dy = c2.second - c1.second
        val d = sqrt(dx * dx + dy * dy)
        val a = (r1 * r1 - r2 * r2 + d * d) / (2f * d)
        val h = sqrt(r1 * r1 - a * a)
        val mx = c1.first + a * dx / d
        val my = c1.second + a * dy / d
        val p1x = mx + h * dy / d
        val p1y = my - h * dx / d
        val p2x = mx - h * dy / d
        val p2y = my + h * dx / d
        // Big arc of the outer disc from p1 round the far side to p2, then back along
        // the inner disc. Sweep 0 on the outer arc keeps the whole path clockwise in
        // screen space only when p1 is the upper crossing; the pair is ordered so.
        return "M${n(p1x)} ${n(p1y)} A${n(r1)} ${n(r1)} 0 1 0 ${n(p2x)} ${n(p2y)} " +
            "A${n(r2)} ${n(r2)} 0 0 1 ${n(p1x)} ${n(p1y)} Z"
    }

    private fun clusterArt() = art {
        val left = poly(22f, 32f, 34f, 44f, 34f, 84f, 10f, 84f, 10f, 44f)
        val right = poly(78f, 36f, 90f, 48f, 90f, 84f, 66f, 84f, 66f, 48f)
        val centre = poly(50f, 7f, 66f, 23f, 66f, 84f, 34f, 84f, 34f, 23f)
        val rock = "M6 85 Q20 78 50 80 Q80 78 94 85 Q94 94 50 94 Q6 94 6 85 Z"
        body(rgb(0xD6BDFF), rgb(0x8D63F0)) { +left; +right; +centre; +rock }
        body(rgb(0xFFC6E9), rgb(0xE070C0)) { +left }
        body(rgb(0xBDF3FF), rgb(0x4FB5E8)) { +right }
        body(rgb(0xD9C2FF), rgb(0x8D63F0)) { +centre }
        body(rgb(0xA79FDB), rgb(0x5F58A6)) { +rock }
        details {
            fill(poly(50f, 7f, 66f, 23f, 66f, 80f, 50f, 80f), White, alpha = 0.22f)
            fill(poly(22f, 32f, 34f, 44f, 34f, 80f, 22f, 80f), White, alpha = 0.22f)
            fill(poly(78f, 36f, 90f, 48f, 90f, 80f, 78f, 80f), White, alpha = 0.22f)
            line("M34 23 L66 23", Ink, 1.3f, 0.25f)
            gloss(ellipse(41f, 30f, 2.6f, 7f, 8f), 0.5f)
            face(50f, 56f, 0.95f)
            twinkle(86f, 18f, 6f, White)
            twinkle(14f, 20f, 4.5f, rgb(0xFFE56E))
        }
    }

    private fun snowflakeArt() = art {
        val cx = 50f
        val cy = 50f
        fun PathBuilder.flake() {
            for (i in 0 until 6) {
                val a = -90f + i * 60f
                val (ex, ey) = polar(cx, cy, 39f, a)
                +capsule(cx, cy, ex, ey, 7.5f)
                for (side in listOf(-1f, 1f)) {
                    val (bx, by) = polar(cx, cy, 25f, a)
                    val (tx, ty) = polar(bx, by, 12f, a + side * 52f)
                    +capsule(bx, by, tx, ty, 5.5f)
                }
            }
            +circle(cx, cy, 13f)
        }
        body(White, rgb(0xB6E4FF)) { flake() }
        body(rgb(0xFFFFFF), rgb(0xD6F0FF)) { +circle(cx, cy, 12.5f) }
        details {
            for (i in 0 until 6) {
                val a = -90f + i * 60f
                val (sx, sy) = polar(cx, cy, 16f, a)
                val (ex, ey) = polar(cx, cy, 33f, a)
                line("M${n(sx)} ${n(sy)} L${n(ex)} ${n(ey)}", rgb(0x7FB9F2), 1.4f, 0.55f)
            }
            face(cx, cy + 1f, 0.58f)
            twinkle(14f, 16f, 5f, rgb(0x7FD0FF))
            twinkle(88f, 86f, 4.5f, rgb(0xFFF08A))
        }
    }

    private fun magicStarArt() = art {
        val wand = capsule(14f, 88f, 50f, 52f, 8f)
        body(rgb(0xB89CFF), rgb(0x6B46D8)) { +wand }
        details {
            fill(capsule(40f, 62f, 46f, 56f, 8.2f), rgb(0xFFE680), rgb(0xF2A81E))
            line("M18 84 L30 72", White, 1.6f, 0.45f)
        }
        body(rgb(0xFFC6F0), rgb(0xB06BFF)) { +star(62f, 38f, 31f, 15.5f, startDeg = -90f) }
        details {
            gloss(ellipse(54f, 22f, 4.4f, 2.2f, -55f), 0.6f)
            face(62f, 40f, 0.82f)
            twinkle(16f, 30f, 6.5f, rgb(0xFFF08A))
            twinkle(88f, 80f, 6f, rgb(0x8FE6FF))
            twinkle(16f, 60f, 4f, White)
            dot(90f, 54f, 2.2f, White, 0.9f)
        }
    }

    private fun crownArt() = art {
        val crown = "M14 70 L10 30 L34 48 L50 20 L66 48 L90 30 L86 70 Z"
        val band = roundRect(14f, 64f, 72f, 19f, 6f)
        val balls = circle(10f, 28f, 6.4f) + " " + circle(50f, 16f, 7f) + " " + circle(90f, 28f, 6.4f)
        body(rgb(0xFFE98A), rgb(0xF2A81E)) { +crown; +band; +balls }
        body(rgb(0xFFDB66), rgb(0xE59A18)) { +band }
        body(rgb(0xFFFFFF), rgb(0xFFD3EA)) { +balls }
        details {
            gloss(ellipse(26f, 46f, 3f, 9f, -18f), 0.5f)
            // jewels on the band
            fill(circle(30f, 74f, 4.6f), rgb(0xFF7A8A), rgb(0xE22F55), edge = Ink, edgeWidth = 1.5f)
            fill(circle(50f, 74f, 4.6f), rgb(0x9FE8FF), rgb(0x3E8FF0), edge = Ink, edgeWidth = 1.5f)
            fill(circle(70f, 74f, 4.6f), rgb(0x8BF0B0), rgb(0x23A55A), edge = Ink, edgeWidth = 1.5f)
            gloss(ellipse(28.6f, 72.4f, 1.2f, 1.2f), 0.8f)
            gloss(ellipse(48.6f, 72.4f, 1.2f, 1.2f), 0.8f)
            gloss(ellipse(68.6f, 72.4f, 1.2f, 1.2f), 0.8f)
            twinkle(88f, 52f, 5f, White)
            twinkle(12f, 58f, 4f, rgb(0x8FE6FF))
        }
    }
}
