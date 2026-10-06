package com.sabalapps.arrowescape.ui.discovery

/** Sunset Canyon: Sun · Cactus · Mountain · Canyon Arch · Eagle · Treasure Chest. */
internal object CanyonDiscoveryArt {

    val specs: Map<String, DiscoveryArtSpec> by lazy {
        mapOf(
            "canyon_sun" to sunArt(),
            "canyon_cactus" to cactusArt(),
            "canyon_mountain" to mountainArt(),
            "canyon_arch" to archArt(),
            "canyon_eagle" to eagleArt(),
            "canyon_treasure_chest" to chestArt()
        )
    }

    private fun sunArt() = art {
        val cx = 50f
        val cy = 50f
        fun PathBuilder.rays() {
            for (i in 0 until 12) {
                val a = i * 30f
                val (x1, y1) = polar(cx, cy, 27f, a - 9f)
                val (tx, ty) = polar(cx, cy, 42f, a)
                val (x2, y2) = polar(cx, cy, 27f, a + 9f)
                +poly(x1, y1, tx, ty, x2, y2)
            }
        }
        body(rgb(0xFFD25A), rgb(0xFF9A2E)) { rays(); +circle(cx, cy, 27f) }
        body(rgb(0xFFF08A), rgb(0xFFA52E)) { +circle(cx, cy, 26f) }
        details {
            gloss(ellipse(38f, 33f, 8f, 3.6f, -40f), 0.55f)
            face(cx, cy + 2f, 1.15f)
            twinkle(88f, 16f, 5.5f, White)
        }
    }

    private fun cactusArt() = art {
        fun PathBuilder.cactus() {
            +roundRect(38f, 16f, 24f, 66f, 12f)
            +roundRect(14f, 38f, 12f, 28f, 6f)
            +roundRect(14f, 56f, 30f, 12f, 6f)
            +roundRect(74f, 28f, 12f, 30f, 6f)
            +roundRect(56f, 46f, 30f, 12f, 6f)
        }
        val pot = roundRect(30f, 76f, 40f, 17f, 5f)
        body(rgb(0x82E08E), rgb(0x2E9E5E)) { cactus(); +pot }
        body(rgb(0xF0A26C), rgb(0xC9603A)) { +pot }
        details {
            gloss(roundRect(42f, 24f, 4f, 36f, 2f), 0.45f)
            gloss(roundRect(17f, 42f, 3.4f, 14f, 1.7f), 0.45f)
            gloss(roundRect(77f, 32f, 3.4f, 14f, 1.7f), 0.45f)
            line("M32 76 L68 76", rgb(0x9A3F22), 1.5f, 0.35f)
            face(50f, 50f, 0.95f)
            // a flower on top
            for (i in 0 until 5) {
                val (x, y) = polar(50f, 14f, 5f, -90f + i * 72f)
                fill(circle(x, y, 3.6f), rgb(0xFF8FB8), edge = Ink, edgeWidth = 1.2f)
            }
            fill(circle(50f, 14f, 3.2f), rgb(0xFFE56E), edge = Ink, edgeWidth = 1.2f)
            twinkle(90f, 76f, 5f, rgb(0xFFE56E))
        }
    }

    private fun mountainArt() = art {
        val big = poly(8f, 88f, 38f, 20f, 68f, 88f)
        val small = poly(46f, 88f, 70f, 38f, 94f, 88f)
        body(rgb(0xF7AB78), rgb(0xC7623C)) { +big; +small }
        body(rgb(0xE98954), rgb(0xA84A30)) { +small }
        details {
            // snow
            fill(poly(38f, 20f, 29f, 40f, 34f, 37f, 38f, 43f, 43f, 37f, 47f, 40f), White, soften = true)
            fill(poly(70f, 38f, 63f, 52f, 67f, 50f, 70f, 55f, 74f, 50f, 77f, 52f), White, soften = true)
            // strata on the big face
            line("M17 72 L58 72", rgb(0x8A3E24), 1.5f, 0.25f)
            line("M26 56 L51 56", rgb(0x8A3E24), 1.5f, 0.2f)
            gloss(ellipse(30f, 52f, 3f, 9f, 28f), 0.35f)
            face(38f, 70f, 0.9f)
            twinkle(88f, 18f, 5.5f, rgb(0xFFE56E))
        }
    }

    private fun archArt() = art {
        val outer = "M8 90 L8 56 C8 28 28 12 50 12 C72 12 92 28 92 56 L92 90 Z"
        val inner = "M27 90 L27 62 C27 46 37 37 50 37 C63 37 73 46 73 62 L73 90 Z"
        // the sunset seen through the arch, behind the rock
        details {
            fill(inner, rgb(0xFFF2B8), rgb(0xFF9A6B))
            dot(50f, 74f, 13f, rgb(0xFFD84D))
            dot(50f, 74f, 13f, rgb(0xFFF6B0), 0.45f)
            fill("M27 90 L27 83 Q50 76 73 83 L73 90 Z", rgb(0xFFD79A), rgb(0xE9985C))
        }
        body(rgb(0xF4AB74), rgb(0xC95F38), evenOdd = true) { +outer; +inner }
        details {
            line("M11 70 L24 70", rgb(0x8A3E24), 1.5f, 0.25f)
            line("M11 81 L24 81", rgb(0x8A3E24), 1.5f, 0.2f)
            line("M76 70 L89 70", rgb(0x8A3E24), 1.5f, 0.25f)
            line("M76 81 L89 81", rgb(0x8A3E24), 1.5f, 0.2f)
            gloss(ellipse(26f, 31f, 6.5f, 2.8f, -40f), 0.4f)
            face(50f, 25f, 0.7f)
            twinkle(90f, 10f, 5f, White)
        }
    }

    private fun eagleArt() = art {
        body(rgb(0xB98351), rgb(0x6E4526)) {
            +"M8 56 C8 40 22 32 34 46 L50 58 L66 46 C78 32 92 40 92 56 C92 76 72 90 50 90 C28 90 8 76 8 56 Z"
            +circle(50f, 38f, 22f)
        }
        details {
            line("M16 62 Q24 72 32 66", rgb(0xEBC79C), 1.6f, 0.55f)
            line("M16 74 Q24 83 32 77", rgb(0xEBC79C), 1.6f, 0.45f)
            line("M84 62 Q76 72 68 66", rgb(0xEBC79C), 1.6f, 0.55f)
            line("M84 74 Q76 83 68 77", rgb(0xEBC79C), 1.6f, 0.45f)
        }
        body(rgb(0xFFFFFF), rgb(0xF1E7D6)) { +circle(50f, 38f, 21f) }
        body(rgb(0xFFD86B), rgb(0xF2A21E)) {
            +"M40 45 L60 45 C62 55 56 66 50 70 C44 66 38 55 40 45 Z"
        }
        details {
            line("M41 48 Q50 52 59 48", rgb(0xB8741A), 1.4f, 0.55f)
            // bright-eyed and friendly: gently arched brows, not a scowl — the angled
            // brows this had read stern beside the other twenty-nine
            fill(ellipse(40f, 37f, 4.4f, 5.2f), Ink)
            fill(ellipse(60f, 37f, 4.4f, 5.2f), Ink)
            dot(41.4f, 35.2f, 1.6f, White)
            dot(61.4f, 35.2f, 1.6f, White)
            line("M33 30 Q39 26.5 45 30", Ink, 1.8f)
            line("M67 30 Q61 26.5 55 30", Ink, 1.8f)
            fill(ellipse(31f, 47f, 3.6f, 2.3f), Blush, alpha = 0.6f)
            fill(ellipse(69f, 47f, 3.6f, 2.3f), Blush, alpha = 0.6f)
            gloss(ellipse(38f, 22f, 6f, 2.6f, -25f), 0.5f)
            twinkle(90f, 20f, 5.5f, rgb(0xFFE56E))
        }
    }

    private fun chestArt() = art {
        val lid = "M14 52 L14 40 C14 22 30 14 50 14 C70 14 86 22 86 40 L86 52 Z"
        val box = roundRect(14f, 50f, 72f, 40f, 6f)
        body(rgb(0xDDA866), rgb(0xA8672F)) { +lid; +box }
        body(rgb(0xD39A58), rgb(0x9C5C2A)) { +box }
        body(rgb(0xEDB877), rgb(0xBC7A3A)) { +lid }
        details {
            // gold bands and trim
            fill(poly(27f, 52f, 27f, 28f, 36f, 19.5f, 36f, 52f), rgb(0xFFE680), rgb(0xF2A81E), soften = true)
            fill(poly(64f, 52f, 64f, 19.5f, 73f, 28f, 73f, 52f), rgb(0xFFE680), rgb(0xF2A81E), soften = true)
            fill(roundRect(26f, 52f, 11f, 36f, 2f), rgb(0xFFE680), rgb(0xF2A81E))
            fill(roundRect(63f, 52f, 11f, 36f, 2f), rgb(0xFFE680), rgb(0xF2A81E))
            line("M14 52 L86 52", rgb(0x6E3F17), 1.8f, 0.55f)
            gloss(ellipse(32f, 30f, 7f, 2.6f, -35f), 0.4f)
            // the lock
            fill(roundRect(43f, 44f, 14f, 17f, 3.5f), rgb(0xFFE680), rgb(0xF2A81E), edge = Ink, edgeWidth = 1.7f)
            dot(50f, 50f, 2.4f, Ink)
            fill(roundRect(49f, 51f, 2f, 6f, 1f), Ink)
            twinkle(88f, 14f, 6f, White)
            twinkle(10f, 30f, 4.5f, rgb(0xFFE56E))
        }
    }
}
