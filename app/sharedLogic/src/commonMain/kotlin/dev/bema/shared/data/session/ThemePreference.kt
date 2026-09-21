package dev.bema.shared.data.session

/**
 * The client's own light/dark preference, independent of instance settings.
 * It lives in the shared module so Android and iOS read the same value, and is persisted
 * in the key-value store so it survives a relaunch.
 *
 * [ThemeMode.SYSTEM] defers the choice to the OS; resolving it needs the
 * platform's own dark-mode signal, so the two UIs do that part themselves.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

val ThemeModeOptions: List<SettingOption> = listOf(
    SettingOption("system", "System"),
    SettingOption("light", "Light"),
    SettingOption("dark", "Dark")
)

fun canonicalThemeMode(raw: String?): ThemeMode = when (raw?.trim()?.lowercase()) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

fun themeModeValue(mode: ThemeMode): String = mode.name.lowercase()

fun themeModeLabel(mode: ThemeMode): String =
    ThemeModeOptions.firstOrNull { it.value == themeModeValue(mode) }?.label ?: "System"
