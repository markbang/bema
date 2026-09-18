package dev.bema.shared

import dev.bema.shared.data.session.MemosTimelineController
import dev.bema.shared.data.session.ThemeMode
import dev.bema.shared.data.session.canonicalThemeMode
import dev.bema.shared.data.session.themeModeLabel
import dev.bema.shared.data.session.themeModeValue
import dev.bema.shared.data.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferenceTest {
    @Test
    fun canonicalizesUnknownOrBlankValuesToSystem() {
        assertEquals(ThemeMode.LIGHT, canonicalThemeMode(" light "))
        assertEquals(ThemeMode.DARK, canonicalThemeMode("Dark"))
        assertEquals(ThemeMode.SYSTEM, canonicalThemeMode("sepia"))
        assertEquals(ThemeMode.SYSTEM, canonicalThemeMode(null))
    }

    @Test
    fun roundTripsBetweenValueAndLabel() {
        assertEquals("dark", themeModeValue(ThemeMode.DARK))
        assertEquals("Dark", themeModeLabel(ThemeMode.DARK))
        assertEquals("System", themeModeLabel(ThemeMode.SYSTEM))
    }

    @Test
    fun persistsThemeModeAcrossControllerInstances() {
        val store = ThemeMemoryStore()
        val controller = MemosTimelineController(store)
        assertEquals(ThemeMode.SYSTEM, controller.state.value.themeMode)

        controller.setThemeMode(ThemeMode.LIGHT)

        assertEquals("light", store.getString("ui.themeMode"))
        assertEquals(ThemeMode.LIGHT, MemosTimelineController(store).state.value.themeMode)
    }
}

private class ThemeMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
