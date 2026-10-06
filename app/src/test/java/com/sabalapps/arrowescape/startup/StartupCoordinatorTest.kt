package com.sabalapps.arrowescape.startup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Real coroutines, tiny real timeouts: the promises that nothing can trap the player on the loading screen. */
class StartupCoordinatorTest {

    private fun step(
        task: StartupTask,
        timeoutMs: Long = 2_000,
        run: suspend () -> Unit = {}
    ) = StartupStep(task, timeoutMs, run)

    private fun allTasks(override: Map<StartupTask, StartupStep> = emptyMap()): List<StartupStep> =
        StartupTask.entries.map { override[it] ?: step(it) }

    @Test
    fun `every step runs and every task settles`() = runBlocking {
        val ran = CopyOnWriteArrayList<StartupTask>()
        val coordinator = StartupCoordinator(StartupTask.entries.map { t -> step(t) { ran += t } })
        coordinator.run()

        assertEquals(StartupTask.entries.toSet(), ran.toSet())
        val progress = coordinator.progress.value
        assertTrue(progress.allSettled)
        assertTrue(progress.failed.isEmpty())
        assertEquals(1f, progress.fraction, 0f)
    }

    @Test
    fun `required work runs before optional work`() = runBlocking {
        val order = CopyOnWriteArrayList<StartupTask>()
        val coordinator = StartupCoordinator(
            // Handed in optional-first on purpose.
            StartupTask.entries.sortedBy { it.required }.map { t -> step(t) { order += t } }
        )
        coordinator.run()
        val firstOptional = order.indexOfFirst { !it.required }
        assertTrue(order.take(firstOptional).all { it.required })
        assertTrue(order.drop(firstOptional).all { !it.required })
    }

    @Test
    fun `progress only ever moves forward as tasks settle`() = runBlocking {
        val seen = CopyOnWriteArrayList<Float>()
        val coordinator = StartupCoordinator(allTasks())
        val watcher = launch(Dispatchers.Unconfined) {
            coordinator.progress.collect { seen += it.fraction }
        }
        coordinator.run()
        watcher.cancel()
        assertEquals(seen.sorted(), seen.toList())
        assertTrue(seen.all { it in 0f..1f })
    }

    @Test
    fun `an optional step that throws is a failure the game never notices`() = runBlocking {
        val coordinator = StartupCoordinator(
            allTasks(mapOf(StartupTask.SCENERY to step(StartupTask.SCENERY) { error("decode failed") }))
        )
        coordinator.run()
        val progress = coordinator.progress.value
        assertTrue(progress.allSettled)
        assertEquals(setOf(StartupTask.SCENERY), progress.failed)
        assertTrue(progress.requiredSettled)
        assertTrue(progress.isReady(0))
    }

    @Test
    fun `a required step that throws still settles, so the game opens and finds out`() = runBlocking {
        val coordinator = StartupCoordinator(
            allTasks(mapOf(StartupTask.PROGRESS to step(StartupTask.PROGRESS) { throw IllegalStateException("bad save") }))
        )
        coordinator.run()
        assertTrue(coordinator.progress.value.requiredSettled)
        assertTrue(StartupTask.PROGRESS in coordinator.progress.value.failed)
    }

    @Test
    fun `a suspending step that never finishes is given up on at its timeout`() = runBlocking {
        val coordinator = StartupCoordinator(
            allTasks(
                mapOf(StartupTask.REMINDER to step(StartupTask.REMINDER, timeoutMs = 80) { delay(60_000) })
            )
        )
        val started = System.nanoTime()
        coordinator.run()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("run() took ${tookMs}ms", tookMs < 3_000)
        assertTrue(coordinator.progress.value.allSettled)
        assertTrue(StartupTask.REMINDER in coordinator.progress.value.failed)
    }

    @Test
    fun `a step stuck in a blocking call cannot hold startup up past its timeout`() = runBlocking {
        val coordinator = StartupCoordinator(
            allTasks(
                mapOf(
                    StartupTask.SCENERY to step(StartupTask.SCENERY, timeoutMs = 50) { Thread.sleep(1_500) }
                )
            )
        )
        val started = System.nanoTime()
        coordinator.run()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("startup waited ${tookMs}ms on a blocked step", tookMs < 1_000)
        assertTrue(StartupTask.SCENERY in coordinator.progress.value.failed)
    }

    @Test
    fun `abandoning startup stops it rather than recording failures`() = runBlocking {
        val coordinator = StartupCoordinator(
            allTasks(mapOf(StartupTask.SETTINGS to step(StartupTask.SETTINGS, timeoutMs = 10_000) { delay(60_000) }))
        )
        val job: Job = launch(Dispatchers.Default) { coordinator.run() }
        delay(100)
        job.cancel(CancellationException("activity gone"))
        job.join()
        assertTrue(job.isCancelled)
        assertFalse("a cancelled startup is not a failed step", StartupTask.SETTINGS in coordinator.progress.value.failed)
    }

    @Test
    fun `no steps at all is already finished`() = runBlocking {
        val coordinator = StartupCoordinator(emptyList())
        coordinator.run()
        assertTrue(coordinator.progress.value.settled.isEmpty())
    }
}
