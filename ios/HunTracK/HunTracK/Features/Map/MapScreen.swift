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
    case zone(BoundaryZoneType)
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
}

// MARK: - The UIKit map host

/// Animated visual estimate; positions and spread use the same model as Wind & Scent.
private final class ScentSmokeRenderer: MKOverlayRenderer {
    let fix: GpsFix
    let weather: WeatherSnapshot
    let plume: Plume

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
        let speed = max(0, min(60, weather.windSpeedKmh))
        let period = 1 / (0.08 + speed * 0.006)
        let phase = Date().timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: period) / period
        let downwind = (weather.windDirectionDeg + 180).truncatingRemainder(dividingBy: 360)
        let metersPerPoint = MKMetersPerMapPointAtLatitude(fix.latitude)
        let colors = [UIColor.systemPink.withAlphaComponent(0.45).cgColor,
                      UIColor.systemPink.withAlphaComponent(0).cgColor] as CFArray
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) else { return }
        for index in 0..<32 {
            let age = (phase + Double(index) / 32).truncatingRemainder(dividingBy: 1)
            let distance = 3 + age * plume.lengthM
            let halfWidth = plume.halfWidthAt(distanceM: distance)
            let lane = Double(index % 5 - 2) / 2.5
            let lateral = halfWidth * (lane * 0.55 + 0.25 * sin(phase * 2 * .pi + Double(index)))
            let along = Self.destination(latitude: fix.latitude, longitude: fix.longitude, meters: distance, bearing: downwind)
            let position = Self.destination(latitude: along.latitude, longitude: along.longitude, meters: lateral, bearing: downwind + 90)
            let centre = point(for: MKMapPoint(position))
            let geographicRadius = (8 + halfWidth * 0.24) / metersPerPoint
            let radius = CGFloat(max(5 / Double(zoomScale), min(24 / Double(zoomScale), geographicRadius)))
            let opacity = CGFloat(min(1, min(age / 0.08, (1 - age) / 0.25)))
            context.saveGState()
            context.setAlpha(opacity)
            context.drawRadialGradient(gradient, startCenter: centre, startRadius: 0,
                                       endCenter: centre, endRadius: radius, options: [])
            context.restoreGState()
        }
    }

    private static func destination(latitude: Double, longitude: Double, meters: Double, bearing: Double) -> CLLocationCoordinate2D {
        let distance = meters / 6371000
        let direction = bearing * .pi / 180
        let lat = latitude * .pi / 180
        let lon = longitude * .pi / 180
        let targetLat = asin(sin(lat) * cos(distance) + cos(lat) * sin(distance) * cos(direction))
        let targetLon = lon + atan2(sin(direction) * sin(distance) * cos(lat), cos(distance) - sin(lat) * sin(targetLat))
        return CLLocationCoordinate2D(latitude: targetLat * 180 / .pi, longitude: targetLon * 180 / .pi)
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
        // MapKit can still clamp this requested range for a map type/location.
        mapView.setCameraZoomRange(MKMapView.CameraZoomRange(minCenterCoordinateDistance: 1, maxCenterCoordinateDistance: 2000000), animated: false)

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
            pendingCoordinates: pendingPoints.map { "\($0.orderIndex):\($0.latitude):\($0.longitude)" }
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
    private var gridKey = ""
    private var lastPlumeFix: GpsFix?
    private var lastPlumeWeather: WeatherSnapshot?
    private var lastPlumeFresh = false

    init(parent: MapViewHost) {
        self.parent = parent
    }

    func startSmokeAnimation(_ mapView: MKMapView) {
        let timer = Timer(timeInterval: 0.12, repeats: true) { [weak self, weak mapView] _ in
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
            overlayKinds[ObjectIdentifier(polygon)] = .zone(boundary.zoneType)
            mapView.addOverlay(polygon)
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
        guard lastPlumeFix != parent.windFix || lastPlumeWeather != parent.windWeather || lastPlumeFresh != fresh else { return }
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
            view.image = markerImage(speciesId: marker?.speciesId, category: category, tint: hunt.tint)
            view.centerOffset = CGPoint(x: 0, y: -25)
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

    private func markerImage(speciesId: String?, category: MarkerCategory, tint: UIColor) -> UIImage {
        let size = CGSize(width: 50, height: 58)
        return UIGraphicsImageRenderer(size: size).image { context in
            let cg = context.cgContext
            let pin = UIBezierPath()
            pin.move(to: CGPoint(x: 14, y: 37))
            pin.addLine(to: CGPoint(x: 25, y: 56))
            pin.addLine(to: CGPoint(x: 36, y: 37))
            pin.close()
            (category == .cull ? UIColor.systemRed : tint).setFill()
            pin.fill()
            let circle = CGRect(x: 3, y: 2, width: 44, height: 44)
            cg.saveGState()
            UIBezierPath(ovalIn: circle).addClip()
            UIColor.systemBackground.setFill()
            cg.fill(circle)
            if category != .cull,
               let name = MarkerArtwork.photoName(speciesId: speciesId, category: category),
               let image = UIImage(named: name) {
                let scale = max(circle.width / image.size.width, circle.height / image.size.height)
                let width = image.size.width * scale
                let height = image.size.height * scale
                image.draw(in: CGRect(x: circle.midX - width / 2, y: circle.midY - height / 2, width: width, height: height))
            } else if category == .cull {
                let skull = "☠︎" as NSString
                let font = UIFont.systemFont(ofSize: 33, weight: .bold)
                let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.systemRed]
                let textSize = skull.size(withAttributes: attributes)
                skull.draw(at: CGPoint(x: circle.midX - textSize.width / 2, y: circle.midY - textSize.height / 2),
                           withAttributes: attributes)
            } else if let symbol = UIImage(systemName: MarkerArtwork.symbol(for: category),
                                           withConfiguration: UIImage.SymbolConfiguration(pointSize: 25, weight: .bold)) {
                symbol.withTintColor(tint, renderingMode: .alwaysOriginal)
                    .draw(in: CGRect(x: 10, y: 9, width: 30, height: 30))
            }
            cg.restoreGState()
            (category == .cull ? UIColor.systemRed : tint).setStroke()
            let border = UIBezierPath(ovalIn: circle)
            border.lineWidth = 3
            border.stroke()
        }.withRenderingMode(.alwaysOriginal)
    }

    func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
        guard let hunt = view.annotation as? HuntAnnotation, hunt.kind == .fieldMarker else { return }
        parent.onSelectMarker(hunt.recordId)
    }

    func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
        switch overlayKinds[ObjectIdentifier(overlay)] {
        case .zone(let zoneType):
            guard let polygon = overlay as? MKPolygon else {
                return MKOverlayRenderer(overlay: overlay)
            }
            let color = mapUIColor(hex: zoneType.mapColorHex)
            let renderer = MKPolygonRenderer(polygon: polygon)
            renderer.strokeColor = color
            renderer.lineWidth = 2
            renderer.fillColor = color.withAlphaComponent(CGFloat(zoneType.mapFillOpacity))
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
            renderer.strokeColor = UIColor.systemTeal
            renderer.lineWidth = 3
            renderer.lineDashPattern = [6, 4]
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
        let span = min(2000000, max(1, mapView.region.span.latitudeDelta * 111000))
        updateGrid(mapView)
        // Hop to the next runloop so we never mutate SwiftUI state mid-update.
        DispatchQueue.main.async { [weak self] in
            self?.parent.onRegionChanged(centre, span)
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
    @State private var boundaryMode: BoundaryCaptureMode = .draw
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
    @State private var pendingPoints: [BoundaryPoint] = []
    @State private var boundaryName = ""

    @State private var mapCentreGridRef: String?

    public init() {}

    public var body: some View {
        ScreenBackground {
            ZStack(alignment: .top) {
                MapViewHost(
                    centre: centre,
                    spanMeters: spanMeters,
                    regionRequestToken: regionRequestToken,
                    markers: db.markers.filter { marker in
                        db.preferences.showCullPinsOnMap || marker.category != .cull
                    },
                    culls: db.culls,
                    boundaries: db.boundaries,
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
                    bronze: UIColor(theme.palette.bronze),
                    isDrawing: capture?.mode == .draw,
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

                topBar
            }
            .overlay(alignment: .trailing) {
                if capture == nil {
                    mapControls.padding(.trailing, 14)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { bottomPanel }
        }
        .sheet(isPresented: $showingAddSheet) {
            QuickAddMarkerSheet(
                isPresented: $showingAddSheet,
                latitude: markerCoordinate?.latitude ?? centre.latitude,
                longitude: markerCoordinate?.longitude ?? centre.longitude,
                useMyFix: markerCoordinate == nil
            )
        }
        .sheet(isPresented: $showingLayers) {
            MapLayersSheet()
        }
        .sheet(isPresented: $showingHoming) {
            homingSheet
        }
        .sheet(isPresented: $showingBoundarySetup) {
            PermissionBoundarySheet(permissions: db.permissions, initialMode: boundaryMode, returnToPermissions: false) { pending in
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

            if capture != nil {
                captureBar
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
        GlassPanel(compact: true) {
            SectionLabel("Boundary capture")
            Text(capture?.zoneType.displayName ?? "")
                .font(.field(size: 15, weight: .semibold))
                .foregroundColor(theme.palette.cream)
            Text(captureHelp)
                .font(.field(size: 12))
                .foregroundColor(theme.palette.mist)

            HStack(spacing: 10) {
                Text("\(pendingPoints.count) points")
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)

                if capture?.mode == .walk {
                    Button("Add current fix") {
                        if let fix = location.latestFix {
                            appendWalkPoint(fix)
                        }
                    }
                    .font(.field(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
                }
            }

            HuntButton("Save boundary", icon: "checkmark") {
                saveBoundary()
            }
            .disabled(pendingPoints.count < 3)
            HStack {
                Button("Undo point") {
                    if !pendingPoints.isEmpty { pendingPoints.removeLast() }
                }
                .disabled(pendingPoints.isEmpty)
                Spacer()
                Button("Cancel") { finishBoundaryCapture() }
            }
            .font(.field(size: 13, weight: .semibold))
            .foregroundColor(theme.palette.bronzeBright)
        }
    }

    private var captureHelp: String {
        capture?.mode == .walk
            ? "Walk the boundary — each GPS fix is appended in order."
            : "Drag on the map to draw the fence line — lift your finger to finish. "
                + "You can also tap corners one at a time."
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
        VStack(spacing: 8) {
            mapControl("Zoom in around GPS", symbol: "plus") { zoom(by: 0.5) }
            mapControl("Zoom out around GPS", symbol: "minus") { zoom(by: 2) }
            mapControl("Centre on my GPS location", symbol: "figure.stand") { recenter() }
            HuntMarkLocationButton {
                markerCoordinate = nil
                showingAddSheet = true
            }
            .disabled(location.latestFix == nil || capture != nil)
        }
    }

    private func mapControl(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.field(size: 22, weight: .bold))
                .frame(width: 48, height: 48)
                .foregroundColor(theme.palette.cream)
                .background(theme.palette.charcoal.opacity(0.9))
                .cornerRadius(10)
        }
        .accessibilityLabel(title)
    }

    private func zoom(by factor: Double) {
        guard let fix = location.latestFix else { mapMessage = "Wait for a GPS fix to zoom around your location."; return }
        followUser = false
        centre = CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude)
        spanMeters = min(2000000, max(1, spanMeters * factor))
        regionRequestToken += 1
    }

    private func recenter() {
        guard let fix = location.latestFix else { mapMessage = "Your GPS location is not available yet."; return }
        followUser = true
        centre = CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude)
        regionRequestToken += 1
    }

    private func startBoundarySetup(_ mode: BoundaryCaptureMode) {
        guard capture == nil else { mapMessage = "Save or cancel the current boundary first."; return }
        boundaryMode = mode
        showingBoundarySetup = true
    }

    private func syncBoundaryCapture() {
        guard capture != db.pendingBoundary else { return }
        capture = db.pendingBoundary
        pendingPoints = []
        boundaryName = db.pendingBoundary?.name ?? ""
        if capture?.mode == .draw { followUser = false }
        if capture?.mode == .walk, let fix = location.latestFix { appendWalkPoint(fix) }
    }

    private func finishBoundaryCapture() {
        let returnToPermissions = capture?.returnToPermissions == true
        capture = nil
        pendingPoints = []
        db.pendingBoundary = nil
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
        } else if !db.boundaries.isEmpty {
            GlassPanel(compact: true) {
                SectionLabel("Boundaries")
                ForEach(db.boundaries) { boundary in
                    HStack {
                        Circle()
                            .fill(Color(hex: boundary.zoneType.mapColorHex))
                            .frame(width: 8, height: 8)
                        Text(boundary.name)
                            .font(.field(size: 13, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                        Spacer()
                        Text(areaText(boundary))
                            .font(.field(size: 11))
                            .foregroundColor(theme.palette.mist)
                        Button("Delete") {
                            db.deleteBoundary(id: boundary.id)
                        }
                        .font(.field(size: 11, weight: .semibold))
                        .foregroundColor(theme.palette.danger)
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
        if capture?.mode == .draw {
            appendDraftPoint(coordinate, minimumMeters: 3, playClick: true)
        } else if capture == nil, db.preferences.markAnywhereEnabled {
            followUser = false
            markerCoordinate = coordinate
            showingAddSheet = true
        }
    }

    /// Adds a draft corner, ignoring samples that are too close to the previous one
    /// so a dragged stroke (or a twitchy tap) cannot pile up duplicate points.
    private func appendDraftPoint(
        _ coordinate: CLLocationCoordinate2D,
        minimumMeters: Double,
        playClick: Bool
    ) {
        guard capture?.mode == .draw else { return }
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
        db.addBoundary(boundary)

        if let permissionId = capture.permissionId,
           let stored = db.boundaries.last {
            if let index = db.permissions.firstIndex(where: { $0.id == permissionId }) {
                var permission = db.permissions[index]
                permission.boundaryAreaId = stored.id
                db.updatePermission(permission)
            }
        }

        finishBoundaryCapture()
        sound.play(.success)
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
            Text("Markers, tracks and boundaries are stored on this device. Saving an area only saves its coordinates; it does not download basemap tiles. iOS maps need an internet connection and cached tiles are not guaranteed offline.")
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

// MARK: - Add marker sheet (kept from the original screen)

public struct QuickAddMarkerSheet: View {
    @Binding var isPresented: Bool
    var latitude: Double
    var longitude: Double

    @State private var name: String = ""
    @State private var category: MarkerCategory = .highSeat
    @State private var notes: String = ""
    @State private var useMyFix: Bool
    @State private var saveOfflineArea: Bool = false

    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared

    public init(isPresented: Binding<Bool>, latitude: Double = 0, longitude: Double = 0, useMyFix: Bool = true) {
        self._isPresented = isPresented
        self.latitude = latitude
        self.longitude = longitude
        self._useMyFix = State(initialValue: useMyFix)
    }

    private var fix: GpsFix {
        location.latestFix ?? location.fallbackFix()
    }

    private var targetLatitude: Double { useMyFix ? fix.latitude : latitude }
    private var targetLongitude: Double { useMyFix ? fix.longitude : longitude }

    public var body: some View {
        NavigationView {
            ScreenBackground {
                Form {
                    Section(header: Text("Marker Details").foregroundColor(theme.palette.bronze)) {
                        TextField("Name", text: $name)
                        Picker("Category", selection: $category) {
                            ForEach(MarkerCategory.allCases, id: \.self) { cat in
                                Label(cat.label, systemImage: MarkerArtwork.symbol(for: cat)).tag(cat)
                            }
                        }
                        TextField("Notes (optional)", text: $notes)
                    }

                    Section(header: Text("Location").foregroundColor(theme.palette.bronze)) {
                        Toggle("Use my GPS fix", isOn: $useMyFix)
                        Text(String(format: "%.5f, %.5f", targetLatitude, targetLongitude))
                        if let grid = OsGridRef.latLngToOsGrid(
                            latitude: targetLatitude,
                            longitude: targetLongitude
                        ) {
                            Text(grid).foregroundColor(theme.palette.bronzeBright)
                        } else {
                            Text("Outside GB").foregroundColor(theme.palette.mist)
                        }
                        if let gps = location.latestFix {
                            MetricRow("GPS accuracy", String(format: "±%.0f m", gps.accuracyMeters))
                        } else if useMyFix {
                            Text("Waiting for GPS — a marker will not be saved at the fallback location.")
                                .foregroundColor(theme.palette.gold)
                        }
                    }

                    Section(header: Text("Offline").foregroundColor(theme.palette.bronze)) {
                        Toggle("Also save this area's coordinates (not map tiles)", isOn: $saveOfflineArea)
                    }

                    Button("Save Marker") {
                        db.addMarker(
                            FieldMarker(
                                category: category,
                                name: name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                    ? category.label
                                    : name,
                                notes: notes,
                                latitude: targetLatitude,
                                longitude: targetLongitude,
                                elevationMeters: useMyFix ? fix.altitudeMeters : nil
                            )
                        )
                        if saveOfflineArea {
                            _ = db.createRegionAround(latitude: targetLatitude, longitude: targetLongitude)
                        }
                        isPresented = false
                    }
                    .foregroundColor(theme.palette.bronze)
                    .disabled(useMyFix && location.latestFix == nil)
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Add Field Marker")
            .navigationBarItems(leading: Button("Cancel") { isPresented = false })
        }
    }
}
