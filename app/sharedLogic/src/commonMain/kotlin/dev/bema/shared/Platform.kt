package dev.bema.shared

/** Name of the platform this code currently runs on. Implemented per target. */
expect fun platformName(): String

/**
 * The device's current offset from UTC in seconds.
 *
 * Used to fold memo timestamps into local days for the activity heatmap; reading it
 * once is enough for a grid, where a DST shift only nudges a cell boundary.
 */
expect fun deviceUtcOffsetSeconds(): Int
