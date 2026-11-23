import SwiftUI

public struct WeatherScreen: View {
    @ObservedObject private var weatherService = WeatherService.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared

    public init() {}

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Weather")
                            .font(.field(size: 32, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        Text("UK forecast · live · Open-Meteo")
                            .font(.field(size: 14))
                            .foregroundColor(theme.palette.mist)
                        if let w = weatherService.weather {
                            Text("Updated \(Self.relativeAge(w.fetchedAtEpochMs))")
                                .font(.field(size: 12, weight: .semibold))
                                .foregroundColor(theme.palette.gold)
                        }
                    }
                    .padding(.top, 8)

                    alertsSection

                    currentConditions

                    hourlySection

                    dailySection
                }
                .padding(16)
            }
        }
    }

    // MARK: - Sections

    @ViewBuilder
    private var alertsSection: some View {
        let alerts = alertFlags
        if !alerts.isEmpty {
            GlassPanel {
                SectionLabel("Alerts")
                Text(alerts.joined(separator: " · "))
                    .font(.field(size: 16, weight: .semibold))
                    .foregroundColor(theme.palette.gold)
            }
        }
    }

    private var currentConditions: some View {
        GlassPanel {
            SectionLabel("Current Conditions")
            if let w = weatherService.weather {
                let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                let sun = AstronomyCalculator.calculate(latitude: w.latitude, longitude: w.longitude, epochMs: nowMs)
                let currentIsDay = nowMs >= sun.sunriseEpochMs && nowMs <= sun.sunsetEpochMs
                HStack(spacing: 16) {
                    if currentIsDay {
                        Image(systemName: Self.weatherSymbol(w.weatherCode))
                            .font(.field(size: 48))
                            .foregroundColor(theme.palette.gold)
                    } else {
                        CurrentNightSkyIcon(code: w.weatherCode, epochMs: nowMs)
                    }
                    VStack(alignment: .leading) {
                        Text(Self.weatherCodeLabel(w.weatherCode))
                            .font(.field(size: 16, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                        Text(formatTemp(w.temperatureC))
                            .font(.field(size: 36, weight: .bold))
                            .foregroundColor(theme.palette.bronze)
                        Text("Feels like \(formatTemp(w.feelsLikeC))")
                            .font(.field(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }
                }
                .padding(.vertical, 8)

                MetricRow("Wind", "\(formatWindSpeed(w.windSpeedKmh)) \(bearingLabel(w.windDirectionDeg))")
                if let gust = w.windGustKmh {
                    MetricRow("Gusts", formatWindSpeed(gust))
                }
                MetricRow("Humidity", "\(w.humidityPercent)%")
                MetricRow("Pressure", String(format: "%.1f hPa", w.pressureHpa))
                if let vis = w.visibilityKm {
                    MetricRow("Visibility", String(format: "%.1f km", vis))
                }
            } else if weatherService.loading {
                Text("Loading forecast data…")
                    .foregroundColor(theme.palette.mist)
            } else {
                Text(weatherService.error ?? "Waiting for location fix…")
                    .foregroundColor(theme.palette.mist)
            }
        }
    }

    private var hourlySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel("Next 24 hours")
            let hours = Self.upcomingHours(weatherService.weather?.hourly ?? [])
            if hours.isEmpty {
                GlassPanel {
                    Text("Hourly forecast unavailable.")
                        .font(.field(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(hours) { hour in
                            HourCard(
                                hour: hour,
                                useCelsius: db.preferences.useCelsius,
                                windUnit: db.preferences.windUnit,
                                latitude: weatherService.weather?.latitude ?? 0,
                                longitude: weatherService.weather?.longitude ?? 0
                            )
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    private var dailySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel("Five day")
            let days = weatherService.weather?.daily ?? []
            if days.isEmpty {
                GlassPanel {
                    Text("Daily forecast unavailable.")
                        .font(.field(size: 13))
                        .foregroundColor(theme.palette.mist)
                }
            } else {
                ForEach(days) { day in
                    DayCard(
                        day: day,
                        useCelsius: db.preferences.useCelsius,
                        windUnit: db.preferences.windUnit
                    )
                }
            }
        }
    }

    private var alertFlags: [String] {
        var flags: [String] = []
        for day in weatherService.weather?.daily ?? [] {
            if day.frostRisk, !flags.contains("Frost risk") { flags.append("Frost risk") }
            if day.fogRisk, !flags.contains("Fog") { flags.append("Fog") }
            if day.stormRisk, !flags.contains("Storm alert") { flags.append("Storm alert") }
        }
        return flags
    }

    // MARK: - Formatting

    private func formatTemp(_ celsius: Double) -> String {
        db.preferences.useCelsius
            ? String(format: "%.1f°C", celsius)
            : String(format: "%.1f°F", celsius * 9 / 5 + 32)
    }

    private func formatWindSpeed(_ kmh: Double) -> String {
        db.preferences.windUnit == .mph
            ? String(format: "%.1f mph", kmh * 0.621371)
            : String(format: "%.1f km/h", kmh)
    }

    private func bearingLabel(_ degrees: Double) -> String {
        Self.bearingToCardinal(degrees)
    }

    // MARK: - Static helpers

    private static func relativeAge(_ epochMs: Int64) -> String {
        let minutes = max(0, Int((Date().timeIntervalSince1970 * 1000 - Double(epochMs)) / 60000))
        if minutes < 1 { return "just now" }
        if minutes == 1 { return "1 min ago" }
        if minutes < 60 { return "\(minutes) min ago" }
        return "\(minutes / 60)h ago"
    }

    private static func bearingToCardinal(_ degrees: Double) -> String {
        let directions = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                          "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
        let normalized = ((degrees.truncatingRemainder(dividingBy: 360)) + 360)
            .truncatingRemainder(dividingBy: 360)
        let index = Int((normalized / 22.5).rounded()) % directions.count
        return directions[index]
    }

    private static func weatherSymbol(_ code: Int) -> String {
        switch code {
        case 0: return "sun.max.fill"
        case 1, 2: return "cloud.sun.fill"
        case 3: return "cloud.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51...57: return "cloud.drizzle.fill"
        case 61...67: return "cloud.rain.fill"
        case 71...77: return "cloud.snow.fill"
        case 80...82: return "cloud.heavyrain.fill"
        case 85, 86: return "cloud.snow.fill"
        case 95...99: return "cloud.bolt.rain.fill"
        default: return "cloud.sun.fill"
        }
    }

    private static func weatherCodeLabel(_ code: Int) -> String {
        switch code {
        case 0: return "Clear sky"
        case 1: return "Mainly clear"
        case 2: return "Partly cloudy"
        case 3: return "Overcast"
        case 45, 48: return "Fog"
        case 51, 53, 55: return "Drizzle"
        case 56, 57: return "Freezing drizzle"
        case 61, 63, 65: return "Rain"
        case 66, 67: return "Freezing rain"
        case 71, 73, 75: return "Snow"
        case 77: return "Snow grains"
        case 80, 81, 82: return "Rain showers"
        case 85, 86: return "Snow showers"
        case 95: return "Thunderstorm"
        case 96, 99: return "Thunderstorm with hail"
        default: return "Mixed conditions"
        }
    }

    /// Drops the current London hour and everything before it. The first chip is +1.
    private static func upcomingHours(_ hours: [HourlyForecast]) -> [HourlyForecast] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London") ?? .current
        let start = calendar.dateInterval(of: .hour, for: Date())?.start ?? Date()
        let next = calendar.date(byAdding: .hour, value: 1, to: start) ?? start
        let nextMs = Int64(next.timeIntervalSince1970 * 1000)
        return Array(hours.filter { $0.epochMs >= nextMs }.prefix(24))
    }
}

private struct CurrentNightSkyIcon: View {
    let code: Int
    let epochMs: Int64
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        let phaseName = MoonPhase.name(at: Date(timeIntervalSince1970: Double(epochMs) / 1000))
        ZStack {
            Image(systemName: "sparkles")
                .font(.field(size: 40))
                .foregroundColor(theme.palette.cream.opacity(0.9))
            if phaseName != .new {
                Image(systemName: phaseName.symbolName)
                    .font(.field(size: 36))
                    .foregroundColor(theme.palette.cream)
            }
            if code == 1 || code == 2 || code == 3 {
                Image(systemName: "cloud.fill")
                    .font(.field(size: code == 3 ? 34 : 26))
                    .foregroundColor(theme.palette.mist.opacity(code == 3 ? 0.92 : 0.78))
                    .offset(x: 14, y: 10)
            }
        }
        .frame(width: 56, height: 56)
    }
}

// MARK: - Cards

public struct HourCard: View {
    let hour: HourlyForecast
    let useCelsius: Bool
    let windUnit: WindUnit
    let latitude: Double
    let longitude: Double
    @ObservedObject private var theme = ThemeManager.shared

    public var body: some View {
        GlassPanel(compact: true) {
            VStack(spacing: 6) {
                Text(Self.offsetLabel(hour.epochMs))
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.cream)
                nightOrDayIcon
                    .frame(height: 22)
                Text(Self.temp(hour.temperatureC, useCelsius: useCelsius))
                    .font(.field(size: 16, weight: .bold))
                    .foregroundColor(theme.palette.bronze)
                Text("\(hour.rainProbabilityPercent)%")
                    .font(.field(size: 11))
                    .foregroundColor(theme.palette.mist)
                Text(Self.wind(hour.windSpeedKmh, unit: windUnit))
                    .font(.field(size: 11))
                    .foregroundColor(theme.palette.mist)
                Text(Self.cardinal(hour.windDirectionDeg))
                    .font(.field(size: 11, weight: .semibold))
                    .foregroundColor(theme.palette.mist)
            }
            .frame(width: 62)
        }
    }

    private var isNightClearOrCloudy: Bool {
        let day = Self.isCivilDay(latitude: latitude, longitude: longitude, epochMs: hour.epochMs)
        return !day && (hour.weatherCode == 0 || (1...3).contains(hour.weatherCode))
    }

    @ViewBuilder
    private var nightOrDayIcon: some View {
        if isNightClearOrCloudy {
            let phase = MoonPhase.fraction(epochMs: hour.epochMs)
            let phaseName = MoonPhase.name(of: phase)
            ZStack {
                Image(systemName: "sparkles")
                    .font(.field(size: 16))
                    .foregroundColor(theme.palette.cream.opacity(0.9))
                if phaseName != .new {
                    Image(systemName: phaseName.symbolName)
                        .font(.field(size: 15))
                        .foregroundColor(theme.palette.cream)
                }
                if hour.weatherCode == 1 || hour.weatherCode == 2 || hour.weatherCode == 3 {
                    Image(systemName: "cloud.fill")
                        .font(.field(size: hour.weatherCode == 3 ? 16 : 12))
                        .foregroundColor(theme.palette.mist.opacity(hour.weatherCode == 3 ? 0.92 : 0.78))
                        .offset(x: 7, y: 5)
                }
            }
        } else {
            Image(systemName: Self.symbol(hour.weatherCode))
                .font(.field(size: 20))
                .foregroundColor(theme.palette.gold)
        }
    }

    /// Current London hour is "Now". Later hours are "+1", "+2", … on their own line,
    /// so the number is not mixed up with the temperature underneath.
    static func offsetLabel(_ epochMs: Int64, now: Date = Date()) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London") ?? .current
        let slot = Date(timeIntervalSince1970: Double(epochMs) / 1000)
        let slotHour = calendar.dateInterval(of: .hour, for: slot)?.start ?? slot
        let nowHour = calendar.dateInterval(of: .hour, for: now)?.start ?? now
        let ahead = calendar.dateComponents([.hour], from: nowHour, to: slotHour).hour ?? 0
        if ahead == 0 { return "Now" }
        if ahead > 0 { return "+\(ahead)" }
        return "\(ahead)"
    }

    private static func temp(_ celsius: Double, useCelsius: Bool) -> String {
        useCelsius ? "\(Int(celsius.rounded()))°" : "\(Int((celsius * 9 / 5 + 32).rounded()))°"
    }

    private static func wind(_ kmh: Double, unit: WindUnit) -> String {
        unit == .mph ? String(format: "%.0fmph", kmh * 0.621371) : String(format: "%.0fkm/h", kmh)
    }

    private static func cardinal(_ degrees: Double) -> String {
        let directions = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                          "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
        let normalized = ((degrees.truncatingRemainder(dividingBy: 360)) + 360)
            .truncatingRemainder(dividingBy: 360)
        return directions[Int((normalized / 22.5).rounded()) % directions.count]
    }

    /// Daylight for one hourly chip. The chip is day when that clock hour overlaps
    /// civil dawn through civil dusk. The forecast is_day flag is ignored.
    static func isCivilDay(latitude: Double, longitude: Double, epochMs: Int64) -> Bool {
        let hourEnd = epochMs + 3_600_000
        if hourOverlapsCivilDay(latitude: latitude, longitude: longitude, sample: epochMs, hourEnd: hourEnd) {
            return true
        }
        return hourOverlapsCivilDay(latitude: latitude, longitude: longitude, sample: hourEnd - 1, hourEnd: hourEnd)
    }

    private static func hourOverlapsCivilDay(
        latitude: Double,
        longitude: Double,
        sample: Int64,
        hourEnd: Int64
    ) -> Bool {
        let sun = AstronomyCalculator.calculate(latitude: latitude, longitude: longitude, epochMs: sample)
        let hourStart = hourEnd - 3_600_000
        return hourStart < sun.civilTwilightEndEpochMs && hourEnd > sun.civilTwilightBeginEpochMs
    }

    /// Open-Meteo `is_day` when the hour has one; otherwise the shared sunrise/sunset calculator.
    static func isDaytime(flag: Bool?, latitude: Double, longitude: Double, epochMs: Int64) -> Bool {
        if let flag { return flag }
        let sun = AstronomyCalculator.calculate(latitude: latitude, longitude: longitude, epochMs: epochMs)
        return epochMs >= sun.sunriseEpochMs && epochMs <= sun.sunsetEpochMs
    }

    private static func symbol(_ code: Int, isDay: Bool = true) -> String {
        switch code {
        case 0: return "sun.max.fill"
        case 1, 2: return "cloud.sun.fill"
        case 3: return "cloud.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51...57: return "cloud.drizzle.fill"
        case 61...67: return "cloud.rain.fill"
        case 71...77: return "cloud.snow.fill"
        case 80...82: return "cloud.heavyrain.fill"
        case 85, 86: return "cloud.snow.fill"
        case 95...99: return "cloud.bolt.rain.fill"
        default: return isDay ? "cloud.sun.fill" : "cloud.moon.fill"
        }
    }

}

public struct DayCard: View {
    let day: DailyForecast
    let useCelsius: Bool
    let windUnit: WindUnit
    @ObservedObject private var theme = ThemeManager.shared

    public var body: some View {
        GlassPanel {
            HStack {
                Text(Self.dayName(day.epochMs))
                    .font(.field(size: 16, weight: .semibold))
                    .foregroundColor(theme.palette.cream)
                Spacer()
                Image(systemName: Self.symbol(day.weatherCode))
                    .font(.field(size: 18))
                    .foregroundColor(theme.palette.gold)
            }

            MetricRow("Range", Self.range(day.tempMinC, day.tempMaxC, useCelsius: useCelsius))
            MetricRow("Rain", "\(day.rainProbabilityPercent)%")
            MetricRow("Wind max", Self.wind(day.windSpeedMaxKmh, unit: windUnit))

            let flags = Self.flags(day)
            if !flags.isEmpty {
                Text(flags.joined(separator: " · "))
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.gold)
                    .padding(.top, 2)
            }
        }
    }

    private static func dayName(_ epochMs: Int64) -> String {
        dayFormatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    private static func range(_ min: Double, _ max: Double, useCelsius: Bool) -> String {
        if useCelsius {
            return "\(Int(min.rounded()))° / \(Int(max.rounded()))°C"
        }
        let low = min * 9 / 5 + 32
        let high = max * 9 / 5 + 32
        return "\(Int(low.rounded()))° / \(Int(high.rounded()))°F"
    }

    private static func wind(_ kmh: Double, unit: WindUnit) -> String {
        unit == .mph ? String(format: "%.1f mph", kmh * 0.621371) : String(format: "%.1f km/h", kmh)
    }

    private static func flags(_ day: DailyForecast) -> [String] {
        var values: [String] = []
        if day.frostRisk { values.append("Frost") }
        if day.fogRisk { values.append("Fog") }
        if day.stormRisk { values.append("Storm") }
        return values
    }

    private static func symbol(_ code: Int) -> String {
        switch code {
        case 0: return "sun.max.fill"
        case 1, 2: return "cloud.sun.fill"
        case 3: return "cloud.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51...57: return "cloud.drizzle.fill"
        case 61...67: return "cloud.rain.fill"
        case 71...77: return "cloud.snow.fill"
        case 80...82: return "cloud.heavyrain.fill"
        case 85, 86: return "cloud.snow.fill"
        case 95...99: return "cloud.bolt.rain.fill"
        default: return "cloud.sun.fill"
        }
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.timeZone = TimeZone(identifier: "Europe/London")
        formatter.dateFormat = "EEE d MMM"
        return formatter
    }()
}
