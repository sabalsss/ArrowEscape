package com.sabalapps.arrowescape.ui.discovery

/** Forest: Leaf · Mushroom · Tree · Butterfly · Fox · Owl. */
internal object ForestDiscoveryArt {

    val specs: Map<String, DiscoveryArtSpec> by lazy {
        mapOf(
            "forest_leaf" to leafArt(),
            "forest_mushroom" to mushroomArt(),
            "forest_tree" to treeArt(),
            "forest_butterfly" to butterflyArt(),
            "forest_fox" to foxArt(),
            "forest_owl" to owlArt()
        )
    }

    private fun leafArt() = art {
        val blade = "M50 9 C82 26 90 60 50 86 C10 60 18 26 50 9 Z"
        body(rgb(0xB4F078), rgb(0x3FB04F)) {
            +blade
            +capsule(50f, 80f, 50f, 93f, 6f)
        }
        details {
            // veins, kept to the sides so the face has the middle
            line("M50 20 L50 37", rgb(0x2D8B3F), 1.8f, 0.55f)
            line("M50 68 L50 84", rgb(0x2D8B3F), 1.8f, 0.55f)
            line("M33 34 Q39 38 43 45", rgb(0x2D8B3F), 1.6f, 0.45f)
            line("M67 34 Q61 38 57 45", rgb(0x2D8B3F), 1.6f, 0.45f)
            line("M26 52 Q33 55 40 62", rgb(0x2D8B3F), 1.6f, 0.45f)
            line("M74 52 Q67 55 60 62", rgb(0x2D8B3F), 1.6f, 0.45f)
            gloss(ellipse(36f, 30f, 3.6f, 9f, 28f), 0.5f)
            face(50f, 52f, 1.0f)
            twinkle(84f, 26f, 6f, rgb(0xFFF08A))
        }
        // a dewdrop
        body(rgb(0xD2F3FF), rgb(0x57B6F0), ring = true) {
            +"M18 62 C18 62 10 72 10 77 C10 82 14 85 18 85 C22 85 26 82 26 77 C26 72 18 62 18 62 Z"
        }
        details { gloss(ellipse(15f, 77f, 1.8f, 3.2f, 15f), 0.8f) }
    }

    private fun mushroomArt() = art {
        val cap = "M10 56 C10 28 30 12 50 12 C70 12 90 28 90 56 C90 63 84 65 78 65 L22 65 C16 65 10 63 10 56 Z"
        val stem = "M36 62 C34 78 35 90 42 91 L58 91 C65 90 66 78 64 62 Z"
        body(rgb(0xFF8383), rgb(0xE5384F)) { +cap; +stem }
        body(rgb(0xFFF6E2), rgb(0xF0D3A2)) { +stem }
        body(rgb(0xFF8383), rgb(0xE5384F)) { +cap }
        details {
            gloss(ellipse(25f, 31f, 8f, 3.6f, -40f), 0.45f)
            dot(30f, 42f, 7f, White, 0.95f)
            dot(54f, 28f, 6f, White, 0.95f)
            dot(73f, 46f, 8f, White, 0.95f)
            dot(49f, 50f, 4.5f, White, 0.95f)
            face(50f, 77f, 0.8f)
            line("M38 86 Q50 90 62 86", rgb(0xC79A5E), 1.4f, 0.45f)
            twinkle(86f, 18f, 5.5f, rgb(0xFFF08A))
        }
    }

    private fun treeArt() = art {
        fun PathBuilder.canopy() {
            +circle(50f, 34f, 22f)
            +circle(31f, 52f, 18f)
            +circle(69f, 52f, 18f)
            +circle(50f, 53f, 21f)
        }
        val trunk = "M44 58 L56 58 L57 82 C57 86 61 88 66 91 L34 91 C39 88 43 86 43 82 Z"
        body(rgb(0x92E468), rgb(0x2FA84F)) { canopy(); +trunk }
        body(rgb(0xD59A62), rgb(0x9A5F33)) { +trunk }
        body(rgb(0x92E468), rgb(0x2FA84F)) { canopy() }
        details {
            gloss(ellipse(40f, 22f, 8f, 4f, -35f), 0.45f)
            dot(30f, 56f, 4.2f, rgb(0xFF5A5F))
            dot(70f, 54f, 4.2f, rgb(0xFF5A5F))
            dot(57f, 27f, 4.2f, rgb(0xFF5A5F))
            gloss(ellipse(29.5f, 54.8f, 1.2f, 1.2f), 0.8f)
            gloss(ellipse(69.5f, 52.8f, 1.2f, 1.2f), 0.8f)
            gloss(ellipse(56.5f, 25.8f, 1.2f, 1.2f), 0.8f)
            face(50f, 46f, 1.05f)
            line("M48 70 L48 84", rgb(0x6F4220), 1.4f, 0.4f)
        }
    }

    private fun butterflyArt() = art {
        val upperL = "M47 46 C36 14 8 12 9 36 C10 50 30 58 47 52 Z"
        val upperR = "M53 46 C64 14 92 12 91 36 C90 50 70 58 53 52 Z"
        val lowerL = "M47 54 C34 54 18 62 22 78 C26 90 44 84 47 64 Z"
        val lowerR = "M53 54 C66 54 82 62 78 78 C74 90 56 84 53 64 Z"
        body(rgb(0xC9A8FF), rgb(0x8A5CF0)) {
            +upperL; +upperR; +lowerL; +lowerR
            +roundRect(45.5f, 32f, 9f, 50f, 4.5f)
            +circle(50f, 33f, 7.5f)
        }
        body(rgb(0xFFB9DC), rgb(0xFF6FAE)) { +lowerL; +lowerR }
        body(rgb(0xC9A8FF), rgb(0x8A5CF0)) { +upperL; +upperR }
        body(rgb(0x8C79E0), rgb(0x3F2F8F)) {
            +roundRect(46f, 38f, 8f, 44f, 4f)
            +circle(50f, 33f, 7f)
        }
        details {
            dot(23f, 29f, 6f, White, 0.9f)
            dot(77f, 29f, 6f, White, 0.9f)
            dot(30f, 74f, 3.8f, White, 0.9f)
            dot(70f, 74f, 3.8f, White, 0.9f)
            gloss(ellipse(28f, 22f, 7f, 3f, -35f), 0.4f)
            gloss(ellipse(72f, 22f, 7f, 3f, 35f), 0.4f)
            line("M47 27 Q43 17 36 13", Ink, 1.8f)
            line("M53 27 Q57 17 64 13", Ink, 1.8f)
            dot(36f, 13f, 2.4f, Ink)
            dot(64f, 13f, 2.4f, Ink)
            fill(ellipse(47f, 33f, 1.2f, 1.7f), White)
            fill(ellipse(53f, 33f, 1.2f, 1.7f), White)
            line("M48.5 37 Q50 38.6 51.5 37", White, 1.1f)
            twinkle(88f, 70f, 5f, rgb(0xFFE56E))
        }
    }

    private fun foxArt() = art {
        val head = "M14 34 L25 9 L42 25 C47 24 53 24 58 25 L75 9 L86 34 " +
            "C91 50 84 67 70 77 C62 83 56 87 50 87 C44 87 38 83 30 77 C16 67 9 50 14 34 Z"
        body(rgb(0xFFB35A), rgb(0xF0701A)) { +head }
        body(rgb(0xFFF8EA), rgb(0xF6DDB8)) {
            +"M50 87 C41 87 28 72 24 55 C34 63 42 61 50 61 C58 61 66 63 76 55 C72 72 59 87 50 87 Z"
        }
        details {
            fill(poly(27f, 16f, 38f, 27f, 28f, 31f), rgb(0xCB5E20), soften = true)
            fill(poly(73f, 16f, 62f, 27f, 72f, 31f), rgb(0xCB5E20), soften = true)
            gloss(ellipse(36f, 33f, 6f, 2.6f, -30f), 0.4f)
            // the face
            fill(ellipse(35f, 49f, 2.9f, 3.8f), Ink)
            fill(ellipse(65f, 49f, 2.9f, 3.8f), Ink)
            dot(36f, 47.6f, 1.1f, White)
            dot(66f, 47.6f, 1.1f, White)
            fill(ellipse(25f, 58f, 4f, 2.6f), Blush, alpha = 0.7f)
            fill(ellipse(75f, 58f, 4f, 2.6f), Blush, alpha = 0.7f)
            fill("M44.5 66 Q50 63.5 55.5 66 Q55.5 71.5 50 73.5 Q44.5 71.5 44.5 66 Z", Ink, soften = true)
            line("M50 74 L50 77 M50 77 Q46 81 42 77 M50 77 Q54 81 58 77", Ink, 1.5f)
            twinkle(88f, 22f, 5.5f, rgb(0xFFE56E))
        }
    }

    private fun owlArt() = art {
        val tufts = poly(20f, 34f, 17f, 9f, 41f, 24f) + " " + poly(80f, 34f, 83f, 9f, 59f, 24f)
        body(rgb(0xD6A878), rgb(0x8B5B36)) {
            +circle(50f, 55f, 34f)
            +tufts
        }
        body(rgb(0xB5834F), rgb(0x7B4C2A)) {
            +ellipse(21f, 62f, 7f, 17f, 14f)
            +ellipse(79f, 62f, 7f, 17f, -14f)
        }
        details {
            fill(ellipse(50f, 71f, 20f, 19f), rgb(0xFFF1D2), rgb(0xF0D29B), soften = true)
            for (i in 0 until 3) {
                val x = 41f + i * 9f
                line("M${n(x - 3f)} 68 Q${n(x)} 72 ${n(x + 3f)} 68", rgb(0xB88A52), 1.5f, 0.55f)
                line("M${n(x - 3f)} 77 Q${n(x)} 81 ${n(x + 3f)} 77", rgb(0xB88A52), 1.5f, 0.55f)
            }
        }
        body(rgb(0xFFFFFF), rgb(0xFFF1D2)) {
            +circle(36f, 47f, 13f)
            +circle(64f, 47f, 13f)
        }
        details {
            dot(37f, 48f, 6.6f, Ink)
            dot(63f, 48f, 6.6f, Ink)
            dot(39f, 45.5f, 2.2f, White)
            dot(65f, 45.5f, 2.2f, White)
            dot(35f, 51f, 1.1f, White, 0.8f)
            dot(61f, 51f, 1.1f, White, 0.8f)
            fill(poly(50f, 55f, 46f, 60.5f, 50f, 66f, 54f, 60.5f), rgb(0xFFB23E), rgb(0xF58A1F), soften = true)
            fill(ellipse(22f, 56f, 3.4f, 2.2f), Blush, alpha = 0.6f)
            fill(ellipse(78f, 56f, 3.4f, 2.2f), Blush, alpha = 0.6f)
            fill(ellipse(40f, 91f, 5f, 3f), rgb(0xF58A1F), soften = true)
            fill(ellipse(60f, 91f, 5f, 3f), rgb(0xF58A1F), soften = true)
            gloss(ellipse(36f, 26f, 8f, 3f, -20f), 0.35f)
            twinkle(90f, 40f, 5f, rgb(0xFFE56E))
        }
    }
}
