import Foundation
import Combine
import CryptoKit

/// Local-first store for every HunTracK collection.
///
/// Mirrors the Android Room schema (16 entities / 13 DAOs) with a JSON-backed
/// store per collection. Each collection is encoded independently so a decode
/// failure in one never takes the others down.
public class DatabaseService: ObservableObject {
    public static let shared = DatabaseService()

    // MARK: - Published collections

    @Published public var markers: [FieldMarker] = [] {
        didSet { save(markers, Self.kMarkers) }
    }
    @Published public var routes: [Route] = [] {
        didSet { save(routes, Self.kRoutes) }
    }
    @Published public var trackPoints: [TrackPoint] = [] {
        didSet { save(trackPoints, Self.kTrackPoints) }
    }
    @Published public var offlineRegions: [OfflineMapRegion] = [] {
        didSet { save(offlineRegions, Self.kOfflineRegions) }
    }
    @Published public var calendarEntries: [CalendarDayEntry] = [] {
        didSet { save(calendarEntries, Self.kCalendar) }
    }
    @Published public var culls: [CullRecord] = [] {
        didSet { save(culls, Self.kCulls) }
    }
    @Published public var permissions: [HuntingPermission] = [] {
        didSet { save(permissions, Self.kPermissions) }
    }
    @Published public var boundaries: [BoundaryArea] = [] {
        didSet { save(boundaries, Self.kBoundaries) }
    }
    @Published public var emergency: EmergencyProfile = EmergencyProfile() {
        didSet { save(emergency, Self.kEmergency) }
    }
    @Published public var callJournal: [CallJournalEntry] = [] {
        didSet { save(callJournal, Self.kCallJournal) }
    }
    @Published public var userCalls: [UserCall] = [] {
        didSet { save(userCalls, Self.kUserCalls) }
    }
    @Published public var audioPacks: [AudioPack] = [] {
        didSet { save(audioPacks, Self.kAudioPacks) }
    }
    @Published public var preferences: UserPreferences = UserPreferences() {
        didSet {
            save(preferences, Self.kPrefs)
            applyPreferences()
        }
    }

    /// Last successful forecast, used for offline fallback (Android weather cache).
    @Published public var cachedWeather: WeatherSnapshot? {
        didSet { saveOptional(cachedWeather, Self.kWeatherCache) }
    }

    /// Live TideTimes.org.uk payloads keyed by station id (Android `tide_events_cache`).
    @Published public var tideCache: [String: TideCacheEntry] = [:] {
        didSet { save(tideCache, Self.kTideCache) }
    }

    // MARK: - Transient session state (not persisted)

    /// Boundary capture started from the Permissions wallet, completed on the map.
    /// Published so the Map tab reacts when the wallet hands a capture over.
    @Published public var pendingBoundary: PendingBoundaryCreation?

    // MARK: - Keys

    private static let kMarkers = "hunttrack_markers_v2"
    private static let kRoutes = "hunttrack_routes_v1"
    private static let kTrackPoints = "hunttrack_trackpoints_v1"
    private static let kOfflineRegions = "hunttrack_offline_regions_v1"
    private static let kCalendar = "hunttrack_calendar_v1"
    private static let kCulls = "hunttrack_culls_v1"
    private static let kPermissions = "hunttrack_permissions_v1"
    private static let kBoundaries = "hunttrack_boundaries_v1"
    private static let kEmergency = "hunttrack_emergency_v1"
    private static let kCallJournal = "hunttrack_call_journal_v1"
    private static let kUserCalls = "hunttrack_user_calls_v1"
    private static let kAudioPacks = "hunttrack_audio_packs_v1"
    private static let kPrefs = "hunttrack_prefs_v2"
    private static let kWeatherCache = "hunttrack_weather_cache_v1"
    private static let kTideCache = "hunttrack_tide_cache_v1"
    private static let kLegacyMarkers = "hunttrack_markers_v1"
    private static let kLegacyPrefs = "hunttrack_prefs_v1"

    private let defaults = UserDefaults.standard
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init() {
        loadAll()
        applyPreferences()
    }

    // MARK: - Loading

    private func loadAll() {
        // Migrate the pre-v2 blobs once so existing testers keep their data.
        if defaults.data(forKey: Self.kMarkers) == nil,
           let legacy = defaults.data(forKey: Self.kLegacyMarkers),
           let decoded = try? decoder.decode([FieldMarker].self, from: legacy) {
            defaults.set(legacy, forKey: Self.kMarkers)
        }
        if defaults.data(forKey: Self.kPrefs) == nil,
           let legacy = defaults.data(forKey: Self.kLegacyPrefs) {
            defaults.set(legacy, forKey: Self.kPrefs)
        }

        markers = load([FieldMarker].self, Self.kMarkers) ?? []
        routes = load([Route].self, Self.kRoutes) ?? []
        trackPoints = load([TrackPoint].self, Self.kTrackPoints) ?? []
        offlineRegions = load([OfflineMapRegion].self, Self.kOfflineRegions) ?? []
        calendarEntries = load([CalendarDayEntry].self, Self.kCalendar) ?? []
        culls = load([CullRecord].self, Self.kCulls) ?? []
        permissions = load([HuntingPermission].self, Self.kPermissions) ?? []
        boundaries = load([BoundaryArea].self, Self.kBoundaries) ?? []
        emergency = load(EmergencyProfile.self, Self.kEmergency) ?? EmergencyProfile()
        callJournal = load([CallJournalEntry].self, Self.kCallJournal) ?? []
        userCalls = load([UserCall].self, Self.kUserCalls) ?? []
        audioPacks = load([AudioPack].self, Self.kAudioPacks) ?? UkFieldCatalog.audioPacks
        preferences = load(UserPreferences.self, Self.kPrefs) ?? UserPreferences()
        cachedWeather = load(WeatherSnapshot.self, Self.kWeatherCache)
        tideCache = load([String: TideCacheEntry].self, Self.kTideCache) ?? [:]
    }

    private func load<T: Decodable>(_ type: T.Type, _ key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? decoder.decode(type, from: data)
    }

    // MARK: - Saving

    private func save<T: Encodable>(_ value: T, _ key: String) {
        guard let data = try? encoder.encode(value) else { return }
        defaults.set(data, forKey: key)
    }

    private func saveOptional<T: Encodable>(_ value: T?, _ key: String) {
        guard let value = value else {
            defaults.removeObject(forKey: key)
            return
        }
        save(value, key)
    }

    private func applyPreferences() {
        ThemeManager.shared.mode = preferences.themeMode
        SoundManager.shared.soundsEnabled = preferences.soundsEnabled
        SoundManager.shared.vibrationEnabled = preferences.vibrationEnabled
    }

    /// Explicit write-through for preference edits made via `Binding`.
    public func savePreferences() {
        save(preferences, Self.kPrefs)
        applyPreferences()
    }

    // MARK: - Identity

    private func nextId(_ existing: [Int64]) -> Int64 {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let highest = existing.max() ?? 0
        return max(now, highest + 1)
    }

    // MARK: - Markers

    public func addMarker(_ marker: FieldMarker) {
        var m = marker
        if m.id == 0 {
            m.id = nextId(markers.map { $0.id })
        }
        markers.append(m)
        SoundManager.shared.play(.markerPlace)
    }

    public func deleteMarker(id: Int64) {
        markers.removeAll { $0.id == id }
    }

    public func clearAllMarkers() {
        markers.removeAll()
    }

    // MARK: - Routes & tracks

    @discardableResult
    public func startRoute(name: String? = nil) -> Int64 {
        let id = nextId(routes.map { $0.id })
        let route = Route(
            id: id,
            name: name ?? "Track \(DateFormatter.localizedString(from: Date(), dateStyle: .short, timeStyle: .short))",
            startedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        routes.append(route)
        return id
    }

    public func appendTrackPoint(routeId: Int64, latitude: Double, longitude: Double, elevationMeters: Double? = nil, speedMps: Float? = nil) {
        let point = TrackPoint(
            id: nextId(trackPoints.map { $0.id }),
            routeId: routeId,
            latitude: latitude,
            longitude: longitude,
            elevationMeters: elevationMeters,
            speedMps: speedMps,
            recordedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        trackPoints.append(point)

        guard let index = routes.firstIndex(where: { $0.id == routeId }) else { return }
        let points = trackPoints.filter { $0.routeId == routeId }
        routes[index].distanceMeters = Self.pathLength(points)
    }

    public func endRoute(routeId: Int64) {
        guard let index = routes.firstIndex(where: { $0.id == routeId }) else { return }
        routes[index].endedAtEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        routes[index].distanceMeters = Self.pathLength(trackPoints.filter { $0.routeId == routeId })
    }

    public func deleteRoute(routeId: Int64) {
        routes.removeAll { $0.id == routeId }
        trackPoints.removeAll { $0.routeId == routeId }
    }

    public func clearRoutes() {
        routes.removeAll()
        trackPoints.removeAll()
    }

    public func points(forRoute routeId: Int64) -> [TrackPoint] {
        trackPoints.filter { $0.routeId == routeId }.sorted { $0.recordedAtEpochMs < $1.recordedAtEpochMs }
    }

    /// Cumulative haversine length in metres, matching Android's distance accumulation.
    private static func pathLength(_ points: [TrackPoint]) -> Double {
        let ordered = points.sorted { $0.recordedAtEpochMs < $1.recordedAtEpochMs }
        guard ordered.count > 1 else { return 0 }
        var total = 0.0
        for index in 1..<ordered.count {
            total += haversineMeters(
                ordered[index - 1].latitude, ordered[index - 1].longitude,
                ordered[index].latitude, ordered[index].longitude
            )
        }
        return total
    }

    public static func haversineMeters(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let earthRadius = 6371000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
                sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // MARK: - Offline map packs

    /// Registers a ~15 square mile pack centred on a coordinate, mirroring Android.
    @discardableResult
    public func createRegionAround(latitude: Double, longitude: Double) -> OfflineMapRegion {
        // 15 sq miles is ~38.85 sq km, so a square is ~6.23 km per side.
        let sideMeters = 6233.0
        let halfLat = (sideMeters / 2) / 111320.0
        let cosLat = max(0.01, cos(latitude * .pi / 180))
        let halfLon = (sideMeters / 2) / (111320.0 * cosLat)

        let region = OfflineMapRegion(
            id: nextId(offlineRegions.map { $0.id }),
            name: String(format: "Pack %.4f, %.4f", latitude, longitude),
            north: latitude + halfLat,
            south: latitude - halfLat,
            east: longitude + halfLon,
            west: longitude - halfLon,
            sizeBytes: 0,
            status: .ready,
            path: nil
        )
        offlineRegions.append(region)
        return region
    }

    public func deleteRegion(id: Int64) {
        offlineRegions.removeAll { $0.id == id }
    }

    // MARK: - Calendar

    public func entry(forDayStart dayStartEpochMs: Int64) -> CalendarDayEntry? {
        calendarEntries.first { $0.dayStartEpochMs == dayStartEpochMs }
    }

    /// Insert or update the entry for a day. An entry cleared to empty is deleted,
    /// matching the Android behaviour where blank days leave no row behind.
    public func upsertCalendarEntry(_ entry: CalendarDayEntry) {
        var value = entry
        value.updatedAtEpochMs = Int64(Date().timeIntervalSince1970 * 1000)

        guard let index = calendarEntries.firstIndex(where: { $0.dayStartEpochMs == value.dayStartEpochMs }) else {
            if value.hasContent {
                value.id = nextId(calendarEntries.map { $0.id })
                calendarEntries.append(value)
            }
            return
        }

        if value.hasContent {
            value.id = calendarEntries[index].id
            calendarEntries[index] = value
        } else {
            calendarEntries.remove(at: index)
        }
    }

    public func deleteCalendarEntry(id: Int64) {
        calendarEntries.removeAll { $0.id == id }
    }

    // MARK: - Cull log

    public func addCull(_ record: CullRecord) {
        var value = record
        if value.id == 0 {
            value.id = nextId(culls.map { $0.id })
        }
        culls.append(value)
    }

    public func updateCull(_ record: CullRecord) {
        guard let index = culls.firstIndex(where: { $0.id == record.id }) else {
            addCull(record)
            return
        }
        culls[index] = record
    }

    public func deleteCull(id: Int64) {
        culls.removeAll { $0.id == id }
    }

    /// CSV export backed by the same columns the Android cull log uses.
    public func cullCSV() -> String {
        var lines: [String] = []
        lines.append("Date,Species,Sex,Age class,Count,OS Grid,Location,Carcass test,Notes,Latitude,Longitude")

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "Europe/London")

        for record in culls.sorted(by: { $0.culledAtEpochMs < $1.culledAtEpochMs }) {
            let date = formatter.string(from: Date(timeIntervalSince1970: Double(record.culledAtEpochMs) / 1000))
            let fields = [
                date,
                record.speciesLabel,
                record.sex.label,
                record.ageClass.label,
                String(record.count),
                record.osGridRef,
                record.locationName,
                record.carcassTestStatus.label,
                record.notes,
                record.latitude.map { String($0) } ?? "",
                record.longitude.map { String($0) } ?? ""
            ]
            lines.append(fields.map(Self.csvField).joined(separator: ","))
        }
        return lines.joined(separator: "\n")
    }

    private static func csvField(_ value: String) -> String {
        if value.contains(",") || value.contains("\"") || value.contains("\n") {
            return "\"" + value.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        return value
    }

    // MARK: - Permissions wallet

    public func addPermission(_ permission: HuntingPermission) {
        var value = permission
        if value.id == 0 {
            value.id = nextId(permissions.map { $0.id })
        }
        permissions.append(value)
    }

    public func updatePermission(_ permission: HuntingPermission) {
        guard let index = permissions.firstIndex(where: { $0.id == permission.id }) else {
            addPermission(permission)
            return
        }
        permissions[index] = permission
    }

    public func deletePermission(id: Int64) {
        permissions.removeAll { $0.id == id }
    }

    // MARK: - Boundaries

    public func addBoundary(_ boundary: BoundaryArea) {
        var value = boundary
        if value.id == 0 {
            value.id = nextId(boundaries.map { $0.id })
        }
        if value.areaSqMeters == 0 {
            value.areaSqMeters = Self.areaSqMeters(value.points)
        }
        boundaries.append(value)
    }

    public func updateBoundary(_ boundary: BoundaryArea) {
        guard let index = boundaries.firstIndex(where: { $0.id == boundary.id }) else {
            addBoundary(boundary)
            return
        }
        boundaries[index] = boundary
    }

    public func deleteBoundary(id: Int64) {
        boundaries.removeAll { $0.id == id }
        for index in permissions.indices where permissions[index].boundaryAreaId == id {
            permissions[index].boundaryAreaId = nil
        }
    }

    /// Shoelace area over an equirectangular projection — matches Android's BoundaryMath.
    public static func areaSqMeters(_ points: [BoundaryPoint]) -> Double {
        let ordered = points.sorted { $0.orderIndex < $1.orderIndex }
        guard ordered.count >= 3 else { return 0 }

        let meanLat = ordered.map { $0.latitude }.reduce(0, +) / Double(ordered.count)
        let metersPerDegLat = 111320.0
        let metersPerDegLon = 111320.0 * max(0.01, cos(meanLat * .pi / 180))

        var projected: [(Double, Double)] = []
        for point in ordered {
            projected.append((point.longitude * metersPerDegLon, point.latitude * metersPerDegLat))
        }

        var sum = 0.0
        for index in 0..<projected.count {
            let next = (index + 1) % projected.count
            sum += projected[index].0 * projected[next].1
            sum -= projected[next].0 * projected[index].1
        }
        return abs(sum) / 2.0
    }

    public static func perimeterMeters(_ points: [BoundaryPoint]) -> Double {
        let ordered = points.sorted { $0.orderIndex < $1.orderIndex }
        guard ordered.count >= 2 else { return 0 }
        var total = 0.0
        for index in 1..<ordered.count {
            total += haversineMeters(
                ordered[index - 1].latitude, ordered[index - 1].longitude,
                ordered[index].latitude, ordered[index].longitude
            )
        }
        if ordered.count >= 3, let first = ordered.first, let last = ordered.last {
            total += haversineMeters(last.latitude, last.longitude, first.latitude, first.longitude)
        }
        return total
    }

    // MARK: - Emergency profile

    public func saveEmergencyProfile(_ profile: EmergencyProfile) {
        emergency = profile
    }

    /// Salted SHA-256, matching the Android pin-hash approach (never store plaintext).
    public static func hashPin(_ pin: String) -> String {
        let digest = SHA256.hash(data: Data(("hunttrack.pin.v1." + pin).utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    public static func verifyPin(_ pin: String, hash: String?) -> Bool {
        guard let hash = hash, !hash.isEmpty else { return false }
        return hashPin(pin) == hash
    }

    public static func hasPin(_ hash: String?) -> Bool {
        !(hash ?? "").isEmpty
    }

    // MARK: - Call journal

    public func addCallJournalEntry(_ entry: CallJournalEntry) {
        var value = entry
        if value.id == 0 {
            value.id = nextId(callJournal.map { $0.id })
        }
        callJournal.append(value)
    }

    public func deleteCallJournalEntry(id: Int64) {
        callJournal.removeAll { $0.id == id }
    }

    // MARK: - User calls

    public func addUserCall(_ call: UserCall) {
        var value = call
        if value.id == 0 {
            value.id = nextId(userCalls.map { $0.id })
        }
        userCalls.append(value)
    }

    public func deleteUserCall(id: Int64) {
        userCalls.removeAll { $0.id == id }
    }

    // MARK: - Audio packs

    public func updateAudioPack(_ pack: AudioPack) {
        if let idx = audioPacks.firstIndex(where: { $0.id == pack.id }) {
            audioPacks[idx] = pack
        } else {
            audioPacks.append(pack)
        }
    }

    // MARK: - Weather cache

    public func cacheWeather(_ snapshot: WeatherSnapshot) {
        cachedWeather = snapshot
    }

    public func clearWeatherCache() {
        cachedWeather = nil
    }

    // MARK: - Tide cache

    public func cacheTideEvents(stationId: String, events: [TideEvent], fetchedAtEpochMs: Int64) {
        guard !events.isEmpty else { return }
        var updated = tideCache
        updated[stationId] = TideCacheEntry(
            stationId: stationId,
            events: events,
            fetchedAtEpochMs: fetchedAtEpochMs
        )
        tideCache = updated
    }

    public func clearTideCache() {
        tideCache = [:]
    }

    // MARK: - Danger zone

    public func clearAll() {
        markers = []
        routes = []
        trackPoints = []
        offlineRegions = []
        calendarEntries = []
        culls = []
        permissions = []
        boundaries = []
        emergency = EmergencyProfile()
        callJournal = []
        userCalls = []
        audioPacks = UkFieldCatalog.audioPacks
        cachedWeather = nil
    }
}
