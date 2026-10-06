package com.sabalapps.arrowescape.retention

import com.sabalapps.arrowescape.notification.ReminderPreferenceRepository

/**
 * The one object that decides whether the game asks the player for something, and remembers
 * that it did. A thin shell over [RetentionPromptPolicy]: the policy is pure; this adds the
 * two things a pure function cannot hold — the session, and the clock.
 *
 * **A session is the life of one of these.** The app creates one when the game starts and
 * keeps it until the process or activity goes, so "one prompt per session" means one per
 * launch. The flag is set when a prompt is *shown*, not when it is answered.
 */
class RetentionCoordinator(
    private val prompts: RetentionPromptRepository,
    private val reminders: ReminderPreferenceRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var promptShownThisSession = false

    /**
     * A won Campaign level's result has fully settled. Returns what to show, if anything, and
     * records that it is being shown — so calling this twice for the same result cannot
     * produce two prompts.
     *
     * Pass `null` for anything that is not a won Campaign level.
     */
    fun onCampaignResultSettled(win: CampaignWin?): RetentionPrompt? {
        val reminder = reminders.preference.value
        val prompt = RetentionPromptPolicy.decide(
            win = win,
            prompts = prompts.state.value,
            reminder = ReminderFacts(enabled = reminder.enabled, introShown = reminder.introShown),
            promptShownThisSession = promptShownThisSession,
            nowMs = clock()
        ) ?: return null

        promptShownThisSession = true
        when (prompt) {
            RetentionPrompt.DailyReminderInvite -> reminders.markIntroShown()
            is RetentionPrompt.RateAndShare ->
                prompts.recordPromptShown(nowMs = clock(), completionCount = win?.completedCount ?: 0)
        }
        return prompt
    }

    /** The player pressed Rate: from the prompt or from Settings. Proactive rate prompts end here. */
    fun onRateActionTapped() = prompts.recordRateActionTapped()

    /** The player pressed Share: from the prompt or from Settings. Share is not offered again. */
    fun onShareActionTapped() = prompts.recordShareActionTapped()
}
