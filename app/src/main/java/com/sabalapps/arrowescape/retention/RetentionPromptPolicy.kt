package com.sabalapps.arrowescape.retention

/** What the game may put in front of the player once a result has settled. */
sealed interface RetentionPrompt {

    /** "Want a daily mystery?" — the offer to turn the daily reminder on. */
    data object DailyReminderInvite : RetentionPrompt

    /**
     * "Enjoying Arrow Escape?" — [showShare] is false once the player has pressed Share,
     * because Share is not offered again after that.
     */
    data class RateAndShare(val showShare: Boolean) : RetentionPrompt
}

/** A won Campaign level, as the policy needs to see it. A loss, or any other mode, is simply no [CampaignWin]. */
data class CampaignWin(
    /** How many Campaign levels are cleared, including this one. */
    val completedCount: Int,
    /** This clear finished a world or the album: its own celebration is on screen and is not shared. */
    val finishesASet: Boolean
)

/** What the daily reminder's preference says, as far as prompting is concerned. */
data class ReminderFacts(val enabled: Boolean, val introShown: Boolean)

/**
 * When — and whether — the game asks the player for anything after a level.
 *
 * Pure, so every rule below is a plain unit test. The rules, in the order they are applied:
 *
 *  1. **Only a won Campaign level asks.** A loss, Endless, Daily and the replayed tutorial
 *     never do; the reveal after a win is the reward and a failure is no time to ask.
 *  2. **One prompt per app session**, of any kind. Level 1 then Level 2 in one sitting is
 *     one prompt, not two.
 *  3. **Never on top of a celebration.** A first clear that finishes a world or the album
 *     has its own moment; the prompt waits for the next win.
 *  4. **The reminder invite comes first**, once the player has cleared two levels and so
 *     knows what the Daily Challenge is. It is asked once, ever; "Not Now" is final (the
 *     Settings switch is always there).
 *  5. **Rate and Share** is asked at milestones — the first two levels, then
 *     [LATER_MILESTONES] — and stops for good once the player has pressed Rate. The two
 *     opening milestones are the onboarding pair and carry no cooldown; every later one
 *     needs [COOLDOWN_MS] since the last prompt.
 *
 * Nothing is rewarded, and the prompt is the same whoever the player is: there is no
 * "do you like it?" branch that sends happy players to the store and others elsewhere.
 */
object RetentionPromptPolicy {

    /** The first two levels: the earliest moments a prompt is allowed. No cooldown applies to them. */
    val ONBOARDING_MILESTONES = listOf(1, 2)

    /** Cleared-level counts after the opening pair. */
    val LATER_MILESTONES = listOf(6, 12, 20, 30)

    /** Between two prompts once past the onboarding pair: a week. */
    const val COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000

    /** Levels cleared before the reminder is offered: enough to know what a Daily Challenge is. */
    const val REMINDER_AFTER_LEVELS = 2

    private val ALL_MILESTONES = ONBOARDING_MILESTONES + LATER_MILESTONES

    /**
     * The milestone this win has reached and not yet been asked about: the highest one at
     * or below [completedCount] that is above [lastPromptCompletionCount]. Null when there
     * is none. "Highest" so a player who sailed past a milestone while a prompt was
     * suppressed is asked once, not once per milestone passed.
     */
    fun milestoneFor(completedCount: Int, lastPromptCompletionCount: Int): Int? =
        ALL_MILESTONES.filter { it <= completedCount && it > lastPromptCompletionCount }.maxOrNull()

    fun decide(
        win: CampaignWin?,
        prompts: RetentionPromptState,
        reminder: ReminderFacts,
        promptShownThisSession: Boolean,
        nowMs: Long
    ): RetentionPrompt? {
        if (win == null) return null
        if (promptShownThisSession) return null
        if (win.finishesASet) return null

        if (win.completedCount >= REMINDER_AFTER_LEVELS && !reminder.introShown && !reminder.enabled) {
            return RetentionPrompt.DailyReminderInvite
        }

        if (prompts.rateActionTapped) return null
        val milestone = milestoneFor(win.completedCount, prompts.lastPromptCompletionCount) ?: return null
        val isOnboarding = milestone in ONBOARDING_MILESTONES
        if (!isOnboarding && prompts.lastPromptAtMs > 0L && nowMs - prompts.lastPromptAtMs < COOLDOWN_MS) {
            return null
        }
        return RetentionPrompt.RateAndShare(showShare = !prompts.shareActionTapped)
    }
}
