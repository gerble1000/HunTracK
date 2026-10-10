import SwiftUI

public struct WindScreen: View {
    @ObservedObject private var weatherService = WeatherService.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @State private var selectedMarkerId: Int64?
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Wind & Scent")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(theme.palette.cream)
                        .padding(.top, 16)
                    
                    GlassPanel {
                        SectionLabel("Scent Dispersion Dial")
                        if let w = weatherService.weather {
                            let downwind = (Int(w.windDirectionDeg) + 180) % 360
                            VStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .stroke(theme.palette.slate, lineWidth: 8)
                                        .frame(width: 200, height: 200)
                                    
                                    Image(systemName: "arrow.down")
                                        .font(.system(size: 40, weight: .bold))
                                        .foregroundColor(theme.palette.bronzeBright)
                                        .rotationEffect(.degrees(Double(w.windDirectionDeg)))
                                }
                                
                                Text("\(String(format: "%.1f km/h", w.windSpeedKmh)) · Blowing to \(downwind)°")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundColor(theme.palette.cream)
                                
                                Text("Your scent travels downwind towards \(downwind)°. Approach quarry from downwind (into the face of the wind).")
                                    .font(.system(size: 13))
                                    .foregroundColor(theme.palette.mist)
                            }
                            .frame(maxWidth: .infinity)
                        } else {
                            Text("Waiting for live wind data…")
                                .foregroundColor(theme.palette.mist)
                        }
                    }
                    
                    SectionLabel("Field Markers Check")
                    if db.markers.isEmpty {
                        Text("No markers placed yet. Drop markers on the map to evaluate scent risk.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.mist)
                    } else {
                        ForEach(db.markers) { marker in
                            GlassPanel(compact: true) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(marker.name)
                                            .font(.system(size: 16, weight: .semibold))
                                            .foregroundColor(theme.palette.cream)
                                        Text(marker.category.label)
                                            .font(.system(size: 13))
                                            .foregroundColor(theme.palette.mist)
                                    }
                                    Spacer()
                                    Text("Clear")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(theme.palette.success)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(theme.palette.success.opacity(0.2))
                                        .cornerRadius(6)
                                }
                            }
                        }
                    }
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
