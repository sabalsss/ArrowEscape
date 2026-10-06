package com.sabalapps.arrowescape.startup

/**
 * The timing of the loading screen's arrow assembly, as plain numbers — the drawing lives in
 * Compose, but *when* each piece moves is decided here so it can be pinned by a JVM test:
 * every piece arrives, the whole thing is assembled by [SplashProgress.ASSEMBLY_MS], and no
 * piece ever has a progress outside 0..1.
 *
 * ```
 *   0 ms   navy, a soft glow rising
 *  60 ms   four arrow discs start travelling in, 70ms apart, spinning as they come
 * 420 ms   four small sparkles arrive in the gaps
 * 520 ms   ARROW / ESCAPE fades up
 * 700 ms   the gold star settles in the middle
 * 900 ms   assembled
 * ```
 */
object SplashChoreography {

    /** The four discs: up, right, down, left. */
    const val PIECES = 4

    private const val PIECE_FIRST_START_MS = 60L
    private const val PIECE_STAGGER_MS = 70L
    private const val PIECE_TRAVEL_MS = 520L

    private const val SPARKLE_START_MS = 420L
    private const val SPARKLE_STAGGER_MS = 40L
    private const val SPARKLE_MS = 320L

    private const val CENTRE_START_MS = 700L
    private const val CENTRE_MS = 200L

    const val TITLE_START_MS = 520L
    const val TITLE_MS = 380L

    private const val BACKGROUND_MS = 420L

    /** Reduced motion: the finished emblem simply fades in, all at once. */
    const val REDUCED_FADE_MS = 360L

    private fun window(elapsedMs: Long, startMs: Long, lengthMs: Long): Float =
        ((elapsedMs - startMs).toFloat() / lengthMs).coerceIn(0f, 1f)

    /** How far disc [index] has travelled, 0 (not started) to 1 (in place). */
    fun pieceProgress(index: Int, elapsedMs: Long): Float =
        window(elapsedMs, PIECE_FIRST_START_MS + PIECE_STAGGER_MS * index, PIECE_TRAVEL_MS)

    /** The small sparkle [index]'s arrival. */
    fun sparkleProgress(index: Int, elapsedMs: Long): Float =
        window(elapsedMs, SPARKLE_START_MS + SPARKLE_STAGGER_MS * index, SPARKLE_MS)

    /** The star in the middle. */
    fun centreProgress(elapsedMs: Long): Float = window(elapsedMs, CENTRE_START_MS, CENTRE_MS)

    /** The words. */
    fun titleProgress(elapsedMs: Long): Float = window(elapsedMs, TITLE_START_MS, TITLE_MS)

    /** The navy's glow coming up behind it all. */
    fun backgroundProgress(elapsedMs: Long): Float = window(elapsedMs, 0L, BACKGROUND_MS)

    /** The whole emblem fading in under reduced motion. */
    fun reducedProgress(elapsedMs: Long): Float = window(elapsedMs, 0L, REDUCED_FADE_MS)

    /** The first moment every piece, the sparkles, the star and the title are all in place. */
    val assembledAtMs: Long = maxOf(
        PIECE_FIRST_START_MS + PIECE_STAGGER_MS * (PIECES - 1) + PIECE_TRAVEL_MS,
        SPARKLE_START_MS + SPARKLE_STAGGER_MS * (PIECES - 1) + SPARKLE_MS,
        CENTRE_START_MS + CENTRE_MS,
        TITLE_START_MS + TITLE_MS
    )

    /** The glow pulse that follows a full bar: 0..1..0 over [SplashProgress.PULSE_MS]. */
    fun pulse(sinceReadyMs: Long): Float {
        val t = (sinceReadyMs.toFloat() / SplashProgress.PULSE_MS).coerceIn(0f, 1f)
        return kotlin.math.sin(Math.PI.toFloat() * t)
    }
}
