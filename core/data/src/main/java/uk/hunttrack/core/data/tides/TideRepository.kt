package uk.hunttrack.core.data.tides

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.hunttrack.core.data.db.TideCacheDao
import uk.hunttrack.core.data.db.TideEventsCacheEntity
import uk.hunttrack.core.data.db.TideStationCacheEntity
import uk.hunttrack.domain.geo.CalendarDates
import uk.hunttrack.domain.model.TideDataSource
import uk.hunttrack.domain.model.TideEvent
import uk.hunttrack.domain.model.TideEventType
import uk.hunttrack.domain.model.TideForecast
import uk.hunttrack.domain.model.TideStation
import uk.hunttrack.domain.tides.LunarTideCalculator
import uk.hunttrack.domain.tides.UkTideStationCatalog
import java.time.Instant

@Singleton
class TideRepository @Inject constructor(
    private val tideTimesClient: TideTimesClient,
    private val cacheDao: TideCacheDao,
    private val json: Json,
) {
    suspend fun getStations(forceRefresh: Boolean = false): Result<List<TideStation>> {
        val now = System.currentTimeMillis()
        val cached = cacheDao.getStationsCache()
        if (!forceRefresh && cached != null && now - cached.fetchedAtEpochMs < STATIONS_CACHE_TTL_MS) {
            return Result.success(decodeStations(cached.payloadJson))
        }
        val stations = UkTideStationCatalog.allStations()
        val payload = json.encodeToString(
            TideStationListDto(
                stations.map {
                    TideStationDto(it.id, it.name, it.country, it.latitude, it.longitude)
                },
            ),
        )
        cacheDao.upsertStationsCache(TideStationCacheEntity(payloadJson = payload, fetchedAtEpochMs = now))
        return Result.success(stations)
    }

    suspend fun getForecast(
        station: TideStation,
        forceRefresh: Boolean = false,
    ): TideForecast {
        val now = System.currentTimeMillis()
        val cached = cacheDao.getEventsCache(station.id)
        if (!forceRefresh && cached != null && !needsDailyRefresh(cached.fetchedAtEpochMs, now)) {
            val cachedEvents = decodeEvents(cached.payloadJson)
            // Skip stale on-device estimates so we pick up live TideTimes data.
            if (cachedEvents.isNotEmpty() && cachedEvents.none { it.isApproximate }) {
                return TideForecast(
                    station = station,
                    events = cachedEvents,
                    fetchedAtEpochMs = cached.fetchedAtEpochMs,
                    source = TideDataSource.CACHE,
                )
            }
        }

        val profile = UkTideStationCatalog.profileFor(station)
        val live = runCatching { tideTimesClient.fetchEvents(profile.slug, days = 7) }.getOrNull()
        val (events, source) = when {
            !live.isNullOrEmpty() -> live to TideDataSource.LIVE
            cached != null -> decodeEvents(cached.payloadJson) to TideDataSource.CACHE
            else -> LunarTideCalculator.forecast(profile, now, days = 7) to TideDataSource.CALCULATED
        }

        if (source == TideDataSource.LIVE) {
            val payload = json.encodeToString(
                TideEventsPayloadDto(
                    stationId = station.id,
                    events = events.map {
                        TideEventDto(it.epochMs, it.type.name, it.heightMeters, it.isApproximate)
                    },
                ),
            )
            cacheDao.upsertEventsCache(
                TideEventsCacheEntity(
                    stationId = station.id,
                    payloadJson = payload,
                    fetchedAtEpochMs = now,
                ),
            )
            return TideForecast(station, events, now, TideDataSource.LIVE)
        }

        return TideForecast(station, events, cached?.fetchedAtEpochMs ?: now, source)
    }

    suspend fun clearCache() {
        cacheDao.clearEventsCache()
        cacheDao.clearStationsCache()
    }

    fun nearestStation(stations: List<TideStation>, latitude: Double, longitude: Double): TideStation? {
        if (stations.isEmpty()) return null
        return stations.minByOrNull { station ->
            val dLat = station.latitude - latitude
            val dLng = station.longitude - longitude
            dLat * dLat + dLng * dLng
        }
    }

    private fun decodeStations(payloadJson: String): List<TideStation> =
        json.decodeFromString<TideStationListDto>(payloadJson).stations.map { it.toDomain() }

    private fun decodeEvents(payloadJson: String): List<TideEvent> =
        json.decodeFromString<TideEventsPayloadDto>(payloadJson).events.map { it.toDomain() }

    private fun needsDailyRefresh(fetchedAtEpochMs: Long, now: Long): Boolean {
        val zone = CalendarDates.zone
        val fetchedDay = Instant.ofEpochMilli(fetchedAtEpochMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return fetchedDay.isBefore(today)
    }

    companion object {
        private const val STATIONS_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}

private fun TideStationDto.toDomain() = TideStation(id, name, country, latitude, longitude)

private fun TideEventDto.toDomain() = TideEvent(
    epochMs = epochMs,
    type = runCatching { TideEventType.valueOf(type) }.getOrDefault(TideEventType.UNKNOWN),
    heightMeters = heightMeters,
    isApproximate = isApproximate,
)
