package com.sabalapps.arrowescape.ui.world

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The world artwork, for the menus.
 *
 * The game screen paints a world at the full 1440×2560 because the board sits
 * over it for minutes at a time. A menu only needs the *impression* of it behind
 * a scrim, and a level grid wants five of them on screen at once — five full
 * decodes is ~74MB of pixels to draw behind some tiles. So menus take a
 * down-sampled decode instead: `BitmapFactory`'s `inSampleSize` does the work at
 * decode time, which is both cheaper to hold and cheaper to draw.
 *
 * It is a dozen lines of platform API rather than an image-loading library, on
 * purpose (§21). Decodes run off the main thread and land in a small byte-bounded
 * [LruCache], so re-entering a menu is free and the cache can never grow past a
 * handful of megabytes however many worlds are visited.
 */
private object WorldArtCache {
    /** ~20MB: five worlds at 1/2 size plus five at 1/4, with room to spare. */
    private const val MAX_KB = 20 * 1024

    private val cache = object : LruCache<Long, ImageBitmap>(MAX_KB) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int =
            value.asAndroidBitmap().byteCount / 1024
    }

    fun key(resId: Int, sampleSize: Int): Long = (resId.toLong() shl 8) or sampleSize.toLong()

    fun peek(key: Long): ImageBitmap? = cache.get(key)

    fun load(context: Context, resId: Int, sampleSize: Int, key: Long): ImageBitmap? {
        cache.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            // nodpi artwork: no density scaling wanted, just the sample.
            inScaled = false
        }
        val bitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, resId, options)
        }.getOrNull() ?: return null
        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }
}

/**
 * Decodes [world]'s menu backdrop into the cache ahead of the first frame that wants it —
 * what the loading screen does for the world Home will be dressed in, so Home opens with its
 * artwork already there instead of fading it in. Blocking: call it off the main thread.
 * Returns whether the artwork is now cached; a failed decode is not an error (the menus
 * degrade to their gradient).
 */
fun warmMenuBackdrop(context: Context, world: GameWorld): Boolean {
    val resId = WorldStyle.of(world).background
    val key = WorldArtCache.key(resId, BACKDROP_SAMPLE)
    return WorldArtCache.load(context.applicationContext, resId, BACKDROP_SAMPLE, key) != null
}

/**
 * The down-sampled artwork for [world], or null for the frames before the decode
 * lands (and for good, if it fails — callers draw a gradient underneath either way).
 *
 * @param sampleSize 2 for a full-screen menu backdrop (720×1280), 4 for a band or
 *   a thumbnail (360×640).
 */
@Composable
fun rememberWorldArt(world: GameWorld, sampleSize: Int): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val resId = WorldStyle.of(world).background
    val key = WorldArtCache.key(resId, sampleSize)
    val art by produceState(initialValue = WorldArtCache.peek(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                WorldArtCache.load(context, resId, sampleSize, key)
            }
        }
    }
    return art
}

/**
 * The world's artwork as a cropped, decorative image: for a band behind a level
 * section, a thumbnail inside a card. It fades in when the decode lands (and not
 * at all when it was already cached), and contributes no semantics.
 *
 * @param muted desaturate it — what a world the player has not reached looks like.
 */
@Composable
fun WorldArtImage(
    world: GameWorld,
    modifier: Modifier = Modifier,
    sampleSize: Int = 4,
    muted: Boolean = false,
    alignment: Alignment = Alignment.Center
) {
    val art = rememberWorldArt(world, sampleSize)
    // Fades in only when the decode arrives late; a cached image is just there,
    // so scrolling a band back into view never flickers.
    val arrivedLate = remember(world, sampleSize) { art == null }
    var shown by remember(world, sampleSize) { mutableStateOf(!arrivedLate) }
    LaunchedEffect(art != null) { if (art != null) shown = true }
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(240), label = "worldArtFade")
    if (art == null) return
    Image(
        bitmap = art,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        colorFilter = if (muted) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.25f) })
        } else {
            null
        },
        modifier = modifier.alpha(fade)
    )
}
