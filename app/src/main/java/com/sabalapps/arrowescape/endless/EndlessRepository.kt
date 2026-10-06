package com.sabalapps.arrowescape.endless

import android.content.Context
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.progress.SharedPrefsProgressStore
import com.sabalapps.arrowescape.shape.MysteryShapePuzzles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Endless Mode's saved state: how far the player has got, and the generated
 * board they are part-way through.
 *
 * Kept deliberately separate from `ProgressRepository`. The two write different
 * keys, parse independently and fall back independently, so a corrupt endless
 * save can only ever cost the player an endless puzzle — campaign progress is
 * not read, not rewritten and not reachable from here. The reverse holds too:
 * generated boards carry [PuzzleGenerator.ENDLESS_LEVEL_ID], which no catalogue
 * lookup matches, so an endless board cannot be restored as a campaign level.
 *
 * Storage is the same hand-parsed delimited format the campaign uses, for the
 * same reason: every field is range checked on the way back in, and anything
 * that fails a check is discarded rather than guessed at.
 */
class EndlessRepository(
    private val store: ProgressStore,
    /**
     * Where the seed for a brand new puzzle comes from. Injected so tests can
     * pin it; in the app it is the clock, which is all the entropy a local,
     * single-player puzzle seed needs.
     */
    private val newSeed: () -> Long = { SeededRandom.derive(System.nanoTime(), SEED_SALT) }
) {

    private val _progress = MutableStateFlow(readProgress())
    val progress: StateFlow<EndlessProgress> = _progress.asStateFlow()

    private val _savedGame = MutableStateFlow(readSavedGame())

    /** The unfinished endless board, or null. Drives Home's Endless subtitle. */
    val savedGame: StateFlow<EndlessSavedGame?> = _savedGame.asStateFlow()

    /** A seed for a puzzle that has not been generated yet. */
    fun nextSeed(): Long = newSeed()

    // ---- progression --------------------------------------------------------

    /**
     * Records a cleared puzzle: the count and the streak go up, and the next
     * puzzle number — which is what picks the next tier — comes into view.
     */
    fun markCompleted() {
        val current = _progress.value
        val streak = current.currentStreak + 1
        writeProgress(
            current.copy(
                puzzleNumber = (current.puzzleNumber + 1).coerceAtMost(EndlessProgress.MAX_PUZZLE),
                totalCompleted = current.totalCompleted + 1,
                currentStreak = streak,
                bestStreak = maxOf(current.bestStreak, streak)
            )
        )
    }

    /**
     * Records running out of lives. The puzzle number does not move — the player
     * gets another go at the same tier — but the streak is over.
     */
    fun markFailed() {
        val current = _progress.value
        if (current.currentStreak == 0) return
        writeProgress(current.copy(currentStreak = 0))
    }

    // ---- the unfinished board ------------------------------------------------

    fun loadInProgress(): EndlessSavedGame? = _savedGame.value

    fun saveInProgress(game: EndlessSavedGame) {
        _savedGame.value = game
        store.putString(
            KEY_SAVED_GAME,
            listOf(
                GAME_VERSION.toString(),
                game.seed.toString(),
                game.puzzleNumber.toString(),
                game.tier.name,
                game.lives.toString(),
                game.remainingArrowIds.sorted().joinToString(","),
                MysteryShapePuzzles.GENERATOR_VERSION.toString()
            ).joinToString(FIELD)
        )
    }

    fun clearInProgress() {
        _savedGame.value = null
        store.putString(KEY_SAVED_GAME, null)
    }

    // ---- serialisation -------------------------------------------------------

    private fun readSavedGame(): EndlessSavedGame? {
        val raw = store.getString(KEY_SAVED_GAME) ?: return null
        val saved = parseSavedGame(raw)
        if (saved == null) store.putString(KEY_SAVED_GAME, null)
        return saved
    }

    /**
     * Anything that does not describe a board that could exist is treated as
     * absent: a bad version, an unparseable seed, a puzzle number outside the
     * possible range, an unknown tier, an impossible life count or an empty
     * arrow set. Whether the ids actually belong to *this* seed's board is
     * checked by the caller, which is the only place that has the board.
     */
    private fun parseSavedGame(raw: String): EndlessSavedGame? {
        val parts = raw.split(FIELD)
        // Version 1 (six fields) was written by the abstract rectangular generator and describes a
        // board this app can no longer build, so it is dropped like any other save it cannot honour.
        // Version 2 names the shape generator it was written against.
        if (parts.size != 7) return null
        if (parts[0].toIntOrNull() != GAME_VERSION) return null
        if (parts[6].toIntOrNull() != MysteryShapePuzzles.GENERATOR_VERSION) return null

        val seed = parts[1].toLongOrNull() ?: return null
        val puzzleNumber = parts[2].toIntOrNull()
            ?.takeIf { it in EndlessProgress.FIRST_PUZZLE..EndlessProgress.MAX_PUZZLE }
            ?: return null
        // An unknown tier name means the board cannot be rebuilt as it was, so
        // the save is dropped rather than silently regenerated at another tier.
        val tier = EndlessTier.entries.firstOrNull { it.name == parts[3] } ?: return null
        val lives = parts[4].toIntOrNull()?.takeIf { it in 1..GameState.STARTING_LIVES } ?: return null
        val ids = parseIds(parts[5]) ?: return null
        if (ids.isEmpty()) return null

        return EndlessSavedGame(
            seed = seed,
            puzzleNumber = puzzleNumber,
            tier = tier,
            remainingArrowIds = ids,
            lives = lives
        )
    }

    private fun readProgress(): EndlessProgress {
        val raw = store.getString(KEY_PROGRESS) ?: return EndlessProgress()
        val parsed = parseProgress(raw)
        if (parsed == null) {
            // Corrupt: start endless over, and drop the board saved against it.
            // Campaign keys are not touched.
            store.putString(KEY_PROGRESS, null)
            store.putString(KEY_SAVED_GAME, null)
            return EndlessProgress()
        }
        return parsed
    }

    private fun parseProgress(raw: String): EndlessProgress? {
        val parts = raw.split(FIELD)
        if (parts.size != 5) return null
        if (parts[0].toIntOrNull() != PROGRESS_VERSION) return null

        val puzzleNumber = parts[1].toIntOrNull()
            ?.takeIf { it in EndlessProgress.FIRST_PUZZLE..EndlessProgress.MAX_PUZZLE }
            ?: return null
        val completed = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val streak = parts[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val best = parts[4].toIntOrNull()?.takeIf { it >= 0 } ?: return null

        return EndlessProgress(
            puzzleNumber = puzzleNumber,
            totalCompleted = completed,
            currentStreak = streak,
            // A best that is under the live streak would be a save written by a
            // bug; take the larger rather than reporting a number the player has
            // already beaten.
            bestStreak = maxOf(best, streak)
        )
    }

    private fun writeProgress(next: EndlessProgress) {
        store.putString(
            KEY_PROGRESS,
            listOf(
                PROGRESS_VERSION.toString(),
                next.puzzleNumber.toString(),
                next.totalCompleted.toString(),
                next.currentStreak.toString(),
                next.bestStreak.toString()
            ).joinToString(FIELD)
        )
        _progress.value = next
    }

    /** A comma separated id list. Null — not empty — when anything is not a number. */
    private fun parseIds(raw: String): Set<Int>? {
        if (raw.isEmpty()) return emptySet()
        val ids = HashSet<Int>()
        for (piece in raw.split(",")) {
            ids += piece.toIntOrNull() ?: return null
        }
        return ids
    }

    companion object {
        /** The counters. Unchanged by the shape generator: no stat is ever wiped by a board change. */
        private const val PROGRESS_VERSION = 1

        /** The in-progress board. 2 = names the generator version (see [MysteryShapePuzzles.GENERATOR_VERSION]). */
        private const val GAME_VERSION = 2
        private const val FIELD = "|"
        private const val KEY_PROGRESS = "endless_progress"
        private const val KEY_SAVED_GAME = "endless_game"

        /** Keeps clock-derived seeds out of step with anything else seeded by the clock. */
        private const val SEED_SALT = 0x4152524f57L // "ARROW"

        @Volatile
        private var instance: EndlessRepository? = null

        /** One repository per process, for the same reason the campaign has one. */
        fun get(context: Context): EndlessRepository =
            instance ?: synchronized(this) {
                instance ?: EndlessRepository(SharedPrefsProgressStore(context)).also {
                    instance = it
                }
            }
    }
}
