package com.sabalapps.arrowescape.ui

/**
 * One-shot things worth reacting to with sound and haptics. Kept separate from
 * the game state so the ViewModel stays free of Android dependencies.
 */
sealed interface GameEvent {
    data object ArrowEscaped : GameEvent
    data object MoveBlocked : GameEvent
    data object LevelComplete : GameEvent
    data object GameOver : GameEvent
}
