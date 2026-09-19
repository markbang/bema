package dev.bema.shared.data.session

/**
 * Just enough calendar arithmetic to lay out the activity grid: which local day a
 * timestamp falls on, and how a month's days line up under weekday headings.
 *
 * Derived from the epoch day rather than pulled from a date library, so both UIs
 * build the grid from one implementation. The algorithms are the usual
 * days-from-civil / civil-from-days pair, which is why the round trip is tested.
 */
data class CivilDate(val year: Int, val month: Int, val day: Int)

object CalendarDays {

    /** Days since 1970-01-01 for a proleptic Gregorian [year]-[month]-[day]. */
    fun epochDay(year: Int, month: Int, day: Int): Long {
        val shiftedYear = if (month <= 2) year - 1 else year
        val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
        val yearOfEra = shiftedYear - era * 400
        val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era.toLong() * 146097L + dayOfEra.toLong() - 719468L
    }

    /** The date [epochDay] names. */
    fun dateOf(epochDay: Long): CivilDate {
        val z = epochDay + 719468L
        val era = (if (z >= 0) z else z - 146096L) / 146097L
        val dayOfEra = z - era * 146097L
        val yearOfEra = (dayOfEra - dayOfEra / 1460L + dayOfEra / 36524L - dayOfEra / 146096L) / 365L
        val year = yearOfEra + era * 400L
        val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
        val monthPart = (5L * dayOfYear + 2L) / 153L
        val day = dayOfYear - (153L * monthPart + 2L) / 5L + 1L
        val month = monthPart + if (monthPart < 10L) 3L else -9L
        val civilYear = year + if (month <= 2L) 1L else 0L
        return CivilDate(civilYear.toInt(), month.toInt(), day.toInt())
    }

    /** 0 = Sunday, matching how the grid is drawn. */
    fun weekdayOf(epochDay: Long): Int = (((epochDay + 4L) % 7L) + 7L).toInt() % 7

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> error("month out of range: $month")
    }

    fun isLeapYear(year: Int): Boolean =
        year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    /** The month [delta] months away from [year]-[month], rolling the year over. */
    fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val zeroBased = year * 12 + (month - 1) + delta
        return zeroBased.floorDiv(12) to zeroBased.mod(12) + 1
    }
}
