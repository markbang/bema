package dev.bema.shared

import platform.Foundation.NSTimeZone
import platform.UIKit.UIDevice

actual fun platformName(): String =
    UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

actual fun deviceUtcOffsetSeconds(): Int =
    NSTimeZone.localTimeZone.secondsFromGMT.toInt()
