import SwiftUI

public struct WeatherScreen: View {
    @ObservedObject private var weatherService = WeatherService.shared
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var theme = ThemeManager.shared
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Weather")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        Text("UK forecast · live · Open-Meteo")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }
                    .padding(.top, 8)
                    
                    GlassPanel {
                        SectionLabel("Current Conditions")
                        if let w = weatherService.weather {
                            HStack(spacing: 16) {
                                Image(systemName: "cloud.sun.fill")
                                    .font(.system(size: 48))
                                    .foregroundColor(theme.palette.gold)
                                VStack(alignment: .leading) {
                                    Text(String(format: "%.1f°C", w.temperatureC))
                                        .font(.system(size: 36, weight: .bold))
                                        .foregroundColor(theme.palette.bronze)
                                    Text("Feels like \(String(format: "%.1f°C", w.feelsLikeC))")
                                        .font(.system(size: 14))
                                        .foregroundColor(theme.palette.mist)
                                }
                            }
                            .padding(.vertical, 8)
                            
                            MetricRow("Wind", String(format: "%.1f km/h", w.windSpeedKmh))
                            MetricRow("Wind Direction", String(format: "%.0f°", w.windDirectionDeg))
                            MetricRow("Humidity", "\(w.humidityPercent)%")
                            MetricRow("Pressure", String(format: "%.1f hPa", w.pressureHpa))
                            if let vis = w.visibilityKm {
                                MetricRow("Visibility", String(format: "%.1f km", vis))
                            }
                        } else {
                            Text("Loading forecast data…")
                                .foregroundColor(theme.palette.mist)
                        }
                    }
                }
                .padding(16)
            }
        }
    }
}
