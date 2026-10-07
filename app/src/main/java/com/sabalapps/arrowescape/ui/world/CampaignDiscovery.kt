package com.sabalapps.arrowescape.ui.world

import com.sabalapps.arrowescape.progress.PlayerProgress

/**
 * What kind of thing a [CampaignDiscovery] is. A content category — it groups
 * the catalogue for a later collection screen and nothing else; no rule, reward
 * or save reads it.
 */
enum class DiscoveryType { NATURE, ANIMAL, OBJECT, TREASURE, SPACE }

/**
 * The one thing hidden behind a Campaign level: "Level 4 is a Bird".
 *
 * Product content, not gameplay and not presentation. It is deliberately kept
 * out of [com.sabalapps.arrowescape.game.Level] — [com.sabalapps.arrowescape.game.Level.name]
 * stays "Level N" and the arrow layouts are untouched — and it is keyed by
 * [levelId], the persisted catalogue id, so it can be looked up from any
 * progress record without the level object in hand.
 *
 * [artKey] is a stable, lowercase, machine-readable name for the artwork this
 * discovery is drawn with. `ui.discovery.DiscoveryArtRegistry` resolves it, and
 * it is the only thing that does: no screen looks art up by level id. Like a
 * level id it must not change once a build has shipped.
 *
 * [world] is not chosen here: [CampaignDiscoveries] reads it from [GameWorlds],
 * so a discovery is always in the world its level is played in.
 */
data class CampaignDiscovery(
    val levelId: Int,
    val name: String,
    val world: GameWorld,
    val type: DiscoveryType,
    val artKey: String
)

/**
 * The authoritative catalogue of Campaign discoveries: exactly one per level.
 *
 * Pure Kotlin, like [GameWorlds] beside it — no Compose, no Android, no `R` —
 * so the catalogue and its collected-state helpers are testable on a plain JVM.
 *
 * **Collected state is derived, not stored.** A discovery is collected when its
 * level is in [PlayerProgress.completedLevels]. There is no second set of
 * "discovered" ids to keep in step with that one, and no new save format; the
 * helpers below only read the progress record that already exists. If a later
 * phase needs to remember that a first-time reveal has been *shown*, that is a
 * separate fact and gets its own state then.
 *
 * To add a level: append its discovery here, in level order. `CampaignDiscoveriesTest`
 * fails if the catalogue and `Levels.ALL` disagree on which levels exist.
 */
object CampaignDiscoveries {

    /** Every discovery, in level order. */
    val all: List<CampaignDiscovery> = listOf(
        // ---- Sky Garden
        discovery(1, "Heart", DiscoveryType.OBJECT, "sky_heart"),
        discovery(2, "Star", DiscoveryType.SPACE, "sky_star"),
        discovery(3, "Kite", DiscoveryType.OBJECT, "sky_kite"),
        discovery(4, "Bird", DiscoveryType.ANIMAL, "sky_bird"),
        discovery(5, "Cloud", DiscoveryType.NATURE, "sky_cloud"),
        discovery(6, "Flower", DiscoveryType.NATURE, "sky_flower"),

        // ---- Forest
        discovery(7, "Leaf", DiscoveryType.NATURE, "forest_leaf"),
        discovery(8, "Mushroom", DiscoveryType.NATURE, "forest_mushroom"),
        discovery(9, "Tree", DiscoveryType.NATURE, "forest_tree"),
        discovery(10, "Butterfly", DiscoveryType.ANIMAL, "forest_butterfly"),
        discovery(11, "Fox", DiscoveryType.ANIMAL, "forest_fox"),
        discovery(12, "Owl", DiscoveryType.ANIMAL, "forest_owl"),

        // ---- Sunset Canyon
        discovery(13, "Sun", DiscoveryType.SPACE, "canyon_sun"),
        discovery(14, "Cactus", DiscoveryType.NATURE, "canyon_cactus"),
        discovery(15, "Mountain", DiscoveryType.NATURE, "canyon_mountain"),
        discovery(16, "Canyon Arch", DiscoveryType.NATURE, "canyon_arch"),
        discovery(17, "Eagle", DiscoveryType.ANIMAL, "canyon_eagle"),
        discovery(18, "Treasure Chest", DiscoveryType.TREASURE, "canyon_treasure_chest"),

        // ---- Crystal Night
        discovery(19, "Gem", DiscoveryType.TREASURE, "crystal_gem"),
        discovery(20, "Crescent Moon", DiscoveryType.SPACE, "crystal_moon"),
        discovery(21, "Crystal", DiscoveryType.NATURE, "crystal_cluster"),
        discovery(22, "Snowflake", DiscoveryType.NATURE, "crystal_snowflake"),
        discovery(23, "Magic Star", DiscoveryType.TREASURE, "crystal_magic_star"),
        discovery(24, "Crown", DiscoveryType.TREASURE, "crystal_crown"),

        // ---- Cosmic
        discovery(25, "Comet", DiscoveryType.SPACE, "cosmic_comet"),
        discovery(26, "Rocket", DiscoveryType.OBJECT, "cosmic_rocket"),
        discovery(27, "Planet", DiscoveryType.SPACE, "cosmic_planet"),
        discovery(28, "UFO", DiscoveryType.OBJECT, "cosmic_ufo"),
        discovery(29, "Satellite", DiscoveryType.OBJECT, "cosmic_satellite"),
        discovery(30, "Galaxy", DiscoveryType.SPACE, "cosmic_galaxy"),
    )

    private val byLevel: Map<Int, CampaignDiscovery> = all.associateBy { it.levelId }

    /** The discovery behind [levelId], or null for an id that is not a Campaign level. */
    fun forLevel(levelId: Int): CampaignDiscovery? = byLevel[levelId]

    /** The discoveries made in [world], in level order. */
    fun forWorld(world: GameWorld): List<CampaignDiscovery> = all.filter { it.world == world }

    /** How many discoveries [world] holds. */
    fun totalCount(world: GameWorld): Int = forWorld(world).size

    /** How many discoveries there are in all. */
    val totalCount: Int get() = all.size

    /**
     * The collected discoveries, newest first, at most [limit] of them.
     *
     * "Newest" is the highest cleared level: the Campaign unlocks strictly in
     * order, so the level cleared most recently *for the first time* is always the
     * highest one, and nothing has to be recorded to know it. Home uses this to
     * show a few thumbnails — only ever of things already found.
     */
    fun latestCollected(progress: PlayerProgress, limit: Int): List<CampaignDiscovery> =
        all.filter { isCollected(it, progress) }.asReversed().take(limit.coerceAtLeast(0))

    /** True once the player has cleared the level [discovery] is hidden behind. */
    fun isCollected(discovery: CampaignDiscovery, progress: PlayerProgress): Boolean =
        progress.isCompleted(discovery.levelId)

    /**
     * How many discoveries the player holds. Counted against the catalogue, not
     * taken from `completedLevels.size`, so an id that is not a Campaign level
     * can never inflate it.
     */
    fun collectedCount(progress: PlayerProgress): Int =
        all.count { isCollected(it, progress) }

    /** How many of [world]'s discoveries the player holds. */
    fun collectedCount(world: GameWorld, progress: PlayerProgress): Int =
        forWorld(world).count { isCollected(it, progress) }

    /** The first uncollected discovery in level order, or null once all are collected. */
    fun nextUndiscovered(progress: PlayerProgress): CampaignDiscovery? =
        all.firstOrNull { !isCollected(it, progress) }

    private fun discovery(
        levelId: Int,
        name: String,
        type: DiscoveryType,
        artKey: String
    ): CampaignDiscovery = CampaignDiscovery(
        levelId = levelId,
        name = name,
        world = GameWorlds.forCampaignLevel(levelId),
        type = type,
        artKey = artKey
    )
}
