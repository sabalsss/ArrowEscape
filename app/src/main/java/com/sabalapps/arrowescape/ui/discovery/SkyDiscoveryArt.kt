package com.sabalapps.arrowescape.ui.discovery

/** Sky Garden: Cloud · Flower · Kite · Bird · Heart · Star. */
internal object SkyDiscoveryArt {

    val specs: Map<String, DiscoveryArtSpec> by lazy {
        mapOf(
            "sky_cloud" to cloud(),
            "sky_flower" to flower(),
            "sky_kite" to kite(),
            "sky_bird" to bird(),
            "sky_heart" to heartArt(),
            "sky_star" to starArt()
        )
    }

    private fun cloud() = art {
        body(White, rgb(0xCFE3FF)) {
            +circle(31f, 61f, 15f)
            +circle(51f, 45f, 22f)
            +circle(72f, 59f, 17f)
            +roundRect(15f, 59f, 73f, 24f, 12f)
        }
        details {
            gloss(ellipse(41f, 33f, 9f, 4.5f, -25f))
            face(52f, 63f, 1.05f)
            twinkle(84f, 32f, 6.5f, rgb(0xFFD84D))
            twinkle(14f, 42f, 4.5f, rgb(0x7FD0FF))
        }
    }

    private fun flower() = art {
        val hx = 50f
        val hy = 35f
        fun PathBuilder.petals() {
            for (i in 0 until 6) {
                val (x, y) = polar(hx, hy, 17f, -90f + i * 60f)
                +circle(x, y, 12f)
            }
        }
        fun PathBuilder.stalk() {
            +roundRect(46.5f, 50f, 7f, 42f, 3.5f)
            +ellipse(35f, 79f, 12f, 5.5f, -28f)
            +ellipse(65f, 70f, 12f, 5.5f, 28f)
        }
        // The whole figure once, for the sticker ring and the outer edge.
        body(rgb(0xFFB6D2), rgb(0xFF6FA6)) { petals(); stalk(); +circle(hx, hy, 14f) }
        body(rgb(0x86E07A), rgb(0x2FA85A)) { stalk() }
        body(rgb(0xFFB6D2), rgb(0xFF6FA6)) { petals() }
        body(rgb(0xFFE56E), rgb(0xFFB62E)) { +circle(hx, hy, 12.5f) }
        details {
            gloss(ellipse(36f, 17f, 6f, 3f, -35f), 0.6f)
            gloss(ellipse(66f, 21f, 4f, 2.2f, 35f), 0.5f)
            line("M50 56 L50 88", rgb(0x1F7A44), 1.4f, 0.35f)
            face(hx, hy + 1f, 0.78f)
        }
    }

    private fun kite() = art {
        body(rgb(0xFF9A86), rgb(0xFF5A6E)) { +poly(50f, 7f, 74f, 33f, 50f, 72f, 26f, 33f) }
        details {
            fill(poly(50f, 7f, 74f, 33f, 50f, 33f), White, alpha = 0.26f)
            fill(poly(26f, 33f, 50f, 33f, 50f, 72f), White, alpha = 0.16f)
            line("M50 11 L50 34", Ink, 1.6f, 0.30f)
            line("M29 33 L71 33", Ink, 1.6f, 0.30f)
            gloss(ellipse(41f, 20f, 5.5f, 2.8f, -50f), 0.55f)
            face(50f, 50f, 0.72f)
            // the tail, with its bows
            line("M50 74 C42 79 58 83 49 88 C45 91 52 92 50 95", Ink, 1.7f)
            // little bow-ties: two triangles meeting on the string
            for ((x, y, c) in listOf(
                Triple(46f, 80f, rgb(0xFFD84D)),
                Triple(53f, 85f, rgb(0x6FD3FF)),
                Triple(48f, 90.5f, rgb(0xFFFFFF))
            )) {
                fill(poly(x - 5f, y - 3.2f, x, y, x - 5f, y + 3.2f), c, soften = true)
                fill(poly(x + 5f, y - 3.2f, x + 5f, y + 3.2f, x, y), c, soften = true)
            }
        }
    }

    private fun bird() = art {
        fun PathBuilder.figure() {
            +circle(46f, 56f, 30f)
            +capsule(25f, 55f, 9f, 47f, 10f)
            +capsule(25f, 62f, 9f, 63f, 10f)
            +ellipse(43f, 26f, 4f, 8f, -12f)
            +ellipse(52f, 27f, 3.6f, 7f, 22f)
        }
        body(rgb(0x92D6FF), rgb(0x3E9BEF)) { figure() }
        body(rgb(0xFFC25E), rgb(0xF58A1F)) { +poly(73f, 49f, 91f, 56f, 73f, 63f) }
        details {
            fill(ellipse(55f, 67f, 17f, 15f), rgb(0xF4FBFF), rgb(0xD6EEFF), soften = true)
        }
        body(rgb(0x6DB8F5), rgb(0x2F7FD9)) { +ellipse(34f, 58f, 13f, 9f, -22f) }
        details {
            gloss(ellipse(40f, 36f, 9f, 4.2f, -28f), 0.5f)
            // wing feathers
            line("M26 60 Q32 64 38 61", Ink, 1.3f, 0.30f)
            // eye
            fill(ellipse(62f, 47f, 3.3f, 4.2f), Ink)
            dot(63f, 45.6f, 1.2f, White)
            fill(ellipse(67f, 58f, 3.8f, 2.5f), Blush, alpha = 0.62f)
            // feet
            line("M40 87 L40 93 M36 93 L44 93", rgb(0xF58A1F), 2.2f)
            line("M54 87 L54 93 M50 93 L58 93", rgb(0xF58A1F), 2.2f)
            twinkle(84f, 28f, 5.5f, rgb(0xFFD84D))
        }
    }

    private fun heartArt() = art {
        body(rgb(0xFFA3BE), rgb(0xFF4D79)) { +heart(50f, 52f, 88f) }
        details {
            gloss(ellipse(30f, 36f, 8f, 4.5f, -40f), 0.6f)
            face(50f, 54f, 1.1f)
            twinkle(82f, 22f, 6f, rgb(0xFFE56E))
            twinkle(16f, 64f, 4f, White)
        }
    }

    private fun starArt() = art {
        body(rgb(0xFFEA7D), rgb(0xFFB02E)) { +star(50f, 55f, 42f, 21f) }
        details {
            gloss(ellipse(46f, 32f, 5f, 2.4f, -65f), 0.6f)
            face(50f, 55f, 1.05f)
            twinkle(86f, 22f, 6f, rgb(0xB5ECFF))
            twinkle(14f, 28f, 4f, rgb(0xFFE56E))
        }
    }
}
