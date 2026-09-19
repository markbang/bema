package dev.bema.shared

import android.os.Build
import java.util.TimeZone

actual fun platformName(): String = "Android API ${Build.VERSION.SDK_INT}"

actual fun deviceUtcOffsetSeconds(): Int =
    TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
