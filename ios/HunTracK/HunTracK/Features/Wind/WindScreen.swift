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
        let riskThreshold: Double = max(35.0, plume.halfAngleDeg * 2.0)
        let reach: Double = plume.lengthM * 1.2
        let isRisk = diff <= riskThreshold && dist <= reach
        return (isRisk ? .risk : .clear, dist, targetBearing, diff)
    }

    private func scentDial(for weather: WeatherSnapshot) -> some View {
        let downwindDegrees: Double = (weather.windDirectionDeg + 180).truncatingRemainder(dividingBy: 360)
        let downwind: Int = Int(downwindDegrees) % 360
        let fromCard: String = cardinalDirection(weather.windDirectionDeg)
        let downCard: String = cardinalDirection(downwindDegrees)
        let character: WindCharacter = windCharacter(speed: weather.windSpeedKmh, gust: weather.windGustKmh)
        let plume: Plume = ScentPlumeGeometry.forConditions(
            speedKmh: weather.windSpeedKmh,
            gustKmh: weather.windGustKmh,
            humidityPercent: weather.humidityPercent,
            temperatureC: weather.temperatureC
        )
        let unit: WindUnit = db.preferences.windUnit
        let speedLine: String = "\(ScentPlumeGeometry.formatWindSpeed(weather.windSpeedKmh, unit: unit)) · \(character.label)"
        let gustLine: String? = weather.windGustKmh.map { gust in
            "Gusts up to \(ScentPlumeGeometry.formatWindSpeed(gust, unit: unit))"
        }
        let lengthText: String = "~\(Int(plume.lengthM))m"
        let advice: String = "Your scent travels downwind towards \(downwind)°. Approach quarry from downwind (into the face of the wind) and keep a minimum standoff of \(Int(plume.approachStandoffM))m."
        return ScentDialReadout(
            directionFromDeg: weather.windDirectionDeg,
            speedKmh: weather.windSpeedKmh,
            gustKmh: weather.windGustKmh,
            scatter: plume.scatter,
            speedLine: speedLine,
            gustLine: gustLine,
            fromText: "\(fromCard) (\(Int(weather.windDirectionDeg))°)",
            downText: "\(downCard) (\(downwind)°)",
            lengthText: lengthText,
            advice: advice
        )
    }

    private var scentWaiting: some View {
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
                    
                    GlassPanel {
                        SectionLabel("Scent Dispersion Dial")
                        if let weather = scentWeather {
                            scentDial(for: weather)
                        } else {
                            scentWaiting
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

private struct ScentDialReadout: View {
    let directionFromDeg: Double
    let speedKmh: Double
    let gustKmh: Double?
    let scatter: Double
    let speedLine: String
    let gustLine: String?
    let fromText: String
    let downText: String
    let lengthText: String
    let advice: String

    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        VStack(spacing: 14) {
            dial
            speed
            bearings
            Text(advice)
                .font(.field(size: 13))
                .foregroundColor(theme.palette.mist)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 8)
        }
        .frame(maxWidth: .infinity)
    }

    private var dial: some View {
        WindSmokeDial(
            directionFromDeg: directionFromDeg,
            speedKmh: speedKmh,
            gustKmh: gustKmh,
            scatter: scatter
        )
        .frame(width: 240, height: 240)
        .padding(.vertical, 8)
    }

    private var speed: some View {
        VStack(spacing: 4) {
            Text(speedLine)
                .font(.field(size: 18, weight: .bold))
                .foregroundColor(theme.palette.cream)
            if let gustLine = gustLine {
                Text(gustLine)
                    .font(.field(size: 14))
                    .foregroundColor(theme.palette.mist)
            }
        }
    }

    private var bearings: some View {
        HStack(spacing: 20) {
            bearingColumn(title: "Wind from", value: fromText, emphasis: theme.palette.gold)
            Divider().frame(height: 24)
            bearingColumn(title: "Scent drifts to", value: downText, emphasis: theme.palette.bronzeBright)
            Divider().frame(height: 24)
            bearingColumn(title: "Plume length", value: lengthText, emphasis: theme.palette.cream)
        }
        .padding(.top, 4)
    }

    private func bearingColumn(title: String, value: String, emphasis: Color) -> some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)
            Text(value)
                .font(.field(size: 15, weight: .semibold))
                .foregroundColor(emphasis)
        }
    }
}

/// Pink smoke on the Wind & Scent dial — the same plume the map draws, not a solid cone.
/// Geometry is computed in ordinary functions so the `Canvas` closure stays small enough
/// for the type checker.
private struct WindSmokeDial: View {
    let directionFromDeg: Double
    let speedKmh: Double
    let gustKmh: Double?
    let scatter: Double

    private static let pink = Color(red: 0.96, green: 0.32, blue: 0.62)
    private static let bronze = Color(red: 0.95, green: 0.78, blue: 0.45)

    var body: some View {
        TimelineView(.animation(minimumInterval: 0.07)) { timeline in
            dialCanvas(at: timeline.date)
        }
        .accessibilityLabel("Pink scent smoke drifting downwind")
    }

    private func dialCanvas(at date: Date) -> some View {
        Canvas { context, size in
            let layout = SmokeLayout.make(
                date: date,
                size: size,
                directionFromDeg: directionFromDeg,
                speedKmh: speedKmh,
                gustKmh: gustKmh,
                scatter: scatter
            )
            WindSmokeDial.paint(layout, into: &context)
        }
        .background(Circle().stroke(Color.white.opacity(0.18), lineWidth: 8))
    }

    private static func paint(_ layout: SmokeLayout, into context: inout GraphicsContext) {
        for dot in layout.dots {
            let rect = CGRect(x: dot.x - dot.radius, y: dot.y - dot.radius, width: dot.diameter, height: dot.diameter)
            let shade = GraphicsContext.Shading.color(pink.opacity(dot.alpha))
            context.fill(Path(ellipseIn: rect), with: shade)
        }
        var arrow = Path()
        arrow.move(to: layout.tail)
        arrow.addLine(to: layout.tip)
        context.stroke(arrow, with: GraphicsContext.Shading.color(bronze), lineWidth: 4)
        let hunter = CGRect(x: layout.centre.x - 6, y: layout.centre.y - 6, width: 12, height: 12)
        context.fill(Path(ellipseIn: hunter), with: GraphicsContext.Shading.color(.white))
    }
}

private struct SmokeDot {
    let x: CGFloat
    let y: CGFloat
    let radius: CGFloat
    let alpha: Double

    var diameter: CGFloat { radius * 2 }
}

private struct SmokeLayout {
    let dots: [SmokeDot]
    let tail: CGPoint
    let tip: CGPoint
    let centre: CGPoint

    static func make(
        date: Date,
        size: CGSize,
        directionFromDeg: Double,
        speedKmh: Double,
        gustKmh: Double?,
        scatter: Double
    ) -> SmokeLayout {
        let centreX: CGFloat = size.width / 2
        let centreY: CGFloat = size.height / 2
        let downwind: Double = (directionFromDeg + 180).truncatingRemainder(dividingBy: 360)
        let radians: Double = downwind * .pi / 180
        let dx: Double = sin(radians)
        let dy: Double = -cos(radians)
        let acrossX: Double = -dy
        let acrossY: Double = dx
        let gust: Double = max(speedKmh, gustKmh ?? speedKmh)
        let gusty: Bool = (gust - speedKmh) >= 8
        let period: Double = speedKmh < 5 ? 4.2 : (speedKmh < 16 ? 2.8 : 1.6)
        let elapsed: Double = date.timeIntervalSinceReferenceDate
        let phase: Double = elapsed.truncatingRemainder(dividingBy: period) / period
        let shortest: Double = Double(min(size.width, size.height))
        let travel: Double = shortest * 0.42
        let count: Int = gusty ? 28 : 22
        var dots: [SmokeDot] = []
        dots.reserveCapacity(count)
        let wobbleTurns: Double = gusty ? 2.2 : 1
        for index in 0..<count {
            let slot: Double = Double(index) / Double(count)
            let age: Double = (phase + slot).truncatingRemainder(dividingBy: 1)
            let lane: Double = Double((index % 5) - 2) / 2.4
            let wobble: Double = sin((phase * 2 * .pi * wobbleTurns) + Double(index))
            let along: Double = age * 0.95
            let spread: Double = (1 - (along * 0.35)) * scatter
            let laneOffset: Double = travel * 0.28 * lane * spread
            let wobbleOffset: Double = travel * 0.08 * wobble
            let offsetX: Double = (dx * travel * along) + (acrossX * laneOffset) + (acrossX * wobbleOffset)
            let offsetY: Double = (dy * travel * along) + (acrossY * laneOffset) + (acrossY * wobbleOffset)
            let radius: CGFloat = CGFloat(7 + ((1 - along) * 10))
            let alpha: Double = 0.15 + ((1 - along) * 0.55)
            dots.append(SmokeDot(
                x: centreX + CGFloat(offsetX),
                y: centreY + CGFloat(offsetY),
                radius: radius,
                alpha: alpha
            ))
        }
        let tipX: CGFloat = centreX + CGFloat(dx * travel * 0.72)
        let tipY: CGFloat = centreY + CGFloat(dy * travel * 0.72)
        let tailX: CGFloat = centreX - CGFloat(dx * 18)
        let tailY: CGFloat = centreY - CGFloat(dy * 18)
        return SmokeLayout(
            dots: dots,
            tail: CGPoint(x: tailX, y: tailY),
            tip: CGPoint(x: tipX, y: tipY),
            centre: CGPoint(x: centreX, y: centreY)
        )
    }
}
