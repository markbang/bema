package dev.bema.shared

import dev.bema.shared.data.session.CalendarDays
import dev.bema.shared.data.session.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarDaysTest {

    @Test
    fun namesTheEpochAndKnownDates() {
        assertEquals(CivilDate(1970, 1, 1), CalendarDays.dateOf(0))
        assertEquals(CivilDate(2026, 9, 19), CalendarDays.dateOf(CalendarDays.epochDay(2026, 9, 19)))
        assertEquals(CivilDate(2000, 2, 29), CalendarDays.dateOf(CalendarDays.epochDay(2000, 2, 29)))
    }

    @Test
    fun roundTripsEveryDayOfSeveralYears() {
        var epochDay = CalendarDays.epochDay(2019, 1, 1)
        val end = CalendarDays.epochDay(2027, 12, 31)
        while (epochDay <= end) {
            val date = CalendarDays.dateOf(epochDay)
            assertEquals(
                epochDay,
                CalendarDays.epochDay(date.year, date.month, date.day),
                "round trip failed for $date"
            )
            epochDay++
        }
    }

    @Test
    fun countsWeekdaysFromSunday() {
        // 1970-01-01 was a Thursday.
        assertEquals(4, CalendarDays.weekdayOf(0))
        // 2026-09-19 is a Saturday.
        assertEquals(6, CalendarDays.weekdayOf(CalendarDays.epochDay(2026, 9, 19)))
    }

    @Test
    fun knowsMonthLengths() {
        assertEquals(31, CalendarDays.daysInMonth(2026, 1))
        assertEquals(28, CalendarDays.daysInMonth(2026, 2))
        assertEquals(29, CalendarDays.daysInMonth(2024, 2))
        assertEquals(28, CalendarDays.daysInMonth(1900, 2))
        assertEquals(29, CalendarDays.daysInMonth(2000, 2))
        assertEquals(30, CalendarDays.daysInMonth(2026, 9))
    }
}
