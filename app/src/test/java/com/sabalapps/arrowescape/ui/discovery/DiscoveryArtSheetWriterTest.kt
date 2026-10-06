package com.sabalapps.arrowescape.ui.discovery

import java.io.File
import org.junit.Test

/** Dev-only: refreshes the SVG sheets under `build/reports/discovery`. Asserts nothing about the art. */
class DiscoveryArtSheetWriterTest {
    @Test
    fun `writes the review sheets`() {
        DiscoveryArtSheet.write(File("build/reports/discovery"))
    }
}
