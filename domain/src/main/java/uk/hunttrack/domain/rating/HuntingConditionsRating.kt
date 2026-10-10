package uk.hunttrack.domain.rating

import uk.hunttrack.domain.model.ActivityRating
import uk.hunttrack.domain.model.AstronomyInfo
import uk.hunttrack.domain.model.WeatherSnapshot
import java.util.Calendar
import java.util.TimeZone

object HuntingConditionsRating {

    fun rate(weather: WeatherSnapshot?, astronomy: AstronomyInfo?, nowEpochMs: Long): ActivityRating {
        return if (isRabbitFoxWindow(astronomy, nowEpochMs)) {
            RabbitFoxActivityRating.rate(weather, astronomy, nowEpochMs)
        } else {
            DeerActivityRating.rate(weather, astronomy, nowEpochMs)
        }
    }

    /**
     * After golden hour ends (sunset) until sunrise — prime rabbit & fox window.
     * Uses OR because the window crosses midnight (today's sunrise is in the past after dusk).
     */
    private fun isRabbitFoxWindow(astronomy: AstronomyInfo?, nowEpochMs: Long): Boolean {
        if (astronomy != null) {
            return nowEpochMs >= astronomy.goldenHourEndEpochMs ||
                nowEpochMs < astronomy.sunriseEpochMs
        }
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Europe/London")).apply {
            timeInMillis = nowEpochMs
        }.get(Calendar.HOUR_OF_DAY)
        return hour >= 20 || hour < 5
    }
}
