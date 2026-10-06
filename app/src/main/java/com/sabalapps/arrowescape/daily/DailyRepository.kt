package com.sabalapps.arrowescape.daily

import android.content.Context
import com.sabalapps.arrowescape.game.GameState
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.progress.SharedPrefsProgressStore
import com.sabalapps.arrowescape.time.DateProvider
import com.sabalapps.arrowescape.time.GameDate
import com.sabalapps.arrowescape.time.SystemDateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Daily Challenge's saved state: the streak counters, and the board the
 * player is part-way through today.
 *
 * ## Isolation
 *
 * This is the third independent save, alongside campaign and endless, and it is
 * the most isolated of the three: it lives in its own preferences file
 * ([SharedPrefsProgressStore.DAILY_NAME]) rather than merely under its own keys.
 * Nothing here reads or writes campaign, endless or settings storage, so a
 * corrupt daily save — a bad version, a date that is not a date, an impossible
 * life count — can reset the Daily Challenge and nothing else. `DailyRepository`
 * does not even have a reference it could use to reach the others.
 *
 * ## Dates
 *
 * Every date-dependent decision goes through [dates], never the clock directly.
 * See [DateProvider] for why, including the note on what a player with a
 * movable device clock can do to their own streak.
 */
class DailyRepository(
    private val store: ProgressStore,
    private val dates: DateProvider
) {

    private val _progress = MutableStateFlow(readProgress())
    val progress: StateFlow<DailyProgress> = _progress.asStateFlow()

    private val _savedGame = MutableStateFlow(readSavedGame())

    /** The unfinished daily board, or null. Never yesterday's — see [loadInProgress]. */
    val savedGame: StateFlow<DailySavedGame?> = _savedGame.asStateFlow()

    /** Today, as the rest of the app should ask for it. */
    fun today(): GameDate = dates.today()

    /** True when today's puzzle is already cleared. */
    fun isTodayCompleted(): Boolean = _progress.value.isCompletedOn(today())

    // ---- progression --------------------------------------------------------

    /**
     * Records [date]'s puzzle as cleared. Idempotent per date: calling it again
     * for the same day is a no-op, which is what makes Replay free. The streak
     * rules live in [DailyProgress.completing].
     */
    fun markCompleted(date: GameDate = today()) {
        val next = _progress.value.completing(date)
        if (next == _progress.value) return
        writeProgress(next)
    }

    // ---- the unfinished board ------------------------------------------------

    /**
     * The board to restore, or null.
     *
     * A save from an earlier day is *not* restored. Yesterday's board is a
     * different puzzle from today's — the whole point of a daily is that the
     * date picks the board — so handing it back under today's heading would be
     * showing the player the wrong puzzle and letting them complete the wrong
     * day. A stale board is discarded instead, which costs an unfinished puzzle
     * the player cannot get back to anyway.
     */
    fun loadInProgress(): DailySavedGame? {
        val saved = _savedGame.value ?: return null
        if (saved.date != today()) {
            clearInProgress()
            return null
        }
        return saved
    }

    fun saveInProgress(game: DailySavedGame) {
        _savedGame.value = game
        store.putString(
            KEY_SAVED_GAME,
            listOf(
                SAVE_VERSION.toString(),
                game.date.iso,
                game.seed.toString(),
                game.lives.toString(),
                game.remainingArrowIds.sorted().joinToString(",")
            ).joinToString(FIELD)
        )
    }

    fun clearInProgress() {
        _savedGame.value = null
        store.putString(KEY_SAVED_GAME, null)
    }

    // ---- serialisation -------------------------------------------------------

    private fun readSavedGame(): DailySavedGame? {
        val raw = store.getString(KEY_SAVED_GAME) ?: return null
        val saved = parseSavedGame(raw)
        if (saved == null) store.putString(KEY_SAVED_GAME, null)
        return saved
    }

    /**
     * Anything that does not describe a board that could exist is treated as
     * absent: a bad version, a date that is not a real day, an unparseable
     * seed, an impossible life count, an empty arrow set, or a seed that is not
     * the one that date derives to under the current
     * [DailyChallenge.GENERATOR_VERSION]. Whether the ids belong to that
     * board is checked by the caller, which is the only place that has it.
     */
    private fun parseSavedGame(raw: String): DailySavedGame? {
        val parts = raw.split(FIELD)
        if (parts.size != 5) return null
        if (parts[0].toIntOrNull() != SAVE_VERSION) return null

        val date = GameDate.parse(parts[1]) ?: return null
        val seed = parts[2].toLongOrNull() ?: return null
        // The scheme version is mixed into every seed, so a mismatch means this
        // save was written against a different daily scheme and the board it
        // describes is not the board this date builds any more.
        if (seed != DailyChallenge.seedFor(date)) return null
        val lives = parts[3].toIntOrNull()?.takeIf { it in 1..GameState.STARTING_LIVES } ?: return null
        val ids = parseIds(parts[4]) ?: return null
        if (ids.isEmpty()) return null

        return DailySavedGame(date = date, seed = seed, remainingArrowIds = ids, lives = lives)
    }

    private fun readProgress(): DailyProgress {
        val raw = store.getString(KEY_PROGRESS) ?: return DailyProgress()
        val parsed = parseProgress(raw)
        if (parsed == null) {
            // Corrupt: start the Daily Challenge over, and drop the board saved
            // against it. No other mode's keys are touched, and no other mode's
            // keys are even in this file.
            store.putString(KEY_PROGRESS, null)
            store.putString(KEY_SAVED_GAME, null)
            return DailyProgress()
        }
        return parsed
    }

    private fun parseProgress(raw: String): DailyProgress? {
        val parts = raw.split(FIELD)
        if (parts.size != 5) return null
        if (parts[0].toIntOrNull() != SAVE_VERSION) return null

        // An empty field is "never completed a daily", which is a valid state;
        // a non-empty field that is not a date is corruption.
        val last = if (parts[1].isEmpty()) null else GameDate.parse(parts[1]) ?: return null
        val streak = parts[2].toIntOrNull()?.takeIf { it in 0..DailyProgress.MAX_COUNT } ?: return null
        val best = parts[3].toIntOrNull()?.takeIf { it in 0..DailyProgress.MAX_COUNT } ?: return null
        val total = parts[4].toIntOrNull()?.takeIf { it in 0..DailyProgress.MAX_COUNT } ?: return null

        // A streak with no day behind it, or a total smaller than the streak it
        // would have taken to build, is a save written by a bug rather than by
        // play. Neither is recoverable, so it is treated as corruption.
        if (last == null && (streak > 0 || total > 0)) return null
        if (last != null && (streak == 0 || total == 0)) return null
        if (total < streak) return null

        return DailyProgress(
            lastCompletedDate = last,
            currentStreak = streak,
            // A best under the live streak would be a save written by a bug;
            // take the larger rather than report a number already beaten.
            bestStreak = maxOf(best, streak),
            totalCompleted = total
        )
    }

    private fun writeProgress(next: DailyProgress) {
        store.putString(
            KEY_PROGRESS,
            listOf(
                SAVE_VERSION.toString(),
                next.lastCompletedDate?.iso ?: "",
                next.currentStreak.toString(),
                next.bestStreak.toString(),
                next.totalCompleted.toString()
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
        private const val SAVE_VERSION = 1
        private const val FIELD = "|"
        private const val KEY_PROGRESS = "daily_progress"
        private const val KEY_SAVED_GAME = "daily_game"

        @Volatile
        private var instance: DailyRepository? = null

        /** One repository per process, for the same reason the other two have one. */
        fun get(context: Context): DailyRepository =
            instance ?: synchronized(this) {
                instance ?: DailyRepository(
                    store = SharedPrefsProgressStore(context, SharedPrefsProgressStore.DAILY_NAME),
                    dates = SystemDateProvider()
                ).also { instance = it }
            }
    }
}
