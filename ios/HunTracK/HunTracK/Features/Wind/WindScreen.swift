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
    @State private var lastWindRequest: Date?
    
    public init() {}
    
    private var hunterFix: GpsFix? {
        location.latestFix
    }

    private var scentWeather: WeatherSnapshot? {
        ScentPlumeGeometry.usableWeather(weatherService.weather ?? db.cachedWeather, for: hunterFix)
    }
    
    private func refreshWindIfNeeded(_ fix: GpsFix?) {
        guard scentWeather == nil, let fix = fix, !weatherService.loading else { return }
        if let last = lastWindRequest, Date().timeIntervalSince(last) < 60 { return }
        lastWindRequest = Date()
        weatherService.fetchWeather(latitude: fix.latitude, longitude: fix.longitude)
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
        guard let w = scentWeather, let hunter = hunterFix else {
            return (.unknown, nil, nil, nil)
        }
        let downwind = Double((Int(w.windDirectionDeg) + 180) % 360)
        let targetBearing = bearing(lat1: hunter.latitude, lon1: hunter.longitude, lat2: marker.latitude, lon2: marker.longitude)
        let dist = DatabaseService.haversineMeters(hunter.latitude, hunter.longitude, marker.latitude, marker.longitude)
        let diff = angularDiff(downwind, targetBearing)
        
        let plume = ScentPlumeGeometry.forConditions(
            speedKmh: w.windSpeedKmh,
            gustKmh: w.windGustKmh,
            humidityPercent: w.humidityPercent,
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
                                .font(.field(size: 28, weight: .bold))
                                .foregroundColor(theme.palette.cream)
                            Text("Will animals smell you from downwind?")
                                .font(.field(size: 14))
                                .foregroundColor(theme.palette.mist)
                        }
                        Spacer()
                        Button(action: { showGuide = true }) {
                            Image(systemName: "questionmark.circle")
                                .font(.field(size: 22))
                                .foregroundColor(theme.palette.bronzeBright)
                        }
                    }
                    .padding(.top, 16)
                    
                    // Wind dial panel
                    GlassPanel {
                        SectionLabel("Scent Dispersion Dial")
                        if let w = scentWeather {
                            let downwind = (Int(w.windDirectionDeg) + 180) % 360
                            let fromCard = cardinalDirection(Double(w.windDirectionDeg))
                            let downCard = cardinalDirection(Double(downwind))
                            let character = windCharacter(speed: w.windSpeedKmh, gust: w.windGustKmh)
                            let plume = ScentPlumeGeometry.forConditions(
                                speedKmh: w.windSpeedKmh,
                                gustKmh: w.windGustKmh,
                                humidityPercent: w.humidityPercent,
                                temperatureC: w.temperatureC
                            )
                            
                            VStack(spacing: 14) {
                                WindSmokeDial(
                                    directionFromDeg: w.windDirectionDeg,
                                    speedKmh: w.windSpeedKmh,
                                    gustKmh: w.windGustKmh,
                                    plume: plume
                                )
                                .frame(width: 240, height: 240)
                                .padding(.vertical, 8)
                                
                                Text("\(ScentPlumeGeometry.formatWindSpeed(w.windSpeedKmh, unit: db.preferences.windUnit)) · \(character.label)")
                                    .font(.field(size: 18, weight: .bold))
                                    .foregroundColor(theme.palette.cream)
                                
                                if let gust = w.windGustKmh {
                                    Text("Gusts up to \(ScentPlumeGeometry.formatWindSpeed(gust, unit: db.preferences.windUnit))")
                                        .font(.field(size: 14))
                                        .foregroundColor(theme.palette.mist)
                                }
                                
                                HStack(spacing: 20) {
                                    VStack(spacing: 2) {
                                        Text("Wind from")
                                            .font(.field(size: 12))
                                            .foregroundColor(theme.palette.mist)
                                        Text("\(fromCard) (\(Int(w.windDirectionDeg))°)")
                                            .font(.field(size: 15, weight: .semibold))
                                            .foregroundColor(theme.palette.gold)
                                    }
                                    Divider().frame(height: 24)
                                    VStack(spacing: 2) {
                                        Text("Scent drifts to")
                                            .font(.field(size: 12))
                                            .foregroundColor(theme.palette.mist)
                                        Text("\(downCard) (\(downwind)°)")
                                            .font(.field(size: 15, weight: .bold))
                                            .foregroundColor(theme.palette.bronzeBright)
                                    }
                                    Divider().frame(height: 24)
                                    VStack(spacing: 2) {
                                        Text("Plume length")
                                            .font(.field(size: 12))
                                            .foregroundColor(theme.palette.mist)
                                        Text("~\(Int(plume.lengthM))m")
                                            .font(.field(size: 15, weight: .semibold))
                                            .foregroundColor(theme.palette.cream)
                                    }
                                }
                                .padding(.top, 4)
                                
                                Text("Your scent travels downwind towards \(downwind)°. Approach quarry from downwind (into the face of the wind) and keep a minimum standoff of \(Int(plume.approachStandoffM))m.")
                                    .font(.field(size: 13))
                                    .foregroundColor(theme.palette.mist)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 8)
                            }
                            .frame(maxWidth: .infinity)
                        } else {
                            VStack(spacing: 10) {
                                Text("Waiting for GPS and recent local wind data…")
                                    .font(.field(size: 14))
                                    .foregroundColor(theme.palette.mist)
                                Button("Refresh Weather") {
                                    if let fix = hunterFix {
                                        weatherService.fetchWeather(latitude: fix.latitude, longitude: fix.longitude)
                                    }
                                }
                                .font(.field(size: 14, weight: .semibold))
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
                                .font(.field(size: 14))
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
                                                    .font(.field(size: 16, weight: .semibold))
                                                    .foregroundColor(theme.palette.cream)
                                                Text(marker.category.label)
                                                    .font(.field(size: 13))
                                                    .foregroundColor(theme.palette.mist)
                                            }
                                            Spacer()
                                            
                                            switch eval.risk {
                                            case .risk:
                                                HStack(spacing: 4) {
                                                    Image(systemName: "exclamationmark.triangle.fill")
                                                    Text("RISK")
                                                }
                                                .font(.field(size: 12, weight: .bold))
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
                                                .font(.field(size: 12, weight: .bold))
                                                .foregroundColor(theme.palette.success)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(theme.palette.success.opacity(0.2))
                                                .cornerRadius(6)
                                            case .unknown:
                                                Text("Waiting for wind or GPS")
                                                    .font(.field(size: 12))
                                                    .foregroundColor(theme.palette.mist)
                                            }
                                        }
                                        
                                        if let dist = eval.distanceM, let bearingDeg = eval.bearingDeg {
                                            HStack(spacing: 12) {
                                                Text("\(Int(dist))m away")
                                                    .font(.field(size: 13))
                                                    .foregroundColor(theme.palette.mist)
                                                Text("·")
                                                    .foregroundColor(theme.palette.mist)
                                                Text("\(Int(bearingDeg))° \(cardinalDirection(bearingDeg))")
                                                    .font(.field(size: 13))
                                                    .foregroundColor(theme.palette.mist)
                                            }
                                        }
                                        
                                        if isSelected {
                                            Divider().background(theme.palette.slate)
                                            if eval.risk == .risk {
                                                Text("⚠️ WARNING: Your scent cone is drifting straight towards this target location. Consider circling around for an upwind approach.")
                                                    .font(.field(size: 12))
                                                    .foregroundColor(theme.palette.danger)
                                            } else if eval.risk == .clear {
                                                Text("✓ Clear: The estimated scent plume is away from this target. Wind can change; verify conditions in the field.")
                                                    .font(.field(size: 12))
                                                    .foregroundColor(theme.palette.success)
                                            } else {
                                                Text("Scent risk is unavailable until GPS and recent local wind data are available.")
                                                    .font(.field(size: 12))
                                                    .foregroundColor(theme.palette.mist)
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
        .onAppear {
            location.requestPermission()
            location.startUpdating()
            refreshWindIfNeeded(location.latestFix)
        }
        .onChange(of: location.latestFix) { fix in
            refreshWindIfNeeded(fix)
        }
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
                            .font(.field(size: 22, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                            .padding(.top, 16)
                        
                        GlassPanel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Rule 1: The Face of the Wind")
                                    .font(.field(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("Always stalk into the face of the wind or across it. Scent travels downwind at the speed of the air mass, alerting deer long before you arrive.")
                                    .font(.field(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }
                        
                        GlassPanel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Thermal Drafts")
                                    .font(.field(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("In early morning, cool air drains downhill into valleys and hollows. As sunlight warms the hillside, thermals lift upward. Watch the transition period where scent can eddy unpredictably.")
                                    .font(.field(size: 14))
                                    .foregroundColor(theme.palette.mist)
                            }
                        }
                        
                        GlassPanel {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Wind Burn & Standoff")
                                    .font(.field(size: 16, weight: .bold))
                                    .foregroundColor(theme.palette.gold)
                                Text("High winds compress the scent cone into a narrow stream with rapid dispersal. Light winds (under 3 km/h) allow scent to pool and spread wide. Maintain at least 60–100m standoff.")
                                    .font(.field(size: 14))
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

/// Pink smoke on the Wind & Scent dial — the same plume the map draws, not a solid cone.
private struct WindSmokeDial: View {
    let directionFromDeg: Double
    let speedKmh: Double
    let gustKmh: Double?
    let plume: Plume

    var body: some View {
        TimelineView(.animation(minimumInterval: 0.07)) { timeline in
            Canvas { context, size in
                let centre = CGPoint(x: size.width / 2, y: size.height / 2)
                let downwind = (directionFromDeg + 180).truncatingRemainder(dividingBy: 360)
                let radians = downwind * .pi / 180
                let dx = sin(radians)
                let dy = -cos(radians)
                let px = -dy
                let py = dx
                let gust = max(speedKmh, gustKmh ?? speedKmh)
                let gusty = gust - speedKmh >= 8
                let period: Double = speedKmh < 5 ? 4.2 : (speedKmh < 16 ? 2.8 : 1.6)
                let phase = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: period) / period
                let travel = min(size.width, size.height) * 0.42
                let pink = Color(red: 0.96, green: 0.32, blue: 0.62)
                let count = gusty ? 28 : 22
                for index in 0..<count {
                    let age = (phase + Double(index) / Double(count)).truncatingRemainder(dividingBy: 1)
                    let lane = Double((index % 5) - 2) / 2.4
                    let wobble = sin(phase * 2 * .pi * (gusty ? 2.2 : 1) + Double(index))
                    let along = age * 0.95
                    let spread = (1 - along * 0.35) * plume.scatter
                    let x = centre.x + CGFloat(dx * travel * along + px * travel * 0.28 * lane * spread + px * travel * 0.08 * wobble)
                    let y = centre.y + CGFloat(dy * travel * along + py * travel * 0.28 * lane * spread + py * travel * 0.08 * wobble)
                    let radius = 7 + (1 - along) * 10
                    let alpha = 0.15 + (1 - along) * 0.55
                    let rect = CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)
                    context.fill(Path(ellipseIn: rect), with: .color(pink.opacity(alpha)))
                }
                var arrow = Path()
                let tip = CGPoint(x: centre.x + CGFloat(dx * travel * 0.72), y: centre.y + CGFloat(dy * travel * 0.72))
                let tail = CGPoint(x: centre.x - CGFloat(dx * 18), y: centre.y - CGFloat(dy * 18))
                arrow.move(to: tail)
                arrow.addLine(to: tip)
                context.stroke(arrow, with: .color(Color(red: 0.95, green: 0.78, blue: 0.45)), lineWidth: 4)
                context.fill(Path(ellipseIn: CGRect(x: centre.x - 6, y: centre.y - 6, width: 12, height: 12)), with: .color(.white))
            }
            .background(
                Circle()
                    .stroke(Color.white.opacity(0.18), lineWidth: 8)
            )
        }
        .accessibilityLabel("Pink scent smoke drifting downwind")
    }
}
