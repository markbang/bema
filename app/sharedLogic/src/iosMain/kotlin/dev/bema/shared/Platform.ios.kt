package dev.bema.shared

// Objective-C class properties arrive as extension properties in Kotlin/Native, so
// the package import is what brings `NSTimeZone.localTimeZone` into scope.
import platform.Foundation.*
import platform.UIKit.UIDevice

actual fun platformName(): String =
    UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

actual fun deviceUtcOffsetSeconds(): Int =
    NSTimeZone.localTimeZone.secondsFromGMT.toInt()
