package dev.bema.shared.data.session

/** Shared options for the app and instance settings forms. */

data class SettingOption(val value: String, val label: String)

val UploadSizeOptions: List<SettingOption> = listOf(
    SettingOption("0", "Unlimited"),
    SettingOption("8", "8 MiB"),
    SettingOption("16", "16 MiB"),
    SettingOption("32", "32 MiB"),
    SettingOption("64", "64 MiB"),
    SettingOption("128", "128 MiB"),
    SettingOption("256", "256 MiB")
)

fun uploadSizeLabel(value: String): String =
    UploadSizeOptions.firstOrNull { it.value == value }?.label
        ?: value.toLongOrNull()?.let { "$it MiB" }
        ?: "Unlimited"

/** Keeps an existing custom limit selectable instead of silently rewriting it. */
fun uploadSizeOptionsFor(current: String): List<SettingOption> =
    if (UploadSizeOptions.any { it.value == current }) UploadSizeOptions
    else listOf(SettingOption(current, uploadSizeLabel(current))) + UploadSizeOptions
