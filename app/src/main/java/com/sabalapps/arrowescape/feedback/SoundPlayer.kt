package com.sabalapps.arrowescape.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * The five sounds the game can play.
 *
 * Assets are looked up by name at runtime, so the game runs perfectly well with
 * none of them present. To replace a sound, drop a short `.ogg` or `.wav` file
 * into `app/src/main/res/raw/` using exactly these file names — nothing else
 * needs to change.
 *
 * [gain] is the playback trim applied to the asset, which is mastered close to
 * full scale. It is the whole mix balance and the only knob worth touching:
 * `ESCAPE` fires on nearly every tap so it sits well under the one-shot
 * stingers, while `LEVEL_COMPLETE` is the loudest moment in the game.
 *
 * These values are deliberately high. The first cut of the assets was mastered
 * to roughly -21 dBFS and then trimmed again here, which left the sounds
 * inaudible on a phone speaker.
 */
enum class GameSound(val resourceName: String, val gain: Float) {
    ESCAPE("sfx_escape", 0.55f),

    /** The solved shape's outline closing: a tiny rising two-note chime, well under the win stinger. */
    SHAPE_CONFIRM("sfx_shape_confirm", 0.62f),
    BLOCKED("sfx_blocked", 1.0f),
    LEVEL_COMPLETE("sfx_level_complete", 0.95f),
    GAME_OVER("sfx_game_over", 1.0f)
}

/**
 * Thin [SoundPool] wrapper. Every step is optional and failure-tolerant: a
 * missing asset, a failed load or a play() on a released pool must never take
 * the game down.
 */
class SoundPlayer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Sample ids that have finished decoding.
     *
     * [SoundPool.load] is asynchronous: it hands back a sample id immediately
     * but the sample cannot be played until the load actually completes, and
     * playing early fails silently. A sound only enters this map once its
     * load callback reports success, so [play] can never address a sample that
     * is not ready. Written from the load callback and read from the UI
     * thread, hence the concurrent map.
     */
    private val readyIds = ConcurrentHashMap<GameSound, Int>()
    private val pending = mutableMapOf<Int, GameSound>()

    @Volatile
    private var released = false

    private val pool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }.getOrNull()

    init {
        pool?.setOnLoadCompleteListener { _, sampleId, status ->
            val sound = pending.remove(sampleId)
            if (status == LOAD_SUCCESS && sound != null) {
                readyIds[sound] = sampleId
            } else if (sound != null) {
                Log.w(TAG, "Failed to decode ${sound.resourceName} (status=$status)")
            }
        }
        GameSound.entries.forEach { sound ->
            val resId = resourceIdFor(sound)
            if (resId == 0) return@forEach
            // load() returns 0 on failure, which is not a usable sample id.
            val sampleId = runCatching { pool?.load(appContext, resId, 1) }.getOrNull()
            if (sampleId != null && sampleId != 0) {
                pending[sampleId] = sound
            } else {
                Log.w(TAG, "Could not load ${sound.resourceName}")
            }
        }
        if (pending.isEmpty()) {
            Log.i(TAG, "No sound assets found in res/raw; running silently.")
        }
    }

    /** Plays [sound] if it is loaded and the player is enabled. Never throws. */
    fun play(sound: GameSound, enabled: Boolean) {
        if (!enabled || released) return
        val sampleId = readyIds[sound] ?: return
        val volume = MASTER_VOLUME * sound.gain
        runCatching { pool?.play(sampleId, volume, volume, 1, 0, 1f) }
            .onFailure { Log.w(TAG, "Could not play $sound", it) }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { pool?.release() }
        readyIds.clear()
        pending.clear()
    }

    /** 0 when the asset is missing, which the caller treats as "stay silent". */
    private fun resourceIdFor(sound: GameSound): Int = runCatching {
        appContext.resources.getIdentifier(sound.resourceName, "raw", appContext.packageName)
    }.getOrDefault(0)

    private companion object {
        const val TAG = "SoundPlayer"
        const val MAX_STREAMS = 4
        const val LOAD_SUCCESS = 0

        /** Full scale: the per-sound [GameSound.gain] carries the balance. */
        const val MASTER_VOLUME = 1.0f
    }
}
