package com.sabalapps.arrowescape.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the game opens. Resolved once per launch, and navigated to once. */
enum class StartupDestination {
    /** The default: Home. */
    Home,

    /** Today's Daily Challenge — what tapping the daily reminder asks for. */
    Daily;

    companion object {
        /** The value of [LaunchIntents.EXTRA_DESTINATION] that asks for [Daily]. */
        const val DAILY_EXTRA_VALUE = "daily"

        /** What an intent's destination extra means. Anything unrecognised — or absent — is Home. */
        fun fromExtra(value: String?): StartupDestination =
            if (value == DAILY_EXTRA_VALUE) Daily else Home
    }
}

/** The intent contract between the daily reminder (which sends it) and `MainActivity` (which reads it). */
object LaunchIntents {
    const val EXTRA_DESTINATION = "com.sabalapps.arrowescape.extra.DESTINATION"
}

/**
 * Holds the destination a launch asked for until the app is ready to go there — and hands it
 * over exactly once.
 *
 * The reminder can arrive in three states: the app is not running (the intent comes with
 * `onCreate`, while the loading screen is still up), it is running and visible (`onNewIntent`),
 * or it is backgrounded (also `onNewIntent`). All three end the same way: [offer] records what
 * was asked for, the app's navigation calls [consume] when it can act on it, and a second
 * consume finds nothing. So the player is taken to the Daily Challenge once, not
 * Home → Daily → Home, and a recreated activity cannot replay a launch that has already happened.
 *
 * A [StartupDestination.Home] request is not recorded: Home is what the app does anyway, and it
 * never overwrites a request that is still waiting.
 */
class LaunchRouter {
    private val _pending = MutableStateFlow<StartupDestination?>(null)

    /** The destination waiting to be navigated to, or null. For the navigation layer to observe. */
    val pending: StateFlow<StartupDestination?> = _pending.asStateFlow()

    fun offer(destination: StartupDestination) {
        // Home is not a request: it must not wipe a Daily that is still waiting its turn.
        if (destination != StartupDestination.Home) _pending.value = destination
    }

    /** Takes the pending destination, leaving none behind. Null when there is nothing to do. */
    fun consume(): StartupDestination? {
        val taken = _pending.value
        _pending.value = null
        return taken
    }
}
