package com.sabalapps.arrowescape.tutorial

/**
 * What onboarding persists: one bit per lesson — whether Level 1's "an arrow with a
 * clear path escapes" has been taught, and whether Level 2's "removing one arrow can
 * free another" has. Each is recorded the moment its lesson works, separately, so
 * finishing one never marks the other.
 *
 * It is one bit behind an interface rather than a field on a repository because
 * of who needs it. The flag belongs with the settings — that is where the
 * "Replay Tutorial" action lives, and that is the storage it is written to — but
 * the thing that reads and sets it is the ViewModel, which has to stay
 * constructible without an Android `Context` so the tutorial can be tested as
 * plain Kotlin. `SettingsRepository` implements this; a test uses
 * [InMemoryTutorialFlagStore].
 */
interface TutorialFlagStore {

    /** Level 1's lesson. False on a fresh install. */
    fun isTutorialCompleted(): Boolean

    fun setTutorialCompleted(completed: Boolean)

    /** Level 2's lesson ([DependencyLesson]). False on a fresh install. */
    fun isDependencyLessonCompleted(): Boolean

    fun setDependencyLessonCompleted(completed: Boolean)
}

/** Test and preview double. Also the default for a ViewModel built by hand. */
class InMemoryTutorialFlagStore(
    private var completed: Boolean = false,
    private var dependencyCompleted: Boolean = false
) : TutorialFlagStore {
    override fun isTutorialCompleted(): Boolean = completed

    override fun setTutorialCompleted(completed: Boolean) {
        this.completed = completed
    }

    override fun isDependencyLessonCompleted(): Boolean = dependencyCompleted

    override fun setDependencyLessonCompleted(completed: Boolean) {
        dependencyCompleted = completed
    }
}
