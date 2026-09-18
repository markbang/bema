package dev.bema.shared.data.storage

import dev.bema.shared.IosInterop
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * One file per key under Application Support, written with
 * `NSFileProtectionComplete` so the device key encrypts them at rest.
 *
 * These values are not all caches: `PersistentCookieStorage` keeps the account
 * session cookie here, and that cookie mints access tokens, so `NSUserDefaults`
 * (an unencrypted plist) is not good enough. Android's counterpart encrypts with
 * a Keystore key; full-strength equivalent would mean an AES-GCM implementation
 * reachable from Kotlin/Native, which is not worth carrying for this data.
 *
 * One file per key rather than one blob keeps a write proportional to the value,
 * which matters for the avatar cache (up to 512 KiB per avatar).
 *
 * Note: file protection is a no-op on the Simulator, so only a device proves it.
 */
@OptIn(ExperimentalForeignApi::class)
actual object PlatformKeyValueStore : KeyValueStore {
    private const val DIRECTORY_NAME = "bema-store"

    private val fileManager = NSFileManager.defaultManager

    private val directory: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
            ?: error("Application Support directory is unavailable")
        val path = "$base/$DIRECTORY_NAME"
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
        path
    }

    override fun getString(key: String): String? {
        val data = fileManager.contentsAtPath(pathFor(key)) as? NSData ?: return null
        return IosInterop.byteArray(data).decodeToString()
    }

    override fun putString(key: String, value: String) {
        val created = fileManager.createFileAtPath(
            path = pathFor(key),
            contents = IosInterop.nsData(value.encodeToByteArray()),
            attributes = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete)
        )
        check(created) { "Failed to write shared storage value for key '$key'" }
    }

    override fun remove(key: String) {
        fileManager.removeItemAtPath(pathFor(key), error = null)
    }

    private fun pathFor(key: String): String = "$directory/${fileNameFor(key)}"

    /**
     * Keys carry account ids and usernames, which may contain `/` or other
     * characters the filesystem would treat as structure. `%` is itself escaped,
     * so the mapping is unambiguous.
     */
    private fun fileNameFor(key: String): String {
        val out = StringBuilder(key.length + 8)
        for (ch in key) {
            val safe = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.'
            if (safe) {
                out.append(ch)
            } else {
                out.append('%').append(ch.code.toString(16).padStart(4, '0'))
            }
        }
        return out.toString()
    }
}
