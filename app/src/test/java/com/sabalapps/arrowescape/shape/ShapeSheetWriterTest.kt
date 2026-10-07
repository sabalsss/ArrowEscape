package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.game.CampaignShapes
import java.io.File
import org.junit.Test

/**
 * Dev-only: refreshes the silhouette review sheets under `build/reports/shapes`. It
 * asserts nothing about the shapes — `CampaignShapesTest` and `MysteryShapeCatalogTest`
 * do that — it exists so a person can *look* at them.
 *
 * Render a sheet with headless Chrome:
 * `chrome --headless --screenshot=out.png --window-size=1300,H file:///.../campaign-shapes.svg`
 */
class ShapeSheetWriterTest {

    @Test
    fun `writes the campaign sheet`() {
        val entries = CampaignShapes.ALL.map { shape ->
            ShapeSheet.Entry(
                label = "${shape.levelId}  ${shape.discovery}",
                mask = ShapeMask.parse(shape.rows),
                note = "${shape.size} cells"
            )
        }
        // Three sheets of ten so each renders at a size that is easy to read.
        entries.chunked(10).forEachIndexed { index, chunk ->
            ShapeSheet.write(
                File("build/reports/shapes/campaign-shapes-${index + 1}.svg"),
                "Campaign silhouettes ${index * 10 + 1}–${index * 10 + chunk.size}",
                chunk,
                perRow = 5
            )
        }
    }

    @Test
    fun `writes the mystery catalogue sheet`() {
        val entries = MysteryShapes.all.map { t ->
            ShapeSheet.Entry(
                label = t.name,
                mask = t.mask,
                note = "${t.cellCount} cells${if (t.allowMirror) " · flips" else ""}"
            )
        }
        entries.chunked(14).forEachIndexed { index, chunk ->
            ShapeSheet.write(
                File("build/reports/shapes/mystery-shapes-${index + 1}.svg"),
                "Daily / Endless mystery shapes ${index * 14 + 1}–${index * 14 + chunk.size}",
                chunk,
                perRow = 5
            )
        }
    }

    @Test
    fun `writes the representative contour sheet`() {
        val campaign = listOf(1 to "Heart", 10 to "Butterfly", 17 to "Eagle", 26 to "Rocket").map { (id, name) ->
            ShapeSheet.Entry("Campaign · $name", ShapeMask.of(com.sabalapps.arrowescape.game.Levels.byId(id)!!), "level $id")
        }
        val mystery = listOf("fish", "house", "key").map { id ->
            val t = MysteryShapes.byId(id)!!
            ShapeSheet.Entry("Mystery · ${t.name}", t.mask, "${t.cellCount} cells")
        }
        ShapeSheet.write(File("build/reports/shapes/contour-states.svg"), "Contours traced from the puzzles' own cells", campaign + mystery, perRow = 4)
    }
}
