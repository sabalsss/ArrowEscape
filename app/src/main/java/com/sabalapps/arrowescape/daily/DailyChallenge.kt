package com.sabalapps.arrowescape.daily

import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.GeneratedPuzzle
import com.sabalapps.arrowescape.endless.SeededRandom
import com.sabalapps.arrowescape.shape.MysteryShapePuzzles
import com.sabalapps.arrowescape.shape.MysteryShapes
import com.sabalapps.arrowescape.shape.ShapeVariant
import com.sabalapps.arrowescape.time.GameDate

/**
 * One mystery shape per calendar day, built by the shape generator from a seed that is a pure
 * function of the date.
 *
 * ## What the date decides
 *
 * Everything, and nothing else does: the **picture** (which template, flipped or not), the
 * **tier** it is generated at, and — through the seed — the direction of every arrow. Two players
 * on the same local date running the same [GENERATOR_VERSION] are handed the same board down to
 * the last arrow, and the same player revisiting a date gets the board they had. That is also
 * what lets a part-finished daily be saved as a date and a handful of arrow ids.
 *
 * ## Why there is still no second rules engine
 *
 * A daily is built by `ShapePuzzleGenerator`, whose every legality decision goes through
 * `MoveValidator` and whose result is replayed through `BoardAnalysis.verifyOrder` before it is
 * returned. It is solvable by construction, with a free witness, like every other generated board.
 *
 * ## Variety without memory
 *
 * The pictures are dealt like a shuffled deck: the days are cut into cycles as long as the pool,
 * every cycle visits each picture once, in an order shuffled by the cycle number, and a cycle's
 * first picture is never the previous cycle's last. So no picture repeats until the whole pool has
 * been seen, two days in a row are never the same, and there is still nothing to store — the
 * order is a function of the date.
 */
object DailyChallenge {

    /**
     * The version of the daily *scheme*: the seed derivation, the picture dealing and the tier rule
     * — not the generator's internals.
     *
     * It is mixed into every daily seed, so bumping it re-cuts every future day's puzzle. An
     * in-progress daily already saved under another version no longer matches the seed its date
     * derives to and is discarded by [DailyRepository]'s seed check, which costs at most one
     * unfinished puzzle; **the streak, the best streak and the completed-day total are not touched**
     * (they live in a separate record keyed by date).
     *
     * 1 = the abstract rectangular generator (`PuzzleGenerator`), Medium/Hard drawn 3:2.
     * 2 = shape-first: a [MysteryShapes] picture per day, Medium for the smaller pictures and Hard
     *     for the larger ones.
     */
    const val GENERATOR_VERSION = 2

    /** The seed for [date]. Pure: same date and version, same number, forever. */
    fun seedFor(date: GameDate): Long =
        SeededRandom.derive(stableHash("daily${date.iso}v$GENERATOR_VERSION"))

    /**
     * The picture [date] is built to, and which way round. Pure — see the class comment for how
     * the days are dealt.
     */
    fun shapeFor(date: GameDate): ShapeVariant {
        val pool = MysteryShapes.dailyPool
        val size = pool.size
        val day = date.epochDay
        val cycle = Math.floorDiv(day, size.toLong())
        val position = Math.floorMod(day, size.toLong()).toInt()
        val template = pool[orderOf(cycle, size)[position]]

        val rng = SeededRandom(SeededRandom.derive(seedFor(date), MIRROR_SALT))
        val variants = template.variants
        return variants[rng.nextInt(variants.size)]
    }

    /** Which tier [date]'s board is generated at: the day's picture decides. */
    fun tierFor(date: GameDate): EndlessTier =
        if (shapeFor(date).template.cellCount >= MysteryShapes.DAILY_HARD_FROM_CELLS) {
            EndlessTier.HARD
        } else {
            EndlessTier.MEDIUM
        }

    /** [date]'s puzzle. Solvable by construction, with a witness, like any other. */
    fun generate(date: GameDate): GeneratedPuzzle =
        MysteryShapePuzzles.generate(
            variant = shapeFor(date),
            seed = seedFor(date),
            tier = tierFor(date),
            name = NAME
        )

    /**
     * Rebuilds the board a save refers to. Separate from [generate] only so a restored daily is
     * obviously the same call as a fresh one — a date is the whole of a daily's identity, so there
     * is nothing else to pass.
     */
    fun generateForSave(date: GameDate): GeneratedPuzzle = generate(date)

    /** What the HUD calls it. */
    const val NAME = "Daily Challenge"

    /** The order the pool is dealt in during [cycle]: a shuffle, with no repeat across the seam. */
    private fun orderOf(cycle: Long, size: Int): IntArray {
        val order = shuffled(cycle, size)
        // The only way a picture can repeat on consecutive days is across a cycle seam, so the
        // seam is checked: if this cycle opens with the picture the last one closed with, swap it
        // with the next. (Only the first two slots are ever touched, and the last slot of the
        // previous cycle's *raw* shuffle is what it closed with, since size > 2.)
        val previousLast = shuffled(cycle - 1, size).last()
        if (order[0] == previousLast) {
            val swap = order[0]
            order[0] = order[1]
            order[1] = swap
        }
        return order
    }

    private fun shuffled(cycle: Long, size: Int): IntArray {
        val rng = SeededRandom(SeededRandom.derive(stableHash("dailyshapes$GENERATOR_VERSION"), cycle))
        val order = IntArray(size) { it }
        for (i in size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = order[i]
            order[i] = order[j]
            order[j] = t
        }
        return order
    }

    /**
     * FNV-1a, 64-bit, written out.
     *
     * `String.hashCode` would do — it is specified rather than
     * implementation-defined — but it is 32 bits of a very weak mix, and a daily
     * seed is the one number in the app that has to be identical on every
     * device and every future runtime. Eight lines of arithmetic over the UTF-16
     * code units has no dependency that could ever be re-specified underneath
     * it.
     */
    internal fun stableHash(text: String): Long {
        var hash = -3_750_763_034_362_895_579L // 0xCBF29CE484222325
        for (char in text) {
            hash = hash xor char.code.toLong()
            hash *= 1_099_511_628_211L // 0x100000001B3
        }
        return hash
    }

    /** Keeps the flip of a day's picture out of step with the board's own stream. */
    private const val MIRROR_SALT = 0x4d495252L // "MIRR"
}
