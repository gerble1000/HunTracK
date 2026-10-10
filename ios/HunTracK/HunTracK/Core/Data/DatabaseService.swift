import Foundation
import Combine

public class DatabaseService: ObservableObject {
    public static let shared = DatabaseService()
    
    @Published public var markers: [FieldMarker] = []
    @Published public var preferences: UserPreferences = UserPreferences()
    
    private let markersKey = "hunttrack_markers_v1"
    private let prefsKey = "hunttrack_prefs_v1"
    
    private init() {
        loadPreferences()
        loadMarkers()
    }
    
    public func addMarker(_ marker: FieldMarker) {
        var m = marker
        if m.id == 0 {
            m.id = Int64(Date().timeIntervalSince1970 * 1000)
        }
        markers.append(m)
        saveMarkers()
        SoundManager.shared.play(.markerPlace)
    }
    
    public func deleteMarker(id: Int64) {
        markers.removeAll { $0.id == id }
        saveMarkers()
    }
    
    public func clearAllMarkers() {
        markers.removeAll()
        saveMarkers()
    }
    
    private func saveMarkers() {
        if let data = try? JSONEncoder().encode(markers) {
            UserDefaults.standard.set(data, forKey: markersKey)
        }
    }
    
    private func loadMarkers() {
        if let data = UserDefaults.standard.data(forKey: markersKey),
           let saved = try? JSONDecoder().decode([FieldMarker].self, from: data) {
            self.markers = saved
        }
    }
    
    public func savePreferences(_ prefs: UserPreferences) {
        self.preferences = prefs
        if let data = try? JSONEncoder().encode(prefs) {
            UserDefaults.standard.set(data, forKey: prefsKey)
        }
    }
    
    private func loadPreferences() {
        if let data = UserDefaults.standard.data(forKey: prefsKey),
           let saved = try? JSONDecoder().decode(UserPreferences.self, from: data) {
            self.preferences = saved
            ThemeManager.shared.mode = saved.themeMode
            SoundManager.shared.soundsEnabled = saved.soundsEnabled
            SoundManager.shared.vibrationEnabled = saved.vibrationEnabled
        }
    }
}
