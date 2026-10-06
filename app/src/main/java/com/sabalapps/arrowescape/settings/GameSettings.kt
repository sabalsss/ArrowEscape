package com.sabalapps.arrowescape.settings

/** Which colour scheme the player has chosen. */
enum class ThemeOption(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(key: String?): ThemeOption =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Everything the player can change. Sound and haptics default to on. */
data class GameSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val theme: ThemeOption = ThemeOption.SYSTEM
)
