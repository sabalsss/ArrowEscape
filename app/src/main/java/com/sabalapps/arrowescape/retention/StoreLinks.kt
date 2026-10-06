package com.sabalapps.arrowescape.retention

/**
 * Where the game lives on Google Play, and what a friend is sent. One place, so the
 * Rate button, the Share button and the in-app review fallback cannot disagree about it.
 */
object StoreLinks {
    const val PACKAGE_NAME = "com.sabalapps.arrowescape"

    /** The Play app, if it is installed. */
    const val MARKET_URI = "market://details?id=$PACKAGE_NAME"

    /** The listing on the web: the fallback when there is no Play app to open, and what a share carries. */
    const val WEB_URL = "https://play.google.com/store/apps/details?id=$PACKAGE_NAME"

    /** What the Share chooser sends. Short, and says what the game is without spoiling a single shape. */
    const val SHARE_TEXT =
        "I'm playing Arrow Escape — solve mystery arrow shapes and reveal what's hidden!\n$WEB_URL"

    const val SHARE_CHOOSER_TITLE = "Share Arrow Escape"
}
