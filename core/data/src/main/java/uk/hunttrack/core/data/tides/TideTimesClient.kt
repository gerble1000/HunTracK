package uk.hunttrack.core.data.tides

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.hunttrack.domain.geo.CalendarDates
import uk.hunttrack.domain.model.TideEvent
import uk.hunttrack.domain.model.TideEventType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Fetches UK high/low water from [tidetimes.org.uk](https://www.tidetimes.org.uk) daily pages.
 * No API key. Times are Europe/London (BST/GMT as published on the site).
 */
@Singleton
class TideTimesClient @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun fetchEvents(slug: String, days: Int = 7): List<TideEvent> = withContext(Dispatchers.IO) {
        val zone = CalendarDates.zone
        val today = LocalDate.now(zone)
        val dayCount = days.coerceIn(1, 7)
        coroutineScope {
            (0 until dayCount).map { offset ->
                async {
                    val date = today.plusDays(offset.toLong())
                    fetchDay(slug, date)
                }
            }.awaitAll().flatten().sortedBy { it.epochMs }
        }
    }

    private fun fetchDay(slug: String, date: LocalDate): List<TideEvent> {
        val url = "$BASE_URL/${slug.trim('/')}-tide-times-${DATE_COMPACT.format(date)}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("TideTimes HTTP ${response.code} for $slug on $date")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("Empty TideTimes response for $slug on $date")
            return parseDayHtml(body, date)
        }
    }

    internal fun parseDayHtml(html: String, date: LocalDate): List<TideEvent> {
        val zone = CalendarDates.zone
        val events = mutableListOf<TideEvent>()
        val matcher = ROW_REGEX.findAll(html)
        for (match in matcher) {
            val type = when (match.groupValues[1].lowercase()) {
                "high" -> TideEventType.HIGH
                "low" -> TideEventType.LOW
                else -> TideEventType.UNKNOWN
            }
            val time = LocalTime.parse(match.groupValues[2], TIME_FMT)
            val height = match.groupValues[3].toDoubleOrNull() ?: continue
            val epochMs = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            events += TideEvent(
                epochMs = epochMs,
                type = type,
                heightMeters = height,
                isApproximate = false,
            )
        }
        return events
    }

    companion object {
        private const val BASE_URL = "https://www.tidetimes.org.uk"
        private const val USER_AGENT =
            "HunTracK/1.0 (Android; UK tide times; +https://www.tidetimes.org.uk)"
        private val DATE_COMPACT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        /** Only BST/local rows (class vis2); ignore GMT duplicate rows (vis0). */
        private val ROW_REGEX = Regex(
            """<tr class="vis2">\s*<td class="tal">(High|Low)</td>\s*<td class="tac"><span>(\d{2}:\d{2})</span></td>\s*<td class="tar">([\d.]+)m</td>""",
            setOf(RegexOption.IGNORE_CASE),
        )
    }
}
