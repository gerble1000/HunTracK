package uk.hunttrack.core.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT,
        @Query("hourly") hourly: String = HOURLY,
        @Query("daily") daily: String = DAILY,
        @Query("timezone") timezone: String = "Europe/London",
        @Query("forecast_days") forecastDays: Int = 5,
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        private const val CURRENT =
            "temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code,cloud_cover,visibility"
        private const val HOURLY =
            "temperature_2m,wind_speed_10m,wind_direction_10m,precipitation_probability,weather_code"
        private const val DAILY =
            "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,wind_speed_10m_max"
    }
}

@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val current: CurrentWeather? = null,
    val hourly: HourlyWeather? = null,
    val daily: DailyWeather? = null,
)

@Serializable
data class CurrentWeather(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature2m: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @SerialName("surface_pressure") val surfacePressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed10m: Double? = null,
    @SerialName("wind_direction_10m") val windDirection10m: Double? = null,
    @SerialName("wind_gusts_10m") val windGusts10m: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    val visibility: Double? = null,
)

@Serializable
data class HourlyWeather(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature2m: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection10m: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

@Serializable
data class DailyWeather(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeed10mMax: List<Double?> = emptyList(),
)
