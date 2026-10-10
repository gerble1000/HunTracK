import CoreLocation
import Combine

public struct CompassReading {
    public let magneticHeading: Float
    public let trueHeading: Float
    public let accuracy: Float
    public let available: Bool
}

public class CompassManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    public static let shared = CompassManager()
    
    private let clManager = CLLocationManager()
    @Published public var heading: CompassReading = CompassReading(magneticHeading: 0, trueHeading: 0, accuracy: 0, available: true)
    
    public override init() {
        super.init()
        clManager.delegate = self
        if CLLocationManager.headingAvailable() {
            clManager.headingFilter = 0.5 // 0.5 degree precision
            clManager.startUpdatingHeading()
        } else {
            heading = CompassReading(magneticHeading: 0, trueHeading: 0, accuracy: -1, available: false)
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let mag = Float(newHeading.magneticHeading)
        let trueH = newHeading.trueHeading >= 0 ? Float(newHeading.trueHeading) : (mag - 2.0 + 360.0).truncatingRemainder(dividingBy: 360.0)
        heading = CompassReading(
            magneticHeading: mag,
            trueHeading: trueH,
            accuracy: Float(newHeading.headingAccuracy),
            available: true
        )
    }
}
