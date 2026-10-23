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
}

// MARK: - Snapshot used to keep updateUIView idempotent

private struct MapContentSnapshot: Equatable {
    var markerIds: [Int64] = []
    var cullIds: [Int64] = []
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
    var bronze: UIColor

    /// True while a "Draw it" boundary capture is in progress: map panning is
    /// disabled so a one-finger drag sketches the fence line instead.
    var isDrawing: Bool

    var onRegionChanged: (CLLocationCoordinate2D) -> Void
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

        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap(_:))
        )
        tap.cancelsTouchesInView = false
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
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self

        coordinator.applyStyle(mapView, style: mapStyle)

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
            markerIds: markers.map { $0.id },
            cullIds: showCulls ? culls.filter { $0.latitude != nil && $0.longitude != nil }.map { $0.id } : [],
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

        guard coordinator.lastSnapshot != snapshot else { return }
        coordinator.lastSnapshot = snapshot
        coordinator.rebuild(mapView: mapView, host: self)
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

    init(parent: MapViewHost) {
        self.parent = parent
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
        if !appliedAnnotations.isEmpty {
            mapView.removeAnnotations(appliedAnnotations)
            appliedAnnotations.removeAll()
        }

        // --- Field markers ---
        for marker in host.markers {
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

    // MARK: UIGestureRecognizerDelegate

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        if gestureRecognizer === drawRecognizer {
            return parent.isDrawing
        }
        return true
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    // MARK: MKMapViewDelegate

    func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
        guard let hunt = annotation as? HuntAnnotation else { return nil }

        let identifier = "hunt-\(hunt.kind.rawValue)"
        let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView
            ?? MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)

        view.annotation = annotation
        view.canShowCallout = hunt.kind == .fieldMarker || hunt.kind == .cull
        view.markerTintColor = hunt.tint
        view.glyphTintColor = UIColor.white
        view.animatesWhenAdded = false

        switch hunt.kind {
        case .fieldMarker: view.glyphImage = UIImage(systemName: "mappin")
        case .cull: view.glyphImage = UIImage(systemName: "scope")
        case .homing: view.glyphImage = UIImage(systemName: "arrow.down.circle")
        case .boundaryPoint: view.glyphImage = UIImage(systemName: "circle.fill")
        }

        if hunt.kind == .fieldMarker {
            view.rightCalloutAccessoryView = UIButton(type: .detailDisclosure)
        }
        return view
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

        case .none:
            // Not one of ours (e.g. a future MKTileOverlay) — use the default renderer.
            return MKOverlayRenderer(overlay: overlay)
        }
    }

    func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
        let centre = mapView.centerCoordinate
        // Hop to the next runloop so we never mutate SwiftUI state mid-update.
        DispatchQueue.main.async { [weak self] in
            self?.parent.onRegionChanged(centre)
        }
    }
}

// MARK: - Map screen

public struct MapScreen: View {
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    @ObservedObject private var sound = SoundManager.shared

    @State private var centre = CLLocationCoordinate2D(latitude: 52.4862, longitude: -1.8904)
    @State private var regionRequestToken = 0
    @State private var spanMeters: Double = 1200

    @State private var showingAddSheet = false
    @State private var showingLayers = false
    @State private var showingGridInput = false
    @State private var showingHoming = false

    @State private var selectedMarker: FieldMarker?
    @State private var gridInput = ""

    @State private var activeRouteId: Int64?
    @State private var lastAppendedAtEpochMs: Int64 = 0

    @State private var homingTarget: CLLocationCoordinate2D?
    @State private var homingLabel = ""
    @State private var showArrivalAlert = false

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
                    markers: db.markers,
                    culls: db.culls,
                    boundaries: db.boundaries,
                    routes: db.routes,
                    trackPoints: db.trackPoints,
                    showCulls: db.preferences.showCullPinsOnMap,
                    homingTarget: homingTarget,
                    pendingPoints: pendingPoints,
                    isDrawing: capture?.mode == .draw,
                    mapStyle: db.preferences.mapLayerStyle,
                    bronze: UIColor(theme.palette.bronze),
                    onRegionChanged: { coordinate in
                        centre = coordinate
                        updateGridRef(coordinate)
                    },
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
            .overlay(alignment: .bottomTrailing) { addMarkerButton }
            .overlay(alignment: .bottom) { bottomPanel }
        }
        .sheet(isPresented: $showingAddSheet) {
            QuickAddMarkerSheet(
                isPresented: $showingAddSheet,
                latitude: centre.latitude,
                longitude: centre.longitude
            )
        }
        .sheet(isPresented: $showingLayers) {
            MapLayersSheet()
        }
        .sheet(isPresented: $showingHoming) {
            homingSheet
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
            capture = db.pendingBoundary
            boundaryName = db.pendingBoundary?.name ?? ""
        }
        .onChange(of: location.latestFix) { fix in
            guard let fix = fix else { return }
            recordIfNeeded(fix)
            checkHomingArrival(fix)
            if capture?.mode == .walk {
                appendWalkPoint(fix)
            }
        }
        .onChange(of: db.pendingBoundary?.name) { _ in
            capture = db.pendingBoundary
            boundaryName = db.pendingBoundary?.name ?? ""
        }
    }

    // MARK: - Top bar

    private var topBar: some View {
        VStack(spacing: 8) {
            HStack(spacing: 10) {
                Text("Map")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(theme.palette.cream)
                Spacer()

                if db.preferences.showOsGridRef {
                    Text(mapCentreGridRef ?? "Outside GB")
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
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
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.bronzeBright)
                        .padding(8)
                        .background(theme.palette.charcoal.opacity(0.75))
                        .cornerRadius(8)
                }
            }

            HStack(spacing: 8) {
                recordButton
                homingButton
                if let route = activeRoute {
                    Text(routeText(route))
                        .font(.system(size: 11, weight: .semibold))
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
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.mist)
                            .padding(7)
                            .background(theme.palette.charcoal.opacity(0.75))
                            .cornerRadius(8)
                    }
                }
            }

            if capture != nil {
                captureBar
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
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
            .font(.system(size: 12, weight: .bold))
            .foregroundColor(activeRouteId == nil ? theme.palette.cream : theme.palette.danger)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(theme.palette.charcoal.opacity(0.75))
            .cornerRadius(8)
        }
    }

    private var homingButton: some View {
        Button {
            if homingTarget == nil {
                // Prefer an existing homing marker, else use the map centre.
                if let marker = db.markers.first(where: { $0.category == .homing }) {
                    homingTarget = CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude)
                    homingLabel = marker.name
                } else {
                    homingTarget = centre
                    homingLabel = "Map centre"
                }
                sound.play(.markerPlace)
            } else {
                endHoming()
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: homingTarget == nil ? "arrow.down.circle" : "xmark.circle")
                Text(homingTarget == nil ? "Homing" : "End homing")
            }
            .font(.system(size: 12, weight: .bold))
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
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(theme.palette.cream)
            Text(captureHelp)
                .font(.system(size: 12))
                .foregroundColor(theme.palette.mist)

            HStack(spacing: 10) {
                Text("\(pendingPoints.count) points")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)

                if capture?.mode == .walk {
                    Button("Add current fix") {
                        if let fix = location.latestFix {
                            appendWalkPoint(fix)
                        }
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
                }
            }

            HStack(spacing: 10) {
                HuntButton("Save boundary", icon: "checkmark") {
                    saveBoundary()
                }
                Button("Cancel") {
                    capture = nil
                    pendingPoints = []
                    db.pendingBoundary = nil
                }
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(theme.palette.mist)
            }
        }
    }

    private var captureHelp: String {
        capture?.mode == .walk
            ? "Walk the boundary — each GPS fix is appended in order."
            : "Drag on the map to draw the fence line — lift your finger to finish. "
                + "You can also tap corners one at a time."
    }

    private var addMarkerButton: some View {
        Button {
            showingAddSheet = true
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(theme.palette.charcoal)
                .frame(width: 56, height: 56)
                .background(Circle().fill(theme.palette.bronze))
                .shadow(radius: 4)
        }
        .padding(24)
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
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 3) {
                        Text(homingDistanceText)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(theme.palette.bronzeBright)
                        Text(homingBearingText)
                            .font(.system(size: 12))
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
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(theme.palette.cream)
                        Spacer()
                        Text(areaText(boundary))
                            .font(.system(size: 11))
                            .foregroundColor(theme.palette.mist)
                        Button("Delete") {
                            db.deleteBoundary(id: boundary.id)
                        }
                        .font(.system(size: 11, weight: .semibold))
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
                            .font(.system(size: 26, weight: .bold))
                            .foregroundColor(theme.palette.cream)

                        GlassPanel {
                            SectionLabel("Target")
                            Text(homingLabel.isEmpty ? "Not set" : homingLabel)
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            MetricRow("Distance", homingDistanceText)
                            MetricRow("Bearing", homingBearingText)
                            Text("An arrival alert fires inside 25 m, and the target is highlighted on the map.")
                                .font(.system(size: 12))
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
                                                .font(.system(size: 14, weight: .semibold))
                                                .foregroundColor(theme.palette.cream)
                                            Spacer()
                                            Text(distanceText(marker))
                                                .font(.system(size: 12))
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
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(theme.palette.danger)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Homing")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var markersWithHoming: [FieldMarker] {
        db.markers.filter { $0.category == .homing }
    }

    // MARK: - Actions

    private func handleMapTap(_ coordinate: CLLocationCoordinate2D) {
        appendDraftPoint(coordinate, minimumMeters: 3, playClick: true)
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

        self.capture = nil
        pendingPoints = []
        db.pendingBoundary = nil
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
        guard distance <= 25 else { return }
        homingTarget = nil
        sound.play(.arrival)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        showArrivalAlert = true
    }

    private func endHoming() {
        homingTarget = nil
        homingLabel = ""
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
        guard let target = OsGridRef.osGridToLatLng(ref) else { return }
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

    var body: some View {
        NavigationView {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        GlassPanel {
                            SectionLabel("Map layers")
                            Text("Pick a basemap")
                                .font(.system(size: 13))
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
                            Toggle("Show OS Grid Reference", isOn: Binding(
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
        }
    }

    private var offlinePanel: some View {
        GlassPanel {
            SectionLabel("Offline packs")
            Text("Registers the area you are standing in so it is available in the field.")
                .font(.system(size: 12))
                .foregroundColor(theme.palette.mist)

            if db.offlineRegions.isEmpty {
                Text("No packs saved yet.")
                    .font(.system(size: 13))
                    .foregroundColor(theme.palette.mist)
            } else {
                ForEach(db.offlineRegions) { region in
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(region.name)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(theme.palette.cream)
                            Text(String(format: "N %.4f  S %.4f  E %.4f  W %.4f",
                                        region.north, region.south, region.east, region.west))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(theme.palette.mist)
                            Text(region.status.label)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(theme.palette.bronzeBright)
                        }
                        Spacer()
                        Button("Delete") {
                            db.deleteRegion(id: region.id)
                        }
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(theme.palette.danger)
                    }
                    .padding(.vertical, 3)
                }
            }

            Button {
                let fix = location.latestFix ?? location.fallbackFix()
                _ = db.createRegionAround(latitude: fix.latitude, longitude: fix.longitude)
            } label: {
                Text("Download 15 sq mile pack around me")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(theme.palette.bronzeBright)
            }
            .padding(.top, 4)
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
                            .font(.system(size: 26, weight: .bold))
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
    @State private var useMyFix: Bool = true
    @State private var saveOfflineArea: Bool = false

    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared

    public init(isPresented: Binding<Bool>, latitude: Double = 0, longitude: Double = 0) {
        self._isPresented = isPresented
        self.latitude = latitude
        self.longitude = longitude
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
                                Text(cat.label).tag(cat)
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
                        MetricRow("GPS accuracy", String(format: "±%.0f m", fix.accuracyMeters))
                    }

                    Section(header: Text("Offline").foregroundColor(theme.palette.bronze)) {
                        Toggle("Also save a 15 sq mile offline pack here", isOn: $saveOfflineArea)
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
                }
            }
            .navigationTitle("Add Field Marker")
            .navigationBarItems(leading: Button("Cancel") { isPresented = false })
        }
    }
}
