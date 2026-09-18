package dev.bema.shared.data.session

/**
 * Option sets and normalisation for the instance settings form.
 *
 * These lived in the Android UI layer, which would have meant a second copy in
 * Swift the moment iOS grew a settings screen. The instance reports a locale or
 * appearance the client may not know, so each helper canonicalises what came
 * back and falls back to the raw value rather than dropping it.
 */

data class SettingOption(val value: String, val label: String)

val LocaleOptions: List<SettingOption> = listOf(
    SettingOption("en", "English"),
    SettingOption("zh-Hans", "简体中文"),
    SettingOption("zh-Hant", "繁體中文"),
    SettingOption("ja", "日本語"),
    SettingOption("ko", "한국어"),
    SettingOption("fr", "Français"),
    SettingOption("de", "Deutsch"),
    SettingOption("es", "Español"),
    SettingOption("ru", "Русский"),
    SettingOption("pt-BR", "Português (Brasil)"),
    SettingOption("pt-PT", "Português"),
    SettingOption("vi", "Tiếng Việt"),
    SettingOption("ar", "العربية"),
    SettingOption("th", "ไทย"),
    SettingOption("id", "Bahasa Indonesia"),
    SettingOption("it", "Italiano"),
    SettingOption("nl", "Nederlands"),
    SettingOption("pl", "Polski"),
    SettingOption("tr", "Türkçe"),
    SettingOption("uk", "Українська")
)

val AppearanceOptions: List<SettingOption> = listOf(
    SettingOption("system", "System"),
    SettingOption("light", "Light"),
    SettingOption("dark", "Dark")
)

val UploadSizeOptions: List<SettingOption> = listOf(
    SettingOption("0", "Unlimited"),
    SettingOption("8", "8 MiB"),
    SettingOption("16", "16 MiB"),
    SettingOption("32", "32 MiB"),
    SettingOption("64", "64 MiB"),
    SettingOption("128", "128 MiB"),
    SettingOption("256", "256 MiB")
)

fun canonicalLocale(raw: String): String {
    val value = raw.trim()
    return when {
        value.isEmpty() -> "en"
        value.equals("zh", true) ||
            value.startsWith("zh-CN", true) ||
            value.startsWith("zh-Hans", true) ||
            value.equals("zh-SG", true) -> "zh-Hans"
        value.startsWith("zh-TW", true) ||
            value.startsWith("zh-HK", true) ||
            value.startsWith("zh-Hant", true) -> "zh-Hant"
        value.startsWith("en", true) -> "en"
        value.startsWith("ja", true) -> "ja"
        value.startsWith("ko", true) -> "ko"
        value.equals("pt-PT", true) -> "pt-PT"
        value.startsWith("pt", true) -> "pt-BR"
        else -> LocaleOptions.firstOrNull { it.value.equals(value, true) }?.value ?: value
    }
}

fun canonicalAppearance(raw: String): String = when (raw.trim().lowercase()) {
    "light" -> "light"
    "dark" -> "dark"
    else -> "system"
}

fun localeLabel(value: String): String =
    LocaleOptions.firstOrNull { it.value == value }?.label ?: value.ifBlank { "English" }

fun appearanceLabel(value: String): String =
    AppearanceOptions.firstOrNull { it.value == value }?.label ?: "System"

fun uploadSizeLabel(value: String): String =
    UploadSizeOptions.firstOrNull { it.value == value }?.label
        ?: value.toLongOrNull()?.let { "$it MiB" }
        ?: "Unlimited"

/** Keeps an unknown server value selectable instead of silently rewriting it. */
fun localeOptionsFor(current: String): List<SettingOption> =
    if (LocaleOptions.any { it.value == current }) LocaleOptions
    else listOf(SettingOption(current, current)) + LocaleOptions

fun uploadSizeOptionsFor(current: String): List<SettingOption> =
    if (UploadSizeOptions.any { it.value == current }) UploadSizeOptions
    else listOf(SettingOption(current, uploadSizeLabel(current))) + UploadSizeOptions
