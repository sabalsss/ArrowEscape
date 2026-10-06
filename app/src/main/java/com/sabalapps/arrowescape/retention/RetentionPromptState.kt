package com.sabalapps.arrowescape.retention

import android.content.Context
import com.sabalapps.arrowescape.progress.ProgressStore
import com.sabalapps.arrowescape.progress.SharedPrefsProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the game remembers about asking the player to rate or share it, and nothing
 * else.
 *
 * ## What this can and cannot know
 *
 * Android does not tell an app whether a Play Store review was ever submitted — the
 * in-app review flow reports only that it *finished*, and a tap on a link to the listing
 * tells us nothing about what happened on the other side. So there is deliberately no
 * `hasRated` here. The names say what actually happened:
 *
 *  - [ratePromptShown] — the dialog was put in front of the player at least once;
 *  - [rateActionTapped] — the player pressed the Rate button. It means *they acted on the
 *    request*, not that a review exists, and it is what ends proactive rate prompting;
 *  - [shareActionTapped] — the same for Share, which stops Share being offered again;
 *  - [promptCount], [lastPromptAtMs], [lastPromptCompletionCount] — how often, and when
 *    (wall-clock time and how many Campaign levels were cleared), the player was last asked.
 */
data class RetentionPromptState(
    val promptCount: Int = 0,
    val lastPromptAtMs: Long = 0L,
    val lastPromptCompletionCount: Int = 0,
    val ratePromptShown: Boolean = false,
    val rateActionTapped: Boolean = false,
    val shareActionTapped: Boolean = false
)

/**
 * The prompt record, in a file of its own beside the daily reminder's preference. Same
 * style as the other repositories: a short delimited string, range-checked on the way in,
 * and anything that fails a check is read as "never asked" — which errs towards asking
 * once more, never towards asking more often than the policy allows.
 */
class RetentionPromptRepository(private val store: ProgressStore) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<RetentionPromptState> = _state.asStateFlow()

    /** The player was just shown the prompt, when [completionCount] Campaign levels were cleared. */
    fun recordPromptShown(nowMs: Long, completionCount: Int) {
        write(
            _state.value.copy(
                promptCount = (_state.value.promptCount + 1).coerceAtMost(MAX_COUNT),
                lastPromptAtMs = nowMs.coerceAtLeast(0L),
                lastPromptCompletionCount = completionCount.coerceIn(0, MAX_COUNT),
                ratePromptShown = true
            )
        )
    }

    /** The Rate button was pressed. Ends proactive rate prompts for good. */
    fun recordRateActionTapped() {
        if (_state.value.rateActionTapped) return
        write(_state.value.copy(rateActionTapped = true))
    }

    /** The Share button was pressed. Share is not offered again. */
    fun recordShareActionTapped() {
        if (_state.value.shareActionTapped) return
        write(_state.value.copy(shareActionTapped = true))
    }

    private fun write(next: RetentionPromptState) {
        _state.value = next
        store.putString(
            KEY,
            listOf(
                VERSION.toString(),
                next.promptCount.toString(),
                next.lastPromptAtMs.toString(),
                next.lastPromptCompletionCount.toString(),
                flag(next.ratePromptShown),
                flag(next.rateActionTapped),
                flag(next.shareActionTapped)
            ).joinToString(FIELD)
        )
    }

    private fun read(): RetentionPromptState {
        val raw = store.getString(KEY) ?: return RetentionPromptState()
        val parsed = parse(raw)
        if (parsed == null) store.putString(KEY, null)
        return parsed ?: RetentionPromptState()
    }

    private fun parse(raw: String): RetentionPromptState? {
        val parts = raw.split(FIELD)
        if (parts.size != 7) return null
        if (parts[0].toIntOrNull() != VERSION) return null
        val count = parts[1].toIntOrNull()?.takeIf { it in 0..MAX_COUNT } ?: return null
        val lastAt = parts[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val lastCompletions = parts[3].toIntOrNull()?.takeIf { it in 0..MAX_COUNT } ?: return null
        return RetentionPromptState(
            promptCount = count,
            lastPromptAtMs = lastAt,
            lastPromptCompletionCount = lastCompletions,
            ratePromptShown = parseFlag(parts[4]) ?: return null,
            rateActionTapped = parseFlag(parts[5]) ?: return null,
            shareActionTapped = parseFlag(parts[6]) ?: return null
        )
    }

    companion object {
        private const val VERSION = 1
        private const val FIELD = "|"
        private const val KEY = "retention_prompts"
        private const val MAX_COUNT = 100_000

        private fun flag(value: Boolean) = if (value) "1" else "0"

        private fun parseFlag(raw: String): Boolean? = when (raw) {
            "1" -> true
            "0" -> false
            else -> null
        }

        @Volatile
        private var instance: RetentionPromptRepository? = null

        fun get(context: Context): RetentionPromptRepository =
            instance ?: synchronized(this) {
                instance ?: RetentionPromptRepository(
                    SharedPrefsProgressStore(context, SharedPrefsProgressStore.RETENTION_NAME)
                ).also { instance = it }
            }
    }
}
