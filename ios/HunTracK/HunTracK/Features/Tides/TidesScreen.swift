import SwiftUI
import Foundation

// MARK: - Station profile

/// A UK tide station plus the constants needed for the offline estimate.
///
/// Mirrors `TideStationProfile` in the Android module. `springRangeM`,
/// `neapRangeM`, `meanLevelM` and `hwLagMinutes` are fallback values only —
/// the on-device model is an approximation, not an official tide table.
public struct TideStationProfile: Equatable {
    public let station: TideStation
    /// Path slug on tidetimes.org.uk (retained for parity; not used offline).
    public let slug: String
    /// Typical spring tidal range (m).
    public let springRangeM: Double
    /// Typical neap tidal range (m).
    public let neapRangeM: Double
    /// Chart-datum offset for displayed heights (m).
    public let meanLevelM: Double
    /// Minutes after lunar transit until high water.
    public let hwLagMinutes: Double

    public init(
        station: TideStation,
        slug: String,
        springRangeM: Double,
        neapRangeM: Double,
        meanLevelM: Double,
        hwLagMinutes: Double
    ) {
        self.station = station
        self.slug = slug
        self.springRangeM = springRangeM
        self.neapRangeM = neapRangeM
        self.meanLevelM = meanLevelM
        self.hwLagMinutes = hwLagMinutes
    }
}

// MARK: - Catalog

/// The UK port catalog, ported station-for-station from `UkTideStationCatalog`.
public enum UkTideStationCatalog {

    public static let profiles: [TideStationProfile] = [
        port("dover", "Dover", "dover", 51.127, 1.314, spring: 6.5, neap: 4.0, level: 3.2, lag: 95.0),
        port("liverpool", "Liverpool (Gladstone Dock)", "liverpool-gladstone-dock", 53.450, -3.020, spring: 8.3, neap: 4.5, level: 4.0, lag: -25.0),
        port("holyhead", "Holyhead", "holyhead", 53.310, -4.620, spring: 5.6, neap: 3.2, level: 2.8, lag: 50.0),
        port("milford", "Milford Haven", "milford-haven", 51.714, -5.050, spring: 6.8, neap: 3.5, level: 3.4, lag: 110.0),
        port("plymouth", "Plymouth (Devonport)", "plymouth-devonport", 50.366, -4.192, spring: 4.8, neap: 2.4, level: 2.4, lag: 125.0),
        port("falmouth", "Falmouth", "falmouth", 50.152, -5.055, spring: 4.5, neap: 2.2, level: 2.2, lag: 130.0),
        port("southampton", "Southampton", "southampton", 50.898, -1.404, spring: 4.2, neap: 2.0, level: 2.1, lag: 105.0),
        port("portsmouth", "Portsmouth", "portsmouth", 50.805, -1.110, spring: 4.0, neap: 2.0, level: 2.0, lag: 100.0),
        port("brighton", "Brighton Marina", "brighton-marina", 50.820, -0.137, spring: 5.8, neap: 3.2, level: 2.9, lag: 90.0),
        port("london_bridge", "London Bridge (Tower Pier)", "london-bridge-tower-pier", 51.508, -0.075, spring: 7.0, neap: 4.5, level: 3.5, lag: 120.0),
        port("harwich", "Harwich", "harwich", 51.953, 1.288, spring: 3.8, neap: 2.0, level: 1.9, lag: 85.0),
        port("lowestoft", "Lowestoft", "lowestoft", 52.475, 1.751, spring: 2.8, neap: 1.5, level: 1.4, lag: 80.0),
        port("great_yarmouth", "Great Yarmouth (Britannia Pier)", "great-yarmouth-britannia-pier", 52.608, 1.730, spring: 2.5, neap: 1.3, level: 1.3, lag: 75.0),
        port("hull", "Hull (Albert Dock)", "hull-albert-dock", 53.741, -0.332, spring: 6.0, neap: 3.5, level: 3.0, lag: 70.0),
        port("immingham", "Immingham", "immingham", 53.630, -0.195, spring: 6.5, neap: 3.8, level: 3.2, lag: 68.0),
        port("tynemouth", "River Tyne (North Shields)", "river-tyne-north-shields", 55.010, -1.450, spring: 4.5, neap: 2.5, level: 2.3, lag: 75.0),
        port("leith", "Leith", "leith", 55.980, -3.170, spring: 5.0, neap: 2.8, level: 2.5, lag: 60.0),
        port("aberdeen", "Aberdeen", "aberdeen", 57.144, -2.080, spring: 3.9, neap: 2.0, level: 1.9, lag: 55.0),
        port("inverness", "Inverness", "inverness", 57.481, -4.224, spring: 4.5, neap: 2.5, level: 2.2, lag: 45.0),
        port("ullapool", "Ullapool", "ullapool", 57.896, -5.160, spring: 4.8, neap: 2.6, level: 2.4, lag: 40.0),
        port("oban", "Oban", "oban", 56.415, -5.475, spring: 4.0, neap: 2.2, level: 2.0, lag: 35.0),
        port("greenock", "Greenock", "greenock", 55.948, -4.756, spring: 3.5, neap: 1.8, level: 1.7, lag: 50.0),
        port("stranraer", "Stranraer", "stranraer", 54.903, -5.027, spring: 3.2, neap: 1.6, level: 1.6, lag: 45.0),
        port("belfast", "Belfast", "belfast", 54.617, -5.900, spring: 3.5, neap: 1.8, level: 1.7, lag: 55.0),
        port("larne", "Larne", "larne", 54.851, -5.807, spring: 3.2, neap: 1.6, level: 1.6, lag: 50.0),
        port("dublin", "Dublin (North Wall)", "dublin-north-wall", 53.350, -6.200, spring: 4.0, neap: 2.0, level: 2.0, lag: 65.0),
        port("cardiff", "Cardiff", "cardiff", 51.463, -3.180, spring: 12.0, neap: 6.5, level: 6.0, lag: 90.0),
        port("swansea", "Swansea", "swansea", 51.621, -3.936, spring: 8.5, neap: 4.5, level: 4.2, lag: 100.0),
        port("llandudno", "Llandudno", "llandudno", 53.330, -3.830, spring: 7.5, neap: 4.0, level: 3.8, lag: 55.0),
        port("workington", "Workington", "workington", 54.650, -3.570, spring: 6.0, neap: 3.2, level: 3.0, lag: 60.0),
        port("barrow", "Barrow (Ramsden Dock)", "barrow-ramsden-dock", 54.110, -3.230, spring: 8.0, neap: 4.2, level: 4.0, lag: 65.0),
        port("st_marys", "St Mary's (Isles of Scilly)", "st-marys", 49.917, -6.317, spring: 5.0, neap: 2.5, level: 2.5, lag: 140.0)
    ]

    public static func allStations() -> [TideStation] {
        profiles.map { $0.station }
    }

    public static func profileFor(stationId: String) -> TideStationProfile? {
        profiles.first { $0.station.id == stationId }
    }

    public static func profileFor(station: TideStation) -> TideStationProfile? {
        profileFor(stationId: station.id) ?? nearestProfile(latitude: station.latitude, longitude: station.longitude)
    }

    /// Nearest catalog port to a coordinate, by great-circle distance.
    public static func nearestProfile(latitude: Double, longitude: Double) -> TideStationProfile? {
        profiles.min { a, b in
            let da = DatabaseService.haversineMeters(latitude, longitude, a.station.latitude, a.station.longitude)
            let db = DatabaseService.haversineMeters(latitude, longitude, b.station.latitude, b.station.longitude)
            return da < db
        }
    }

    private static func port(
        _ id: String,
        _ name: String,
        _ slug: String,
        _ latitude: Double,
        _ longitude: Double,
        spring: Double,
        neap: Double,
        level: Double,
        lag: Double
    ) -> TideStationProfile {
        let station = TideStation(
            id: id,
            name: name,
            country: "UK",
            latitude: latitude,
            longitude: longitude
        )
        return TideStationProfile(
            station: station,
            slug: slug,
            springRangeM: spring,
            neapRangeM: neap,
            meanLevelM: level,
            hwLagMinutes: lag
        )
    }
}

// MARK: - Lunar tide model

/// Offline UK semidiurnal tide model built from the lunar hour angle, the
/// per-port spring/neap range and the high-water lag.
///
/// This is an approximation for field planning. It is NOT an official tide
/// table — verify against local charts before anything safety-critical.
public enum LunarTideCalculator {

    /// M2 lunar semidiurnal period (hours).
    private static let semidiurnalHours = 12.42

    /// Peak/valley events between now and `days` ahead.
    public static func forecast(profile: TideStationProfile, fromEpochMs: Int64, days: Int = 7) -> [TideEvent] {
        let dayMs: Int64 = 86_400_000
        let stepMs: Int64 = 5 * 60 * 1000
        let endMs = fromEpochMs + Int64(days) * dayMs

        var samples: [(t: Int64, h: Double)] = []
        var t = fromEpochMs - stepMs
        while t <= endMs + stepMs {
            samples.append((t: t, h: heightAt(profile: profile, epochMs: t)))
            t += stepMs
        }
        return findExtrema(samples: samples, fromEpochMs: fromEpochMs)
    }

    /// Modelled water height in metres at an instant.
    public static func heightAt(profile: TideStationProfile, epochMs: Int64) -> Double {
        let latitude = profile.station.latitude
        let longitude = profile.station.longitude

        let moonPhase = AstronomyCalculator.calculate(
            latitude: latitude,
            longitude: longitude,
            epochMs: epochMs
        ).moonPhase

        let springNeapCosine = cos(2 * Double.pi * moonPhase)
        let springNeap = springNeapCosine * springNeapCosine
        let range = profile.neapRangeM + (profile.springRangeM - profile.neapRangeM) * springNeap

        let hourAngle = moonHourAngleRad(latitude: latitude, longitude: longitude, epochMs: epochMs)
        let lagRad = (profile.hwLagMinutes / (semidiurnalHours * 60.0)) * 2 * Double.pi
        let phase = 2 * hourAngle + lagRad

        return profile.meanLevelM + (range / 2.0) * cos(phase)
    }

    // MARK: Internals

    private static func findExtrema(samples: [(t: Int64, h: Double)], fromEpochMs: Int64) -> [TideEvent] {
        guard samples.count >= 3 else { return [] }
        var events: [TideEvent] = []
        let leadWindow: Int64 = 30 * 60 * 1000

        for index in 1..<(samples.count - 1) {
            let previous = samples[index - 1]
            let middle = samples[index]
            let next = samples[index + 1]

            if middle.t < fromEpochMs - leadWindow { continue }

            if middle.h > previous.h && middle.h >= next.h {
                events.append(TideEvent(
                    epochMs: middle.t,
                    type: .high,
                    heightMeters: max(0.0, middle.h),
                    isApproximate: true
                ))
            } else if middle.h < previous.h && middle.h <= next.h {
                events.append(TideEvent(
                    epochMs: middle.t,
                    type: .low,
                    heightMeters: max(0.0, middle.h),
                    isApproximate: true
                ))
            }
        }
        return events.sorted { $0.epochMs < $1.epochMs }
    }

    private static func moonHourAngleRad(latitude: Double, longitude: Double, epochMs: Int64) -> Double {
        let d = toDays(epochMs)
        let coords = moonCoords(d)
        let lw = rad(-longitude)
        return siderealTime(d: d, lw: lw) - coords.ra
    }

    private static func toJulian(_ epochMs: Int64) -> Double { Double(epochMs) / 86_400_000.0 + 2_440_587.5 }
    private static func toDays(_ epochMs: Int64) -> Double { toJulian(epochMs) - 2_451_545.0 }

    private static func siderealTime(d: Double, lw: Double) -> Double {
        rad(280.16) + rad(360.9856235) * d - lw
    }

    private static func moonCoords(_ d: Double) -> (ra: Double, dec: Double) {
        let l = rad(218.316) + rad(13.176396) * d
        let m = rad(134.963) + rad(13.064993) * d
        let f = rad(297.850) + rad(0.1114034) * d
        let longitude = l + rad(6.289) * sin(m)
        let latitude = rad(5.128) * sin(f)
        let ra = atan2(
            sin(longitude) * cos(obliquity()) - tan(latitude) * sin(obliquity()),
            cos(longitude)
        )
        let dec = asin(sin(latitude) * cos(obliquity()) + cos(latitude) * sin(obliquity()) * sin(longitude))
        return (ra: ra, dec: dec)
    }

    private static func obliquity() -> Double { rad(23.4397) }
    private static func rad(_ degrees: Double) -> Double { degrees * Double.pi / 180.0 }
    private static func tan(_ x: Double) -> Double { sin(x) / cos(x) }
}

// MARK: - Formatting helpers

private enum TideFormat {
    static let event: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.timeZone = TimeZone(identifier: "Europe/London")
        formatter.dateFormat = "EEE d MMM HH:mm"
        return formatter
    }()

    static let dayHeading: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB")
        formatter.timeZone = TimeZone(identifier: "Europe/London")
        formatter.dateFormat = "EEEE d MMMM"
        return formatter
    }()

    static func typeLabel(_ type: TideEventType) -> String {
        switch type {
        case .high: return "High water"
        case .low: return "Low water"
        case .unknown: return "Tide"
        }
    }
}

// MARK: - Settings section

/// Tide settings block, designed to be embedded either in Settings or on its own screen.
public struct TideSettingsSection: View {
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var theme = ThemeManager.shared

    @State private var events: [TideEvent] = []
    @State private var note: String?

    public init() {}

    private var stations: [TideStation] { UkTideStationCatalog.allStations() }

    private var activeProfile: TideStationProfile? {
        let stationId = db.preferences.tideStationId
        guard !stationId.isEmpty else { return nil }
        return UkTideStationCatalog.profileFor(stationId: stationId)
    }

    public var body: some View {
        GlassPanel {
            SectionLabel("UK tide times")
            Text("Approximate high and low water, calculated on this device from the lunar cycle and each port's tidal range. Verify against local charts before relying on it.")
                .font(.system(size: 12))
                .foregroundColor(theme.palette.mist)

            Toggle("Show UK tide times", isOn: Binding(
                get: { db.preferences.tidesEnabled },
                set: { newValue in
                    db.preferences.tidesEnabled = newValue
                    db.savePreferences()
                    recompute()
                }
            ))
            .font(.system(size: 15))
            .foregroundColor(theme.palette.cream)

            if db.preferences.tideStationName.isEmpty {
                Text("No tide station selected")
                    .font(.system(size: 13))
                    .foregroundColor(theme.palette.mist)
                    .padding(.top, 4)
            } else {
                MetricRow("Station", db.preferences.tideStationName)
            }

            stationPicker

            HStack(spacing: 8) {
                HuntButton("Nearest station", icon: "location.fill") { useNearestStation() }
                HuntButton("Clear station", icon: "xmark.circle") { clearStation() }
            }
            .padding(.top, 4)

            if let note = note {
                Text(note)
                    .font(.system(size: 12))
                    .foregroundColor(theme.palette.gold)
                    .padding(.top, 2)
            }

            if db.preferences.tidesEnabled && !events.isEmpty {
                eventList
            }

            Text("Source: on-device model (calculated, approximate)")
                .font(.system(size: 11))
                .foregroundColor(theme.palette.mist)
                .padding(.top, 4)
        }
        .onAppear { recompute() }
        .onChange(of: db.preferences.tideStationId) { _ in recompute() }
        .onChange(of: db.preferences.tidesEnabled) { _ in recompute() }
    }

    private var stationPicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            SectionLabel("Station")
            Picker("Station", selection: Binding(
                get: { db.preferences.tideStationId },
                set: { newValue in
                    guard let profile = UkTideStationCatalog.profileFor(stationId: newValue) else { return }
                    db.preferences.tideStationId = profile.station.id
                    db.preferences.tideStationName = profile.station.name
                    db.preferences.tideStationLatitude = profile.station.latitude
                    db.preferences.tideStationLongitude = profile.station.longitude
                    db.savePreferences()
                    recompute()
                }
            )) {
                Text("None").tag("")
                ForEach(stations) { station in
                    Text(station.name).tag(station.id)
                }
            }
            .pickerStyle(.menu)
            .tint(theme.palette.bronzeBright)
        }
    }

    private var eventList: some View {
        VStack(alignment: .leading, spacing: 2) {
            SectionLabel("Next tides")
                .padding(.top, 8)
            ForEach(events) { event in
                HStack {
                    Text(TideFormat.typeLabel(event.type))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    Spacer()
                    Text(TideFormat.event.string(from: Date(timeIntervalSince1970: Double(event.epochMs) / 1000)))
                        .font(.system(size: 13))
                        .foregroundColor(theme.palette.mist)
                    Text(String(format: "%.2f m%@", event.heightMeters, event.isApproximate ? " ~" : ""))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: Actions

    private func useNearestStation() {
        let fix = location.latestFix ?? location.fallbackFix()
        guard let profile = UkTideStationCatalog.nearestProfile(latitude: fix.latitude, longitude: fix.longitude) else {
            note = "No UK tide stations available."
            return
        }
        db.preferences.tideStationId = profile.station.id
        db.preferences.tideStationName = profile.station.name
        db.preferences.tideStationLatitude = profile.station.latitude
        db.preferences.tideStationLongitude = profile.station.longitude
        db.savePreferences()
        note = location.latestFix == nil
            ? "GPS not fixed yet — used a default UK position."
            : nil
        recompute()
    }

    private func clearStation() {
        db.preferences.tideStationId = ""
        db.preferences.tideStationName = ""
        db.preferences.tideStationLatitude = nil
        db.preferences.tideStationLongitude = nil
        db.savePreferences()
        note = nil
        events = []
    }

    /// Recalculates the upcoming high/low waters. Two days of events is enough
    /// for the list and keeps the sample count reasonable.
    private func recompute() {
        guard db.preferences.tidesEnabled, let profile = activeProfile else {
            events = []
            return
        }
        let from = Int64(Date().timeIntervalSince1970 * 1000)
        let result = LunarTideCalculator.forecast(profile: profile, fromEpochMs: from, days: 2)
        events = Array(result.prefix(14))
    }
}

// MARK: - Standalone screen

/// Standalone Tides screen, reachable from the More menu.
public struct TidesScreen: View {
    @ObservedObject private var theme = ThemeManager.shared

    public init() {}

    public var body: some View {
        ScreenBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Tides")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(theme.palette.cream)
                        Text("UK high & low water")
                            .font(.system(size: 14))
                            .foregroundColor(theme.palette.mist)
                    }
                    .padding(.top, 8)

                    TideSettingsSection()

                    GlassPanel {
                        SectionLabel("Using tides in the field")
                        Text("Rising water pushes fish and wildfowl inshore and concentrates feeding activity around the tide edges. Slack water either side of high and low is usually the most productive window, and an incoming tide is generally better than a falling one for foreshore and estuary shooting.")
                            .font(.system(size: 13))
                            .foregroundColor(theme.palette.cream)
                        Text("Check your quarry's legal season and any local byelaws before shooting on tidal ground.")
                            .font(.system(size: 12))
                            .foregroundColor(theme.palette.mist)
                            .padding(.top, 4)
                    }
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
