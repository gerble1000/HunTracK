import SwiftUI
import CoreLocation

public enum ScentRisk {
    case unknown
    case clear
    case risk
}

public enum WindCharacter {
    case slowNarrow
    case steady
    case fast
    case variableWide
    
    public var label: String {
        switch self {
        case .slowNarrow: return "Light · scent pools nearby"
        case .steady: return "Steady · long linear plume"
        case .fast: return "High · wind burn plume"
        case .variableWide: return "Gusty · scattered pockets"
        }
    }
}

public struct WindScreen: View {
    @ObservedObject private var weatherService = WeatherService.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var theme = ThemeManager.shared
    @State private var selectedMarkerId: Int64?
    @State private var showGuide: Bool = false
    
    public init() {}
    
    private var hunterFix: GpsFix? {
        location.latestFix ?? location.fallbackFix()
    }
    
    private func windCharacter(speed: Double?, gust: Double?) -> WindCharacter {
        guard let s = speed else { return .steady }
        let g = gust ?? s
        if g - s >= 8.0 {
            return .variableWide
        } else if s < 3.2 {
            return .slowNarrow
        } else if s >= 24.1 {
            return .fast
        } else {
            return .steady
        }
    }
    
    private func bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let phi1 = lat1 * .pi / 180.0
        let phi2 = lat2 * .pi / 180.0
        let deltaLambda = (lon2 - lon1) * .pi / 180.0
        let y = sin(deltaLambda) * cos(phi2)
        let x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        let degrees = atan2(y, x) * 180.0 / .pi
        return (degrees + 360.0).truncatingRemainder(dividingBy: 360.0)
    }
    
    private func angularDiff(_ a: Double, _ b: Double) -> Double {
        let diff = abs(a - b).truncatingRemainder(dividingBy: 360.0)
        return diff > 180.0 ? 360.0 - diff : diff
    }
    
    private func cardinalDirection(_ deg: Double) -> String {
        let cardinals = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                         "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
        let index = Int((deg + 11.25) / 22.5) % 16
        return cardinals[index]
    }
    
    private func evaluateRisk(for marker: FieldMarker) -> (risk: ScentRisk, distanceM: Double?, bearingDeg: Double?, diffDeg: Double?) {
        guard let w = weatherService.weather, let hunter = hunterFix else {
            return (.unknown, nil, nil, nil)
        }
        let downwind = Double((Int(w.windDirectionDeg) + 180) % 360)
        let targetBearing = bearing(lat1: hunter.latitude, lon1: hunter.longitude, lat2: marker.latitude, lon2: marker.longitude)
        let dist = DatabaseService.haversineMeters(hunter.latitude, hunter.longitude, marker.latitude, marker.longitude)
        let diff = angularDiff(downwind, targetBearing)
        
        let plume = ScentPlumeGeometry.forConditions(
            speedKmh: w.windSpeedKmh,
            gustKmh: w.windGustKmh,
            humidityPercent: w.relativeHumidityPercent,
            temperatureC: w.temperatureC
        )
        let riskThreshold = max(35.0, plume.halfAngleDeg * 2.0)
        let isRisk = diff <= riskThreshold && dist <= (plume.lengthM * 1.2)
        return (isRisk ? .risk : .clear, dist, targetBearing, diff)
    }
    
    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Header
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Wind & Scent")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(theme.palette.cream)
                            Text("Will animals smell you from downwind?")
                                .font(.system(size: 14))
                                .foregroundColor(theme.palette.mist)
                        }
                        Spacer()
                        Button(action: { showGuide = true }) {
                            Image(systemName: "questionmark.circle")
                                .font(.system(size: 22))
                                .foregroundColor(theme.palette.bronzeBright)
                        }
                    }
                    .padding(.top, 16)
                    
                    // Wind dial panel
                    GlassPanel {
                        SectionLabel("Scent Dispersion Dial")
                        if let w = weatherService.weather {
                            let downwind = (Int(w.windDirectionDeg) + 180) % 360
                            let fromCard = cardinalDirection(Double(w.windDirectionDeg))
                            let downCard = cardinalDirection(Double(downwind))
                            let character = windCharacter(speed: w.windSpeedKmh, gust: w.windGustKmh)
                            let plume = ScentPlumeGeometry.forConditions(
                                speedKmh: w.windSpeedKmh,
                                gustKmh: w.windGustKmh,
                                humidityPercent: w.relativeHumidityPercent,
                                temperatureC: w.temperatureC
                            )
                            
                            VStack(spacing: 14) {
                                ZStack {
                                    Circle()
                                        .stroke(theme.palette.slate.opacity(0.6), lineWidth: 10)
                                        .frame(width: 220, height: 220)
                                    
                                    // Scent cone wedge indicator
                                    Circle()
                                        .trim(from: 0.0, to: CGFloat(plume.halfAngleDeg * 2.0 / 360.0))
                                        .stroke(theme.palette.bronze.opacity(0.35), style: StrokeStyle(lineWidth: 30, lineCap: .round))
                                        .frame(width: 170, height: 170)
                                        .rotationEffect(.degrees(Double(downwind) - plume.halfAngleDeg - 90))
                                    
                                    // Hunter center dot
                                    Circle()
                                        .fill(theme.palette.gold)
                                        .frame(width: 14, height: 14)
                                    
                                    // Downwind arrow (your scent travels this way)
                                    VStack(spacing: 0) {
                                        Image(systemName: "arrow.down")
                                            .font(.system(size: 38, weight: .black))
                                            .foregroundColor(theme.palette.bronzeBright)
                                        Spacer()
                                    }
                                    .frame(width: 40, height: 180)
                                    .rotationEffect(.degrees(Double(w.windDirectionDeg)))
                                }
                                .padding(.vertical, 8)
                                
                                Text("\(String(format: "%.1f km/h", w.windSpeedKmh)) · \(character.label)")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(theme.palette.cream)
                                
                                if let gust = w.windGustKmh {
                                    Text("Gusts up to \(String(format: "%.1f km/h", gust))")
                                        .font(.system(size: 14))
                                        .foregroundColor(theme.palette.mist)
                                }
                                
                                HStack(spacing: 20) {
                                    VStack(spacing: 2) {
                                        Text("Wind from")
                                            .font(.system(size: 12))
                                            .foregroundColor(theme.palette.mist)
                                        Text("\(fromCard) (\(Int(w.windDirectionDeg))°)")
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(theme.palette.gold)
                                    }
                                    Divider().frame(height: 24)
                                    VStack(spacing: 2) {
                                        Text("Scent drifts to")
                                            .font(.system(size: 12))
                                            .foregroundColor(theme.palette.mist)
                                        Text("\(downCard) (\(downwind)°)")
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(theme.palette.bronzeBright)
                                    }
                                    Divider().frame(height: 24)
                                    VStack(spacing: 2) {
                                        Text("Plume length")
                                            .font(.system(size: 12))
                                            .foregroundColor(theme.palette.mist)
                                        Text("~\(Int(plume.lengthM))m")
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(theme.palette.cream)
                                    }
                                }
                                .padding(.top, 4)
                                
                                Text("Your scent travels downwind towards \(downwind)°. Approach quarry from downwind (into the face of the wind) and keep a minimum standoff of \(Int(plume.approachStandoffM))m.")
                                    .font(.system(size: 13))
                                    .foregroundColor(theme.palette.mist)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 8)
                            }
                            .frame(maxWidth: .infinity)
                        } else {
                            VStack(spacing: 10) {
                                Text("Waiting for live wind data…")
                                    .font(.system(size: 14))
                                    .foregroundColor(theme.palette.mist)
                                Button("Refresh Weather") {
                                    if let fix = hunterFix {
                                        weatherService.fetchWeather(latitude: fix.latitude, longitude: fix.longitude)
                                    }
                                }
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(theme.palette.bronzeBright)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                        }
                    }
                    
                    // Field Markers Check
                    SectionLabel("Field Markers Scent Risk")
                    if db.markers.isEmpty {
                        GlassPanel(compact: true) {
                            Text("No markers placed yet. Drop markers on the map to evaluate live scent risk.")
                                .font(.system(size: 14))
                                .foregroundColor(theme.palette.mist)
                        }
                    } else {
                        ForEach(db.markers) { marker in
                            let eval = evaluateRisk(for: marker)
                            let isSelected = selectedMarkerId == marker.id
                            
                            Button(action: {
                                selectedMarkerId = isSelected ? nil : marker.id
                            }) {
                                GlassPanel(compact: true) {
                                    VStack(alignment: .leading, spacing: 8) {
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
                                            
                                            switch eval.risk {
                                            case .risk:
                                                HStack(spacing: 4) {
                                                    Image(systemName: "exclamationmark.triangle.fill")
                                                    Text("RISK")
                                                }
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundColor(theme.palette.danger)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(theme.palette.danger.opacity(0.2))
                                                .cornerRadius(6)
                                            case .clear:
                                                HStack(spacing: 4) {
                                                    Image(systemName: "checkmark.circle.fill")
                                                    Text("CLEAR")
                                                }
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundColor(theme.palette.success)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(theme.palette.success.opacity(0.2))
                                                .cornerRadius(6)
                                            case .unknown:
                                                Text("Waiting GPS")
                                                    .font(.system(size: 12))
                                                    .foregroundColor(theme.palette.mist)
                                            }
                                        }
                                        
                                        if let dist = eval.distanceM, let bearingDeg = eval.bearingDeg {
                                            HStack(spacing: 12) {
                                                Text("\(Int(dist))m away")
                                                    .font(.system(size: 13))
                                                    .foregroundColor(theme.palette.mist)
                                                Text("·")
                                                    .foregroundColor(theme.palette.mist)
                                                Text("\(Int(bearingDeg))° \(cardinalDirection(bearingDeg))")
                                                    .font(.system(size: 13))
                                                    .foregroundColor(theme.palette.mist)
                                            }
                                        }
                                        
                                        if isSelected {
                                            Divider().background(theme.palette.slate)
                                            if eval.risk == .risk {
                                                Text("⚠️ WARNING: Your scent cone is drifting straight towards this target location. Consider circling around for an upwind approach.")
                                                    .font(.system(size: 12))
                                                    .foregroundColor(theme.palette.danger)
                                            } else {
                                                Text("✓ Clear: Scent is drifting away from this target. Safe approach vector.")
                                                    .font(.system(size: 12))
                                                    .foregroundColor(theme.palette.success)
                                            }
                                        }
                                    }
                                }
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showGuide) {
            WindGuideSheet(isPresented: $showGuide)
        }
    }
}

public struct WindGuideSheet: View {
    @Binding var isPresented: Bool
    @ObservedObject private var theme = ThemeManager.shared
    
    public var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Wind & Scent Field Guide")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                            .padding(.top, 16)
                        
                        GlassPanel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Rule 1: The Face of the Wind")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("Always stalk into the face of the wind or across it. Scent travels downwind at the speed of the air mass, alerting deer long before you arrive.")
                                    .font(.system(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }
                        
                        GlassPanel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Thermal Drafts")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("In early morning, cool air drains downhill into valleys and hollows. As sunlight warms the hillside, thermals lift upward. Watch the transition period where scent can eddy unpredictably.")
                                    .font(.system(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }
                        
                        GlassPanel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Wind Burn & Standoff")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("High winds compress the scent cone into a narrow stream with rapid dispersal. Light winds (under 3 km/h) allow scent to pool and spread wide. Maintain at least 60–100m standoff.")
                                    .font(.system(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .navigationBarItems(trailing: Button("Done") {
                isPresented = false
            }.foregroundColor(theme.palette.bronzeBright))
        }
    }
}
