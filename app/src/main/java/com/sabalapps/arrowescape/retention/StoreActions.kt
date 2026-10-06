package com.sabalapps.arrowescape.retention

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * The Android side of Rate and Share: launching Google's review flow, opening the listing,
 * and sending the system share sheet.
 *
 * ## What the review flow can and cannot tell us
 *
 * Google decides whether the in-app review card appears at all (it is quota-limited), and the
 * API never says whether the player rated, how, or whether the card was shown. The flow
 * "completes" the same way in every case. So nothing here ever claims a rating happened, and
 * the one thing that is guarded against is the player pressing Rate and *seeing nothing*:
 * when the flow cannot be started, or finishes too quickly to have been on screen
 * ([ReviewOutcome]), the Play listing is opened instead — which is what pressing a button that
 * says "Rate" promises.
 */
object StoreActions {

    private const val TAG = "ArrowEscapeStore"

    /** Asks Google's in-app review flow to run, falling back to the Play listing. */
    fun rate(activity: Activity) {
        val manager = runCatching { ReviewManagerFactory.create(activity) }.getOrNull()
        if (manager == null) {
            openListing(activity)
            return
        }
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                Log.d(TAG, "review flow unavailable: ${request.exception}")
                openListing(activity)
                return@addOnCompleteListener
            }
            val startedAt = SystemClock.elapsedRealtime()
            manager.launchReviewFlow(activity, request.result).addOnCompleteListener { launch ->
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (ReviewOutcome.shouldOpenListing(launch.isSuccessful, elapsed)) openListing(activity)
            }
        }
    }

    /** The Play Store listing: the Play app if there is one, the web page if not. */
    fun openListing(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(StoreLinks.MARKET_URI))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(StoreLinks.WEB_URL))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** The system share sheet. Always the chooser: nothing is ever shared without the player picking where. */
    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, StoreLinks.SHARE_TEXT)
        val chooser = Intent.createChooser(send, StoreLinks.SHARE_CHOOSER_TITLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure { Log.w(TAG, "no share target", it) }
    }
}

/**
 * Whether a finished review flow should be followed by opening the Play listing. Pure, so the
 * rule is a plain test.
 *
 * The review card is a sheet that has to animate in, be read and be dismissed; a flow that
 * "finishes" within [MIN_VISIBLE_MS] of being launched was not on screen. That is how Google's
 * quota looks from outside, and it is the case the listing is for. A flow that ran longer is
 * left strictly alone — whatever the player did there is theirs.
 */
object ReviewOutcome {
    const val MIN_VISIBLE_MS = 700L

    fun shouldOpenListing(launchSucceeded: Boolean, elapsedMs: Long): Boolean =
        !launchSucceeded || elapsedMs < MIN_VISIBLE_MS
}
