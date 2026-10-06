package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.daily.DailyProgress
import com.sabalapps.arrowescape.endless.EndlessProgress
import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.progress.PlayerProgress
import com.sabalapps.arrowescape.time.GameDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Stats screen's numbers. All [PlayerStats] does is read three progress
 * records, so what is worth checking is that it reads them from the right place
 * — a stats screen that reported endless puzzles as campaign levels would look
 * entirely plausible.
 */
class PlayerStatsTest {

    @Test
    fun `a fresh install reports zeroes and the full campaign length`() {
        val stats = PlayerStats.from(PlayerProgress(), EndlessProgress(), DailyProgress())

        assertEquals(0, stats.campaignCompleted)
        assertEquals(LevelProgression.count, stats.campaignTotal)
        assertEquals(0, stats.endlessCompleted)
        assertEquals(0, stats.dailyCompleted)
        assertEquals(0, stats.dailyCurrentStreak)
        assertEquals(0, stats.dailyBestStreak)
        assertEquals(0, stats.totalPuzzles)
    }

    @Test
    fun `discoveries found are counted against the catalogue, not the raw completed set`() {
        val stats = PlayerStats.from(
            campaign = PlayerProgress(completedLevels = setOf(1, 2, 3, 31, 99)),
            endless = EndlessProgress(),
            daily = DailyProgress()
        )

        assertEquals(3, stats.campaignDiscoveries)
        assertEquals(LevelProgression.count, stats.campaignTotal)
    }

    @Test
    fun `a fresh install has found nothing`() {
        assertEquals(0, PlayerStats.from(PlayerProgress(), EndlessProgress(), DailyProgress()).campaignDiscoveries)
    }

    @Test
    fun `each mode's numbers come from that mode's record`() {
        val stats = PlayerStats.from(
            campaign = PlayerProgress(completedLevels = setOf(1, 2, 3, 4, 5, 6, 7)),
            endless = EndlessProgress(
                puzzleNumber = 34,
                totalCompleted = 33,
                currentStreak = 4,
                bestStreak = 11
            ),
            daily = DailyProgress(
                lastCompletedDate = GameDate(2026, 10, 4),
                currentStreak = 5,
                bestStreak = 9,
                totalCompleted = 21
            )
        )

        assertEquals(7, stats.campaignCompleted)
        assertEquals(33, stats.endlessCompleted)
        assertEquals(11, stats.endlessBestStreak)
        assertEquals(21, stats.dailyCompleted)
        assertEquals(5, stats.dailyCurrentStreak)
        assertEquals(9, stats.dailyBestStreak)
    }

    @Test
    fun `the overall total is the three modes added up`() {
        val stats = PlayerStats.from(
            campaign = PlayerProgress(completedLevels = setOf(1, 2, 3)),
            endless = EndlessProgress(totalCompleted = 12),
            daily = DailyProgress(
                lastCompletedDate = GameDate(2026, 10, 4),
                currentStreak = 1,
                bestStreak = 1,
                totalCompleted = 4
            )
        )
        assertEquals(19, stats.totalPuzzles)
    }

    @Test
    fun `the endless tier is reported by name and never as a score`() {
        // Players see Beginner through Expert. The numeric difficulty score is a
        // generator-tuning number and stays in the debug log.
        val stats = PlayerStats.from(
            PlayerProgress(),
            EndlessProgress(puzzleNumber = 60),
            DailyProgress()
        )
        assertEquals(EndlessTier.EXPERT.label, stats.endlessTierLabel)
        assertEquals(
            "every tier label should be a plain name",
            listOf("Beginner", "Easy", "Medium", "Hard", "Expert"),
            EndlessTier.entries.map { it.label }
        )
    }

    @Test
    fun `the tier follows the puzzle number through every band`() {
        for ((number, expected) in listOf(
            1 to EndlessTier.BEGINNER,
            6 to EndlessTier.EASY,
            16 to EndlessTier.MEDIUM,
            31 to EndlessTier.HARD,
            51 to EndlessTier.EXPERT
        )) {
            val stats = PlayerStats.from(
                PlayerProgress(),
                EndlessProgress(puzzleNumber = number),
                DailyProgress()
            )
            assertEquals("puzzle $number", expected.label, stats.endlessTierLabel)
        }
    }
}
