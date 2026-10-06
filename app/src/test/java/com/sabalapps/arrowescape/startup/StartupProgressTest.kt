package com.sabalapps.arrowescape.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupProgressTest {

    private val required = StartupTask.entries.filter { it.required }
    private val optional = StartupTask.entries.filter { !it.required }

    private fun progressOf(vararg tasks: StartupTask, failed: Set<StartupTask> = emptySet()) =
        StartupProgress(settled = tasks.toSet(), failed = failed)

    @Test
    fun `nothing has settled at the start`() {
        val start = StartupProgress()
        assertEquals(0f, start.fraction, 0f)
        assertFalse(start.requiredSettled)
        assertFalse(start.allSettled)
        assertFalse(start.isReady(0))
    }

    @Test
    fun `the fraction never exceeds one, for any combination of settled tasks`() {
        val all = StartupTask.entries
        for (mask in 0 until (1 shl all.size)) {
            val subset = all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            val p = StartupProgress(settled = subset)
            assertTrue("mask $mask: ${p.fraction}", p.fraction in 0f..1f)
            assertTrue("mask $mask: target", p.target(0) in 0f..1f)
            assertTrue("mask $mask: target late", p.target(60_000) in 0f..1f)
        }
    }

    @Test
    fun `everything settled is exactly one`() {
        val done = StartupProgress(settled = StartupTask.entries.toSet())
        assertEquals(1f, done.fraction, 0f)
        assertTrue(done.allSettled)
    }

    @Test
    fun `there are required and optional tasks, and the first screen only needs the required ones`() {
        assertTrue(required.isNotEmpty() && optional.isNotEmpty())
        val ready = StartupProgress(settled = required.toSet())
        assertTrue(ready.requiredSettled)
    }

    @Test
    fun `finishing optional tasks alone never lets the game open`() {
        val p = StartupProgress(settled = optional.toSet())
        assertFalse(p.requiredSettled)
        assertFalse(p.isReady(elapsedMs = 60_000))
        assertTrue("held under 100%", p.target(60_000) < 1f)
    }

    @Test
    fun `the bar never reads one until the game can open`() {
        // Required settled, optional still running, grace not yet up: not 1.
        val waiting = StartupProgress(settled = required.toSet())
        assertTrue(waiting.target(elapsedMs = 100) < 1f)
        assertEquals(StartupProgress.HOLD_BELOW.coerceAtMost(waiting.fraction), waiting.target(100), 0f)
    }

    @Test
    fun `everything settled opens the game at once`() {
        val done = StartupProgress(settled = StartupTask.entries.toSet())
        assertTrue(done.isReady(0))
        assertEquals(1f, done.target(0), 0f)
    }

    @Test
    fun `optional work that is slow stops being waited for after a grace period`() {
        val slow = StartupProgress(settled = required.toSet())
        assertFalse(slow.isReady(StartupProgress.OPTIONAL_GRACE_MS - 1))
        assertTrue(slow.isReady(StartupProgress.OPTIONAL_GRACE_MS))
        assertEquals(1f, slow.target(StartupProgress.OPTIONAL_GRACE_MS), 0f)
    }

    @Test
    fun `a failed optional task counts as settled, so it cannot trap the player`() {
        val p = StartupProgress().let { start ->
            StartupTask.entries.fold(start) { acc, task -> acc.settle(task, failed = !task.required) }
        }
        assertTrue(p.allSettled)
        assertEquals(optional.toSet(), p.failed)
        assertTrue(p.isReady(0))
    }

    @Test
    fun `a required task that failed still lets the game open`() {
        val p = StartupTask.entries.fold(StartupProgress()) { acc, task -> acc.settle(task, failed = true) }
        assertTrue(p.requiredSettled)
        assertTrue(p.isReady(0))
    }

    @Test
    fun `settling a task twice changes nothing`() {
        val once = StartupProgress().settle(StartupTask.SETTINGS)
        assertEquals(once, once.settle(StartupTask.SETTINGS))
    }

    @Test
    fun `the weights add up to the whole`() {
        assertEquals(StartupTask.entries.sumOf { it.weight }, StartupTask.totalWeight)
        assertTrue(StartupTask.entries.all { it.weight > 0 })
    }
}
