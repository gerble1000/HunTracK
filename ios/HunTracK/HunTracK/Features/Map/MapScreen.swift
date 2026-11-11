import SwiftUI
import MapKit
import CoreLocation
import UIKit

// MARK: - Colour helper

/// Parses a "#RRGGBB" string into a UIColor (boundary zone colours come from
/// `BoundaryZoneType.mapColorHex`).
private func mapUIColor(hex: String) -> UIColor {
    var cleaned = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
    if cleaned.count == 3 {
        // Expand shorthand #ABC to AABBCC.
        cleaned = cleaned.map { "\($0)\($0)" }.joined()
    }
    var value: UInt64 = 0
    guard Scanner(string: cleaned).scanHexInt64(&value), cleaned.count == 6 else {
        return UIColor(red: 0.8, green: 0.65, blue: 0.35, alpha: 1)
    }
    return UIColor(
        red: CGFloat((value >> 16) & 0xFF) / 255.0,
        green: CGFloat((value >> 8) & 0xFF) / 255.0,
        blue: CGFloat(value & 0xFF) / 255.0,
        alpha: 1.0
    )
}

// MARK: - Annotation kinds

private enum MapPinKind: String {
    case fieldMarker
    case cull
    case homing
    case boundaryPoint
}

private final class HuntAnnotation: NSObject, MKAnnotation {
    let coordinate: CLLocationCoordinate2D
    let title: String?
    let subtitle: String?
    let kind: MapPinKind
    let recordId: Int64
    let tint: UIColor

    init(
        coordinate: CLLocationCoordinate2D,
        title: String?,
        subtitle: String?,
        kind: MapPinKind,
        recordId: Int64,
        tint: UIColor
    ) {
        self.coordinate = coordinate
        self.title = title
        self.subtitle = subtitle
        self.kind = kind
        self.recordId = recordId
        self.tint = tint
    }
}

// MARK: - Overlay bookkeeping

/// Overlays are plain `MKPolyline` / `MKPolygon` instances. The coordinator keeps a
/// side table keyed by object identity, so no MapKit subclassing is needed (subclassing
/// would depend on inheriting MapKit's convenience initialisers).
private enum OverlayKind {
    case route
    case pendingBoundary
    case zone(strokeHex: String, lineWidth: CGFloat, fillHex: String, fillOpacity: Double)
    case fieldZone(zoneId: Int64, kind: BoundaryZoneKind, strokeHex: String, fillHex: String, fillOpacity: Double)
    case grid(major: Bool)
    case plume
}

// MARK: - Snapshot used to keep updateUIView idempotent

private struct MapContentSnapshot: Equatable {
    var markers: [FieldMarker] = []
    var culls: [CullRecord] = []
    var boundaryIds: [Int64] = []
    var boundaryPointCounts: [Int: Int] = [:]
    var routeIds: [Int64] = []
    var routePointCounts: [Int: Int] = [:]
    var showCulls: Bool = false
    var homingLatitude: Double?
    var homingLongitude: Double?
    var pendingCoordinates: [String] = []
    var focusedBoundaryIds: [Int64] = []
    var linkedBoundaryIds: [Int64] = []
    var zoneFingerprints: [String] = []
}

// MARK: - The UIKit map host

/// Drifting pink scent smoke. Puff positions follow the same plume model as Wind & Scent;
/// puff size stays in screen points (as Android's circle layer does) so a country-scale
/// view does not inflate them into a solid cone.
private final class HatchedPolygonRenderer: MKPolygonRenderer {
    var hatchRising = false

    override func draw(_ mapRect: MKMapRect, zoomScale: MKZoomScale, in context: CGContext) {
        super.draw(mapRect, zoomScale: zoomScale, in: context)
        guard let path = path else { return }
        context.saveGState()
        context.addPath(path)
        context.clip()
        context.setStrokeColor((strokeColor ?? UIColor.red).withAlphaComponent(0.9).cgColor)
        context.setLineWidth(1.6 / CGFloat(zoomScale))
        let bounds = path.boundingBox
        let spacing = 8.0 / CGFloat(zoomScale)
        var offset = bounds.minX - bounds.height
        let limit = bounds.maxX + bounds.height
        while offset < limit {
            if hatchRising {
                context.move(to: CGPoint(x: offset, y: bounds.maxY))
                context.addLine(to: CGPoint(x: offset + bounds.height, y: bounds.minY))
            } else {
                context.move(to: CGPoint(x: offset, y: bounds.minY))
                context.addLine(to: CGPoint(x: offset + bounds.height, y: bounds.maxY))
            }
            offset += spacing
        }
        context.strokePath()
        context.restoreGState()
    }
}

private final class ScentSmokeRenderer: MKOverlayRenderer {
    let fix: GpsFix
    let weather: WeatherSnapshot
    let plume: Plume

    private static let pink = UIColor(red: 0.96, green: 0.32, blue: 0.62, alpha: 1)
    private static let highlight = UIColor(red: 0.99, green: 0.78, blue: 0.88, alpha: 1)

    init(overlay: MKOverlay, fix: GpsFix, weather: WeatherSnapshot) {
        self.fix = fix
        self.weather = weather
        self.plume = ScentPlumeGeometry.forConditions(
            speedKmh: weather.windSpeedKmh, gustKmh: weather.windGustKmh,
            humidityPercent: weather.humidityPercent, temperatureC: weather.temperatureC
        )
        super.init(overlay: overlay)
    }

    override func draw(_ mapRect: MKMapRect, zoomScale: MKZoomScale, in context: CGContext) {
        let speed: Double = max(0, min(60, weather.windSpeedKmh))
        let gust: Double = max(speed, weather.windGustKmh ?? speed)
        let gusty: Bool = (gust - speed) >= 8
        let period: Double = Self.driftPeriod(speedKmh: speed)
        let elapsed: Double = Date().timeIntervalSinceReferenceDate
        let phase: Double = elapsed.truncatingRemainder(dividingBy: period) / period
        let downwind: Double = (weather.windDirectionDeg + 180).truncatingRemainder(dividingBy: 360)
        guard let gradient = Self.smokeGradient() else { return }

        let puffCount: Int = gusty ? 40 : 30
        for index in 0..<puffCount {
            let puff = Self.travelPuff(index: index, count: puffCount, phase: phase, gusty: gusty, plume: plume)
            drawPuff(puff, downwind: downwind, zoomScale: zoomScale, gradient: gradient, in: context)
        }
        for index in 0..<10 {
            let puff = Self.sourcePuff(index: index, phase: phase, scatter: plume.scatter)
            drawPuff(puff, downwind: downwind, zoomScale: zoomScale, gradient: gradient, in: context)
        }
        drawArrow(downwind: downwind, zoomScale: zoomScale, in: context)
    }

    private func drawPuff(
        _ puff: SmokePuff,
        downwind: Double,
        zoomScale: MKZoomScale,
        gradient: CGGradient,
        in context: CGContext
    ) {
        let along = Self.destination(latitude: fix.latitude, longitude: fix.longitude, meters: puff.distance, bearing: downwind)
        let crosswind: Double = downwind + 90
        let position = Self.destination(latitude: along.latitude, longitude: along.longitude, meters: puff.lateral, bearing: crosswind)
        let centre: CGPoint = point(for: MKMapPoint(position))
        let scale: Double = Double(zoomScale)
        let radius: CGFloat = CGFloat(puff.screenRadius / scale)
        context.saveGState()
        context.setAlpha(CGFloat(puff.opacity))
        context.drawRadialGradient(gradient, startCenter: centre, startRadius: 0, endCenter: centre, endRadius: radius, options: [])
        context.restoreGState()
    }

    private func drawArrow(downwind: Double, zoomScale: MKZoomScale, in context: CGContext) {
        let tipDistance: Double = plume.lengthM * 0.92
        let backDistance: Double = plume.lengthM * 0.78
        let tip = Self.destination(latitude: fix.latitude, longitude: fix.longitude, meters: tipDistance, bearing: downwind)
        let back = Self.destination(latitude: fix.latitude, longitude: fix.longitude, meters: backDistance, bearing: downwind)
        let left = Self.destination(latitude: tip.latitude, longitude: tip.longitude, meters: 18, bearing: downwind + 140)
        let right = Self.destination(latitude: tip.latitude, longitude: tip.longitude, meters: 18, bearing: downwind - 140)
        let tipPoint: CGPoint = point(for: MKMapPoint(tip))
        let width: CGFloat = 4 / zoomScale
        context.saveGState()
        context.setStrokeColor(Self.pink.cgColor)
        context.setLineWidth(width)
        context.setLineCap(.round)
        context.move(to: point(for: MKMapPoint(back)))
        context.addLine(to: tipPoint)
        context.move(to: point(for: MKMapPoint(left)))
        context.addLine(to: tipPoint)
        context.move(to: point(for: MKMapPoint(right)))
        context.addLine(to: tipPoint)
        context.strokePath()
        context.restoreGState()
    }

    private static func driftPeriod(speedKmh: Double) -> Double {
        if speedKmh < 5 { return 4.2 }
        if speedKmh < 16 { return 2.8 }
        if speedKmh < 25 { return 1.8 }
        return 1.1
    }

    private static func smokeGradient() -> CGGradient? {
        let colors = [highlight.cgColor, pink.withAlphaComponent(0).cgColor] as CFArray
        return CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1])
    }

    private static func travelPuff(index: Int, count: Int, phase: Double, gusty: Bool, plume: Plume) -> SmokePuff {
        let slot: Double = Double(index) / Double(count)
        let age: Double = (phase + slot).truncatingRemainder(dividingBy: 1)
        let distance: Double = 6 + (age * plume.lengthM)
        let halfWidth: Double = plume.halfWidthAt(distanceM: distance) * plume.scatter
        let lane: Double = Double((index % 7) - 3) / 3.2
        let wobbleTurns: Double = gusty ? 2.4 : 1.1
        let wobble: Double = sin((phase * 2 * .pi * wobbleTurns) + (Double(index) * 1.3))
        let wobbleAmount: Double = gusty ? 0.45 : 0.18
        let laneMix: Double = (lane * 0.85) + (wobble * wobbleAmount)
        let alongMix: Double = 0.25 + (0.75 * age)
        let lateral: Double = halfWidth * laneMix * alongMix
        let billow: Double = 0.55 + (0.45 * sin(.pi * age))
        let baseRadius: Double = gusty ? 16 : 13
        let screenRadius: Double = baseRadius + (billow * 18)
        let fadeIn: Double = min(1, age / 0.08)
        let tail: Double = 0.28 + (plume.dilution * 0.2)
        let fadeOut: Double = min(1, (1 - age) / tail)
        let strength: Double = 0.9 - (plume.dilution * 0.35)
        let opacity: Double = min(0.82, max(0.18, fadeIn * fadeOut * strength))
        return SmokePuff(distance: distance, lateral: lateral, screenRadius: screenRadius, opacity: opacity)
    }

    private static func sourcePuff(index: Int, phase: Double, scatter: Double) -> SmokePuff {
        let age: Double = ((phase * 1.4) + (Double(index) * 0.1)).truncatingRemainder(dividingBy: 1)
        let distance: Double = 4 + (age * 28)
        let wobble: Double = sin((phase * 6) + Double(index)) * 8 * scatter
        let screenRadius: Double = 18 + (age * 14)
        let opacity: Double = 0.55 * (1 - (age * 0.45))
        return SmokePuff(distance: distance, lateral: wobble, screenRadius: screenRadius, opacity: opacity)
    }

    private static func destination(latitude: Double, longitude: Double, meters: Double, bearing: Double) -> CLLocationCoordinate2D {
        let earthRadius: Double = 6_371_000
        let angular: Double = meters / earthRadius
        let direction: Double = bearing * .pi / 180
        let lat1: Double = latitude * .pi / 180
        let lon1: Double = longitude * .pi / 180
        let latSin: Double = sin(lat1) * cos(angular)
        let latCos: Double = cos(lat1) * sin(angular) * cos(direction)
        let lat2: Double = asin(latSin + latCos)
        let east: Double = sin(direction) * sin(angular) * cos(lat1)
        let north: Double = cos(angular) - (sin(lat1) * sin(lat2))
        let lon2: Double = lon1 + atan2(east, north)
        return CLLocationCoordinate2D(latitude: lat2 * 180 / .pi, longitude: lon2 * 180 / .pi)
    }
}

private struct SmokePuff {
    let distance: Double
    let lateral: Double
    let screenRadius: Double
    let opacity: Double
}

private enum MapZoom {
    static let minMeters = 50.0
    static let maxMeters = 2_000.0
    static let steps: [Double] = [50, 100, 200, 400, 800, 1_400, 2_000]

    static func clamp(_ meters: Double) -> Double {
        min(maxMeters, max(minMeters, meters))
    }

    static func step(_ current: Double, inward: Bool) -> Double {
        if inward {
            return steps.last(where: { $0 < current * 0.92 }) ?? steps[0]
        }
        return steps.first(where: { $0 > current * 1.08 }) ?? steps[steps.count - 1]
    }

    static func label(_ meters: Double) -> String {
        if meters >= 950 {
            let kilometres = meters / 1000
            if abs(kilometres - kilometres.rounded()) < 0.08 {
                return String(format: "%.0f km", kilometres)
            }
            return String(format: "%.1f km", kilometres)
        }
        return String(format: "%.0f m", meters)
    }
}

private struct MapViewHost: UIViewRepresentable {
    var centre: CLLocationCoordinate2D
    var spanMeters: Double
    var regionRequestToken: Int

    var markers: [FieldMarker]
    var culls: [CullRecord]
    var boundaries: [BoundaryArea]
    var routes: [Route]
    var trackPoints: [TrackPoint]
    var showCulls: Bool
    var homingTarget: CLLocationCoordinate2D?
    var pendingPoints: [BoundaryPoint]
    var mapStyle: MapLayerStyle
    var showGrid: Bool
    var northUpToken: Int
    var windFix: GpsFix?
    var windWeather: WeatherSnapshot?
    var focusedBoundaryIds: Set<Int64>
    var linkedBoundaryIds: Set<Int64>
    var bronze: UIColor

    /// True while a "Draw it" boundary capture is in progress: map panning is
    /// disabled so a one-finger drag sketches the fence line instead.
    var isDrawing: Bool

    var onRegionChanged: (CLLocationCoordinate2D, Double) -> Void
    var onUserPanned: () -> Void
    var onSelectMarker: (Int64) -> Void
    var onMapTapped: (CLLocationCoordinate2D) -> Void
    var onDrawPoint: (CLLocationCoordinate2D) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    /// Duplicate-safe dictionary build — `Dictionary(uniqueKeysWithValues:)` traps on collision.
    static func countMap(_ pairs: [(Int, Int)]) -> [Int: Int] {
        var result: [Int: Int] = [:]
        for pair in pairs {
            result[pair.0] = pair.1
        }
        return result
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView(frame: .zero)
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = true
        mapView.showsCompass = true
        mapView.showsScale = true
        mapView.isRotateEnabled = true
        mapView.isPitchEnabled = false
        mapView.pointOfInterestFilter = .excludingAll
        // Loose camera limits; the exact 50 m–2 km view height is clamped in regionDidChange.
        mapView.setCameraZoomRange(
            MKMapView.CameraZoomRange(minCenterCoordinateDistance: 30, maxCenterCoordinateDistance: 12_000),
            animated: false
        )

        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap(_:))
        )
        tap.cancelsTouchesInView = false
        tap.delegate = context.coordinator
        let longPress = UILongPressGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleLongPress(_:))
        )
        longPress.delegate = context.coordinator
        mapView.addGestureRecognizer(longPress)
        tap.require(toFail: longPress)
        mapView.addGestureRecognizer(tap)

        // Freehand fence drawing: single-finger drag, only active in draw mode.
        let drawPan = UIPanGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleDrawPan(_:))
        )
        drawPan.maximumNumberOfTouches = 1
        drawPan.cancelsTouchesInView = false
        drawPan.delegate = context.coordinator
        mapView.addGestureRecognizer(drawPan)
        context.coordinator.drawRecognizer = drawPan

        context.coordinator.applyStyle(mapView, style: mapStyle)
        context.coordinator.startSmokeAnimation(mapView)
        return mapView
    }

    static func dismantleUIView(_ mapView: MKMapView, coordinator: Coordinator) {
        coordinator.stopSmokeAnimation()
        mapView.delegate = nil
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self

        coordinator.applyStyle(mapView, style: mapStyle)
        if coordinator.lastNorthUpToken != northUpToken {
            coordinator.lastNorthUpToken = northUpToken
            let camera = mapView.camera.copy() as! MKMapCamera
            camera.heading = 0
            mapView.setCamera(camera, animated: true)
        }

        // Panning/zooming would fight the stroke, so lock the map while drawing.
        mapView.isScrollEnabled = !isDrawing
        mapView.isZoomEnabled = !isDrawing
        mapView.isRotateEnabled = !isDrawing

        if coordinator.lastRegionToken != regionRequestToken {
            coordinator.lastRegionToken = regionRequestToken
            let region = MKCoordinateRegion(
                center: centre,
                latitudinalMeters: spanMeters,
                longitudinalMeters: spanMeters
            )
            mapView.setRegion(region, animated: true)
        }

        let snapshot = MapContentSnapshot(
            markers: markers,
            culls: showCulls ? culls.filter { $0.latitude != nil && $0.longitude != nil } : [],
            boundaryIds: boundaries.map { $0.id },
            boundaryPointCounts: Self.countMap(boundaries.map { (Int($0.id), $0.points.count) }),
            routeIds: routes.map { $0.id },
            routePointCounts: Self.countMap(routes.map { route in
                (Int(route.id), trackPoints.filter { $0.routeId == route.id }.count)
            }),
            showCulls: showCulls,
            homingLatitude: homingTarget?.latitude,
            homingLongitude: homingTarget?.longitude,
            pendingCoordinates: pendingPoints.map { "\($0.orderIndex):\($0.latitude):\($0.longitude)" },
            focusedBoundaryIds: focusedBoundaryIds.sorted(),
            linkedBoundaryIds: linkedBoundaryIds.sorted(),
            zoneFingerprints: boundaries.flatMap { boundary in
                boundary.zones.map { "\($0.id):\($0.kind.rawValue):\($0.name):\($0.points.count):\($0.notes)" }
            }
        )

        if coordinator.lastSnapshot != snapshot {
            coordinator.lastSnapshot = snapshot
            coordinator.rebuild(mapView: mapView, host: self)
        }
        coordinator.updateGrid(mapView)
        coordinator.updatePlume(mapView)
    }
}

// MARK: - Coordinator

private final class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
    var parent: MapViewHost
    var lastSnapshot: MapContentSnapshot?
    var lastRegionToken: Int = -1
    var lastStyle: MapLayerStyle?
    var drawRecognizer: UIPanGestureRecognizer?
    private var appliedAnnotations: [MKAnnotation] = []
    private var overlayKinds: [ObjectIdentifier: OverlayKind] = [:]
    var lastNorthUpToken = 0
    private var gridOverlays: [MKPolyline] = []
    private var plumeOverlays: [MKCircle] = []
    private var smokeTimer: Timer?
    private var applyingZoomClamp = false
    private var gridKey = ""
    private var lastPlumeFix: GpsFix?
    private var lastPlumeWeather: WeatherSnapshot?
    private var lastPlumeFresh = false

    init(parent: MapViewHost) {
        self.parent = parent
    }

    func startSmokeAnimation(_ mapView: MKMapView) {
        let timer = Timer(timeInterval: 0.07, repeats: true) { [weak self, weak mapView] _ in
            guard let self = self, let mapView = mapView else { return }
            guard mapView.window != nil, UIApplication.shared.applicationState == .active else { return }
            self.updatePlume(mapView)
            for overlay in self.plumeOverlays {
                mapView.renderer(for: overlay)?.setNeedsDisplay()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        smokeTimer = timer
    }

    func stopSmokeAnimation() {
        smokeTimer?.invalidate()
        smokeTimer = nil
    }

    func applyStyle(_ mapView: MKMapView, style: MapLayerStyle) {
        guard lastStyle != style else { return }
        lastStyle = style
        switch style {
        case .street:
            mapView.mapType = .standard
        case .outdoor:
            mapView.mapType = .mutedStandard
        case .satellite:
            mapView.mapType = .hybrid
        }
    }

    /// Removes every overlay and annotation we previously added, then rebuilds.
    /// Annotation order is de-duplicated by kind+id so repeated updates cannot stack.
    func rebuild(mapView: MKMapView, host: MapViewHost) {
        mapView.removeOverlays(mapView.overlays)
        overlayKinds.removeAll()
        gridOverlays = []
        plumeOverlays = []
        gridKey = ""
        lastPlumeFix = nil
        lastPlumeWeather = nil
        lastPlumeFresh = false
        if !appliedAnnotations.isEmpty {
            mapView.removeAnnotations(appliedAnnotations)
            appliedAnnotations.removeAll()
        }

        // --- Field markers ---
        let linkedCullIds = host.showCulls
            ? Set(host.culls.filter { $0.latitude != nil && $0.longitude != nil }.compactMap { $0.linkedMarkerId })
            : Set<Int64>()
        for marker in host.markers where !linkedCullIds.contains(marker.id) {
            let annotation = HuntAnnotation(
                coordinate: CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude),
                title: marker.name,
                subtitle: marker.category.label,
                kind: .fieldMarker,
                recordId: marker.id,
                tint: host.bronze
            )
            mapView.addAnnotation(annotation)
            appliedAnnotations.append(annotation)
        }

        // --- Cull pins (only when the preference is on) ---
        if host.showCulls {
            for record in host.culls {
                guard let latitude = record.latitude, let longitude = record.longitude else { continue }
                let annotation = HuntAnnotation(
                    coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                    title: record.speciesLabel,
                    subtitle: "Cull · \(record.count)",
                    kind: .cull,
                    recordId: record.id,
                    tint: UIColor.systemRed
                )
                mapView.addAnnotation(annotation)
                appliedAnnotations.append(annotation)
            }
        }

        // --- Homing target ---
        if let target = host.homingTarget {
            let annotation = HuntAnnotation(
                coordinate: target,
                title: "Homing target",
                subtitle: "Guide back to your animal",
                kind: .homing,
                recordId: 0,
                tint: UIColor.systemGreen
            )
            mapView.addAnnotation(annotation)
            appliedAnnotations.append(annotation)
        }

        // --- Boundary polygons ---
        for boundary in host.boundaries {
            let ordered = boundary.points.sorted { $0.orderIndex < $1.orderIndex }
            guard ordered.count >= 3 else { continue }
            var coordinates = ordered.map {
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
            }
            let polygon = MKPolygon(coordinates: &coordinates, count: coordinates.count)
            let linked = host.linkedBoundaryIds.contains(boundary.id)
            let highlighted = host.focusedBoundaryIds.contains(boundary.id)
            let width = CGFloat(highlighted ? 3.5 : (linked ? BoundaryFenceStyle.linkedWidth : BoundaryFenceStyle.drawWidth))
            let stroke = linked ? BoundaryFenceStyle.linkedColor : BoundaryFenceStyle.drawColor
            overlayKinds[ObjectIdentifier(polygon)] = .zone(
                strokeHex: stroke,
                lineWidth: width,
                fillHex: boundary.zoneType.mapColorHex,
                fillOpacity: boundary.zoneType.mapFillOpacity
            )
            mapView.addOverlay(polygon)

            for zone in boundary.zones {
                let zonePoints = zone.points.sorted { $0.orderIndex < $1.orderIndex }
                guard zonePoints.count >= 3 else { continue }
                var zoneCoordinates = zonePoints.map {
                    CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
                }
                let patch = MKPolygon(coordinates: &zoneCoordinates, count: zoneCoordinates.count)
                overlayKinds[ObjectIdentifier(patch)] = .fieldZone(
                    zoneId: zone.id,
                    kind: zone.kind,
                    strokeHex: zone.kind.mapColorHex,
                    fillHex: zone.kind.mapColorHex,
                    fillOpacity: zone.kind.mapFillOpacity
                )
                mapView.addOverlay(patch)
            }
        }

        // --- Tracks ---
        for route in host.routes {
            let points = host.trackPoints
                .filter { $0.routeId == route.id }
                .sorted { $0.recordedAtEpochMs < $1.recordedAtEpochMs }
            guard points.count >= 2 else { continue }
            var coordinates = points.map {
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
            }
            let line = MKPolyline(coordinates: &coordinates, count: coordinates.count)
            overlayKinds[ObjectIdentifier(line)] = .route
            mapView.addOverlay(line)
        }

        // --- In-progress boundary capture ---
        let pending = host.pendingPoints.sorted { $0.orderIndex < $1.orderIndex }
        if pending.count >= 2 {
            var coordinates = pending.map {
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
            }
            let line = MKPolyline(coordinates: &coordinates, count: coordinates.count)
            overlayKinds[ObjectIdentifier(line)] = .pendingBoundary
            mapView.addOverlay(line)
        }
        for point in pending {
            let annotation = HuntAnnotation(
                coordinate: CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude),
                title: "#\(point.orderIndex + 1)",
                subtitle: nil,
                kind: .boundaryPoint,
                recordId: Int64(point.orderIndex),
                tint: UIColor.systemOrange
            )
            mapView.addAnnotation(annotation)
            appliedAnnotations.append(annotation)
        }
    }

    @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began, !parent.isDrawing,
              let mapView = gesture.view as? MKMapView else { return }
        parent.onMapTapped(mapView.convert(gesture.location(in: mapView), toCoordinateFrom: mapView))
    }

    @objc func handleTap(_ gesture: UITapGestureRecognizer) {
        guard let mapView = gesture.view as? MKMapView else { return }
        let point = gesture.location(in: mapView)
        let coordinate = mapView.convert(point, toCoordinateFrom: mapView)
        parent.onMapTapped(coordinate)
    }

    /// Samples the finger as it drags, so a fence line can be sketched freehand
    /// the way Android's draw mode does (tap-to-add still works too).
    @objc func handleDrawPan(_ gesture: UIPanGestureRecognizer) {
        guard parent.isDrawing, let mapView = gesture.view as? MKMapView else { return }
        switch gesture.state {
        case .began, .changed:
            let point = gesture.location(in: mapView)
            let coordinate = mapView.convert(point, toCoordinateFrom: mapView)
            parent.onDrawPoint(coordinate)
        default:
            break
        }
    }

    func updateGrid(_ mapView: MKMapView) {
        let region = mapView.region
        let span = region.span.latitudeDelta * 111000
        let spacing: Double = span > 500000 ? 100000 : (span > 50000 ? 10000 : (span > 5000 ? 1000 : 100))
        let key = "\(parent.showGrid):\(region.center.latitude):\(region.center.longitude):\(region.span.latitudeDelta):\(region.span.longitudeDelta)"
        guard gridKey != key else { return }
        gridKey = key
        mapView.removeOverlays(gridOverlays)
        for line in gridOverlays { overlayKinds.removeValue(forKey: ObjectIdentifier(line)) }
        gridOverlays = []
        guard parent.showGrid else { return }
        let lines = OsGridRef.gridLinesForViewport(
            south: region.center.latitude - region.span.latitudeDelta / 2,
            west: region.center.longitude - region.span.longitudeDelta / 2,
            north: region.center.latitude + region.span.latitudeDelta / 2,
            east: region.center.longitude + region.span.longitudeDelta / 2,
            spacingMeters: spacing
        )
        for grid in lines {
            let coordinates = grid.points.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
            let line = MKPolyline(coordinates: coordinates, count: coordinates.count)
            overlayKinds[ObjectIdentifier(line)] = .grid(major: grid.major)
            gridOverlays.append(line)
            mapView.addOverlay(line, level: .aboveRoads)
        }
    }

    func updatePlume(_ mapView: MKMapView) {
        let fresh = ScentPlumeGeometry.usableWeather(parent.windWeather, for: parent.windFix) != nil
        let moved: Bool
        if let last = lastPlumeFix, let fix = parent.windFix {
            moved = DatabaseService.haversineMeters(last.latitude, last.longitude, fix.latitude, fix.longitude) > 20
        } else {
            moved = (lastPlumeFix == nil) != (parent.windFix == nil)
        }
        let weatherChanged = lastPlumeWeather != parent.windWeather || lastPlumeFresh != fresh
        let shouldDraw = fresh && (plumeOverlays.isEmpty || moved || weatherChanged)
        let shouldClear = !fresh && !plumeOverlays.isEmpty
        guard shouldDraw || shouldClear else { return }
        lastPlumeFix = parent.windFix
        lastPlumeWeather = parent.windWeather
        lastPlumeFresh = fresh
        mapView.removeOverlays(plumeOverlays)
        for plume in plumeOverlays { overlayKinds.removeValue(forKey: ObjectIdentifier(plume)) }
        plumeOverlays = []
        guard fresh, let fix = parent.windFix, let weather = parent.windWeather else { return }
        let plume = ScentPlumeGeometry.forConditions(
            speedKmh: weather.windSpeedKmh, gustKmh: weather.windGustKmh,
            humidityPercent: weather.humidityPercent, temperatureC: weather.temperatureC
        )
        let overlay = MKCircle(center: CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude),
                               radius: plume.lengthM * 1.5 + 100)
        overlayKinds[ObjectIdentifier(overlay)] = .plume
        plumeOverlays.append(overlay)
        mapView.addOverlay(overlay, level: .aboveRoads)
    }

    // MARK: UIGestureRecognizerDelegate

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        var view = touch.view
        while let current = view {
            if current is MKAnnotationView || current is UIControl { return false }
            view = current.superview
        }
        return true
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        if gestureRecognizer === drawRecognizer {
            return parent.isDrawing
        }
        return true
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRequireFailureOf otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        guard let tap = gestureRecognizer as? UITapGestureRecognizer,
              let other = otherGestureRecognizer as? UITapGestureRecognizer else { return false }
        return tap.numberOfTapsRequired == 1 && other.numberOfTapsRequired == 2
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        // A tap must fail when the user pans/pinches; a drawn stroke may coexist
        // with MapKit's disabled recognizers without blocking pencil input.
        gestureRecognizer === drawRecognizer || otherGestureRecognizer === drawRecognizer
    }

    // MARK: MKMapViewDelegate

    func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
        if annotation is MKUserLocation {
            let identifier = "hunt-user"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
                ?? MKAnnotationView(annotation: annotation, reuseIdentifier: identifier)
            view.annotation = annotation
            view.image = UIImage(systemName: "figure.stand", withConfiguration: UIImage.SymbolConfiguration(pointSize: 30, weight: .bold))?
                .withTintColor(parent.bronze, renderingMode: .alwaysOriginal)
            view.canShowCallout = false
            view.accessibilityLabel = "Your GPS location"
            return view
        }
        guard let hunt = annotation as? HuntAnnotation else { return nil }

        let identifier = "hunt-\(hunt.kind.rawValue)"
        if hunt.kind == .fieldMarker || hunt.kind == .cull {
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
                ?? MKAnnotationView(annotation: annotation, reuseIdentifier: identifier)
            view.annotation = annotation
            view.canShowCallout = true
            let marker = parent.markers.first { $0.id == hunt.recordId }
            let category: MarkerCategory = hunt.kind == .cull ? .cull : (marker?.category ?? .otherWildlife)
            let speciesId = hunt.kind == .cull ? nil : marker?.speciesId
            let diameter: CGFloat = (hunt.kind == .cull || category == .cull)
                ? MarkerArtwork.cullDiameter
                : MarkerArtwork.markerDiameter
            view.image = MarkerArtwork.icon(category: category, speciesId: speciesId, diameter: diameter)
            view.centerOffset = CGPoint(x: 0, y: -diameter / 2)
            view.rightCalloutAccessoryView = hunt.kind == .fieldMarker ? UIButton(type: .detailDisclosure) : nil
            return view
        }

        let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView
            ?? MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
        view.annotation = annotation
        view.canShowCallout = false
        view.markerTintColor = hunt.tint
        view.glyphTintColor = .white
        view.glyphImage = UIImage(systemName: hunt.kind == .homing ? "arrow.down.circle" : "circle.fill")
        return view
    }

    func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
        guard let hunt = view.annotation as? HuntAnnotation, hunt.kind == .fieldMarker else { return }
        parent.onSelectMarker(hunt.recordId)
    }

    func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
        switch overlayKinds[ObjectIdentifier(overlay)] {
        case .zone(let strokeHex, let lineWidth, let fillHex, let fillOpacity):
            guard let polygon = overlay as? MKPolygon else {
                return MKOverlayRenderer(overlay: overlay)
            }
            let renderer = MKPolygonRenderer(polygon: polygon)
            renderer.strokeColor = mapUIColor(hex: strokeHex)
            renderer.lineWidth = lineWidth
            renderer.fillColor = mapUIColor(hex: fillHex).withAlphaComponent(CGFloat(fillOpacity))
            return renderer

        case .fieldZone(_, let kind, let strokeHex, _, let fillOpacity):
            guard let polygon = overlay as? MKPolygon else {
                return MKOverlayRenderer(overlay: overlay)
            }
            let renderer = HatchedPolygonRenderer(polygon: polygon)
            renderer.strokeColor = mapUIColor(hex: strokeHex)
            renderer.lineWidth = 2.6
            renderer.fillColor = mapUIColor(hex: strokeHex).withAlphaComponent(CGFloat(fillOpacity))
            renderer.hatchRising = kind == .sold
            if kind == .sold {
                renderer.lineDashPattern = [NSNumber(value: 6), NSNumber(value: 4)]
            }
            return renderer

        case .route:
            guard let line = overlay as? MKPolyline else {
                return MKOverlayRenderer(overlay: overlay)
            }
            let renderer = MKPolylineRenderer(polyline: line)
            renderer.strokeColor = UIColor.systemOrange
            renderer.lineWidth = 4
            return renderer

        case .pendingBoundary:
            guard let line = overlay as? MKPolyline else {
                return MKOverlayRenderer(overlay: overlay)
            }
            let renderer = MKPolylineRenderer(polyline: line)
            renderer.strokeColor = mapUIColor(hex: BoundaryFenceStyle.drawColor)
            renderer.lineWidth = CGFloat(BoundaryFenceStyle.drawWidth)
            return renderer

        case .grid(let major):
            guard let line = overlay as? MKPolyline else { return MKOverlayRenderer(overlay: overlay) }
            let renderer = MKPolylineRenderer(polyline: line)
            renderer.strokeColor = UIColor.systemYellow.withAlphaComponent(major ? 0.8 : 0.45)
            renderer.lineWidth = major ? 1.5 : 0.75
            return renderer

        case .plume:
            guard let fix = parent.windFix, let weather = parent.windWeather else {
                return MKOverlayRenderer(overlay: overlay)
            }
            return ScentSmokeRenderer(overlay: overlay, fix: fix, weather: weather)

        case .none:
            // Not one of ours (e.g. a future MKTileOverlay) — use the default renderer.
            return MKOverlayRenderer(overlay: overlay)
        }
    }

    func mapView(_ mapView: MKMapView, regionWillChangeAnimated animated: Bool) {
        let gestures = (mapView.gestureRecognizers ?? []) + mapView.subviews.flatMap { $0.gestureRecognizers ?? [] }
        if gestures.contains(where: { $0.state == .began || $0.state == .changed }) {
            DispatchQueue.main.async { [weak self] in self?.parent.onUserPanned() }
        }
    }

    func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
        let centre = mapView.centerCoordinate
        let height = mapView.region.span.latitudeDelta * 111_000
        let clamped = MapZoom.clamp(height)
        if !applyingZoomClamp, abs(height - clamped) > max(8, clamped * 0.06) {
            applyingZoomClamp = true
            let region = MKCoordinateRegion(
                center: centre,
                latitudinalMeters: clamped,
                longitudinalMeters: clamped
            )
            mapView.setRegion(region, animated: false)
            applyingZoomClamp = false
            return
        }
        updateGrid(mapView)
        // Hop to the next runloop so we never mutate SwiftUI state mid-update.
        DispatchQueue.main.async { [weak self] in
            self?.parent.onRegionChanged(centre, clamped)
        }
    }
}

// MARK: - Map screen

public struct MapScreen: View {
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var sound = SoundManager.shared
    @ObservedObject private var weatherService = WeatherService.shared

    @State private var centre = CLLocationCoordinate2D(latitude: 52.4862, longitude: -1.8904)
    @State private var regionRequestToken = 0
    @State private var spanMeters: Double = 1200

    @State private var showingAddSheet = false
    @State private var showingLayers = false
    @State private var showingGridInput = false
    @State private var showingHoming = false
    @State private var showingBoundarySetup = false
    @State private var northUpToken = 0
    @State private var markerCoordinate: CLLocationCoordinate2D?
    @State private var followUser = true
    @State private var lastWeatherRequest: GpsFix?
    @State private var mapMessage: String?

    @State private var selectedMarker: FieldMarker?
    @State private var gridInput = ""

    @State private var activeRouteId: Int64?
    @State private var lastAppendedAtEpochMs: Int64 = 0

    @State private var homingTarget: CLLocationCoordinate2D?
    @State private var homingLabel = ""
    @State private var showArrivalAlert = false
    @State private var arrivalNotified = false

    @State private var capture: PendingBoundaryCreation?
    @State private var zoneCapture: PendingZoneCapture?
    @State private var selectedZone: BoundaryZone?
    @State private var pendingPoints: [BoundaryPoint] = []
    @State private var boundaryName = ""

    private var isCapturing: Bool { capture != nil || zoneCapture != nil }

    @State private var mapCentreGridRef: String?

    public init() {}

    public var body: some View {
        ScreenBackground {
            ZStack(alignment: .top) {
                MapViewHost(
                    centre: centre,
                    spanMeters: spanMeters,
                    regionRequestToken: regionRequestToken,
                    markers: db.markers,
                    culls: db.culls,
                    boundaries: visibleBoundaries,
                    routes: db.routes,
                    trackPoints: db.trackPoints,
                    showCulls: db.preferences.showCullPinsOnMap,
                    homingTarget: homingTarget,
                    pendingPoints: pendingPoints,
                    mapStyle: db.preferences.mapLayerStyle,
                    showGrid: db.preferences.showOsGridRef,
                    northUpToken: northUpToken,
                    windFix: location.latestFix,
                    windWeather: weatherService.weather ?? db.cachedWeather,
                    focusedBoundaryIds: Set(db.permissionMapFocus.map { $0.boundaryAreaId }),
                    linkedBoundaryIds: linkedBoundaryIds,
                    bronze: UIColor(theme.palette.bronze),
                    isDrawing: capture?.mode == .draw || zoneCapture != nil,
                    onRegionChanged: { coordinate, visibleSpan in
                        centre = coordinate
                        spanMeters = visibleSpan
                        updateGridRef(coordinate)
                    },
                    onUserPanned: { followUser = false },
                    onSelectMarker: { markerId in
                        selectedMarker = db.markers.first { $0.id == markerId }
                    },
                    onMapTapped: { coordinate in
                        handleMapTap(coordinate)
                    },
                    onDrawPoint: { coordinate in
                        appendDraftPoint(coordinate, minimumMeters: 5, playClick: false)
                    }
                )
                .ignoresSafeArea()
            }
            .overlay(alignment: .top) {
                VStack(spacing: 6) {
                    topBar
                    if !db.permissionMapFocus.isEmpty {
                        focusBanner
                            .padding(.horizontal, 14)
                    }
                    if let zone = selectedZone {
                        zoneBanner(zone)
                            .padding(.horizontal, 14)
                    }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if !isCapturing {
                    mapControls
                        .padding(.trailing, 12)
                        .padding(.bottom, 8)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if isCapturing {
                    captureBar
                        .padding(.horizontal, 12)
                        .padding(.top, 4)
                        .padding(.bottom, 6)
                        .background(theme.palette.forestDeep.opacity(0.94))
                } else {
                    bottomPanel
                }
            }
        }
        .sheet(isPresented: $showingAddSheet) {
            MarkLocationSheet(
                isPresented: $showingAddSheet,
                pinnedLatitude: markerCoordinate?.latitude,
                pinnedLongitude: markerCoordinate?.longitude
            )
        }
        .sheet(isPresented: $showingLayers) {
            MapLayersSheet()
        }
        .sheet(isPresented: $showingHoming) {
            homingSheet
        }
        .sheet(isPresented: $showingBoundarySetup) {
            PermissionBoundarySheet(permissions: db.permissions, returnToPermissions: false) { pending in
                db.pendingBoundary = pending
            }
        }
        .alert("Map", isPresented: Binding(
            get: { mapMessage != nil },
            set: { if !$0 { mapMessage = nil } }
        )) {
            Button("OK") { mapMessage = nil }
        } message: {
            Text(mapMessage ?? "")
        }
        .sheet(item: $selectedMarker) { marker in
            MarkerDetailSheet(marker: marker) {
                selectedMarker = nil
            }
        }
        .alert("Did you arrive?", isPresented: $showArrivalAlert) {
            Button("On target") {
                endHoming()
            }
            Button("Keep going", role: .cancel) { }
        } message: {
            Text("You are within 25 m of \(homingLabel.isEmpty ? "your homing target" : homingLabel).")
        }
        .alert("Go to OS Grid", isPresented: $showingGridInput) {
            TextField("e.g. SU 37290 15552", text: $gridInput)
            Button("Go") {
                goToGridRef(gridInput)
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("Enter a UK Ordnance Survey grid reference.")
        }
        .onAppear {
            location.requestPermission()
            location.startUpdating()
            if let fix = location.latestFix {
                centre = CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude)
                regionRequestToken += 1
            }
            updateGridRef(centre)
            syncBoundaryCapture()
            syncZoneCapture()
            refreshWindIfNeeded()
        }
        .onChange(of: location.latestFix) { fix in
            guard let fix = fix else { return }
            if followUser {
                centre = CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude)
                regionRequestToken += 1
            }
            refreshWindIfNeeded()
            recordIfNeeded(fix)
            checkHomingArrival(fix)
            if capture?.mode == .walk {
                appendWalkPoint(fix)
            }
        }
        .onChange(of: db.pendingBoundary) { _ in
            syncBoundaryCapture()
        }
        .onChange(of: db.pendingZone) { _ in
            syncZoneCapture()
        }
        .onChange(of: db.permissionMapFocus) { focus in
            frameBoundaries(focus.map { $0.boundaryAreaId })
        }
        .onChange(of: db.preferences.showOsGridRef) { _ in updateGridRef(centre) }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            refreshWindIfNeeded()
        }
        .onReceive(Timer.publish(every: 30, on: .main, in: .common).autoconnect()) { _ in
            if UIApplication.shared.applicationState == .active { refreshWindIfNeeded() }
        }
    }

    // MARK: - Top bar

    private var topBar: some View {
        VStack(spacing: 8) {
            HStack(spacing: 10) {
                Text("Map")
                    .font(.field(size: 22, weight: .bold))
                    .foregroundColor(theme.palette.cream)
                Spacer()

                if db.preferences.showOsGridRef {
                    Text(mapCentreGridRef ?? "Outside GB")
                        .font(.field(size: 12, weight: .semibold, design: .monospaced))
                        .foregroundColor(theme.palette.bronzeBright)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(theme.palette.charcoal.opacity(0.75))
                        .cornerRadius(6)
                        .onTapGesture { showingGridInput = true }
                }

                Button {
                    showingLayers = true
                } label: {
                    Image(systemName: "square.3.layers.3d")
                        .font(.field(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                        .padding(8)
                        .background(theme.palette.charcoal.opacity(0.75))
                        .cornerRadius(8)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                recordButton
                homingButton
                mapMenu
                if let route = activeRoute {
                    Text(routeText(route))
                        .font(.field(size: 11, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(theme.palette.charcoal.opacity(0.75))
                        .cornerRadius(6)
                }
                Spacer()
                if !db.routes.isEmpty {
                    Menu {
                        Button("Delete last track", role: .destructive) {
                            if let last = db.routes.last {
                                db.deleteRoute(routeId: last.id)
                            }
                        }
                        Button("Delete all tracks", role: .destructive) {
                            activeRouteId = nil
                            db.clearRoutes()
                        }
                    } label: {
                        Image(systemName: "trash")
                            .font(.field(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.mist)
                            .padding(7)
                            .background(theme.palette.charcoal.opacity(0.75))
                            .cornerRadius(8)
                    }
                }
            }

            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
        .background(theme.palette.forestDeep.opacity(0.9))
    }

    private var activeRoute: Route? {
        guard let id = activeRouteId else { return nil }
        return db.routes.first { $0.id == id }
    }

    private var recordButton: some View {
        Button {
            if activeRouteId == nil {
                let id = db.startRoute()
                activeRouteId = id
                lastAppendedAtEpochMs = 0
                sound.play(.gpsLock)
            } else if let id = activeRouteId {
                db.endRoute(routeId: id)
                activeRouteId = nil
                sound.play(.success)
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: activeRouteId == nil ? "record.circle" : "stop.circle.fill")
                Text(activeRouteId == nil ? "Record" : "Stop")
            }
            .font(.field(size: 12, weight: .bold))
            .foregroundColor(activeRouteId == nil ? theme.palette.cream : theme.palette.danger)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(theme.palette.charcoal.opacity(0.75))
            .cornerRadius(8)
        }
    }

    private var homingButton: some View {
        Button {
            showingHoming = true
        } label: {
            HStack(spacing: 5) {
                Image(systemName: homingTarget == nil ? "arrow.down.circle" : "xmark.circle")
                Text(homingTarget == nil ? "Homing" : "End homing")
            }
            .font(.field(size: 12, weight: .bold))
            .foregroundColor(homingTarget == nil ? theme.palette.cream : theme.palette.success)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(theme.palette.charcoal.opacity(0.75))
            .cornerRadius(8)
        }
    }

    private var captureBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    SectionLabel(zoneCapture == nil ? "Boundary capture" : (zoneCapture?.zoneId == nil ? "Draw zone" : "Redraw zone"))
                    Text(zoneCapture?.kind.displayName ?? capture?.zoneType.displayName ?? "")
                        .font(.field(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                    if let zoneName = zoneCapture?.name, !zoneName.isEmpty {
                        Text(zoneName)
                            .font(.field(size: 13, weight: .semibold))
                            .foregroundColor(theme.palette.bronzeBright)
                    }
                }
                Spacer(minLength: 8)
                Text(MapZoom.label(spanMeters))
                    .font(.field(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(theme.palette.bronzeBright)
            }
            Text(captureHelp)
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 8) {
                Text("\(pendingPoints.count) points")
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
                if pendingPoints.count >= 3 {
                    Text(areaText(BoundaryArea(name: "", zoneType: capture?.zoneType ?? .hunting, points: pendingPoints, areaSqMeters: DatabaseService.areaSqMeters(pendingPoints))))
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                if capture?.mode == .walk {
                    Button("Add fix") {
                        if let fix = location.latestFix { appendWalkPoint(fix) }
                    }
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
                }
                zoomChip("plus") { zoom(inward: true) }
                zoomChip("minus") { zoom(inward: false) }
            }

            HuntButton(zoneCapture == nil ? "Save boundary" : "Save zone", icon: "checkmark") {
                if zoneCapture != nil { saveZone() } else { saveBoundary() }
            }
                .disabled(pendingPoints.count < 3)

            HStack(spacing: 10) {
                Button {
                    if !pendingPoints.isEmpty { pendingPoints.removeLast() }
                } label: {
                    Text("Undo point")
                        .font(.field(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(theme.palette.charcoal)
                        .foregroundColor(pendingPoints.isEmpty ? theme.palette.mist : theme.palette.cream)
                        .cornerRadius(10)
                }
                .disabled(pendingPoints.isEmpty)

                Button {
                    finishBoundaryCapture()
                } label: {
                    Text("Cancel")
                        .font(.field(size: 15, weight: .bold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(theme.palette.danger)
                        .foregroundColor(theme.palette.cream)
                        .cornerRadius(10)
                }
            }
        }
        .padding(12)
    }

    private func zoomChip(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.field(size: 16, weight: .bold))
                .frame(width: 36, height: 36)
                .foregroundColor(theme.palette.cream)
                .background(theme.palette.charcoal)
                .cornerRadius(8)
        }
        .accessibilityLabel(symbol == "plus" ? "Zoom in" : "Zoom out")
    }

    private var captureHelp: String {
        if zoneCapture != nil {
            return "Drag on the map to draw the zone. It can extend past the boundary edge. "
                + "You can also tap corners one at a time."
        }
        return capture?.mode == .walk
            ? "Walk the boundary — each GPS fix is appended in order."
            : "Drag on the map to draw the fence line — lift your finger to finish. "
                + "You can also tap corners one at a time."
    }

    private func zoneBanner(_ zone: BoundaryZone) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(zone.kind.displayName)
                    .font(.field(size: 12, weight: .bold))
                    .foregroundColor(theme.palette.cream)
                Text(zone.label)
                    .font(.field(size: 14, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
                    .lineLimit(1)
                if !zone.notes.isEmpty {
                    Text(zone.notes)
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 4)
            Button("Close") { selectedZone = nil }
                .font(.field(size: 12, weight: .bold))
                .foregroundColor(theme.palette.bronzeBright)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(theme.palette.charcoal.opacity(0.92))
        .cornerRadius(8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Selected zone \(zone.kind.displayName) \(zone.label)")
    }

    private var mapMenu: some View {
        Menu {
            Button {
                guard let fix = location.latestFix else { mapMessage = "Wait for a GPS fix before setting home here."; return }
                homingTarget = CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude)
                homingLabel = "Home"
                db.addMarker(FieldMarker(category: .homing, name: "Home", latitude: fix.latitude, longitude: fix.longitude))
            } label: { Label("Home here", systemImage: "flag.fill") }
            Button { northUpToken += 1 } label: { Label("North up", systemImage: "location.north.fill") }
            Button { startBoundarySetup(.walk) } label: { Label("Walk fence", systemImage: "figure.walk") }
            Button { startBoundarySetup(.draw) } label: { Label("Draw fence", systemImage: "pencil.tip.crop.circle") }
            NavigationLink(destination: PermissionsScreen(captureOnCurrentMap: true)) { Label("Boundaries & permissions", systemImage: "doc.text") }
            Button { showingLayers = true } label: { Label("Layers & offline maps", systemImage: "square.3.layers.3d") }
            Button { showingGridInput = true } label: { Label("Go to OS Grid", systemImage: "number") }
            Toggle("Tap or hold to mark", isOn: Binding(
                get: { db.preferences.markAnywhereEnabled },
                set: { db.preferences.markAnywhereEnabled = $0 }
            ))
        } label: {
            Label("Menu", systemImage: "line.3.horizontal")
                .font(.field(size: 14, weight: .semibold))
                .padding(8)
                .foregroundColor(theme.palette.cream)
                .background(theme.palette.charcoal.opacity(0.85))
                .cornerRadius(8)
        }
    }

    private var mapControls: some View {
        VStack(spacing: 6) {
            Text(MapZoom.label(spanMeters))
                .font(.field(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(theme.palette.cream)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(theme.palette.charcoal.opacity(0.9))
                .cornerRadius(8)
                .accessibilityLabel("View height \(MapZoom.label(spanMeters))")
            mapControl("Zoom in", symbol: "plus") { zoom(inward: true) }
            mapControl("Zoom out", symbol: "minus") { zoom(inward: false) }
            mapControl("Centre on my GPS location", symbol: "figure.stand") { recenter() }
            HuntMarkLocationButton {
                markerCoordinate = nil
                showingAddSheet = true
            }
            .disabled(location.latestFix == nil || capture != nil)
        }
    }

    private var visibleBoundaries: [BoundaryArea] {
        db.boundaries.filter { boundary in
            let linked = db.permissions.filter { $0.boundaryAreaId == boundary.id }
            if linked.isEmpty { return true }
            return linked.contains { $0.showOnMap }
        }
    }

    private var linkedBoundaryIds: Set<Int64> {
        Set(db.permissions.compactMap { $0.boundaryAreaId })
    }

    private var focusBanner: some View {
        let labels = db.permissionMapFocus.map { $0.label }.filter { !$0.isEmpty }
        return HStack(spacing: 8) {
            Image(systemName: "link")
                .foregroundColor(theme.palette.bronzeBright)
            Text(labels.isEmpty ? "Linked boundary" : labels.joined(separator: ", "))
                .font(.field(size: 12, weight: .semibold))
                .foregroundColor(theme.palette.cream)
                .lineLimit(1)
            Spacer(minLength: 4)
            Button("Clear") { db.clearMapFocus() }
                .font(.field(size: 12, weight: .bold))
                .foregroundColor(theme.palette.bronzeBright)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(theme.palette.charcoal.opacity(0.9))
        .cornerRadius(8)
    }

    private func mapControl(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.field(size: 16, weight: .bold))
                .frame(width: 36, height: 36)
                .foregroundColor(theme.palette.cream)
                .background(theme.palette.charcoal.opacity(0.9))
                .cornerRadius(8)
                .frame(minWidth: 44, minHeight: 44)
        }
        .accessibilityLabel(title)
    }

    private func frameBoundaries(_ ids: [Int64]) {
        let unique = Set(ids)
        let points = db.boundaries.filter { unique.contains($0.id) }.flatMap { $0.points }
        guard let first = points.first else { return }
        var minLat = first.latitude
        var maxLat = first.latitude
        var minLon = first.longitude
        var maxLon = first.longitude
        for point in points {
            minLat = min(minLat, point.latitude)
            maxLat = max(maxLat, point.latitude)
            minLon = min(minLon, point.longitude)
            maxLon = max(maxLon, point.longitude)
        }
        followUser = false
        centre = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
        let height = max(MapZoom.minMeters, (maxLat - minLat) * 111_000 * 1.8)
        spanMeters = MapZoom.clamp(height)
        regionRequestToken += 1
    }

    private func zoom(inward: Bool) {
        followUser = false
        spanMeters = MapZoom.step(spanMeters, inward: inward)
        regionRequestToken += 1
    }

    private func recenter() {
        guard let fix = location.latestFix else { mapMessage = "Your GPS location is not available yet."; return }
        followUser = true
        centre = CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude)
        regionRequestToken += 1
    }

    private func startBoundarySetup(_: BoundaryCaptureMode) {
        guard capture == nil else { mapMessage = "Save or cancel the current boundary first."; return }
        showingBoundarySetup = true
    }

    private func syncBoundaryCapture() {
        guard zoneCapture == nil else { return }
        guard capture != db.pendingBoundary else { return }
        capture = db.pendingBoundary
        pendingPoints = []
        boundaryName = db.pendingBoundary?.name ?? ""
        if capture?.mode == .draw { followUser = false }
        if capture?.mode == .walk, let fix = location.latestFix { appendWalkPoint(fix) }
    }

    private func syncZoneCapture() {
        guard zoneCapture != db.pendingZone else { return }
        zoneCapture = db.pendingZone
        if zoneCapture != nil {
            capture = nil
            pendingPoints = []
            followUser = false
            if let boundaryId = zoneCapture?.boundaryId {
                frameBoundaries([boundaryId])
            }
        }
    }

    private func finishBoundaryCapture() {
        let returnToPermissions = capture?.returnToPermissions == true || zoneCapture?.returnToPermissions == true
        capture = nil
        zoneCapture = nil
        pendingPoints = []
        db.pendingBoundary = nil
        db.pendingZone = nil
        if returnToPermissions {
            NotificationCenter.default.post(name: .boundaryCaptureFinished, object: nil)
        }
    }

    private func refreshWindIfNeeded() {
        guard let fix = location.latestFix, !weatherService.loading else { return }
        let recent = weatherService.weather ?? db.cachedWeather
        if let recent = recent,
           Date().timeIntervalSince1970 * 1000 - Double(recent.fetchedAtEpochMs) < 600000,
           DatabaseService.haversineMeters(fix.latitude, fix.longitude, recent.latitude, recent.longitude) < 5000 { return }
        if let last = lastWeatherRequest,
           Date().timeIntervalSince1970 * 1000 - Double(last.timestampEpochMs) < 60000 { return }
        lastWeatherRequest = GpsFix(latitude: fix.latitude, longitude: fix.longitude, accuracyMeters: fix.accuracyMeters)
        weatherService.fetchWeather(latitude: fix.latitude, longitude: fix.longitude)
    }

    // MARK: - Bottom panel

    @ViewBuilder
    private var bottomPanel: some View {
        if homingTarget != nil {
            GlassPanel(compact: true) {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        SectionLabel("Homing retrieve")
                        Text(homingLabel.isEmpty ? "Target" : homingLabel)
                            .font(.field(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 3) {
                        Text(homingDistanceText)
                            .font(.field(size: 16, weight: .bold))
                            .foregroundColor(theme.palette.bronzeBright)
                        Text(homingBearingText)
                            .font(.field(size: 12))
                            .foregroundColor(theme.palette.mist)
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 8)
        }
    }

    private var homingSheet: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Homing retrieve")
                            .font(.field(size: 26, weight: .bold))
                            .foregroundColor(theme.palette.cream)

                        GlassPanel {
                            SectionLabel("Target")
                            Text(homingLabel.isEmpty ? "Not set" : homingLabel)
                                .font(.field(size: 18, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            MetricRow("Distance", homingDistanceText)
                            MetricRow("Bearing", homingBearingText)
                            Text("An arrival alert fires inside 25 m, and the target is highlighted on the map.")
                                .font(.field(size: 12))
                                .foregroundColor(theme.palette.mist)
                        }

                        if !markersWithHoming.isEmpty {
                            GlassPanel {
                                SectionLabel("Your homing pins")
                                ForEach(markersWithHoming) { marker in
                                    Button {
                                        homingTarget = CLLocationCoordinate2D(
                                            latitude: marker.latitude,
                                            longitude: marker.longitude
                                        )
                                        homingLabel = marker.name
                                        showingHoming = false
                                    } label: {
                                        HStack {
                                            Image(systemName: "arrow.down.circle")
                                                .foregroundColor(theme.palette.success)
                                            Text(marker.name)
                                                .font(.field(size: 14, weight: .semibold))
                                                .foregroundColor(theme.palette.cream)
                                            Spacer()
                                            Text(distanceText(marker))
                                                .font(.field(size: 12))
                                                .foregroundColor(theme.palette.mist)
                                        }
                                    }
                                }
                            }
                        }

                        HuntButton("Use map centre as target", icon: "target") {
                            homingTarget = centre
                            homingLabel = "Map centre"
                            showingHoming = false
                        }

                        if homingTarget != nil {
                            Button("End homing") {
                                endHoming()
                                showingHoming = false
                            }
                            .font(.field(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.danger)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Homing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Done") { showingHoming = false } } }
        }
    }

    private var markersWithHoming: [FieldMarker] {
        db.markers.filter { $0.category == .homing }
    }

    // MARK: - Actions

    private func handleMapTap(_ coordinate: CLLocationCoordinate2D) {
        if capture?.mode == .draw || zoneCapture != nil {
            appendDraftPoint(coordinate, minimumMeters: 3, playClick: true)
            return
        }
        if let zone = zone(at: coordinate) {
            selectedZone = zone
            return
        }
        selectedZone = nil
        if capture == nil, db.preferences.markAnywhereEnabled {
            followUser = false
            markerCoordinate = coordinate
            showingAddSheet = true
        }
    }

    private func zone(at coordinate: CLLocationCoordinate2D) -> BoundaryZone? {
        for boundary in visibleBoundaries.reversed() {
            for zone in boundary.zones.reversed() where polygonContains(zone.points, latitude: coordinate.latitude, longitude: coordinate.longitude) {
                return zone
            }
        }
        return nil
    }

    private func polygonContains(_ points: [BoundaryPoint], latitude: Double, longitude: Double) -> Bool {
        let ordered = points.sorted { $0.orderIndex < $1.orderIndex }
        guard ordered.count >= 3 else { return false }
        var inside = false
        var previous = ordered.count - 1
        for index in ordered.indices {
            let currentLat = ordered[index].latitude
            let currentLng = ordered[index].longitude
            let previousLat = ordered[previous].latitude
            let previousLng = ordered[previous].longitude
            let crosses = (currentLat > latitude) != (previousLat > latitude)
            if crosses {
                let span = previousLat - currentLat
                let edgeLng = span == 0
                    ? currentLng
                    : (previousLng - currentLng) * (latitude - currentLat) / span + currentLng
                if longitude < edgeLng { inside.toggle() }
            }
            previous = index
        }
        return inside
    }

    /// Adds a draft corner, ignoring samples that are too close to the previous one
    /// so a dragged stroke (or a twitchy tap) cannot pile up duplicate points.
    private func appendDraftPoint(
        _ coordinate: CLLocationCoordinate2D,
        minimumMeters: Double,
        playClick: Bool
    ) {
        guard capture?.mode == .draw || zoneCapture != nil else { return }
        if let last = pendingPoints.last {
            let moved = DatabaseService.haversineMeters(
                last.latitude, last.longitude, coordinate.latitude, coordinate.longitude
            )
            guard moved > minimumMeters else { return }
        }
        pendingPoints.append(
            BoundaryPoint(
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                orderIndex: pendingPoints.count
            )
        )
        if playClick {
            sound.play(.click)
        }
    }

    private func appendWalkPoint(_ fix: GpsFix) {
        // Skip poor fixes so boundaries do not jitter across the field.
        guard fix.accuracyMeters <= 50 else { return }
        if let last = pendingPoints.last {
            let moved = DatabaseService.haversineMeters(
                last.latitude, last.longitude, fix.latitude, fix.longitude
            )
            guard moved > 5 else { return }
        }
        pendingPoints.append(
            BoundaryPoint(
                latitude: fix.latitude,
                longitude: fix.longitude,
                orderIndex: pendingPoints.count
            )
        )
    }

    private func saveBoundary() {
        guard let capture = capture, pendingPoints.count >= 3 else { return }
        let name = boundaryName.trimmingCharacters(in: .whitespacesAndNewlines)
        let boundary = BoundaryArea(
            name: name.isEmpty ? capture.name : name,
            zoneType: capture.zoneType,
            points: pendingPoints,
            areaSqMeters: DatabaseService.areaSqMeters(pendingPoints)
        )
        let boundaryId = db.addBoundary(boundary)
        if let permissionId = capture.permissionId {
            db.linkPermission(id: permissionId, toBoundary: boundaryId)
        }

        finishBoundaryCapture()
        sound.play(.success)
    }

    private func saveZone() {
        guard let zoneCapture = zoneCapture, pendingPoints.count >= 3 else { return }
        let keptArea = db.boundaries.first { $0.id == zoneCapture.boundaryId }?.areaSqMeters
        db.saveZone(
            BoundaryZone(
                id: zoneCapture.zoneId ?? 0,
                boundaryId: zoneCapture.boundaryId,
                name: zoneCapture.name,
                kind: zoneCapture.kind,
                notes: zoneCapture.notes,
                points: pendingPoints
            ),
            toBoundary: zoneCapture.boundaryId
        )
        if let keptArea = keptArea,
           let index = db.boundaries.firstIndex(where: { $0.id == zoneCapture.boundaryId }),
           db.boundaries[index].areaSqMeters != keptArea {
            var area = db.boundaries[index]
            area.areaSqMeters = keptArea
            db.boundaries[index] = area
        }
        let returnToPermissions = zoneCapture.returnToPermissions
        self.zoneCapture = nil
        pendingPoints = []
        db.pendingZone = nil
        sound.play(.success)
        if returnToPermissions {
            NotificationCenter.default.post(name: .boundaryCaptureFinished, object: nil)
        }
    }

    private func recordIfNeeded(_ fix: GpsFix) {
        guard let routeId = activeRouteId else { return }
        // ~2 s between points, and ignore poor fixes (matches Android's sample gate).
        guard fix.timestampEpochMs - lastAppendedAtEpochMs >= 2000 else { return }
        guard fix.accuracyMeters <= 50 else { return }
        lastAppendedAtEpochMs = fix.timestampEpochMs
        db.appendTrackPoint(
            routeId: routeId,
            latitude: fix.latitude,
            longitude: fix.longitude,
            elevationMeters: fix.altitudeMeters,
            speedMps: fix.speedMps
        )
    }

    private func checkHomingArrival(_ fix: GpsFix) {
        guard let target = homingTarget else { return }
        let distance = DatabaseService.haversineMeters(
            fix.latitude, fix.longitude, target.latitude, target.longitude
        )
        guard distance <= 25 else { arrivalNotified = false; return }
        guard !arrivalNotified else { return }
        arrivalNotified = true
        sound.play(.arrival)
        showArrivalAlert = true
    }

    private func endHoming() {
        homingTarget = nil
        homingLabel = ""
        arrivalNotified = false
    }

    private func updateGridRef(_ coordinate: CLLocationCoordinate2D) {
        guard db.preferences.showOsGridRef else {
            mapCentreGridRef = nil
            return
        }
        mapCentreGridRef = OsGridRef.latLngToOsGrid(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        )
    }

    private func goToGridRef(_ ref: String) {
        guard let target = OsGridRef.osGridToLatLng(ref) else {
            mapMessage = "That OS Grid reference is not valid. Use a British National Grid reference such as SU 37290 15552."
            return
        }
        followUser = false
        centre = CLLocationCoordinate2D(latitude: target.latitude, longitude: target.longitude)
        regionRequestToken += 1
        sound.play(.gpsLock)
    }

    // MARK: - Formatting

    private func routeText(_ route: Route) -> String {
        let km = route.distanceMeters / 1000.0
        return String(format: "%.2f km", km)
    }

    private func areaText(_ boundary: BoundaryArea) -> String {
        let hectares = boundary.areaSqMeters / 10000.0
        if hectares >= 1 {
            return String(format: "%.1f ha", hectares)
        }
        return String(format: "%.0f m²", boundary.areaSqMeters)
    }

    private func distanceText(_ marker: FieldMarker) -> String {
        guard let fix = location.latestFix else { return "—" }
        let meters = DatabaseService.haversineMeters(
            fix.latitude, fix.longitude, marker.latitude, marker.longitude
        )
        return formatDistance(meters)
    }

    private var homingDistanceText: String {
        guard let target = homingTarget, let fix = location.latestFix else { return "—" }
        let meters = DatabaseService.haversineMeters(
            fix.latitude, fix.longitude, target.latitude, target.longitude
        )
        return formatDistance(meters)
    }

    private var homingBearingText: String {
        guard let target = homingTarget, let fix = location.latestFix else { return "—" }
        let bearing = bearingDegrees(
            fromLat: fix.latitude,
            fromLon: fix.longitude,
            toLat: target.latitude,
            toLon: target.longitude
        )
        return String(format: "%d° %@", Int(bearing.rounded()), cardinal(bearing))
    }

    private func formatDistance(_ meters: Double) -> String {
        if meters >= 1000 {
            return String(format: "%.2f km", meters / 1000.0)
        }
        return String(format: "%.0f m", meters)
    }

    private func bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) -> Double {
        let phi1 = fromLat * .pi / 180
        let phi2 = toLat * .pi / 180
        let deltaLambda = (toLon - fromLon) * .pi / 180
        let y = sin(deltaLambda) * cos(phi2)
        let x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        let degrees = atan2(y, x) * 180 / .pi
        return (degrees + 360).truncatingRemainder(dividingBy: 360)
    }

    private func cardinal(_ degrees: Double) -> String {
        let points = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                      "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
        let index = Int((degrees / 22.5).rounded()) % 16
        return points[(index + 16) % 16]
    }
}

// MARK: - Layers sheet

private struct MapLayersSheet: View {
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var location = LocationManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        GlassPanel {
                            SectionLabel("Map layers")
                            Text("Pick a basemap")
                                .font(.field(size: 13))
                                .foregroundColor(theme.palette.mist)
                            Picker("Basemap", selection: Binding(
                                get: { db.preferences.mapLayerStyle },
                                set: { db.preferences.mapLayerStyle = $0 }
                            )) {
                                Text("Street").tag(MapLayerStyle.street)
                                Text("Outdoor").tag(MapLayerStyle.outdoor)
                                Text("Satellite").tag(MapLayerStyle.satellite)
                            }
                            .pickerStyle(.segmented)
                        }

                        GlassPanel {
                            SectionLabel("OS Grid & culls")
                            Toggle("Show OS Grid lines & refs", isOn: Binding(
                                get: { db.preferences.showOsGridRef },
                                set: { db.preferences.showOsGridRef = $0 }
                            ))
                            .foregroundColor(theme.palette.cream)
                            Toggle("Cull pins on map", isOn: Binding(
                                get: { db.preferences.showCullPinsOnMap },
                                set: { db.preferences.showCullPinsOnMap = $0 }
                            ))
                            .foregroundColor(theme.palette.cream)
                        }

                        offlinePanel
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Layers")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Done") { dismiss() } } }
        }
    }

    private var offlinePanel: some View {
        GlassPanel {
            SectionLabel("Saved areas & offline use")
            Text("Markers, tracks and boundaries are stored on this device. Saving an area only saves its coordinates; it does not download basemap tiles. Named country areas are under More → Packs. iOS stores the area outline. Apple Maps does not let HunTracK bulk-download tiles the way Android saves an OpenFreeMap pack.")
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)

            if db.offlineRegions.isEmpty {
                Text("No areas saved yet.")
                    .font(.field(size: 13))
                    .foregroundColor(theme.palette.mist)
            } else {
                ForEach(db.offlineRegions) { region in
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(region.name)
                                .font(.field(size: 13, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            Text(String(format: "N %.4f  S %.4f  E %.4f  W %.4f",
                                        region.north, region.south, region.east, region.west))
                                .font(.field(size: 10, design: .monospaced))
                                .foregroundColor(theme.palette.mist)
                            Text("Area coordinates saved · no offline tiles")
                                .font(.field(size: 11, weight: .semibold))
                                .foregroundColor(theme.palette.bronzeBright)
                        }
                        Spacer()
                        Button("Delete") {
                            db.deleteRegion(id: region.id)
                        }
                        .font(.field(size: 11, weight: .semibold))
                        .foregroundColor(theme.palette.danger)
                    }
                    .padding(.vertical, 3)
                }
            }

            Button {
                guard let fix = location.latestFix else { return }
                _ = db.createRegionAround(latitude: fix.latitude, longitude: fix.longitude)
            } label: {
                Text("Save 15 sq mile area coordinates around me")
                    .font(.field(size: 13, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
            }
            .padding(.top, 4)
            .disabled(location.latestFix == nil)
        }
    }
}

// MARK: - Marker detail

private struct MarkerDetailSheet: View {
    let marker: FieldMarker
    let onDismiss: () -> Void

    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(marker.name)
                            .font(.field(size: 26, weight: .bold))
                            .foregroundColor(theme.palette.cream)

                        GlassPanel {
                            SectionLabel("Marker")
                            MetricRow("Category", marker.category.label)
                            MetricRow("Latitude", String(format: "%.5f", marker.latitude))
                            MetricRow("Longitude", String(format: "%.5f", marker.longitude))
                            if let grid = OsGridRef.latLngToOsGrid(
                                latitude: marker.latitude,
                                longitude: marker.longitude
                            ) {
                                MetricRow("OS Grid", grid)
                            }
                            if let elevation = marker.elevationMeters {
                                MetricRow("Elevation", String(format: "%.0f m", elevation))
                            }
                        }

                        if !marker.notes.isEmpty {
                            GlassPanel {
                                SectionLabel("Notes")
                                Text(marker.notes)
                                    .foregroundColor(theme.palette.cream)
                            }
                        }

                        HuntButton("Delete marker", icon: "trash") {
                            db.deleteMarker(id: marker.id)
                            onDismiss()
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Marker")
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarItems(trailing: Button("Done") { onDismiss() })
        }
    }
}

/// Kept so existing call sites open the same Mark location sheet as Android.
public struct QuickAddMarkerSheet: View {
    @Binding var isPresented: Bool
    private let pinnedLatitude: Double?
    private let pinnedLongitude: Double?

    public init(isPresented: Binding<Bool>, latitude: Double = 0, longitude: Double = 0, useMyFix: Bool = true) {
        _isPresented = isPresented
        if useMyFix {
            pinnedLatitude = nil
            pinnedLongitude = nil
        } else {
            pinnedLatitude = latitude
            pinnedLongitude = longitude
        }
    }

    public var body: some View {
        MarkLocationSheet(
            isPresented: $isPresented,
            pinnedLatitude: pinnedLatitude,
            pinnedLongitude: pinnedLongitude
        )
    }
}
