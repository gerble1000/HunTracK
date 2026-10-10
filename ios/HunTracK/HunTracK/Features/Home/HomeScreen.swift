import SwiftUI

public struct HomeScreen: View {
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var weatherService = WeatherService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @State private var showingQuickAdd = false
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("HunTracK")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(theme.palette.bronze)
                        Text("Field dashboard · live updates")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }
                    .padding(.top, 8)
                    
                    // In Case of Emergency Button
                    NavigationLink(destination: EmergencyScreen()) {
                        HStack {
                            Spacer()
                            Text("IN CASE OF EMERGENCY")
                                .font(.system(size: 14, weight: .bold))
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
                    
                    // Hunting Conditions
                    GlassPanel {
                        SectionLabel("Hunting conditions")
                        let rating = currentRating()
                        Text(rating.title)
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                        Text(rating.level.rawValue)
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(colorForLevel(rating.level))
                        Text("Reassessed every minute · weather live")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.mist)
                        
                        ForEach(rating.reasons, id: \.self) { reason in
                            Text("• \(reason)")
                                .font(.system(size: 14))
                                .foregroundColor(theme.palette.cream)
                        }
                    }
                    
                    // Weather
                    GlassPanel {
                        SectionLabel("Weather")
                        if let w = weatherService.weather {
                            let tempStr = String(format: "%.1f°C", w.temperatureC)
                            let feelsStr = String(format: "%.1f°C", w.feelsLikeC)
                            let windStr = String(format: "%.1f km/h", w.windSpeedKmh)
                            
                            MetricRow("Temperature", tempStr)
                            MetricRow("Feels like", feelsStr)
                            MetricRow("Humidity", "\(w.humidityPercent)%")
                            MetricRow("Pressure", String(format: "%.1f hPa", w.pressureHpa))
                            MetricRow("Wind", windStr)
                        } else if weatherService.loading {
                            Text("Loading weather…")
                                .foregroundColor(theme.palette.mist)
                        } else {
                            Text("Waiting for location fix…")
                                .foregroundColor(theme.palette.mist)
                        }
                    }
                    
                    // GPS & OS Grid
                    GlassPanel {
                        SectionLabel("GPS & Field Position")
                        if let fix = location.latestFix {
                            let osGrid = OsGridRef.latLngToOsGrid(latitude: fix.latitude, longitude: fix.longitude) ?? "Outside GB"
                            Text(String(format: "%.5f, %.5f", fix.latitude, fix.longitude))
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            Text(osGrid)
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(theme.palette.bronzeBright)
                                .padding(.top, 2)
                            
                            MetricRow("Accuracy", "±\(Int(fix.accuracyMeters)) m")
                            if let alt = fix.altitudeMeters {
                                MetricRow("Elevation", "\(Int(alt)) m")
                            }
                        } else {
                            Text("Acquiring GPS fix…")
                                .foregroundColor(theme.palette.mist)
                        }
                    }
                }
                .padding(16)
            }
        }
        .onAppear {
            location.requestPermission()
            location.startUpdating()
            if let fix = location.latestFix {
                weatherService.fetchWeather(latitude: fix.latitude, longitude: fix.longitude)
            } else {
                let fallback = location.fallbackFix()
                weatherService.fetchWeather(latitude: fallback.latitude, longitude: fallback.longitude)
            }
        }
    }
    
    private func currentRating() -> ActivityRating {
        let fix = location.latestFix ?? location.fallbackFix()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let astro = AstronomyCalculator.calculate(latitude: fix.latitude, longitude: fix.longitude, epochMs: now)
        return HuntingConditionsRating.rate(weather: weatherService.weather, astronomy: astro, nowEpochMs: now)
    }
    
    private func colorForLevel(_ level: ActivityLevel) -> Color {
        switch level {
        case .high: return theme.palette.success
        case .moderate: return theme.palette.gold
        case .low: return theme.palette.danger
        }
    }
}
