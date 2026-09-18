package dev.bema.shared.data.update

import android.content.Context
import android.os.Build

private var applicationContext: Context? = null

/**
 * Android reads the installed version from the package manager, so the app has to
 * hand its context over first — the same pattern as `PlatformKeyValueStore.install`.
 */
fun installAppRelease(context: Context) {
    applicationContext = context.applicationContext
}

actual fun installedVersionName(): String {
    val context = applicationContext
        ?: error("installAppRelease(context) must be called before reading the app version")
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}

actual fun deviceAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull()
