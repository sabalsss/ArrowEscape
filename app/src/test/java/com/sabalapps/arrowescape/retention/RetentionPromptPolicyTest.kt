package com.sabalapps.arrowescape.retention

import com.sabalapps.arrowescape.retention.RetentionPromptPolicy.COOLDOWN_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the game asks for a rating, a share or a daily reminder — every rule of
 * [RetentionPromptPolicy] as plain data in, a decision out.
 */
class RetentionPromptPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_800_000_000_000L

    private val fresh = RetentionPromptState()
    private val noReminderYet = ReminderFacts(enabled = false, introShown = false)
    private val reminderDone = ReminderFacts(enabled = false, introShown = true)

    private fun win(count: Int, finishesASet: Boolean = false) = CampaignWin(count, finishesASet)

    private fun decide(
        win: CampaignWin?,
        prompts: RetentionPromptState = fresh,
        reminder: ReminderFacts = reminderDone,
        sessionShown: Boolean = false,
        now: Long = t0
    ) = RetentionPromptPolicy.decide(win, prompts, reminder, sessionShown, now)

    // ---- the early milestones -----------------------------------------------

    @Test
    fun `the first completion of level one is eligible for the first prompt`() {
        assertEquals(RetentionPrompt.RateAndShare(showShare = true), decide(win(1)))
    }

    @Test
    fun `level two straight after level one in the same session is suppressed`() {
        // Level 1 was prompted this session.
        val afterLevelOne = fresh.copy(promptCount = 1, lastPromptAtMs = t0, lastPromptCompletionCount = 1, ratePromptShown = true)
        assertNull(decide(win(2), afterLevelOne, sessionShown = true, now = t0 + 60_000))
    }

    @Test
    fun `level two in a later session is eligible again, with no cooldown`() {
        val afterLevelOne = fresh.copy(promptCount = 1, lastPromptAtMs = t0, lastPromptCompletionCount = 1, ratePromptShown = true)
        // An hour later, a new session. The opening pair carries no cooldown.
        assertEquals(
            RetentionPrompt.RateAndShare(true),
            decide(win(2), afterLevelOne, now = t0 + 60 * 60 * 1000)
        )
    }

    @Test
    fun `level two is not asked twice for the same milestone`() {
        val afterLevelTwo = fresh.copy(promptCount = 2, lastPromptAtMs = t0, lastPromptCompletionCount = 2, ratePromptShown = true)
        assertNull(decide(win(2), afterLevelTwo, now = t0 + 30 * day))
    }

    @Test
    fun `a replay of level one asks nothing, because nothing new was cleared`() {
        val afterLevelOne = fresh.copy(promptCount = 1, lastPromptAtMs = t0, lastPromptCompletionCount = 1, ratePromptShown = true)
        assertNull(decide(win(1), afterLevelOne, now = t0 + 30 * day))
    }

    // ---- stop conditions ----------------------------------------------------

    @Test
    fun `pressing rate ends proactive rating prompts for good`() {
        val rated = fresh.copy(rateActionTapped = true)
        for (count in listOf(1, 2, 6, 12, 20, 30)) {
            assertNull("count $count", decide(win(count), rated, now = t0 + 400 * day))
        }
    }

    @Test
    fun `pressing share removes share from later prompts but not the rate request`() {
        val shared = fresh.copy(shareActionTapped = true)
        assertEquals(RetentionPrompt.RateAndShare(showShare = false), decide(win(1), shared))
    }

    @Test
    fun `not now respects the cooldown`() {
        // Prompted at the sixth level; the player said Not Now.
        val notNow = fresh.copy(promptCount = 3, lastPromptAtMs = t0, lastPromptCompletionCount = 6, ratePromptShown = true)
        assertNull("the next milestone, six days later", decide(win(12), notNow, now = t0 + 6 * day))
        assertNull("a minute short of a week", decide(win(12), notNow, now = t0 + COOLDOWN_MS - 60_000))
        assertEquals(RetentionPrompt.RateAndShare(true), decide(win(12), notNow, now = t0 + COOLDOWN_MS))
    }

    @Test
    fun `a loss, or anything that is not a won campaign level, never asks`() {
        assertNull(decide(null))
        assertNull(decide(null, reminder = noReminderYet))
    }

    // ---- the later milestones -----------------------------------------------

    @Test
    fun `between milestones nothing is asked`() {
        val afterTwo = fresh.copy(promptCount = 2, lastPromptAtMs = t0, lastPromptCompletionCount = 2, ratePromptShown = true)
        for (count in 3..5) assertNull("count $count", decide(win(count), afterTwo, now = t0 + 30 * day))
        assertEquals(RetentionPrompt.RateAndShare(true), decide(win(6), afterTwo, now = t0 + 30 * day))
    }

    @Test
    fun `the later milestones are about 6, 12, 20 and 30`() {
        assertEquals(listOf(6, 12, 20, 30), RetentionPromptPolicy.LATER_MILESTONES)
        assertEquals(listOf(1, 2), RetentionPromptPolicy.ONBOARDING_MILESTONES)
    }

    @Test
    fun `skipping past a milestone asks once, not once per milestone passed`() {
        val afterTwo = fresh.copy(promptCount = 2, lastPromptAtMs = t0, lastPromptCompletionCount = 2, ratePromptShown = true)
        // 13 levels cleared and never asked since: one prompt, and it covers 6 and 12.
        assertEquals(12, RetentionPromptPolicy.milestoneFor(13, 2))
        assertEquals(RetentionPrompt.RateAndShare(true), decide(win(13), afterTwo, now = t0 + 30 * day))
        val afterThat = afterTwo.copy(lastPromptCompletionCount = 13, lastPromptAtMs = t0 + 30 * day)
        assertNull(decide(win(14), afterThat, now = t0 + 60 * day))
    }

    // ---- never over a celebration -------------------------------------------

    @Test
    fun `a clear that finishes a world waits, and the milestone is asked at the next win`() {
        val afterTwo = fresh.copy(promptCount = 2, lastPromptAtMs = t0, lastPromptCompletionCount = 2, ratePromptShown = true)
        val later = t0 + 30 * day
        assertNull("sixth level finishes Sky Garden", decide(win(6, finishesASet = true), afterTwo, now = later))
        assertEquals(
            "the milestone is still owed",
            RetentionPrompt.RateAndShare(true),
            decide(win(7), afterTwo, now = later)
        )
    }

    @Test
    fun `finishing the campaign is never interrupted`() {
        val afterTwenty = fresh.copy(promptCount = 4, lastPromptAtMs = t0, lastPromptCompletionCount = 20, ratePromptShown = true)
        assertNull(decide(win(30, finishesASet = true), afterTwenty, now = t0 + 30 * day))
    }

    // ---- once a session -----------------------------------------------------

    @Test
    fun `one prompt a session, whatever it is`() {
        assertNull(decide(win(1), sessionShown = true))
        assertNull(decide(win(2), reminder = noReminderYet, sessionShown = true))
    }

    // ---- the daily reminder invite ------------------------------------------

    @Test
    fun `the reminder is offered after the second level and not before`() {
        assertEquals(RetentionPrompt.RateAndShare(true), decide(win(1), reminder = noReminderYet))
        assertEquals(RetentionPrompt.DailyReminderInvite, decide(win(2), reminder = noReminderYet))
    }

    @Test
    fun `the invite comes before a rating request when both are due`() {
        assertEquals(
            RetentionPrompt.DailyReminderInvite,
            decide(win(2), fresh, reminder = noReminderYet)
        )
    }

    @Test
    fun `the invite is asked once, whatever the answer`() {
        // introShown is set when it is shown; Not Now and Enable both leave it set.
        assertEquals(RetentionPrompt.RateAndShare(true), decide(win(2), reminder = reminderDone))
    }

    @Test
    fun `a player who already turned the reminder on is not invited`() {
        val on = ReminderFacts(enabled = true, introShown = false)
        assertEquals(RetentionPrompt.RateAndShare(true), decide(win(2), reminder = on))
    }

    @Test
    fun `the invite is not blocked by having already rated`() {
        val rated = fresh.copy(rateActionTapped = true)
        assertEquals(RetentionPrompt.DailyReminderInvite, decide(win(3), rated, reminder = noReminderYet))
    }

    @Test
    fun `the invite is also held back over a celebration`() {
        assertNull(decide(win(6, finishesASet = true), reminder = noReminderYet))
    }

    // ---- bounded ------------------------------------------------------------

    @Test
    fun `across a whole campaign the player is asked a handful of times at most`() {
        var state = fresh
        var reminder = noReminderYet
        var now = t0
        var rateAsks = 0
        var invites = 0
        for (count in 1..30) {
            // A new session for every level, and a day between levels: the most permissive play.
            now += day
            val completesSet = count % 6 == 0
            when (val prompt = decide(win(count, completesSet), state, reminder, sessionShown = false, now = now)) {
                RetentionPrompt.DailyReminderInvite -> {
                    invites++
                    reminder = reminder.copy(introShown = true)
                }
                is RetentionPrompt.RateAndShare -> {
                    rateAsks++
                    state = state.copy(
                        promptCount = state.promptCount + 1,
                        lastPromptAtMs = now,
                        lastPromptCompletionCount = count,
                        ratePromptShown = true
                    )
                }
                null -> Unit
            }
        }
        assertEquals("the reminder is offered once", 1, invites)
        assertTrue("rating was asked $rateAsks times", rateAsks <= 6)
    }

    @Test
    fun `the cooldown is a week`() {
        assertEquals(7L * day, COOLDOWN_MS)
    }
}
