import SwiftUI
import MapKit

public struct MapScreen: View {
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 52.4862, longitude: -1.8904),
        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
    )
    @State private var showingAddSheet = false
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            ZStack(alignment: .bottomTrailing) {
                Map(coordinateRegion: $region, showsUserLocation: true, annotationItems: db.markers) { marker in
                    MapAnnotation(coordinate: CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude)) {
                        VStack(spacing: 2) {
                            Image(systemName: "mappin.circle.fill")
                                .font(.system(size: 28))
                                .foregroundColor(theme.palette.bronze)
                                .shadow(radius: 2)
                            Text(marker.name)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(theme.palette.cream)
                                .padding(.horizontal, 4)
                                .background(theme.palette.charcoal.opacity(0.8))
                                .cornerRadius(4)
                        }
                    }
                }
                .ignoresSafeArea(edges: .top)
                
                // Floating Add Marker Button
                Button(action: {
                    showingAddSheet = true
                }) {
                    Image(systemName: "plus")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(theme.palette.charcoal)
                        .frame(width: 56, height: 56)
                        .background(Circle().fill(theme.palette.bronze))
                        .shadow(radius: 4)
                }
                .padding(24)
            }
        }
        .sheet(isPresented: $showingAddSheet) {
            QuickAddMarkerSheet(isPresented: $showingAddSheet)
        }
        .onAppear {
            if let fix = location.latestFix {
                region.center = CLLocationCoordinate2D(latitude: fix.latitude, longitude: fix.longitude)
            }
        }
    }
}

public struct QuickAddMarkerSheet: View {
    @Binding var isPresented: Bool
    @State private var name: String = ""
    @State private var category: MarkerCategory = .highSeat
    @State private var notes: String = ""
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var db = DatabaseService.shared
    @ObservedObject private var theme = ThemeManager.shared
    
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
                    
                    if let fix = location.latestFix {
                        Section(header: Text("Location").foregroundColor(theme.palette.bronze)) {
                            Text(String(format: "%.5f, %.5f", fix.latitude, fix.longitude))
                            if let osGrid = OsGridRef.latLngToOsGrid(latitude: fix.latitude, longitude: fix.longitude) {
                                Text(osGrid).foregroundColor(theme.palette.bronzeBright)
                            }
                        }
                    }
                    
                    Button("Save Marker") {
                        let fix = location.latestFix ?? location.fallbackFix()
                        let marker = FieldMarker(
                            category: category,
                            name: name.isEmpty ? category.label : name,
                            notes: notes,
                            latitude: fix.latitude,
                            longitude: fix.longitude
                        )
                        db.addMarker(marker)
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
