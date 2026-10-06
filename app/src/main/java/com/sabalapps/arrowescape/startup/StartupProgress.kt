package com.sabalapps.arrowescape.startup

/**
 * The real work the loading screen stands for.
 *
 * Each task has a [weight] — roughly how much of the wait it is — and says whether the game
 * can open without it. A *required* task is something the first screen reads (the saved
 * progress behind Continue, the settings behind the theme); an *optional* one only makes the
 * first screen nicer (its artwork already decoded) or sets up something for later (the reminder
 * schedule). Optional tasks may fail or time out and the game still opens.
 */
enum class StartupTask(val weight: Int, val required: Boolean) {
    /** Load the player's settings. */
    SETTINGS(weight = 2, required = true),

    /** Load Campaign, Endless and Daily progress and the unfinished boards. */
    PROGRESS(weight = 4, required = true),

    /** Load the daily-reminder preference and the rate / share record. */
    RETENTION(weight = 1, required = false),

    /** Decode the artwork Home is dressed in, so it is there on the first frame. */
    SCENERY(weight = 2, required = false),

    /** Make sure the reminder's schedule matches the player's choice and the current clock. */
    REMINDER(weight = 2, required = false);

    companion object {
        val totalWeight: Int = entries.sumOf { it.weight }
    }
}

/**
 * Where startup has got to: which tasks have *settled* — finished, failed or timed out; the
 * loading screen does not care which — and which of those settled badly.
 *
 * Immutable, and a pure function of its two sets, so what the loading screen may show is
 * decided here and tested here.
 */
data class StartupProgress(
    val settled: Set<StartupTask> = emptySet(),
    val failed: Set<StartupTask> = emptySet()
) {
    /** The share of the work done, 0..1: the settled tasks' weight over all of it. Never more than 1. */
    val fraction: Float
        get() = (settled.sumOf { it.weight }.toFloat() / StartupTask.totalWeight).coerceIn(0f, 1f)

    /** Everything the first screen reads has been read. */
    val requiredSettled: Boolean get() = StartupTask.entries.filter { it.required }.all { it in settled }

    /** Every task has settled, one way or the other. */
    val allSettled: Boolean get() = settled.size == StartupTask.entries.size

    /**
     * Whether the game can open now, [elapsedMs] after startup began: the required work is
     * done and the optional work either is too or has had [OPTIONAL_GRACE_MS] and is no longer
     * worth waiting for (it carries on in the background; it is never abandoned half-way).
     */
    fun isReady(elapsedMs: Long): Boolean =
        requiredSettled && (allSettled || elapsedMs >= OPTIONAL_GRACE_MS)

    /**
     * The 0..1 the loading bar is heading for. It is the real fraction while there is real
     * work left, held under [HOLD_BELOW] so the bar cannot read "100%" before the game can
     * actually open, and it is 1 the moment [isReady] says the game can.
     */
    fun target(elapsedMs: Long): Float =
        if (isReady(elapsedMs)) 1f else fraction.coerceAtMost(HOLD_BELOW)

    fun settle(task: StartupTask, failed: Boolean = false): StartupProgress = copy(
        settled = settled + task,
        failed = if (failed) this.failed + task else this.failed
    )

    companion object {
        /** How long optional work may keep the loading screen up after the required work is done. */
        const val OPTIONAL_GRACE_MS = 1_000L

        /** The most the bar reads while the game cannot yet open. */
        const val HOLD_BELOW = 0.97f
    }
}
