package dev.bema.shared

import dev.bema.shared.data.model.UserStats
import dev.bema.shared.data.session.CalendarDays
import dev.bema.shared.data.session.TagCount
import dev.bema.shared.data.session.localDayFilter
import dev.bema.shared.data.session.toActivityStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ActivityStatsTest {

    @Test
    fun ordersTagsByUseThenName() {
        val activity = UserStats(tagCount = mapOf("b" to 1, "a" to 2, "c" to 2)).toActivityStats(0)

        assertEquals(listOf(TagCount("a", 2), TagCount("c", 2), TagCount("b", 1)), activity.tagCounts)
    }

    @Test
    fun foldsTimestampsIntoLocalDays() {
        val morning = Instant.parse("2026-09-18T01:00:00Z").epochSeconds
        val alsoMorning = Instant.parse("2026-09-18T01:30:00Z").epochSeconds
        val lateEvening = Instant.parse("2026-09-18T23:30:00Z").epochSeconds
        val offset = 8 * 3600 // UTC+8: 23:30Z is already the next local day

        val activity = UserStats(
            memoCreatedTimestamps = listOf(
                Instant.fromEpochSeconds(morning),
                Instant.fromEpochSeconds(alsoMorning),
                Instant.fromEpochSeconds(lateEvening)
            )
        ).toActivityStats(offset)

        fun dayOf(seconds: Long) = (seconds + offset).floorDiv(86_400L)
        assertEquals(dayOf(morning) + 1, dayOf(lateEvening), "the offset should roll the late memo over")
        assertEquals(
            mapOf(dayOf(morning) to 2, dayOf(lateEvening) to 1),
            activity.dayCounts
        )
    }

    @Test
    fun anEmptyInstanceReadsAsEmpty() {
        val activity = UserStats().toActivityStats(0)

        assertEquals(emptyList(), activity.tagCounts)
        assertEquals(emptyMap(), activity.dayCounts)
        assertEquals(true, activity.isEmpty)
    }

    @Test
    fun buildsTheLocalDayRangeFilter() {
        val epochDay = CalendarDays.epochDay(2026, 9, 19)
        val offset = 8 * 3600

        val filter = localDayFilter(epochDay, offset)

        assertEquals("2026-09-19", filter.label)
        // The range is the local day expressed in UTC seconds, half-open.
        val start = epochDay * 86_400L - offset
        assertEquals(
            "created_ts >= timestamp($start) && created_ts < timestamp(${start + 86_400L})",
            filter.cel
        )
    }
}
