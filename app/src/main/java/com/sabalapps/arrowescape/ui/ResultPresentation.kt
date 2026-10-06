package com.sabalapps.arrowescape.ui

import com.sabalapps.arrowescape.game.GameStatus
import com.sabalapps.arrowescape.ui.discovery.RevealSchedule

/**
 * When the result layer is up, and what it leaves of the screen behind it.
 *
 * Pure Kotlin, like [boardPresentationFor], so the policy is testable on a plain
 * JVM. Presentation only: nothing here touches the board, the state or a save.
 */

/**
 * True while a result — a win's discovery/completion or a loss's Game Over — covers
 * the screen. A win waits for its celebration ([celebrationFinished]); a loss shows
 * at once, because nothing is being celebrated.
 */
internal fun isResultShowing(status: GameStatus, celebrationFinished: Boolean): Boolean = when (status) {
    GameStatus.WON -> celebrationFinished
    GameStatus.LOST -> true
    GameStatus.PLAYING -> false
}

/**
 * Whether the gameplay chrome — HUD, board, footer, Hint — is part of the screen.
 *
 * It is not while a result is up. The result's scrim is translucent so the world
 * artwork stays visible behind it, and anything left underneath would show through
 * as a faded ghost of the controls. The world itself is not chrome and stays; the
 * board's state lives in the ViewModel and is not affected by the chrome leaving.
 *
 * A caller that drops the chrome from composition also drops it from the
 * accessibility tree and from touch, which is the point: nothing behind a result
 * should be focusable or tappable.
 */
internal fun gameplayChromeVisible(resultShowing: Boolean): Boolean = !resultShowing

/**
 * How long the result layer takes to arrive, in ms: the span its scrim fades in over.
 * The gameplay chrome — and the confirmation outline drawn inside it — leaves over the same
 * span, so it is never visible under a fully raised scrim and the bare world is never exposed
 * in between.
 *
 * A discovery reveal brings its scrim up on the reveal's own clock and a shape reveal on its own
 * faster one; everything else rises over 300ms, or 120ms when motion is reduced.
 */
internal fun resultEnterMs(kind: CompletionKind, reducedMotion: Boolean): Int = when {
    kind == CompletionKind.Discovery -> RevealSchedule.forMotion(reducedMotion).scrimMs
    kind == CompletionKind.Shape -> ShapeRevealSchedule.forMotion(reducedMotion).scrimMs
    reducedMotion -> 120
    else -> 300
}
