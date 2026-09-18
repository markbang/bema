package dev.bema.shared.data.update

import platform.Foundation.NSBundle

actual fun installedVersionName(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String).orEmpty()

/** iOS has no APK to install, so there is nothing for the update check to find. */
actual fun deviceAbi(): String? = null
