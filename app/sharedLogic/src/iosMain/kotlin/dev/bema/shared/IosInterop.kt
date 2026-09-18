package dev.bema.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dev.bema.shared.data.session.MemosAppState
import dev.bema.shared.data.session.MemosTimelineController
import dev.bema.shared.data.session.ThemeMode
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Apple-platform primitives the rest of the module would otherwise each
 * re-implement.
 *
 * [observeState] exists because Swift has no way to collect a `StateFlow`
 * (that needs SKIE). The `NSData` conversions exist because a Kotlin `ByteArray`
 * is not an `NSData`: used by `PlatformKeyValueStore` for file storage, and
 * exposed to Swift so it can turn avatar and attachment bytes into `UIImage`.
 */
object IosInterop {

    /** Observes [flow] on the main queue. Call `cancel()` on the result to stop. */
    fun observeState(
        flow: StateFlow<MemosAppState>,
        onEach: (MemosAppState) -> Unit
    ): IosObservation {
        val job = CoroutineScope(Dispatchers.Main).launch {
            flow.collect { onEach(it) }
        }
        return IosObservation(job)
    }

    /**
     * The controller's current theme mode.
     *
     * `StateFlow.value` bridges to Swift as `Any?`, so Swift cannot read it
     * directly; this is the typed accessor for the app's initial interface style.
     */
    fun themeMode(controller: MemosTimelineController): ThemeMode = controller.state.value.themeMode

    @OptIn(ExperimentalForeignApi::class)
    fun nsData(bytes: ByteArray): NSData {
        if (bytes.isEmpty()) return NSData()
        return bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun byteArray(data: NSData): ByteArray {
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        return ByteArray(length).apply {
            usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, length.toULong()) }
        }
    }
}

/** Cancellation handle for [IosInterop.observeState]. */
class IosObservation(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}
