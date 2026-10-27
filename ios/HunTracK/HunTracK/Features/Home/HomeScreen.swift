import SwiftUI
import UIKit

public struct HomeScreen: View {
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var weatherService = WeatherService.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @State private var showingQuickAdd = false
    @State private var showingCopySheet = false
    @ObservedObject private var tides = TideService.shared

    public init() {}

    public var body: some View {
        // Computed once per render and shared by the rating, the astronomy panel
        // and the tide card. AstronomyCalculator is not cheap, so never call it
        // more than once here.
        let fix = location.latestFix ?? location.fallbackFix()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let astro = AstronomyCalculator.calculate(latitude: fix.latitude, longitude: fix.longitude, epochMs: now)
        let rating = HuntingConditionsRating.rate(weather: weatherService.weather, astronomy: astro, nowEpochMs: now)

        return ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header
                    emergencyLink
                    conditionsPanel(rating)
                    weatherPanel
                    astronomyPanel(astro)
                    gpsPanel(fix)
                    tidePanel
                    quickAddButton

                    Text("Guidance only — verify local regulations and conditions.")
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                        .padding(.top, 4)
                }
                .padding(16)
            }
        }
        .sheet(isPresented: $showingCopySheet) {
            HomeCopyLocationSheet(
                cardinal: HomeScreen.formatCoordinates(fix.latitude, fix.longitude),
                decimal: String(format: "%.5f, %.5f", fix.latitude, fix.longitude),
                osGrid: OsGridRef.latLngToOsGrid(latitude: fix.latitude, longitude: fix.longitude)
            )
        }
        .sheet(isPresented: $showingQuickAdd) {
            HomeQuickAddMarkerSheet(latitude: fix.latitude, longitude: fix.longitude)
        }
        .onAppear {
            location.requestPermission()
            location.startUpdating()
            if let latest = location.latestFix {
                weatherService.fetchWeather(latitude: latest.latitude, longitude: latest.longitude)
            } else {
                let fallback = location.fallbackFix()
                weatherService.fetchWeather(latitude: fallback.latitude, longitude: fallback.longitude)
            }
            tides.load()
        }
        .onChange(of: db.preferences.tidesEnabled) { _ in tides.load() }
        .onChange(of: db.preferences.tideStationId) { _ in tides.load() }
    }

    // MARK: - Panels

    @ViewBuilder
    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("HunTracK")
                .font(.field(size: 32, weight: .bold))
                .foregroundColor(theme.palette.bronze)
            Text("Field dashboard · live updates")
                .font(.field(size: 14))
                .foregroundColor(theme.palette.mist)
        }
        .padding(.top, 8)
    }

    @ViewBuilder
    private var emergencyLink: some View {
        NavigationLink(destination: EmergencyScreen()) {
            HStack {
                Spacer()
                Text("IN CASE OF EMERGENCY")
                    .font(.field(size: 14, weight: .bold))
                    .foregroundColor(theme.palette.danger)
                Spacer()
            }
            .padding(.vertical, 12)
            .background(Color.clear)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white, lineWidth: 2)
            )
        }
    }

    @ViewBuilder
    private func conditionsPanel(_ rating: ActivityRating) -> some View {
        GlassPanel {
            SectionLabel("Hunting conditions")
            Text(rating.title)
                .font(.field(size: 20, weight: .semibold))
                .foregroundColor(theme.palette.cream)
            Text(rating.level.rawValue)
                .font(.field(size: 28, weight: .bold))
                .foregroundColor(colorForLevel(rating.level))
            Text("Reassessed every minute · weather live")
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)

            ForEach(rating.reasons, id: \.self) { reason in
                Text("• \(reason)")
                    .font(.field(size: 14))
                    .foregroundColor(theme.palette.cream)
            }
        }
    }

    @ViewBuilder
    private var weatherPanel: some View {
        GlassPanel {
            SectionLabel("Weather")
            if let w = weatherService.weather {
                Text("Updated \(HomeScreen.relativeAge(w.fetchedAtEpochMs))") 
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.gold)

                Text(HomeScreen.weatherCodeLabel(w.weatherCode))
                    .font(.field(size: 18, weight: .semibold))
                    .foregroundColor(theme.palette.cream)

                MetricRow("Temperature", formatTemp(w.temperatureC))
                MetricRow("Feels like", formatTemp(w.feelsLikeC))
                MetricRow("Humidity", "\(w.humidityPercent)%")
                MetricRow("Pressure", String(format: "%.1f hPa", w.pressureHpa))
                MetricRow(
                    "Wind",
                    "\(formatWind(w.windSpeedKmh)) \(HomeScreen.bearingToCardinal(w.windDirectionDeg))"
                )
                if let gust = w.windGustKmh {
                    MetricRow("Gusts", formatWind(gust))
                }
                if let rain = w.rainProbabilityPercent {
                    MetricRow("Rain chance", "\(rain)%")
                }
                if let vis = w.visibilityKm {
                    MetricRow("Visibility", formatVisibility(vis))
                }
            } else if weatherService.loading {
                Text("Loading weather…")
                    .foregroundColor(theme.palette.mist)
            } else {
                Text(weatherService.error ?? "Waiting for location fix…")
                    .foregroundColor(theme.palette.mist)
            }
        }
    }

    @ViewBuilder
    private func astronomyPanel(_ a: AstronomyInfo) -> some View {
        GlassPanel {
            SectionLabel("Astronomy & sun")
            MetricRow("Sunrise", HomeScreen.formatTime(a.sunriseEpochMs))
            MetricRow("Sunset", HomeScreen.formatTime(a.sunsetEpochMs))
            MetricRow("Golden hour", HomeScreen.formatTime(a.goldenHourStartEpochMs))
            MetricRow("Fox & rabbit from", HomeScreen.formatTime(a.goldenHourEndEpochMs))

            Rectangle()
                .fill(theme.palette.glassBorder.opacity(0.5))
                .frame(height: 1)
                .padding(.vertical, 6)

            MetricRow("Moon", HomeScreen.moonPhaseLabel(a.moonPhase))
            MetricRow("Illumination", "\(Int(a.moonIllumination * 100))%")
            MetricRow("Moonrise", HomeScreen.formatTime(a.moonriseEpochMs))
            MetricRow("Moonset", HomeScreen.formatTime(a.moonsetEpochMs))

            Text("Times use 24-hour clock (local time)")
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)
        }
    }

    @ViewBuilder
    private func gpsPanel(_ fix: GpsFix) -> some View {
        GlassPanel {
            SectionLabel("GPS & Field Position")

            let osGrid = db.preferences.showOsGridRef
                ? OsGridRef.latLngToOsGrid(latitude: fix.latitude, longitude: fix.longitude)
                : nil

            Text(HomeScreen.formatCoordinates(fix.latitude, fix.longitude))
                .font(.field(size: 18, weight: .semibold))
                .foregroundColor(theme.palette.cream)

            if let osGrid = osGrid {
                Text(osGrid)
                    .font(.field(size: 18, weight: .bold))
                    .foregroundColor(theme.palette.bronzeBright)
                    .padding(.top, 2)
            }

            if location.latestFix == nil {
                Text("Using UK fallback until satellite lock")
                    .font(.field(size: 12))
                    .foregroundColor(theme.palette.mist)
            }

            Button(action: {
                SoundManager.shared.play(.click)
                showingCopySheet = true
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "doc.on.doc")
                    Text("Copy location")
                        .font(.field(size: 15, weight: .semibold))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(theme.palette.bronze)
                )
                .foregroundColor(theme.palette.ink)
            }
            .padding(.top, 10)

            Rectangle()
                .fill(theme.palette.glassBorder.opacity(0.45))
                .frame(height: 1)
                .padding(.vertical, 8)

            MetricRow("Accuracy", "±\(Int(fix.accuracyMeters)) m")
            if let alt = fix.altitudeMeters {
                MetricRow("Elevation", "\(Int(alt)) m")
            }
            if let bearing = fix.bearingDegrees {
                MetricRow("Heading", "\(Int(bearing))° \(HomeScreen.bearingToCardinal(Double(bearing)))")
            }
        }
    }

    @ViewBuilder
    private var tidePanel: some View {
        if db.preferences.tidesEnabled {
            GlassPanel {
                SectionLabel("Tides")

                if db.preferences.tideStationId.isEmpty {
                    Text("Select a port in Settings → UK tide times")
                        .font(.field(size: 13))
                        .foregroundColor(theme.palette.mist)
                } else {
                    Text(db.preferences.tideStationName.isEmpty ? "Selected port" : db.preferences.tideStationName)
                        .font(.field(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    Text(tides.sourceLabel.isEmpty ? "Loading tide times…" : tides.sourceLabel)
                        .font(.field(size: 12, weight: .medium))
                        .foregroundColor(theme.palette.mist)

                    let upcoming = tides.upcomingEvents(limit: 4)

                    if upcoming.isEmpty {
                        Text(tides.loading ? "Loading tide times…" : "No upcoming tides — pull to refresh")
                            .font(.field(size: 13))
                            .foregroundColor(theme.palette.mist)
                            .padding(.top, 8)
                    } else {
                        ForEach(Array(upcoming.indices), id: \.self) { index in
                            let event = upcoming[index]
                            MetricRow(
                                index == 0
                                    ? "\(HomeScreen.tideLabel(event.type)) (next)"
                                    : HomeScreen.tideLabel(event.type),
                                "\(HomeScreen.formatTime(event.epochMs)) · \(String(format: "%.1f m", event.heightMeters))"
                            )
                        }
                    }

                    Text(tideFooter)
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                        .padding(.top, 6)
                }
            }
        }
    }

    private var tideFooter: String {
        guard let source = tides.forecast?.source else {
            return "Calculated on device from the lunar cycle — approximate."
        }
        switch source {
        case .live: return "Live from TideTimes.org.uk — verify against local charts."
        case .cache: return "Cached tide times, refreshed daily when online."
        case .calculated: return "Calculated on device from the lunar cycle — approximate."
        case .unavailable: return "Tide times unavailable offline."
        }
    }

    @ViewBuilder
    private var quickAddButton: some View {
        HuntButton("Mark current location", icon: "mappin.and.ellipse") {
            showingQuickAdd = true
        }
        .padding(.top, 4)
    }

    // MARK: - Logic

    private func formatTemp(_ celsius: Double) -> String {
        db.preferences.useCelsius
            ? String(format: "%.1f°C", celsius)
            : String(format: "%.1f°F", celsius * 9 / 5 + 32)
    }

    private func formatWind(_ kmh: Double) -> String {
        db.preferences.windUnit == .mph
            ? String(format: "%.1f mph", kmh * 0.621371)
            : String(format: "%.1f km/h", kmh)
    }

    private func formatVisibility(_ km: Double) -> String {
        db.preferences.unitSystem == .imperial
            ? String(format: "%.1f mi", km * 0.621371)
            : String(format: "%.1f km", km)
    }

    private func colorForLevel(_ level: ActivityLevel) -> Color {
        switch level {
        case .high: return theme.palette.success
        case .moderate: return theme.palette.gold
        case .low: return theme.palette.danger
        }
    }

    // MARK: - Formatting

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.timeZone = TimeZone(identifier: "Europe/London")
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    static func formatTime(_ epochMs: Int64?) -> String {
        guard let epochMs = epochMs else { return "—" }
        return timeFormatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    /// Ported from Android's `relativeAge` used by the Home weather panel.
    static func relativeAge(_ epochMs: Int64) -> String {
        let minutes = max(0, Int((Date().timeIntervalSince1970 * 1000 - Double(epochMs)) / 60000))
        if minutes < 1 { return "just now" }
        if minutes == 1 { return "1 min ago" }
        if minutes < 60 { return "\(minutes) min ago" }
        return "\(minutes / 60)h ago"
    }

    /// Ported from Android's `weatherCodeLabel` (WMO codes).
    static func weatherCodeLabel(_ code: Int) -> String {
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

    /// Ported from Android's `moonPhaseLabel`.
    static func moonPhaseLabel(_ phase: Double) -> String {
        if phase < 0.03 || phase > 0.97 { return "New Moon" }
        if phase < 0.22 { return "Waxing Crescent" }
        if phase < 0.28 { return "First Quarter" }
        if phase < 0.47 { return "Waxing Gibbous" }
        if phase < 0.53 { return "Full Moon" }
        if phase < 0.72 { return "Waning Gibbous" }
        if phase < 0.78 { return "Last Quarter" }
        return "Waning Crescent"
    }

    /// Ported from Android's `formatCoordinates`: "52.48620°N  1.89040°W".
    static func formatCoordinates(_ latitude: Double, _ longitude: Double) -> String {
        let latHem = latitude >= 0 ? "N" : "S"
        let lngHem = longitude >= 0 ? "E" : "W"
        return String(format: "%.5f°%@  %.5f°%@", abs(latitude), latHem, abs(longitude), lngHem)
    }

    /// Ported from Android's `bearingToCardinal`.
    static func bearingToCardinal(_ degrees: Double) -> String {
        let directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
        let normalized = ((degrees.truncatingRemainder(dividingBy: 360)) + 360)
            .truncatingRemainder(dividingBy: 360)
        let index = Int((normalized / 45.0).rounded()) % 8
        return directions[index]
    }

    static func tideLabel(_ type: TideEventType) -> String {
        switch type {
        case .high: return "High water"
        case .low: return "Low water"
        case .unknown: return "Tide"
        }
    }
}

// MARK: - Copy location sheet

private struct HomeCopyLocationSheet: View {
    let cardinal: String
    let decimal: String
    let osGrid: String?

    @ObservedObject private var theme = ThemeManager.shared
    @Environment(\.dismiss) private var dismiss
    @State private var copied: String?

    var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Copy location")
                        .font(.field(size: 22, weight: .bold))
                        .foregroundColor(theme.palette.cream)
                    Text("Choose a format to copy to the clipboard")
                        .font(.field(size: 13))
                        .foregroundColor(theme.palette.mist)
                        .padding(.bottom, 4)

                    option(title: "Cardinal GPS", subtitle: cardinal)
                    option(title: "Decimal GPS", subtitle: decimal)

                    if let osGrid = osGrid {
                        option(title: "OS Grid", subtitle: osGrid)
                    } else {
                        Text("OS Grid unavailable outside Great Britain")
                            .font(.field(size: 13))
                            .foregroundColor(theme.palette.mist)
                    }

                    Button("Cancel") { dismiss() }
                        .font(.field(size: 15, weight: .semibold))
                        .foregroundColor(theme.palette.bronze)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .padding(20)
            }
        }
    }

    @ViewBuilder
    private func option(title: String, subtitle: String) -> some View {
        let isCopied = copied == title
        let accent = isCopied ? theme.palette.success : theme.palette.bronzeBright

        Button(action: {
            UIPasteboard.general.string = subtitle
            SoundManager.shared.play(.success)
            copied = title
        }) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.field(size: 15, weight: .semibold))
                        .foregroundColor(accent)
                    Text(subtitle)
                        .font(.field(size: 14))
                        .foregroundColor(theme.palette.cream)
                }
                Spacer()
                Image(systemName: isCopied ? "checkmark" : "doc.on.doc")
                    .foregroundColor(accent)
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(isCopied ? theme.palette.success.opacity(0.14) : theme.palette.forestDeep.opacity(0.55))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(accent.opacity(0.75), lineWidth: 1)
            )
        }
    }
}

// MARK: - Quick add marker sheet

private struct HomeQuickAddMarkerSheet: View {
    let latitude: Double
    let longitude: Double

    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var name: String = ""
    @State private var category: MarkerCategory = .highSeat
    @State private var notes: String = ""

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        GlassPanel {
                            SectionLabel("Marker details")

                            TextField("Name", text: $name)
                                .textFieldStyle(RoundedBorderTextFieldStyle())

                            Picker("Category", selection: $category) {
                                ForEach(MarkerCategory.allCases, id: \.self) { value in
                                    Text(value.label).tag(value)
                                }
                            }

                            TextField("Notes (optional)", text: $notes)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                        }

                        GlassPanel {
                            SectionLabel("Location")
                            Text(String(format: "%.5f, %.5f", latitude, longitude))
                                .foregroundColor(theme.palette.cream)
                            if let osGrid = OsGridRef.latLngToOsGrid(latitude: latitude, longitude: longitude) {
                                Text(osGrid)
                                    .foregroundColor(theme.palette.bronzeBright)
                            }
                        }

                        HuntButton("Save marker here", icon: "mappin") {
                            let marker = FieldMarker(
                                category: category,
                                name: name.isEmpty ? category.label : name,
                                notes: notes,
                                latitude: latitude,
                                longitude: longitude
                            )
                            db.addMarker(marker)
                            dismiss()
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Mark location")
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarItems(leading: Button("Cancel") { dismiss() })
        }
    }
}
