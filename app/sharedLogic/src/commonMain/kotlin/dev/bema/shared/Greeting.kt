package dev.bema.shared

/**
 * Shared business logic used by both entry points:
 * `:app:androidApp` and `app/iosApp` (UIKit).
 *
 * This module intentionally carries no UI dependency: the iOS app renders with
 * UIKit, so Compose Multiplatform is not needed. Code that only some platforms
 * use belongs in a separate `sharedUI` module.
 *
 * The no-argument constructor keeps the Swift call site trivial (`Greeter()`),
 * while the primary constructor allows tests to inject the platform string.
 */
class Greeter(private val platform: String) {
    constructor() : this(platformName())

    fun greet(name: String): String = "Hello, $name! (shared logic running on $platform)"
}
