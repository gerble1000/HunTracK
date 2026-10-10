package uk.hunttrack.domain.season

import java.util.Calendar
import java.util.TimeZone

/**
 * Season dates in catalog are DD-MM (UK style), e.g. red stag 01-08 → 30-04.
 */
object SeasonStatus {

    enum class State { OPEN, CLOSED, UNKNOWN }

    fun status(openFromMonthDay: String, openToMonthDay: String, nowEpochMs: Long = System.currentTimeMillis()): State {
        if (openFromMonthDay == "—" || openToMonthDay == "—" ||
            openFromMonthDay.isBlank() || openToMonthDay.isBlank()
        ) {
            return State.UNKNOWN
        }
        val from = parseDdMm(openFromMonthDay) ?: return State.UNKNOWN
        val to = parseDdMm(openToMonthDay) ?: return State.UNKNOWN
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/London")).apply {
            timeInMillis = nowEpochMs
        }
        val today = cal.get(Calendar.MONTH) + 1 to cal.get(Calendar.DAY_OF_MONTH)
        return if (isInRange(today, from, to)) State.OPEN else State.CLOSED
    }

    private fun parseDdMm(value: String): Pair<Int, Int>? {
        val parts = value.split("-", "/", ".")
        if (parts.size != 2) return null
        val day = parts[0].trim().toIntOrNull() ?: return null
        val month = parts[1].trim().toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return month to day
    }

    /** Inclusive range; supports wrap across New Year (e.g. Aug→Apr). */
    private fun isInRange(today: Pair<Int, Int>, from: Pair<Int, Int>, to: Pair<Int, Int>): Boolean {
        val t = ordinal(today)
        val a = ordinal(from)
        val b = ordinal(to)
        return if (a <= b) t in a..b else t >= a || t <= b
    }

    private fun ordinal(monthDay: Pair<Int, Int>): Int = monthDay.first * 100 + monthDay.second
}
