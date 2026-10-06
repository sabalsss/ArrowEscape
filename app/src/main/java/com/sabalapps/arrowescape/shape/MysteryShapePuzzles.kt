package com.sabalapps.arrowescape.shape

import com.sabalapps.arrowescape.endless.EndlessTier
import com.sabalapps.arrowescape.endless.GeneratedPuzzle
import com.sabalapps.arrowescape.endless.PuzzleMetrics
import com.sabalapps.arrowescape.endless.SeededRandom

/**
 * Endless's puzzles, and the shared last step Daily uses too: a picture from the
 * [MysteryShapes] catalogue, turned into a solvable board by [ShapePuzzleGenerator].
 *
 * ## One seed, one board
 *
 * Which picture, which way round, and which way every arrow points are all pure functions of
 * `(seed, tier)` — no clock, no `Random`, no history — so a board persists as the single `Long`
 * it always has and a restored board is the board the player was looking at. Not repeating a
 * picture is therefore done *above* this, by choosing the seed (see `GameViewModel`), never by
 * making a board depend on what came before it.
 *
 * ## Versioning
 *
 * [GENERATOR_VERSION] is written into every saved Endless board. Change the catalogue, the
 * selection below or the generator in a way that would build a different board for the same
 * `(seed, tier)` and **bump it**: a saved board carrying another version is discarded instead of
 * being restored onto arrows it never described (the same trap `Levels.LAYOUT_VERSION` guards for
 * the Campaign). Nothing else — progress, streaks, totals — carries the version.
 */
object MysteryShapePuzzles {

    /**
     * 1 = the first shape-based Endless (27 templates, `ShapeTierProfile` v1). Version 0 is the
     * abstract rectangular generator (`PuzzleGenerator`), whose saves carry no version at all.
     */
    const val GENERATOR_VERSION = 1

    /** The picture [seed] picks at [tier], and how it is oriented. Pure and cheap. */
    fun choose(seed: Long, tier: EndlessTier): ShapeVariant {
        val rng = SeededRandom(SeededRandom.derive(seed, tier.ordinal.toLong(), CHOICE_SALT))
        val eligible = MysteryShapes.forTier(tier)
        val template = eligible[rng.nextInt(eligible.size)]
        val variants = template.variants
        return variants[rng.nextInt(variants.size)]
    }

    /** Just the template id [choose] would pick — what repeat control compares. */
    fun templateIdFor(seed: Long, tier: EndlessTier): String = choose(seed, tier).template.id

    /** The Endless board for `(seed, tier)`. */
    fun generate(seed: Long, tier: EndlessTier, name: String = "Endless · ${tier.label}"): GeneratedPuzzle =
        generate(choose(seed, tier), seed, tier, name)

    /** A board for an explicit picture — Daily chooses its own — at [tier]'s difficulty. */
    fun generate(variant: ShapeVariant, seed: Long, tier: EndlessTier, name: String): GeneratedPuzzle {
        val board = ShapePuzzleGenerator.generate(
            mask = variant.mask,
            seed = SeededRandom.derive(seed, tier.ordinal.toLong(), BOARD_SALT),
            profile = ShapeTierProfile.of(tier),
            name = name
        )
        return GeneratedPuzzle(
            level = board.level,
            seed = seed,
            tier = tier,
            solution = board.solution,
            metrics = requireNotNull(PuzzleMetrics.measure(board.level)) { "a generated board must be solvable" },
            attempts = board.attempts,
            onTier = board.onProfile,
            shape = ShapeIdentity(
                templateId = variant.template.id,
                name = variant.template.name,
                mirrored = variant.mirrored,
                quarterTurns = variant.quarterTurns
            )
        )
    }

    private const val CHOICE_SALT = 0x53484150L // "SHAP"
    private const val BOARD_SALT = 0x4152524cL // "ARRL"
}
