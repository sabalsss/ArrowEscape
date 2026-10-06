package com.sabalapps.arrowescape.ui.discovery

import androidx.compose.ui.graphics.vector.PathParser
import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The artwork is data, so everything that can go wrong with it short of "does it
 * look nice" — a key with no drawing, a drawing with no key, a path that does not
 * parse, art drawn off the canvas — is checked here on a plain JVM.
 */
class DiscoveryArtRegistryTest {

    /** The thirty keys the product brief names, spelled out so a rename shows here. */
    private val briefKeys = listOf(
        "sky_cloud", "sky_flower", "sky_kite", "sky_bird", "sky_heart", "sky_star",
        "forest_leaf", "forest_mushroom", "forest_tree", "forest_butterfly", "forest_fox", "forest_owl",
        "canyon_sun", "canyon_cactus", "canyon_mountain", "canyon_arch", "canyon_eagle",
        "canyon_treasure_chest",
        "crystal_gem", "crystal_moon", "crystal_cluster", "crystal_snowflake",
        "crystal_magic_star", "crystal_crown",
        "cosmic_comet", "cosmic_rocket", "cosmic_planet", "cosmic_ufo", "cosmic_satellite",
        "cosmic_galaxy"
    )

    private fun allSpecs() = CampaignDiscoveries.all.map { it.artKey to DiscoveryArtRegistry.specFor(it.artKey)!! }

    @Test
    fun `every discovery's art key resolves to a drawing`() {
        CampaignDiscoveries.all.forEach {
            assertNotNull("no artwork for ${it.artKey} (${it.name})", DiscoveryArtRegistry.specFor(it.artKey))
        }
    }

    @Test
    fun `all thirty keys named in the brief resolve`() {
        assertEquals(30, briefKeys.size)
        briefKeys.forEach { assertNotNull("missing $it", DiscoveryArtRegistry.specFor(it)) }
    }

    @Test
    fun `the registry holds exactly the catalogue's keys and nothing orphaned`() {
        assertEquals(CampaignDiscoveries.all.map { it.artKey }.toSet(), DiscoveryArtRegistry.keys)
        assertEquals(briefKeys.toSet(), DiscoveryArtRegistry.keys)
    }

    @Test
    fun `an unknown key has no drawing`() {
        assertNull(DiscoveryArtRegistry.specFor("sky_dragon"))
        assertNull(DiscoveryArtRegistry.specFor(""))
    }

    @Test
    fun `every drawing has a silhouette with the sticker ring, and detail`() {
        allSpecs().forEach { (key, spec) ->
            val bodies = spec.layers.filterIsInstance<BodyLayer>()
            assertTrue("$key has no body", bodies.isNotEmpty())
            assertTrue("$key's first body must carry the ring", bodies.first().ring)
            assertTrue("$key has no detail", spec.layers.any { it is DetailLayer })
        }
    }

    @Test
    fun `every path is valid SVG path data`() {
        allSpecs().forEach { (key, spec) ->
            spec.layers.forEach { layer ->
                val paths = when (layer) {
                    is BodyLayer -> listOf(layer.d)
                    is DetailLayer -> layer.shapes.map { it.d }
                }
                paths.forEach { d ->
                    val nodes = runCatching { PathParser().parsePathString(d).toNodes() }
                        .getOrElse { throw AssertionError("$key: path does not parse: $d", it) }
                    assertTrue("$key: empty path", nodes.isNotEmpty())
                }
            }
        }
    }

    /**
     * The art is authored in a 100 × 100 space, with its bodies kept inside roughly
     * 6..94 so the ink and the ring have room outside them. Anything past the edge
     * of the space would be drawn outside its box — over a neighbouring tile.
     */
    @Test
    fun `no path is drawn off the 100 by 100 canvas`() {
        val number = Regex("-?\\d+(?:\\.\\d+)?")
        allSpecs().forEach { (key, spec) ->
            spec.layers.forEach { layer ->
                val paths = when (layer) {
                    is BodyLayer -> listOf(layer.d)
                    is DetailLayer -> layer.shapes.map { it.d }
                }
                paths.forEach { d ->
                    number.findAll(d).map { it.value.toFloat() }.forEach { v ->
                        assertTrue("$key: $v is off the canvas in: $d", v in -1f..101f)
                    }
                }
            }
        }
    }

    /**
     * A translucent detail drawn with `soften` is a fill and a same-colour stroke at
     * the same alpha, which Skia blends twice where they overlap: a faint rim inside
     * the shape that the SVG review sheet does not show. Translucent details are
     * therefore never softened.
     */
    @Test
    fun `a translucent detail is never softened`() {
        allSpecs().forEach { (key, spec) ->
            spec.layers.filterIsInstance<DetailLayer>().flatMap { it.shapes }.forEach { shape ->
                if (shape.soften) {
                    assertEquals("$key: a softened detail must be opaque", 1f, shape.alpha, 0f)
                    assertEquals(
                        "$key: a softened detail must have an opaque fill",
                        0xFF,
                        (((shape.fill?.top ?: 0xFF000000) shr 24) and 0xFF).toInt()
                    )
                }
            }
        }
    }

    @Test
    fun `colours are opaque in every body and every flat detail`() {
        allSpecs().forEach { (key, spec) ->
            spec.layers.filterIsInstance<BodyLayer>().forEach {
                assertEquals("$key body top", 0xFF, ((it.fill.top shr 24) and 0xFF).toInt())
                assertEquals("$key body bottom", 0xFF, ((it.fill.bottom shr 24) and 0xFF).toInt())
            }
        }
    }
}
