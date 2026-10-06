package com.sabalapps.arrowescape.retention

import com.sabalapps.arrowescape.notification.ReminderPreferenceRepository
import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shell around the policy: it holds the session and the clock, and records what it shows. */
class RetentionCoordinatorTest {

    private val store = InMemoryProgressStore()
    private var now = 1_800_000_000_000L
    private val prompts = RetentionPromptRepository(store)
    private val reminders = ReminderPreferenceRepository(store)

    private fun session() = RetentionCoordinator(prompts, reminders, clock = { now })

    private fun win(count: Int) = CampaignWin(count, finishesASet = false)

    @Test
    fun `level one asks, and records that it did`() {
        val prompt = session().onCampaignResultSettled(win(1))
        assertEquals(RetentionPrompt.RateAndShare(true), prompt)
        assertEquals(1, prompts.state.value.promptCount)
        assertEquals(now, prompts.state.value.lastPromptAtMs)
        assertEquals(1, prompts.state.value.lastPromptCompletionCount)
    }

    @Test
    fun `the same result settling twice cannot ask twice`() {
        val session = session()
        assertEquals(RetentionPrompt.RateAndShare(true), session.onCampaignResultSettled(win(1)))
        assertNull(session.onCampaignResultSettled(win(1)))
        assertEquals(1, prompts.state.value.promptCount)
    }

    @Test
    fun `a second level in the same session is not asked about`() {
        val session = session()
        session.onCampaignResultSettled(win(1))
        assertNull(session.onCampaignResultSettled(win(2)))
        assertFalse("nothing was shown, so nothing is recorded", reminders.preference.value.introShown)
    }

    @Test
    fun `the next session invites the reminder, and asking is recorded`() {
        session().onCampaignResultSettled(win(1))

        now += 60 * 60 * 1000
        val prompt = session().onCampaignResultSettled(win(2))
        assertEquals(RetentionPrompt.DailyReminderInvite, prompt)
        assertTrue(reminders.preference.value.introShown)
        assertEquals("the invite is not a rating request", 1, prompts.state.value.promptCount)
    }

    @Test
    fun `after the invite the rating request is owed at the next session`() {
        session().onCampaignResultSettled(win(1))
        now += 60_000
        session().onCampaignResultSettled(win(2)) // the invite
        now += 60_000
        assertEquals(RetentionPrompt.RateAndShare(true), session().onCampaignResultSettled(win(3)))
    }

    @Test
    fun `a loss never asks`() {
        assertNull(session().onCampaignResultSettled(null))
        assertEquals(RetentionPromptState(), prompts.state.value)
    }

    @Test
    fun `rate and share taps are passed on to the record`() {
        val session = session()
        session.onRateActionTapped()
        session.onShareActionTapped()
        assertTrue(prompts.state.value.rateActionTapped)
        assertTrue(prompts.state.value.shareActionTapped)
        reminders.markIntroShown() // the reminder question is a separate matter, long answered
        now += 365L * 24 * 60 * 60 * 1000
        assertNull("after Rate, no proactive rating prompt", session().onCampaignResultSettled(win(30)))
    }
}
