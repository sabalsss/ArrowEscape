package com.sabalapps.arrowescape.ui.world

import com.sabalapps.arrowescape.progress.PlayerProgress

/*
 * What the player is allowed to know about a discovery, as plain data.
 *
 * Pure Kotlin like `CampaignDiscovery.kt` beside it: no Compose, no Android, no
 * `R`. Everything here is *derived* from the catalogue and a `PlayerProgress` —
 * nothing is stored, so "collected" can never drift from "this level was cleared".
 *
 * ## The spoiler rule
 *
 * The catalogue knows what every level hides; the player must not until they
 * clear it. The way that is kept true is structural rather than a convention a
 * screen has to remember: **only [DiscoverySlot.Collected] carries a
 * [CampaignDiscovery]**. A slot for a level that is merely open ([Mystery]) or not
 * yet reachable ([Locked]) holds a level number and nothing else, so a screen that
 * is handed one *cannot* draw its artwork or read its name, however it is built.
 * The spoken label lives on the slot for the same reason: there is exactly one
 * place that decides what TalkBack may say about each state.
 */

/** One discovery as the player may see it, in exactly one of three states. */
sealed interface DiscoverySlot {

    /** The Campaign level this slot belongs to. */
    val levelId: Int

    /** What a screen reader says. Never names a discovery the player has not found. */
    val spokenLabel: String

    /** Cleared: the identity is the player's now, and the only state that carries it. */
    data class Collected(val discovery: CampaignDiscovery, val stars: Int) : DiscoverySlot {
        override val levelId: Int get() = discovery.levelId
        override val spokenLabel: String
            get() = "${discovery.name}, discovered, ${discovery.world.displayName}"
    }

    /** Open to play but not cleared: a mystery. Carries a level number and nothing else. */
    data class Mystery(override val levelId: Int) : DiscoverySlot {
        override val spokenLabel: String get() = "Undiscovered item, Level $levelId"
    }

    /** Not reachable yet. Carries nothing the player could learn from. */
    data class Locked(override val levelId: Int) : DiscoverySlot {
        override val spokenLabel: String get() = "Locked discovery"
    }

    companion object {
        /** The state of [discovery] for the player whose record is [progress]. */
        fun of(discovery: CampaignDiscovery, progress: PlayerProgress): DiscoverySlot = when {
            CampaignDiscoveries.isCollected(discovery, progress) ->
                Collected(discovery, progress.starsFor(discovery.levelId))
            progress.isUnlocked(discovery.levelId) -> Mystery(discovery.levelId)
            else -> Locked(discovery.levelId)
        }
    }
}

/** One world's six slots, in level order. */
data class WorldCollection(val world: GameWorld, val slots: List<DiscoverySlot>) {
    val total: Int get() = slots.size
    val collected: Int get() = slots.count { it is DiscoverySlot.Collected }

    /** Every discovery in the world is found. */
    val isComplete: Boolean get() = total > 0 && collected == total

    /** "4 / 6 DISCOVERED". */
    val progressLabel: String get() = "$collected / $total DISCOVERED"

    /** Spoken, for a world heading. */
    val spokenSummary: String
        get() = "${world.displayName}, $collected of $total discovered" +
            if (isComplete) ", complete" else ""
}

/** The whole album: five worlds, and what the player holds of each. */
data class DiscoveryCollection(val worlds: List<WorldCollection>) {
    val total: Int get() = worlds.sumOf { it.total }
    val collected: Int get() = worlds.sumOf { it.collected }

    /** All thirty (or however many the catalogue holds) are found. */
    val allFound: Boolean get() = total > 0 && collected == total

    /** "14 / 30 FOUND". */
    val headerLabel: String get() = "$collected / $total FOUND"

    /** The slot for [levelId], or null when it is not a Campaign level. */
    fun slotFor(levelId: Int): DiscoverySlot? =
        worlds.firstNotNullOfOrNull { world -> world.slots.firstOrNull { it.levelId == levelId } }

    companion object {
        fun of(progress: PlayerProgress): DiscoveryCollection = DiscoveryCollection(
            GameWorld.PROGRESSION.mapNotNull { world ->
                val discoveries = CampaignDiscoveries.forWorld(world)
                if (discoveries.isEmpty()) {
                    null
                } else {
                    WorldCollection(world, discoveries.map { DiscoverySlot.of(it, progress) })
                }
            }
        )
    }
}

/** Which set, if any, a first clear has just finished. */
enum class DiscoveryCompletion { NONE, WORLD, CAMPAIGN }

/**
 * What the Campaign result screen reports about the level the player has just
 * cleared: the discovery behind it, whether it is new, and where the collection
 * stands *including this clear*.
 *
 * [isFirstClear] is read **before** the win is written (see
 * `GameViewModel.persistCampaign`), so it is true exactly once per level and a
 * replay never announces a new discovery again. It is a fact of one run, held only
 * as long as that run's result is on screen — nothing about it is persisted, and
 * nothing needs to be: a discovery being "seen" is not a state the game keeps.
 */
data class DiscoveryResult(
    val discovery: CampaignDiscovery,
    val isFirstClear: Boolean,
    val worldCollected: Int,
    val worldTotal: Int,
    val totalCollected: Int,
    val totalCount: Int
) {
    val world: GameWorld get() = discovery.world

    /** This clear finished (or revisited a finished) world. */
    val worldComplete: Boolean get() = worldTotal > 0 && worldCollected >= worldTotal

    /** Every discovery is found. */
    val allFound: Boolean get() = totalCount > 0 && totalCollected >= totalCount

    /**
     * The set this clear *finished*, as opposed to [worldComplete] and [allFound],
     * which are also true when a finished world's level is replayed. Only a first
     * clear completes anything, so a replay never repeats the milestone.
     * Finishing the album outranks finishing its last world.
     */
    val completion: DiscoveryCompletion
        get() = when {
            !isFirstClear -> DiscoveryCompletion.NONE
            allFound -> DiscoveryCompletion.CAMPAIGN
            worldComplete -> DiscoveryCompletion.WORLD
            else -> DiscoveryCompletion.NONE
        }

    /** "SKY GARDEN COMPLETE!" or "ALL DISCOVERIES FOUND"; null when nothing was finished. */
    val completionTitle: String?
        get() = when (completion) {
            DiscoveryCompletion.NONE -> null
            DiscoveryCompletion.WORLD -> "${world.displayName.uppercase()} COMPLETE!"
            DiscoveryCompletion.CAMPAIGN -> "ALL DISCOVERIES FOUND"
        }

    /** "6 / 6 DISCOVERED" for a world, "30 / 30" for the album; null when nothing was finished. */
    val completionCount: String?
        get() = when (completion) {
            DiscoveryCompletion.NONE -> null
            DiscoveryCompletion.WORLD -> progressLabel
            DiscoveryCompletion.CAMPAIGN -> "$totalCollected / $totalCount"
        }

    /**
     * What is shown together when a set is finished: the world's six discoveries, or
     * for the album the finale of each world. Empty unless [completion] is not NONE,
     * and every one of them is already found by then — a completed set is the only
     * time this is non-empty, so it can never name a discovery the player lacks.
     */
    val completionSet: List<CampaignDiscovery>
        get() = when (completion) {
            DiscoveryCompletion.NONE -> emptyList()
            DiscoveryCompletion.WORLD -> CampaignDiscoveries.forWorld(world)
            DiscoveryCompletion.CAMPAIGN -> GameWorld.PROGRESSION.mapNotNull {
                CampaignDiscoveries.forWorld(it).lastOrNull()
            }
        }

    /** "NEW DISCOVERY!" once, "DISCOVERY FOUND" ever after. */
    val headline: String get() = if (isFirstClear) "NEW DISCOVERY!" else "DISCOVERY FOUND"

    /** "FOREST COLLECTION". */
    val collectionTitle: String get() = "${world.displayName.uppercase()} COLLECTION"

    /** "4 / 6 DISCOVERED". */
    val progressLabel: String get() = "$worldCollected / $worldTotal DISCOVERED"

    /** The one thing a screen reader says for the whole reveal. */
    val spokenSummary: String
        get() = buildString {
            append(if (isFirstClear) "New discovery. " else "Discovery found. ")
            append("${discovery.name}. ")
            append("${world.displayName} collection, $worldCollected of $worldTotal discovered.")
            if (allFound) append(" All discoveries found.")
            else if (worldComplete) append(" World complete.")
        }

    companion object {
        /**
         * The result for a clear of [levelId], or null for a level that hides
         * nothing (not a Campaign level).
         *
         * [progress] is the record *after* the win. [levelId] is added to it
         * regardless, so the count includes this clear by construction rather than
         * by the caller having committed first.
         */
        fun of(
            levelId: Int,
            wasCompletedBeforeRun: Boolean,
            progress: PlayerProgress
        ): DiscoveryResult? {
            val discovery = CampaignDiscoveries.forLevel(levelId) ?: return null
            val after = progress.copy(completedLevels = progress.completedLevels + levelId)
            return DiscoveryResult(
                discovery = discovery,
                isFirstClear = !wasCompletedBeforeRun,
                worldCollected = CampaignDiscoveries.collectedCount(discovery.world, after),
                worldTotal = CampaignDiscoveries.totalCount(discovery.world),
                totalCollected = CampaignDiscoveries.collectedCount(after),
                totalCount = CampaignDiscoveries.totalCount
            )
        }
    }
}
