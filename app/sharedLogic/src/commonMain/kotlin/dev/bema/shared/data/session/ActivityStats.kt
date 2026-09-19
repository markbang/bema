package dev.bema.shared.data.session

import dev.bema.shared.data.model.UserStats
import kotlin.time.Instant

/**
 * What the activity panel shows, derived from one user-stats response.
 *
 * Both halves come from the same `GetUserStats` call, which is also where the web
 * client reads them from.
 */
data class ActivityStats(
    /** Tags with their memo counts, most used first. */
    val tagCounts: List<Pair<String, Int>> = emptyList(),
    /** Local epoch-day to how many memos were created that day. */
    val dayCounts: Map<Long, Int> = emptyMap()
) {
    val isEmpty: Boolean get() = tagCounts.isEmpty() && dayCounts.isEmpty()
}

private const val SECONDS_PER_DAY = 86_400L

internal fun UserStats.toActivityStats(utcOffsetSeconds: Int): ActivityStats = ActivityStats(
    tagCounts = tagCount
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value },
    dayCounts = memoCreatedTimestamps
        .groupingBy { (it.epochSeconds + utcOffsetSeconds).floorDiv(SECONDS_PER_DAY) }
        .eachCount()
)
