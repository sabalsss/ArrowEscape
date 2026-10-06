package com.sabalapps.arrowescape.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsTest {

    @Test
    fun `sound and haptics default to on and theme follows the system`() {
        val defaults = GameSettings()
        assertTrue(defaults.soundEnabled)
        assertTrue(defaults.hapticsEnabled)
        assertEquals(ThemeOption.SYSTEM, defaults.theme)
    }

    @Test
    fun `theme keys round trip`() {
        ThemeOption.entries.forEach { option ->
            assertEquals(option, ThemeOption.fromKey(option.key))
        }
    }

    @Test
    fun `an unknown or missing theme key falls back to system`() {
        assertEquals(ThemeOption.SYSTEM, ThemeOption.fromKey(null))
        assertEquals(ThemeOption.SYSTEM, ThemeOption.fromKey("midnight"))
    }
}
