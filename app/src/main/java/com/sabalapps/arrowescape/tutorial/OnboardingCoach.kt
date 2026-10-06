package com.sabalapps.arrowescape.tutorial

/**
 * The one small line of encouragement that follows a player's first clear of Level 1 or
 * Level 2 — after the discovery has had its moment, never during it. Calm and short: the
 * reveal is the reward, this is a nod.
 */
object OnboardingCoach {

    /** The line for a won Campaign level, or null — only a *first* clear of Level 1 or 2 gets one. */
    fun lineForWin(levelId: Int, isFirstClear: Boolean): String? = when {
        !isFirstClear -> null
        levelId == 1 -> "Great start!"
        levelId == 2 -> "You're getting the hang of it!"
        else -> null
    }
}
