package com.sabalapps.arrowescape.endless

/**
 * SplitMix64, written out in full rather than taken from `kotlin.random`.
 *
 * Endless Mode saves a seed and nothing else, so the generator has to rebuild a
 * byte-identical puzzle from that seed on any device, on any future version of
 * the platform. `java.util.Random` is specified tightly enough for that, but
 * `kotlin.random.Random` is not — its algorithm is an implementation detail, so
 * a saved puzzle would be at the mercy of a standard-library update. Twelve
 * lines of arithmetic with no hidden state is the cheaper guarantee.
 */
class SeededRandom(seed: Long) {

    private var state: Long = seed

    /** The next 64 bits of the stream. Every other method is built on this. */
    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        return mix(state)
    }

    /** Uniform in `0 until bound`, by rejection so the top values are not favoured. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        // Everything at or above `limit` would wrap the modulo unevenly; redraw
        // instead. The expected number of redraws is far below one.
        val span = bound.toLong()
        val limit = Long.MAX_VALUE - (Long.MAX_VALUE % span) - 1
        while (true) {
            val draw = nextLong() ushr 1
            if (draw <= limit) return (draw % span).toInt()
        }
    }

    /** Uniform in [range], ends included. */
    fun nextInt(range: IntRange): Int = range.first + nextInt(range.last - range.first + 1)

    /** Uniform in `0.0 until 1.0`. */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()

    /** A uniform element of [items]. */
    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    companion object {
        private const val GOLDEN_GAMMA = -7046029254386353131L // 0x9E3779B97F4A7C15

        /** SplitMix64's finaliser: avalanches every input bit across the output. */
        private fun mix(value: Long): Long {
            var z = value
            z = (z xor (z ushr 30)) * -4658895280553007687L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -7723592293110705685L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }

        /**
         * Folds several numbers into one seed. Used so that `(seed, tier,
         * attempt)` picks an independent-looking stream for every attempt while
         * staying a pure function of the saved seed.
         */
        fun derive(vararg parts: Long): Long {
            var acc = 0x6a09e667f3bcc909L
            for (part in parts) acc = mix(acc xor part) + GOLDEN_GAMMA
            return mix(acc)
        }
    }
}
