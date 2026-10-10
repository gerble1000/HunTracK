package uk.hunttrack.domain.geo

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object CalendarDates {
    val zone: ZoneId = ZoneId.of("Europe/London")

    fun today(): LocalDate = LocalDate.now(zone)

    fun dayStartEpochMs(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun localDateFromDayStart(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun monthGrid(yearMonth: YearMonth): List<LocalDate?> {
        val first = yearMonth.atDay(1)
        val startOffset = first.dayOfWeek.value - 1
        val cells = mutableListOf<LocalDate?>()
        repeat(startOffset) { cells.add(null) }
        for (day in 1..yearMonth.lengthOfMonth()) {
            cells.add(yearMonth.atDay(day))
        }
        while (cells.size % 7 != 0) {
            cells.add(null)
        }
        return cells
    }
}
