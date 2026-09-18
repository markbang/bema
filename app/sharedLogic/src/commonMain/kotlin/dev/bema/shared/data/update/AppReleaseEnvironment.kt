package dev.bema.shared.data.update

/** Version name of the installed build, e.g. "0.0.2", as the platform reports it. */
expect fun installedVersionName(): String

/**
 * ABI this device prefers, spelled the way the release pipeline publishes it
 * (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
 *
 * Null on platforms with no APK to install, which makes the update check a no-op
 * there rather than a prompt that cannot be acted on.
 */
expect fun deviceAbi(): String?
