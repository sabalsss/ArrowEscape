package com.sabalapps.arrowescape.ui.world

import com.sabalapps.arrowescape.endless.SeededRandom
import com.sabalapps.arrowescape.ui.GameMode

/**
 * Which world a board is played in.
 *
 * Every answer here is a pure function of numbers the game already has, so
 * nothing about a world is stored and nothing can drift: reopening a board — a
 * campaign level, an endless puzzle restored after process death, the same day's
 * daily — always puts it back in the same environment. There is deliberately no
 * `Random` anywhere in this file; the one derivation that needs mixing uses
 * [SeededRandom], which is reproducible by construction.
 *
 * Derivation rather than storage is also what keeps the save formats alone.
 * Endless already persists its puzzle number and Daily its date; a world is a
 * function of those, so there is nothing new to write and no new field to
 * range-check on the way back in. It also means no `GENERATOR_VERSION` bump is
 * owed for any of this — a world decides what the board is *painted on*, never
 * which board is generated, so changing a mapping later would re-dress old days
 * without re-cutting them.
 */
object GameWorlds {

    /**
     * Campaign levels per world: 30 levels over the five worlds in
     * [GameWorld.PROGRESSION], so each world covers one fifth of the curve.
     *
     * The boundaries sit at 1–6 Sky Garden, 7–12 Forest, 13–18 Sunset Canyon,
     * 19–24 Crystal Night, 25–30 Cosmic, which keeps the environment changing at
     * a steady pace through the catalogue instead of one world swallowing the
     * long hard stretch at the end.
     */
    const val CAMPAIGN_LEVELS_PER_WORLD = 6

    /**
     * The world for a campaign level id.
     *
     * Level ids are 1-based and persisted, so this is stable for the life of the
     * catalogue. Anything past the last level stays in the last world rather
     * than wrapping, so adding levels later extends Cosmic instead of silently
     * sending level 31 back to Sky Garden.
     */
    fun forCampaignLevel(levelId: Int): GameWorld {
        val block = (levelId - 1).coerceAtLeast(0) / CAMPAIGN_LEVELS_PER_WORLD
        return GameWorld.PROGRESSION[block.coerceAtMost(GameWorld.PROGRESSION.lastIndex)]
    }

    /**
     * The world for an endless puzzle number.
     *
     * Endless has no end, so this cycles rather than clamping: the run keeps
     * travelling instead of settling in Cosmic forever. Derived from the puzzle
     * number — which Endless already carries and already saves — rather than
     * from the seed, because the number is what the player sees, so the
     * environment changing is legible as progress rather than looking arbitrary.
     *
     * One world per puzzle: 1 Sky Garden, 2 Forest, 3 Sunset Canyon, 4 Crystal
     * Night, 5 Cosmic, 6 Sky Garden again. `mod` rather than `%` so a
     * nonsensical non-positive number — which no save can hold, since
     * `EndlessProgress` range-checks it — still lands on a world instead of
     * throwing.
     */
    fun forEndlessPuzzle(puzzleNumber: Int): GameWorld =
        GameWorld.PROGRESSION[(puzzleNumber - 1).mod(GameWorld.PROGRESSION.size)]

    /**
     * The world for a daily puzzle, from the seed the day already has.
     *
     * Daily reuses the same five worlds rather than owning a sixth: one more
     * piece of artwork for one board a day is not a trade worth making, and the
     * day is made identifiable by a restrained golden treatment layered *over*
     * whichever world it landed in (see [WorldStyle.daily]) instead of by its
     * own backdrop.
     *
     * The seed is read, never written: this does not touch
     * [com.sabalapps.arrowescape.daily.DailyChallenge]'s derivation, so no
     * version bump is involved and the day's board is untouched. It goes through
     * [SeededRandom.derive] with a salt of its own so the world is not
     * correlated with the tier draw — which is salted off the same seed — and a
     * run of Hard days does not turn into a run of the same backdrop.
     *
     * Deriving from the seed rather than from the date means consecutive days
     * wander rather than marching through the five in order; the date is equally
     * stable and would have done, but a predictable Monday-is-always-Sky-Garden
     * rotation makes the day's puzzle feel like a calendar slot rather than
     * somewhere new.
     */
    fun forDailySeed(seed: Long): GameWorld {
        val mixed = SeededRandom.derive(seed, DAILY_WORLD_SALT)
        return GameWorld.PROGRESSION[mixed.mod(GameWorld.PROGRESSION.size)]
    }

    /**
     * The world for the board on screen.
     *
     * [levelId] is the current board's level id, which only Campaign reads — a
     * generated board carries `PuzzleGenerator.ENDLESS_LEVEL_ID`, so the other
     * modes answer from their own identity instead.
     *
     * The tutorial is Sky Garden on purpose: it is the first board the player
     * ever sees, it is Level 1's layout, and the lesson is the only thing on
     * screen that should be competing for attention.
     */
    fun forMode(mode: GameMode, levelId: Int): GameWorld = when (mode) {
        GameMode.Campaign -> forCampaignLevel(levelId)
        is GameMode.Endless -> forEndlessPuzzle(mode.puzzleNumber)
        is GameMode.Daily -> forDailySeed(mode.seed)
        GameMode.Tutorial -> GameWorld.SKY_GARDEN
    }

    /** Keeps the world draw out of step with the daily tier draw. */
    private const val DAILY_WORLD_SALT = 0x57524C44L // "WRLD"
}
