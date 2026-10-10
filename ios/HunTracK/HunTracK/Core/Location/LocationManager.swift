import CoreLocation
import Combine

public class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    public static let shared = LocationManager()
    
    private let clManager = CLLocationManager()
    @Published public var latestFix: GpsFix?
    @Published public var authorizationStatus: CLAuthorizationStatus = .notDetermined
    
    public override init() {
        super.init()
        clManager.delegate = self
        clManager.desiredAccuracy = kCLLocationAccuracyBest
        clManager.distanceFilter = 1.0 // 1 metre updates
        authorizationStatus = clManager.authorizationStatus
    }
    
    public func requestPermission() {
        clManager.requestWhenInUseAuthorization()
    }
    
    public func startUpdating() {
        clManager.startUpdatingLocation()
    }
    
    public func stopUpdating() {
        clManager.stopUpdatingLocation()
    }
    
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        if authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways {
            clManager.startUpdatingLocation()
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        latestFix = GpsFix(
            latitude: loc.coordinate.latitude,
            longitude: loc.coordinate.longitude,
            accuracyMeters: Float(loc.horizontalAccuracy),
            altitudeMeters: loc.altitude,
            speedMps: loc.speed >= 0 ? Float(loc.speed) : nil,
            bearingDegrees: loc.course >= 0 ? Float(loc.course) : nil,
            timestampEpochMs: Int64(loc.timestamp.timeIntervalSince1970 * 1000)
        )
    }
    
    public func fallbackFix() -> GpsFix {
        GpsFix(
            latitude: 52.4862,
            longitude: -1.8904,
            accuracyMeters: 999.0,
            altitudeMeters: nil,
            speedMps: nil,
            bearingDegrees: nil,
            timestampEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }
}
