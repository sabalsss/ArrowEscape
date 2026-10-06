package com.sabalapps.arrowescape.endless

import com.sabalapps.arrowescape.game.Direction
import com.sabalapps.arrowescape.game.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The large fuzz run: 1,000 seeds per tier, 5,000 boards in all, every one of
 * them verified and measured.
 *
 * It is not part of the fast suite — it takes seconds rather than milliseconds,
 * and its job is not to gate a build on a fixed expectation but to answer
 * questions about the generator across a big sample: does it ever crash, does it
 * ever build an impossible board, does it ever fail to terminate, how often does
 * the quality filter turn a board down, and how slow is the slowest puzzle a
 * player could be handed.
 *
 * Run it with:
 *
 * ```
 * ./gradlew testDebugUnitTest -Darrowescape.stress=1
 * ```
 *
 * Without the flag every test here is skipped, which is why the normal suite
 * stays quick. The report is printed to stdout; `-i` shows it.
 */
class GeneratorStressTest {

    /** Seeds per tier. Five tiers, so 5,000 boards per run. */
    private val seedsPerTier = 1_000

    /**
     * Skips the test unless the stress flag is set. JUnit treats a failed
     * assumption as "not run" rather than as a failure, which is what keeps
     * these out of the normal suite without hiding them.
     */
    private fun requireStressRun() {
        assumeTrue(
            "set -Darrowescape.stress=1 to run the stress suite",
            !System.getProperty("arrowescape.stress").isNullOrBlank()
        )
    }

    /** One tier's worth of results. */
    private class TierReport(val tier: EndlessTier) {
        var boards = 0
        var attempts = 0
        var fallbacks = 0
        var totalNanos = 0L
        var maxNanos = 0L
        var maxNanosSeed = 0L
        val rejections = HashMap<QualityRules.Rejection, Int>()
        val arrowCounts = ArrayList<Int>(1_024)
        val scores = ArrayList<Double>(1_024)
        val depths = ArrayList<Int>(1_024)
        val layouts = HashSet<List<String>>()

        val averageMs: Double get() = totalNanos / 1e6 / boards
        val maxMs: Double get() = maxNanos / 1e6

        /**
         * Share of constructions the quality filter turned down. Every attempt
         * past the first is one rejected board, so this is the real cost of the
         * filter rather than a guess at it.
         */
        val rejectionRate: Double get() = (attempts - boards).toDouble() / attempts

        fun line(): String = buildString {
            append("%-9s".format(tier.name))
            append(" boards=%4d".format(boards))
            append(" arrows=%2d..%2d".format(arrowCounts.min(), arrowCounts.max()))
            append(" depth=%2d..%2d".format(depths.min(), depths.max()))
            append(" score=%5.1f..%5.1f/avg%5.1f".format(scores.min(), scores.max(), scores.average()))
            append(" attempts/board=%.2f".format(attempts.toDouble() / boards))
            append(" rejected=%4.1f%%".format(rejectionRate * 100))
            append(" fallbacks=%d".format(fallbacks))
            append(" distinct=%d".format(layouts.size))
            append(" avg=%.3fms max=%.2fms(seed %d)".format(averageMs, maxMs, maxNanosSeed))
            if (rejections.isNotEmpty()) append(" fallbackCauses=$rejections")
        }
    }

    private fun run(): List<TierReport> {
        // Warm the JIT so the timings describe the generator rather than the
        // first few hundred microseconds of class loading.
        for (tier in EndlessTier.entries) repeat(50) { PuzzleGenerator.generate(it.toLong(), tier) }

        return EndlessTier.entries.map { tier ->
            val report = TierReport(tier)
            for (seed in 1L..seedsPerTier) {
                val before = System.nanoTime()
                val puzzle = PuzzleGenerator.generate(seed, tier)
                val elapsed = System.nanoTime() - before

                report.boards++
                report.attempts += puzzle.attempts
                report.totalNanos += elapsed
                if (elapsed > report.maxNanos) {
                    report.maxNanos = elapsed
                    report.maxNanosSeed = seed
                }
                if (!puzzle.onTier) {
                    report.fallbacks++
                    tier.baseRules.reject(puzzle.metrics)?.let { report.rejections.merge(it, 1, Int::plus) }
                }
                report.arrowCounts += puzzle.metrics.arrowCount
                report.scores += puzzle.metrics.difficultyScore
                report.depths += puzzle.metrics.peelDepth
                report.layouts += layout(puzzle.level)

                // The two invariants that must hold for every single board, not
                // just on average. Checked inside the loop so a failure names
                // the seed that caused it.
                assertTrue(
                    "tier=$tier seed=$seed produced an unsolvable board: ${puzzle.metrics.debugSummary()}",
                    BoardAnalysis.verifyOrder(puzzle.level.arrows, puzzle.solution)
                )
                assertTrue(
                    "tier=$tier seed=$seed produced ${puzzle.level.columns} columns",
                    puzzle.level.columns <= BoardSize.MAX_COLUMNS
                )
            }
            report
        }
    }

    @Test
    fun `five thousand generated boards are all valid, and here is what they look like`() {
        requireStressRun()

        val reports = run()

        println()
        println("=== generator stress run: ${reports.sumOf { it.boards }} boards ===")
        reports.forEach { println(it.line()) }
        println(
            "overall: avg=%.3fms  worst=%.2fms  rejected=%.1f%%  fallbacks=%d".format(
                reports.sumOf { it.totalNanos } / 1e6 / reports.sumOf { it.boards },
                reports.maxOf { it.maxMs },
                reports.sumOf { it.attempts - it.boards }.toDouble() /
                    reports.sumOf { it.attempts } * 100,
                reports.sumOf { it.fallbacks }
            )
        )
        println()

        // Deliberately loose thresholds. A tight assertion on timing would make
        // this flaky on a loaded machine, and the run's value is the report
        // above; these only catch an outright regression.
        for (report in reports) {
            assertTrue(
                "${report.tier} averaged %.2fms per board".format(report.averageMs),
                report.averageMs < 50.0
            )
            assertTrue(
                "${report.tier} took %.1fms on its worst board (seed %d)"
                    .format(report.maxMs, report.maxNanosSeed),
                report.maxMs < 1_000.0
            )
            assertTrue(
                "${report.tier} fell back off-tier on ${report.fallbacks} of ${report.boards} boards",
                report.fallbacks * 20 <= report.boards
            )
            assertTrue(
                "${report.tier} spent an average of %.1f attempts per board, close to the cap of %d"
                    .format(report.attempts.toDouble() / report.boards, PuzzleGenerator.MAX_ATTEMPTS),
                report.attempts.toDouble() / report.boards < PuzzleGenerator.MAX_ATTEMPTS / 2.0
            )
        }
    }

    @Test
    fun `no tier collapses onto a handful of layouts`() {
        requireStressRun()

        for (report in run()) {
            val distinctShare = report.layouts.size.toDouble() / report.boards
            // Beginner works in 16-20 cells, so some collisions are a fact
            // about the space. Everything else should be essentially all
            // distinct.
            val floor = if (report.tier == EndlessTier.BEGINNER) 0.90 else 0.99
            assertTrue(
                "${report.tier}: ${report.layouts.size} distinct layouts from ${report.boards} seeds",
                distinctShare >= floor
            )
        }
    }

    @Test
    fun `the whole run is reproducible from the seeds alone`() {
        requireStressRun()

        // The claim that makes a one-Long save sufficient, checked at scale: two
        // independent passes over the same seeds have to agree exactly.
        for (tier in EndlessTier.entries) {
            for (seed in 1L..seedsPerTier) {
                val first = PuzzleGenerator.generate(seed, tier)
                val second = PuzzleGenerator.generate(seed, tier)
                assertEquals("tier=$tier seed=$seed is not reproducible", layout(first.level), layout(second.level))
                assertEquals("tier=$tier seed=$seed witness differs", first.solution, second.solution)
            }
        }
    }

    private fun layout(level: Level): List<String> {
        val grid = Array(level.rows) { CharArray(level.columns) { '.' } }
        for (arrow in level.arrows) {
            grid[arrow.row][arrow.col] = when (arrow.direction) {
                Direction.UP -> '^'
                Direction.DOWN -> 'v'
                Direction.LEFT -> '<'
                Direction.RIGHT -> '>'
            }
        }
        return grid.map { String(it) }
    }
}
