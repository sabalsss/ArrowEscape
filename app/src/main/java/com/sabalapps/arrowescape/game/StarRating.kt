package com.sabalapps.arrowescape.game

/**
 * How many stars a cleared Campaign level is worth.
 *
 * Two facts about the run decide it, and nothing else: how many taps were
 * blocked, and whether a hint was asked for. There is no timer, no move count
 * and no par — a star is mastery of the rule, which is exactly "did you read
 * the board before you tapped it".
 *
 * ```
 * stars = 3 - blocked taps - (1 if a hint was used)
 * ```
 * clamped to [MIN]..[MAX], so a clear is always worth at least one star.
 *
 * **Why the thresholds are tight.** A blocked tap costs a life and there are
 * [GameState.STARTING_LIVES] of them, so the third blocked tap *ends the board*
 * — a winning run can only ever have 0, 1 or 2 blocked taps. Spacing the three
 * tiers over "3+ mistakes" would make one star unreachable, so the tiers sit on
 * the range the rules actually allow: flawless, one slip (or a hint), and the
 * most mistakes a run can survive. The clamp keeps the formula honest if
 * [GameState.STARTING_LIVES] is ever raised.
 */
object StarRating {

    /** No rating recorded: a level cleared before stars existed, or not cleared. */
    const val NONE = 0

    /** Every clear is worth at least this. */
    const val MIN = 1

    /** A flawless clear. Also what [isPerfect] means. */
    const val MAX = 3

    /** A hint is help, so it costs the same as one blocked tap. */
    private const val HINT_COST = 1

    fun stars(blockedTaps: Int, hintUsed: Boolean): Int {
        val mistakes = blockedTaps.coerceAtLeast(0) + if (hintUsed) HINT_COST else 0
        return (MAX - mistakes).coerceIn(MIN, MAX)
    }

    /** A clear with no blocked taps and no hint — the Perfect Escape. */
    fun isPerfect(stars: Int): Boolean = stars >= MAX

    /** True when [stars] is a rating that can be stored. Used on the way in from disk. */
    fun isValid(stars: Int): Boolean = stars in MIN..MAX
}
