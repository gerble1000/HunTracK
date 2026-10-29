import Foundation

public struct Plume {
    public let lengthM: Double
    public let halfAngleDeg: Double
    public let approachStandoffM: Double
    
    public func halfWidthAt(distanceM: Double) -> Double {
        distanceM * tan(halfAngleDeg * .pi / 180.0)
    }
}

public enum ScentPlumeGeometry {
    /// Reject old or distant forecasts for a location-dependent scent estimate.
    public static func usableWeather(_ weather: WeatherSnapshot?, for fix: GpsFix?, now: Date = Date()) -> WeatherSnapshot? {
        guard let weather = weather, let fix = fix else { return nil }
        let age = now.timeIntervalSince1970 * 1000 - Double(weather.fetchedAtEpochMs)
        guard age >= 0, age < 1800000,
              DatabaseService.haversineMeters(fix.latitude, fix.longitude, weather.latitude, weather.longitude) < 15000 else { return nil }
        return weather
    }

    public static func forConditions(
        speedKmh: Double?,
        gustKmh: Double? = nil,
        humidityPercent: Int? = nil,
        temperatureC: Double? = nil,
        epochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> Plume {
        let length = plumeLengthM(speedKmh: speedKmh, gustKmh: gustKmh, humidityPercent: humidityPercent, epochMs: epochMs)
        let angle = halfAngleDeg(speedKmh: speedKmh, gustKmh: gustKmh)
        let standoff = approachStandoffM(lengthM: length)
        return Plume(lengthM: length, halfAngleDeg: angle, approachStandoffM: standoff)
    }
    
    public static func plumeLengthM(
        speedKmh: Double?,
        gustKmh: Double? = nil,
        humidityPercent: Int? = nil,
        epochMs: Int64
    ) -> Double {
        let speed = speedKmh ?? 12.0
        var len: Double
        if speed < 3.2 {
            len = 70.0
        } else if speed <= 16.1 {
            len = 457.0 + (speed - 8.0) * 8.0
        } else {
            len = max(180.0, 500.0 - (speed - 16.1) * 12.0)
        }
        
        if let g = gustKmh, g - speed > 10.0 {
            len *= 0.82
        }
        if let h = humidityPercent, h > 75 {
            len *= 1.15
        }
        return min(700.0, max(50.0, len))
    }
    
    public static func halfAngleDeg(speedKmh: Double?, gustKmh: Double?) -> Double {
        let speed = speedKmh ?? 12.0
        var deg: Double
        if speed < 5.0 {
            deg = 24.0
        } else if speed > 22.0 {
            deg = 6.5
        } else {
            deg = 12.0
        }
        if let g = gustKmh, g - speed > 12.0 {
            deg += 5.0
        }
        return deg
    }
    
    public static func approachStandoffM(lengthM: Double) -> Double {
        max(60.0, lengthM * 0.45)
    }
}
