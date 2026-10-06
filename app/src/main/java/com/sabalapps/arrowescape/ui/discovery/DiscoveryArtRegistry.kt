package com.sabalapps.arrowescape.ui.discovery

/**
 * Where an art key becomes a drawing.
 *
 * `CampaignDiscovery.artKey` is the only thing that names a discovery's artwork,
 * and this is the only thing that answers it, so no screen picks art by level id
 * and the thirty keys have exactly one home. Pure data — see [DiscoveryArtSpec] —
 * which is what lets a plain JVM test prove that every key in the catalogue
 * resolves and that nothing here is orphaned.
 */
object DiscoveryArtRegistry {

    private val specs: Map<String, DiscoveryArtSpec> by lazy {
        buildMap {
            putAll(SkyDiscoveryArt.specs)
            putAll(ForestDiscoveryArt.specs)
            putAll(CanyonDiscoveryArt.specs)
            putAll(CrystalDiscoveryArt.specs)
            putAll(CosmicDiscoveryArt.specs)
        }
    }

    /** Every art key that has a drawing. */
    val keys: Set<String> get() = specs.keys

    /** The drawing for [artKey], or null when there is none. */
    fun specFor(artKey: String): DiscoveryArtSpec? = specs[artKey]
}
