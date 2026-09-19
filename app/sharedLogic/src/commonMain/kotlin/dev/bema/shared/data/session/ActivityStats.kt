package dev.bema.shared.data.session

import dev.bema.shared.data.model.UserStats
import dev.bema.shared.deviceUtcOffsetSeconds
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What the activity panel shows, derived from one user-stats response.
 *
 * Both halves come from the same `GetUserStats` call, which is also where the web
 * client reads them from.
 */
data class ActivityStats(
    /** Tags with their memo counts, most used first. */
    val tagCounts: List<TagCount> = emptyList(),
    /** Local epoch-day to how many memos were created that day. */
    val dayCounts: Map<Long, Int> = emptyMap()
) {
    val isEmpty: Boolean get() = tagCounts.isEmpty() && dayCounts.isEmpty()
}

/**
 * One tag and how many memos carry it.
 *
 * A plain class rather than a `Pair` because Swift receives Kotlin pairs as `Any?`,
 * and the panel reads both halves.
 */
data class TagCount(val tag: String, val count: Int)

private const val SECONDS_PER_DAY = 86_400L

/** A CEL filter with the label the UI shows while it is active. */
data class TimelineFilter(val label: String, val cel: String)

/**
 * The filter for one local day, spelled the way the web client's calendar spells it:
 * an epoch-second range in UTC, shifted by the device offset, exclusive of the next
 * day. `timestamp()` takes seconds rather than an RFC 3339 string.
 */
fun localDayFilter(epochDay: Long, utcOffsetSeconds: Int): TimelineFilter {
    val start = epochDay * SECONDS_PER_DAY - utcOffsetSeconds
    val date = CalendarDays.dateOf(epochDay)
    val label = "${date.year}-" + date.month.toString().padStart(2, '0') + "-" + date.day.toString().padStart(2, '0')
    return TimelineFilter(
        label = label,
        cel = "created_ts >= timestamp($start) && created_ts < timestamp(${start + SECONDS_PER_DAY})"
    )
}

/** The local day the device is on now, matching [toActivityStats]'s buckets. */
fun todayEpochDay(): Long =
    (Clock.System.now().epochSeconds + deviceUtcOffsetSeconds()).floorDiv(SECONDS_PER_DAY)

internal fun UserStats.toActivityStats(utcOffsetSeconds: Int): ActivityStats = ActivityStats(
    tagCounts = tagCount
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { TagCount(it.key, it.value) },
    dayCounts = memoCreatedTimestamps
        .groupingBy { (it.epochSeconds + utcOffsetSeconds).floorDiv(SECONDS_PER_DAY) }
        .eachCount()
)
