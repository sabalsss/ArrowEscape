package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.daily.DailyProgress
import com.sabalapps.arrowescape.endless.EndlessProgress
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.StarRating
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.ui.world.CampaignDiscoveries

/**
 * Everything the Stats screen shows, gathered from the three independent
 * progress records.
 *
 * It is a pure function of those three and nothing else, which is the only
 * interesting thing about it: every number here is one the game already keeps
 * because it needs it to work. Nothing is estimated, nothing is derived from a
 * number the game does not actually track, and there is no counter that exists
 * only to be displayed. If a metric is not in this file it is because the game
 * does not know it — "fastest clear" and "arrows removed" are absent for exactly
 * that reason.
 */
data class PlayerStats(
    val campaignCompleted: Int,
    val campaignTotal: Int,
    /**
     * How many Campaign discoveries are found. The same fact as [campaignCompleted] —
     * a discovery is found by clearing its level — but counted against the catalogue,
     * so a stray completed id can never inflate it. This is the figure the Stats
     * screen leads with; nothing new is tracked or stored to produce it.
     */
    val campaignDiscoveries: Int,
    /**
     * Every best star the player holds across the campaign, added up.
     *
     * Not a new counter: [PlayerProgress.bestStars] is already on disk and
     * already the authority on a level's rating, so this is a sum of what is
     * there and nothing is tracked, stored or range-checked to produce it. A
     * level cleared before stars existed contributes nothing, which is correct —
     * it has no rating, rather than a rating of zero.
     */
    val campaignStars: Int,
    val endlessCompleted: Int,
    /** The tier the next endless puzzle will be generated at. */
    val endlessTierLabel: String,
    val endlessBestStreak: Int,
    val dailyCompleted: Int,
    val dailyCurrentStreak: Int,
    val dailyBestStreak: Int
) {
    /** Every board cleared across the three modes. */
    val totalPuzzles: Int get() = campaignCompleted + endlessCompleted + dailyCompleted

    /** Three per level: what [campaignStars] would be with a Perfect Escape on every one. */
    val campaignStarsMax: Int get() = campaignTotal * StarRating.MAX

    companion object {
        fun from(
            campaign: PlayerProgress,
            endless: EndlessProgress,
            daily: DailyProgress
        ): PlayerStats = PlayerStats(
            campaignCompleted = campaign.completedLevels.size,
            campaignTotal = LevelProgression.count,
            campaignDiscoveries = CampaignDiscoveries.collectedCount(campaign),
            campaignStars = campaign.bestStars.values.sum(),
            endlessCompleted = endless.totalCompleted,
            endlessTierLabel = endless.tier.label,
            endlessBestStreak = endless.bestStreak,
            dailyCompleted = daily.totalCompleted,
            dailyCurrentStreak = daily.currentStreak,
            dailyBestStreak = daily.bestStreak
        )
    }
}
