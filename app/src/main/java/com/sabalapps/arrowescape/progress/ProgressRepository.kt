package com.sabalapps.arrowescape.progress

import android.content.Context
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.game.Level
import com.sabalapps.arrowescape.game.LevelProgression
import com.sabalapps.arrowescape.game.Levels
import com.sabalapps.arrowescape.game.StarRating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The player's saved progress: which levels are unlocked and completed, and the
 * board of the level they are part-way through.
 *
 * Storage is a handful of short delimited strings rather than JSON — there is
 * nothing here that needs a parser, and hand-parsing lets every field be range
 * checked on the way back in. Anything that fails a check is discarded and the
 * caller gets a clean default, so a corrupt or hand-edited save can never load
 * a broken board.
 */
class ProgressRepository(private val store: ProgressStore) {

    private val _progress = MutableStateFlow(readProgress())
    val progress: StateFlow<PlayerProgress> = _progress.asStateFlow()

    private val _savedGame = MutableStateFlow(readSavedGame())

    /**
     * The unfinished board, or null. Read from disk once at startup and kept in
     * step from then on, so the UI can observe it without touching storage on
     * every recomposition.
     */
    val savedGame: StateFlow<SavedGame?> = _savedGame.asStateFlow()

    // ---- unlocking and selection -------------------------------------------

    fun isUnlocked(levelId: Int): Boolean = _progress.value.isUnlocked(levelId)

    /** Remembers [levelId] as the level the player is on. Unlocked levels only. */
    fun selectLevel(levelId: Int) {
        if (!isUnlocked(levelId)) return
        writeProgress(_progress.value.copy(currentLevel = levelId))
    }

    /**
     * Records a win: the level joins the completed set, the next one unlocks,
     * and selection moves on to it — so Continue and the grid's highlight both
     * point at what the player should play next rather than what they just
     * finished. On the last level there is nothing after it, so selection stays.
     *
     * [stars] is this run's result, 1-3, or [StarRating.NONE] when the caller is
     * not rating the run. It is recorded only when it *beats* what is already
     * there: replaying a three-star level and making a mistake must not take the
     * third star away, which is the whole point of the record being a best
     * rather than a last.
     */
    fun markCompleted(levelId: Int, stars: Int = StarRating.NONE) {
        if (Levels.byId(levelId) == null) return
        val current = _progress.value
        val nextLevel = minOf(levelId + 1, LevelProgression.count)
        writeProgress(
            current.copy(
                completedLevels = current.completedLevels + levelId,
                highestUnlockedLevel = maxOf(current.highestUnlockedLevel, nextLevel),
                currentLevel = nextLevel,
                bestStars = withBestStars(current.bestStars, levelId, stars)
            )
        )
    }

    /** The player's best run on [levelId], or [StarRating.NONE]. */
    fun starsFor(levelId: Int): Int = _progress.value.starsFor(levelId)

    /**
     * The stars map with [levelId] raised to [stars] if that is an improvement,
     * and returned untouched otherwise — so a downgrade is a no-op rather than
     * a write.
     */
    private fun withBestStars(
        existing: Map<Int, Int>,
        levelId: Int,
        stars: Int
    ): Map<Int, Int> {
        if (!StarRating.isValid(stars)) return existing
        if (stars <= (existing[levelId] ?: StarRating.NONE)) return existing
        return existing + (levelId to stars)
    }

    /** The level the player should land on when they press Continue. */
    fun currentLevel(): Level = LevelProgression.levelOrFirst(_progress.value.currentLevel)

    // ---- the unfinished board ----------------------------------------------

    /**
     * The level in progress, or null when there is nothing to restore. Anything
     * that does not describe a board that could actually exist — unknown level,
     * arrow ids that are not in that level, an impossible life count, an already
     * empty board, or a board saved against a different set of layouts (see
     * [Levels.LAYOUT_VERSION]) — is treated as absent and wiped.
     */
    fun loadInProgress(): SavedGame? = _savedGame.value

    private fun readSavedGame(): SavedGame? {
        val raw = store.getString(KEY_SAVED_GAME) ?: return null
        val saved = parseSavedGame(raw)
        if (saved == null) store.putString(KEY_SAVED_GAME, null)
        return saved
    }

    fun saveInProgress(game: SavedGame) {
        _savedGame.value = game
        store.putString(
            KEY_SAVED_GAME,
            listOf(
                SAVE_VERSION.toString(),
                game.levelId.toString(),
                game.lives.toString(),
                game.remainingArrowIds.sorted().joinToString(","),
                game.blockedTaps.toString(),
                if (game.hintUsed) "1" else "0",
                Levels.LAYOUT_VERSION.toString()
            ).joinToString(FIELD)
        )
    }

    fun clearInProgress() {
        _savedGame.value = null
        store.putString(KEY_SAVED_GAME, null)
    }

    // ---- serialisation ------------------------------------------------------

    /**
     * Version 3 appends the layout version the board was saved against.
     *
     * A saved board is only a list of surviving arrow ids, and an id is a
     * position in a layout, so a board saved against one set of layouts must not
     * be restored onto another: the ids would parse, pass every range check
     * below, and quietly name different arrows. Discovery Phase 2 redrew all
     * thirty Campaign layouts, so this record now carries [Levels.LAYOUT_VERSION]
     * and a save written against any other value is discarded.
     *
     * Versions 1 and 2 predate the field, which makes them layout version 1 by
     * definition — the abstract arrangements that no longer exist — so they are
     * discarded too, and the level they were part-way through simply starts
     * again. That costs a player at most one unfinished board. It deliberately
     * does not touch [parseProgress]: completed levels, stars and the unlock
     * ceiling are keyed by level id, and level ids did not change.
     */
    private fun parseSavedGame(raw: String): SavedGame? {
        val parts = raw.split(FIELD)
        val version = parts[0].toIntOrNull() ?: return null
        if (version != SAVE_VERSION) return null
        if (parts.size != SAVE_FIELDS) return null

        val layoutVersion = parts[6].toIntOrNull() ?: return null
        if (layoutVersion != Levels.LAYOUT_VERSION) return null

        val levelId = parts[1].toIntOrNull() ?: return null
        val level = Levels.byId(levelId) ?: return null

        val lives = parts[2].toIntOrNull() ?: return null
        if (lives !in 1..GameState.STARTING_LIVES) return null

        val ids = parseIds(parts[3]) ?: return null
        // An empty board is a finished level, not one to resume; ids that are not
        // in this level mean the save no longer matches the catalogue.
        if (ids.isEmpty()) return null
        val known = level.arrows.mapTo(HashSet()) { it.id }
        if (!known.containsAll(ids)) return null

        val blockedTaps = parts[4].toIntOrNull()?.takeIf { it in 0..MAX_BLOCKED_TAPS } ?: return null
        val hintUsed = when (parts[5]) {
            "0" -> false
            "1" -> true
            else -> return null
        }

        return SavedGame(
            levelId = levelId,
            remainingArrowIds = ids,
            lives = lives,
            blockedTaps = blockedTaps,
            hintUsed = hintUsed
        )
    }

    private fun readProgress(): PlayerProgress {
        val raw = store.getString(KEY_PROGRESS) ?: return PlayerProgress()
        val parsed = parseProgress(raw)
        if (parsed == null) {
            // Corrupt: start over rather than guess, and drop any board that was
            // saved against it.
            store.putString(KEY_PROGRESS, null)
            store.putString(KEY_SAVED_GAME, null)
            return PlayerProgress()
        }
        return parsed
    }

    /**
     * Version 2 appends the per-level star record. A version 1 record is read
     * exactly as it always was and comes back with no stars, so an upgrading
     * player keeps every unlock and completion and simply has no stars on the
     * levels they cleared before the feature existed. Ids are unchanged, which
     * is what makes that possible.
     */
    private fun parseProgress(raw: String): PlayerProgress? {
        val parts = raw.split(FIELD)
        val version = parts[0].toIntOrNull() ?: return null
        val expectedFields = when (version) {
            LEGACY_VERSION -> 4
            PROGRESS_VERSION -> 5
            else -> return null
        }
        if (parts.size != expectedFields) return null

        val total = LevelProgression.count
        val highest = parts[1].toIntOrNull()?.takeIf { it in 1..total } ?: return null
        val current = parts[2].toIntOrNull()?.takeIf { it in 1..total } ?: return null
        val completed = parseIds(parts[3]) ?: return null
        if (completed.any { it !in 1..total }) return null

        val stars = if (version == PROGRESS_VERSION) {
            parseStars(parts[4], total) ?: return null
        } else {
            emptyMap()
        }

        return PlayerProgress(
            highestUnlockedLevel = highest,
            // A selected level above the unlocked ceiling would let the player
            // resume something they should not have; pull it back instead.
            currentLevel = minOf(current, highest),
            completedLevels = completed,
            // A star against a level the player has not cleared describes a run
            // that cannot have happened, so it is dropped rather than shown.
            bestStars = stars.filterKeys { it in completed }
        )
    }

    private fun writeProgress(next: PlayerProgress) {
        store.putString(
            KEY_PROGRESS,
            listOf(
                PROGRESS_VERSION.toString(),
                next.highestUnlockedLevel.toString(),
                next.currentLevel.toString(),
                next.completedLevels.sorted().joinToString(","),
                next.bestStars.entries
                    .sortedBy { it.key }
                    .joinToString(",") { "${it.key}${PAIR}${it.value}" }
            ).joinToString(FIELD)
        )
        _progress.value = next
    }

    /**
     * `id:stars` pairs. Null — not empty — when anything is not a number, out of
     * the catalogue or not a rating a run could have earned.
     */
    private fun parseStars(raw: String, total: Int): Map<Int, Int>? {
        if (raw.isEmpty()) return emptyMap()
        val stars = HashMap<Int, Int>()
        for (piece in raw.split(",")) {
            val pair = piece.split(PAIR)
            if (pair.size != 2) return null
            val levelId = pair[0].toIntOrNull()?.takeIf { it in 1..total } ?: return null
            val value = pair[1].toIntOrNull()?.takeIf { StarRating.isValid(it) } ?: return null
            if (stars.put(levelId, value) != null) return null
        }
        return stars
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
        /**
         * The two records are numbered separately because they are independent
         * formats. The progress record is version 2 (Phase 6E added the star
         * record and still reads the version 1 it replaces — see [parseProgress]).
         * The in-progress board is version 3 (Discovery Phase 2 added the layout
         * version and reads *no* earlier record — see [parseSavedGame]).
         */
        private const val PROGRESS_VERSION = 2
        private const val SAVE_VERSION = 3

        /** Fields in a version 3 board record. */
        private const val SAVE_FIELDS = 7

        /** A progress record written before stars existed. Read, never written. */
        private const val LEGACY_VERSION = 1

        /**
         * A sanity ceiling on the run tally coming back from disk. The rules
         * bound it far tighter — the third blocked tap ends the board — so
         * anything near this is a hand-edited preference, not a run.
         */
        private const val MAX_BLOCKED_TAPS = 99

        private const val FIELD = "|"

        /** Separates a level id from its star count inside the stars field. */
        private const val PAIR = ":"
        private const val KEY_PROGRESS = "player_progress"
        private const val KEY_SAVED_GAME = "saved_game"

        @Volatile
        private var instance: ProgressRepository? = null

        /**
         * One repository per process. Two instances over the same preferences
         * would each hold their own copy of the progress state and drift apart
         * across a configuration change.
         */
        fun get(context: Context): ProgressRepository =
            instance ?: synchronized(this) {
                instance ?: ProgressRepository(SharedPrefsProgressStore(context)).also {
                    instance = it
                }
            }
    }
}
