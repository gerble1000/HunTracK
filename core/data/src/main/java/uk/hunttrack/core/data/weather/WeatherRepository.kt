package uk.hunttrack.core.data.weather

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.hunttrack.core.data.db.WeatherCacheDao
import uk.hunttrack.core.data.db.WeatherCacheEntity
import uk.hunttrack.domain.model.DailyForecast
import uk.hunttrack.domain.model.HourlyForecast
import uk.hunttrack.domain.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WeatherRepository @Inject constructor(
    private val api: OpenMeteoApi,
    private val cacheDao: WeatherCacheDao,
    private val json: Json,
) {
    suspend fun getWeather(latitude: Double, longitude: Double, forceRefresh: Boolean = false): WeatherSnapshot {
        val key = gridKey(latitude, longitude)
        val cached = cacheDao.get(key)
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && now - cached.fetchedAtEpochMs < CACHE_TTL_MS) {
            return json.decodeFromString(WeatherSnapshotDto.serializer(), cached.payloadJson).toDomain()
        }

        return try {
            val response = api.forecast(latitude, longitude)
            val snapshot = response.toDomain(now)
            cacheDao.upsert(
                WeatherCacheEntity(
                    gridKey = key,
                    latitude = latitude,
                    longitude = longitude,
                    fetchedAtEpochMs = now,
                    payloadJson = json.encodeToString(WeatherSnapshotDto.serializer(), snapshot.toDto()),
                ),
            )
            snapshot
        } catch (e: Exception) {
            cached?.let {
                return json.decodeFromString(WeatherSnapshotDto.serializer(), it.payloadJson).toDomain()
            }
            cacheDao.getLatest()?.let {
                return json.decodeFromString(WeatherSnapshotDto.serializer(), it.payloadJson).toDomain()
            }
            throw e
        }
    }

    suspend fun getCachedWeather(): WeatherSnapshot? {
        val cached = cacheDao.getLatest() ?: return null
        return json.decodeFromString(WeatherSnapshotDto.serializer(), cached.payloadJson).toDomain()
    }

    suspend fun clearCache() = cacheDao.clear()

    private fun gridKey(lat: Double, lng: Double): String {
        val glat = (lat * 20).roundToInt() / 20.0
        val glng = (lng * 20).roundToInt() / 20.0
        return "$glat,$glng"
    }

    companion object {
        private const val CACHE_TTL_MS = 20 * 60 * 1000L
    }
}

@kotlinx.serialization.Serializable
data class WeatherSnapshotDto(
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochMs: Long,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val pressureHpa: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val windGustKmh: Double? = null,
    val rainProbabilityPercent: Int? = null,
    val visibilityKm: Double? = null,
    val weatherCode: Int,
    val cloudCoverPercent: Int? = null,
    val hourly: List<HourlyDto> = emptyList(),
    val daily: List<DailyDto> = emptyList(),
)

@kotlinx.serialization.Serializable
data class HourlyDto(
    val epochMs: Long,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val rainProbabilityPercent: Int,
    val weatherCode: Int,
)

@kotlinx.serialization.Serializable
data class DailyDto(
    val epochMs: Long,
    val tempMinC: Double,
    val tempMaxC: Double,
    val rainProbabilityPercent: Int,
    val weatherCode: Int,
    val windSpeedMaxKmh: Double,
    val frostRisk: Boolean,
    val fogRisk: Boolean,
    val stormRisk: Boolean,
)

private fun WeatherSnapshot.toDto() = WeatherSnapshotDto(
    latitude, longitude, fetchedAtEpochMs, temperatureC, feelsLikeC, humidityPercent, pressureHpa,
    windSpeedKmh, windDirectionDeg, windGustKmh, rainProbabilityPercent, visibilityKm, weatherCode,
    cloudCoverPercent,
    hourly.map { HourlyDto(it.epochMs, it.temperatureC, it.windSpeedKmh, it.windDirectionDeg, it.rainProbabilityPercent, it.weatherCode) },
    daily.map {
        DailyDto(
            it.epochMs, it.tempMinC, it.tempMaxC, it.rainProbabilityPercent, it.weatherCode,
            it.windSpeedMaxKmh, it.frostRisk, it.fogRisk, it.stormRisk,
        )
    },
)

private fun WeatherSnapshotDto.toDomain() = WeatherSnapshot(
    latitude, longitude, fetchedAtEpochMs, temperatureC, feelsLikeC, humidityPercent, pressureHpa,
    windSpeedKmh, windDirectionDeg, windGustKmh, rainProbabilityPercent, visibilityKm, weatherCode,
    cloudCoverPercent,
    hourly.map { HourlyForecast(it.epochMs, it.temperatureC, it.windSpeedKmh, it.windDirectionDeg, it.rainProbabilityPercent, it.weatherCode) },
    daily.map {
        DailyForecast(
            it.epochMs, it.tempMinC, it.tempMaxC, it.rainProbabilityPercent, it.weatherCode,
            it.windSpeedMaxKmh, it.frostRisk, it.fogRisk, it.stormRisk,
        )
    },
)

private fun OpenMeteoResponse.toDomain(fetchedAt: Long): WeatherSnapshot {
    val zone = ZoneId.of("Europe/London")
    val formatter = DateTimeFormatter.ISO_DATE_TIME
    val current = current
    val hourlyList = hourly?.time?.indices?.mapNotNull { i ->
        val time = hourly.time.getOrNull(i) ?: return@mapNotNull null
        val epoch = parseTime(time, zone, formatter)
        HourlyForecast(
            epochMs = epoch,
            temperatureC = hourly.temperature2m.getOrNull(i) ?: return@mapNotNull null,
            windSpeedKmh = hourly.windSpeed10m.getOrNull(i) ?: 0.0,
            windDirectionDeg = hourly.windDirection10m.getOrNull(i) ?: 0.0,
            rainProbabilityPercent = hourly.precipitationProbability.getOrNull(i) ?: 0,
            weatherCode = hourly.weatherCode.getOrNull(i) ?: 0,
        )
    }?.take(24).orEmpty()

    val dailyList = daily?.time?.indices?.mapNotNull { i ->
        val day = daily.time.getOrNull(i) ?: return@mapNotNull null
        val code = daily.weatherCode.getOrNull(i) ?: 0
        val min = daily.temperature2mMin.getOrNull(i) ?: 0.0
        DailyForecast(
            epochMs = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli(),
            tempMinC = min,
            tempMaxC = daily.temperature2mMax.getOrNull(i) ?: min,
            rainProbabilityPercent = daily.precipitationProbabilityMax.getOrNull(i) ?: 0,
            weatherCode = code,
            windSpeedMaxKmh = daily.windSpeed10mMax.getOrNull(i) ?: 0.0,
            frostRisk = min <= 0.0,
            fogRisk = code in listOf(45, 48),
            stormRisk = code >= 95,
        )
    }.orEmpty()

    val visibilityKm = current?.visibility?.let { it / 1000.0 }

    return WeatherSnapshot(
        latitude = latitude,
        longitude = longitude,
        fetchedAtEpochMs = fetchedAt,
        temperatureC = current?.temperature2m ?: 0.0,
        feelsLikeC = current?.apparentTemperature ?: current?.temperature2m ?: 0.0,
        humidityPercent = current?.relativeHumidity2m ?: 0,
        pressureHpa = current?.surfacePressure ?: 0.0,
        windSpeedKmh = current?.windSpeed10m ?: 0.0,
        windDirectionDeg = current?.windDirection10m ?: 0.0,
        windGustKmh = current?.windGusts10m,
        rainProbabilityPercent = hourlyList.firstOrNull()?.rainProbabilityPercent,
        visibilityKm = visibilityKm,
        weatherCode = current?.weatherCode ?: 0,
        cloudCoverPercent = current?.cloudCover,
        hourly = hourlyList,
        daily = dailyList,
    )
}

private fun parseTime(value: String, zone: ZoneId, formatter: DateTimeFormatter): Long {
    return try {
        LocalDateTime.parse(value, formatter).atZone(zone).toInstant().toEpochMilli()
    } catch (_: Exception) {
        Instant.parse(value + "Z").toEpochMilli()
    }
}
