import Foundation

public class WeatherService: ObservableObject {
    public static let shared = WeatherService()

    @Published public var weather: WeatherSnapshot?
    @Published public var loading: Bool = false
    @Published public var error: String?

    private init() {}

    // Open-Meteo query blocks, matching the Android OpenMeteoApi constants exactly.
    private static let currentFields = [
        "temperature_2m",
        "apparent_temperature",
        "relative_humidity_2m",
        "surface_pressure",
        "wind_speed_10m",
        "wind_direction_10m",
        "wind_gusts_10m",
        "weather_code",
        "cloud_cover",
        "visibility"
    ].joined(separator: ",")

    private static let hourlyFields = [
        "temperature_2m",
        "wind_speed_10m",
        "wind_direction_10m",
        "precipitation_probability",
        "weather_code"
    ].joined(separator: ",")

    private static let dailyFields = [
        "temperature_2m_max",
        "temperature_2m_min",
        "precipitation_probability_max",
        "weather_code",
        "wind_speed_10m_max"
    ].joined(separator: ",")

    private static let forecastDays = 5

    public func fetchWeather(latitude: Double, longitude: Double) {
        loading = true
        error = nil

        var components = URLComponents(string: "https://api.open-meteo.com/v1/forecast")
        components?.queryItems = [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "current", value: Self.currentFields),
            URLQueryItem(name: "hourly", value: Self.hourlyFields),
            URLQueryItem(name: "daily", value: Self.dailyFields),
            URLQueryItem(name: "timezone", value: "Europe/London"),
            URLQueryItem(name: "forecast_days", value: String(Self.forecastDays))
        ]

        guard let url = components?.url else {
            loading = false
            error = "Invalid forecast location"
            return
        }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, err in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.loading = false

                if let err = err {
                    self.error = err.localizedDescription
                    // Fall back to the last good forecast so the UI is never blank offline.
                    if let cached = DatabaseService.shared.cachedWeather {
                        self.weather = cached
                    }
                    return
                }

                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    self.error = "Unable to parse forecast"
                    if let cached = DatabaseService.shared.cachedWeather {
                        self.weather = cached
                    }
                    return
                }

                let snapshot = Self.buildSnapshot(
                    json: json,
                    latitude: latitude,
                    longitude: longitude
                )
                self.weather = snapshot
                DatabaseService.shared.cacheWeather(snapshot)
            }
        }.resume()
    }

    // MARK: - Parsing

    private static func buildSnapshot(json: [String: Any], latitude: Double, longitude: Double) -> WeatherSnapshot {
        let current = json["current"] as? [String: Any]
        let hourly = json["hourly"] as? [String: Any]
        let daily = json["daily"] as? [String: Any]

        let hourlyList = parseHourly(hourly)
        let dailyList = parseDaily(daily)

        let visibilityKm = number(current?["visibility"]).map { $0 / 1000.0 }

        let temperature = number(current?["temperature_2m"]) ?? 0.0
        let feelsLike = number(current?["apparent_temperature"]) ?? temperature

        return WeatherSnapshot(
            latitude: latitude,
            longitude: longitude,
            fetchedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
            temperatureC: temperature,
            feelsLikeC: feelsLike,
            humidityPercent: Int(number(current?["relative_humidity_2m"]) ?? 0),
            pressureHpa: number(current?["surface_pressure"]) ?? 1013.0,
            windSpeedKmh: number(current?["wind_speed_10m"]) ?? 0.0,
            windDirectionDeg: number(current?["wind_direction_10m"]) ?? 0.0,
            windGustKmh: number(current?["wind_gusts_10m"]),
            rainProbabilityPercent: hourlyList.first?.rainProbabilityPercent,
            visibilityKm: visibilityKm,
            weatherCode: Int(number(current?["weather_code"]) ?? 0),
            cloudCoverPercent: number(current?["cloud_cover"]).map { Int($0) },
            hourly: hourlyList,
            daily: dailyList
        )
    }

    /// Iterates the `time` array and reads each parallel variable defensively.
    /// Entries whose timestamp will not parse, or whose temperature is missing,
    /// are skipped rather than substituted, matching the Android repository.
    private static func parseHourly(_ hourly: [String: Any]?) -> [HourlyForecast] {
        guard let hourly = hourly,
              let times = hourly["time"] as? [Any] else {
            return []
        }

        let temperatures = hourly["temperature_2m"] as? [Any]
        let windSpeeds = hourly["wind_speed_10m"] as? [Any]
        let windDirections = hourly["wind_direction_10m"] as? [Any]
        let rainProbabilities = hourly["precipitation_probability"] as? [Any]
        let weatherCodes = hourly["weather_code"] as? [Any]

        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var result: [HourlyForecast] = []
        for index in 0..<times.count {
            guard let rawTime = string(times, index),
                  let date = parseLocalDateTime(rawTime) else {
                continue
            }
            guard let temperature = number(temperatures, index) else {
                continue
            }

            let epochMs = Int64(date.timeIntervalSince1970 * 1000)

            result.append(HourlyForecast(
                epochMs: epochMs,
                temperatureC: temperature,
                windSpeedKmh: number(windSpeeds, index) ?? 0.0,
                windDirectionDeg: number(windDirections, index) ?? 0.0,
                rainProbabilityPercent: integer(rainProbabilities, index) ?? 0,
                weatherCode: integer(weatherCodes, index) ?? 0
            ))
        }

        // The API returns whole days from midnight, so trim to the upcoming hours.
        let upcoming = result.filter { $0.epochMs >= now - 3_600_000 }
        let selected = upcoming.isEmpty ? result : upcoming
        return Array(selected.prefix(24))
    }

    private static func parseDaily(_ daily: [String: Any]?) -> [DailyForecast] {
        guard let daily = daily,
              let times = daily["time"] as? [Any] else {
            return []
        }

        let maxTemperatures = daily["temperature_2m_max"] as? [Any]
        let minTemperatures = daily["temperature_2m_min"] as? [Any]
        let rainProbabilities = daily["precipitation_probability_max"] as? [Any]
        let weatherCodes = daily["weather_code"] as? [Any]
        let maxWindSpeeds = daily["wind_speed_10m_max"] as? [Any]

        var result: [DailyForecast] = []
        for index in 0..<times.count {
            guard let rawDay = string(times, index),
                  let dayStart = parseLondonDayStart(rawDay) else {
                continue
            }

            let code = integer(weatherCodes, index) ?? 0
            let minTemp = number(minTemperatures, index) ?? 0.0
            let maxTemp = number(maxTemperatures, index) ?? minTemp
            let maxWind = number(maxWindSpeeds, index) ?? 0.0

            result.append(DailyForecast(
                epochMs: Int64(dayStart.timeIntervalSince1970 * 1000),
                tempMinC: minTemp,
                tempMaxC: maxTemp,
                rainProbabilityPercent: integer(rainProbabilities, index) ?? 0,
                weatherCode: code,
                windSpeedMaxKmh: maxWind,
                // Risk flags mirror the Android repository: frost from minimum
                // temperature, fog from WMO 45/48, storm from thunderstorm codes.
                frostRisk: minTemp <= 0.0,
                fogRisk: code == 45 || code == 48,
                stormRisk: code >= 95
            ))
        }
        return result
    }

    // MARK: - JSON value helpers

    private static func number(_ array: [Any]?, _ index: Int) -> Double? {
        guard let array = array, index >= 0, index < array.count else { return nil }
        return number(array[index])
    }

    private static func number(_ value: Any?) -> Double? {
        if let double = value as? Double { return double }
        if let integer = value as? Int { return Double(integer) }
        if let number = value as? NSNumber { return number.doubleValue }
        return nil
    }

    private static func integer(_ array: [Any]?, _ index: Int) -> Int? {
        guard let array = array, index >= 0, index < array.count else { return nil }
        if let integer = array[index] as? Int { return integer }
        if let double = array[index] as? Double { return Int(double) }
        if let number = array[index] as? NSNumber { return number.intValue }
        return nil
    }

    private static func string(_ array: [Any], _ index: Int) -> String? {
        guard index >= 0, index < array.count else { return nil }
        return array[index] as? String
    }

    // MARK: - Dates

    private static let london: TimeZone = TimeZone(identifier: "Europe/London") ?? TimeZone(secondsFromGMT: 0)!

    /// Parses Open-Meteo local timestamps such as "2026-10-16T13:00".
    /// `timezone=Europe/London` means the values carry no offset, so they must be
    /// interpreted in that zone; an offset-bearing value is accepted as a fallback.
    private static func parseLocalDateTime(_ value: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = london

        for pattern in ["yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH:mm:ss"] {
            formatter.dateFormat = pattern
            if let date = formatter.date(from: value) {
                return date
            }
        }

        let iso = ISO8601DateFormatter()
        if let date = iso.date(from: value) {
            return date
        }
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return iso.date(from: value)
    }

    /// Daily entries arrive as "2026-10-16" and represent a local calendar day.
    private static func parseLondonDayStart(_ value: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = london
        formatter.dateFormat = "yyyy-MM-dd"

        if let date = formatter.date(from: value) {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = london
            return calendar.startOfDay(for: date)
        }

        // Tolerate a full timestamp by trimming to the date component.
        if value.count > 10 {
            let truncated = String(value.prefix(10))
            return parseLondonDayStart(truncated)
        }
        return nil
    }
}
