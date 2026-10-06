package com.sabalapps.arrowescape.ui.world

/**
 * The visual environment a board is played in.
 *
 * A world is **presentation only**. It chooses the artwork behind the board, the
 * tint of the board surface and the strength of the scrim that keeps the HUD
 * readable — and nothing else. The rules, the generator, the arrow language, the
 * HUD structure and every save format are identical in all five, which is what
 * lets the one [com.sabalapps.arrowescape.ui.GameScreen] render all of them and
 * lets a world be *derived* from information the game already has rather than
 * stored.
 *
 * This file is deliberately pure Kotlin: no Compose, no Android, no `R`. The
 * drawable and the colours live in [WorldStyle], so [GameWorlds]' selection
 * logic — the part with rules worth testing — runs on a plain JVM like the rest
 * of the game's logic does.
 *
 * [id] is a stable string so a world can be written down — in a log line, or in
 * a save file if a later phase ever needs one — without depending on the
 * declaration order of this enum. Nothing persists one today, and that is the
 * point: see [GameWorlds].
 *
 * [displayName] is not shown anywhere yet; the HUD names the *mode*, not the
 * world. It exists so the world has a name to be called by in diagnostics and
 * in any later screen that wants to say which environment a board is in.
 */
enum class GameWorld(val id: String, val displayName: String) {

    /** Bright blue sky, soft clouds, floating islands. The beginner world. */
    SKY_GARDEN("sky_garden", "Sky Garden"),

    /** Greens and teal, distant foliage, calm warm light. */
    FOREST("forest", "Forest"),

    /** Warm orange into coral and purple, layered terrain, sunset glow. */
    SUNSET_CANYON("sunset_canyon", "Sunset Canyon"),

    /** Deep blue and purple, restrained glowing crystal forms, light specks. */
    CRYSTAL_NIGHT("crystal_night", "Crystal Night"),

    /** Deep navy and indigo, nebula glow, stars. The late-game world. */
    COSMIC("cosmic", "Cosmic");

    companion object {
        /**
         * The five worlds, in the order a run travels through them.
         *
         * Campaign maps blocks of levels onto this list, Endless cycles through
         * it and Daily picks from it, so every mode draws from exactly these
         * five pieces of artwork and there is no sixth asset to author. The
         * order is the campaign's order, which is also roughly light-to-dark —
         * the late game is the night sky.
         */
        val PROGRESSION: List<GameWorld> =
            listOf(SKY_GARDEN, FOREST, SUNSET_CANYON, CRYSTAL_NIGHT, COSMIC)
    }
}
