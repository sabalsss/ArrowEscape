package com.sabalapps.arrowescape.startup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** One unit of startup work: [run] under a [timeoutMs] that keeps it from holding the game up for good. */
class StartupStep(
    val task: StartupTask,
    val timeoutMs: Long,
    val run: suspend () -> Unit
)

/**
 * Runs the startup steps and reports how far they have got.
 *
 * It exists to make one promise: **nothing here can trap the player on a loading screen.**
 * Every step is bounded by its own timeout, and a step that throws or times out is *settled*
 * all the same — recorded as failed, and moved past. A required step that fails still lets the
 * game open (the repositories behind them already degrade a corrupt save to a clean default, so
 * "open and find out" is the better failure); an optional one is never noticed at all.
 *
 * Pure Kotlin and coroutines, no Android: the steps are handed in, so the whole of this is a
 * plain JVM test.
 */
class StartupCoordinator(
    private val steps: List<StartupStep>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Where steps run. Not the caller's scope: a step is awaited with a timeout, and a
     * timeout can only stop *waiting* for a step that is busy in a blocking call (reading a
     * preferences file), it cannot interrupt it. So the work is its own job, and a step that
     * overruns is left to finish on its own rather than being torn down half-way through a
     * load that every later screen will want finished.
     */
    private val work = CoroutineScope(SupervisorJob() + dispatcher)

    private val _progress = MutableStateFlow(StartupProgress())
    val progress: StateFlow<StartupProgress> = _progress.asStateFlow()

    /** Runs every step in order, required work first. Returns when all have settled. */
    suspend fun run() {
        for (step in steps.sortedBy { !it.task.required }) {
            val job = work.async { step.run() }
            val completed = try {
                withTimeoutOrNull(step.timeoutMs) { job.await(); true } ?: false
            } catch (cancelled: CancellationException) {
                // Not a failed step: the whole startup is being abandoned.
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            _progress.value = _progress.value.settle(step.task, failed = !completed)
        }
    }
}
