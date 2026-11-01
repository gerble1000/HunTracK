import Foundation

public struct Plume {
    public let lengthM: Double
    public let halfAngleDeg: Double
    /// 0–1 how quickly opacity falls along the cone (higher = wind burn / lift).
    public let dilution: Double
    /// Extra lateral scatter for calm meander and gusty eddies.
    public let scatter: Double
    public let approachStandoffM: Double

    public func halfWidthAt(distanceM: Double) -> Double {
        guard distanceM > 0 else { return 0 }
        return distanceM * tan(halfAngleDeg * .pi / 180.0)
    }
}

/// Geographic scent-cone model shared by the map smoke and the Wind & Scent screen.
/// Field bands match the Android `ScentPlumeGeometry`.
public enum ScentPlumeGeometry {
    /// Reject old or distant forecasts for a location-dependent scent estimate.
    public static func usableWeather(_ weather: WeatherSnapshot?, for fix: GpsFix?, now: Date = Date()) -> WeatherSnapshot? {
        guard let weather = weather, let fix = fix else { return nil }
        let age = now.timeIntervalSince1970 * 1000 - Double(weather.fetchedAtEpochMs)
        guard age >= 0, age < 1_800_000,
              DatabaseService.haversineMeters(fix.latitude, fix.longitude, weather.latitude, weather.longitude) < 15_000 else { return nil }
        return weather
    }

    public static func forConditions(
        speedKmh: Double?,
        gustKmh: Double? = nil,
        humidityPercent: Int? = nil,
        temperatureC: Double? = nil,
        epochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> Plume {
        let speed = min(80, max(0, speedKmh ?? defaultSpeedKmh))
        let gust = max(speed, gustKmh ?? speed)
        let gusty = gust - speed >= gustDeltaKmh

        var lengthM: Double
        if speed < lightMaxKmh {
            lengthM = lerp(speed, 0, lightMaxKmh, 55, 90)
        } else if speed < steadyMinKmh {
            lengthM = lerp(speed, lightMaxKmh, steadyMinKmh, 90, steadyMinLengthM)
        } else if speed <= steadyMaxKmh {
            lengthM = lerp(speed, steadyMinKmh, steadyMaxKmh, steadyMinLengthM, 620)
        } else if speed < stiffMinKmh {
            lengthM = lerp(speed, steadyMaxKmh, stiffMinKmh, 620, 450)
        } else {
            lengthM = lerp(speed, stiffMinKmh, 60, 450, 280)
        }

        var halfAngle: Double
        if speed < lightMaxKmh {
            halfAngle = lerp(speed, 0, lightMaxKmh, 11, 9)
        } else if speed < steadyMinKmh {
            halfAngle = lerp(speed, lightMaxKmh, steadyMinKmh, 9, 6)
        } else if speed <= steadyMaxKmh {
            halfAngle = lerp(speed, steadyMinKmh, steadyMaxKmh, 6, 5)
        } else if speed < stiffMinKmh {
            halfAngle = lerp(speed, steadyMaxKmh, stiffMinKmh, 5, 4)
        } else {
            halfAngle = lerp(speed, stiffMinKmh, 60, 4, 2.8)
        }

        var dilution: Double
        if speed < lightMaxKmh {
            dilution = 0.15
        } else if speed <= steadyMaxKmh {
            dilution = 0.22
        } else {
            dilution = lerp(speed, stiffMinKmh, 60, 0.38, 0.55)
        }

        var scatter: Double
        if speed < lightMaxKmh {
            scatter = 1.55
        } else if speed <= steadyMaxKmh {
            scatter = 1.0
        } else {
            scatter = 0.75
        }

        if gusty {
            lengthM *= gustLengthFactor
            halfAngle *= gustAngleFactor
            scatter *= 1.7
            dilution = min(0.7, dilution + 0.12)
        }

        let humidityFactor: Double
        if let humidity = humidityPercent {
            humidityFactor = lerp(Double(min(100, max(0, humidity))), 20, 95, 0.88, 1.22)
        } else {
            humidityFactor = 1
        }
        lengthM *= humidityFactor

        if let temperature = temperatureC {
            let hour = londonHour(epochMs: epochMs)
            let afternoonLift = (12...17).contains(hour) && temperature >= 12
            let eveningPool = (17...22).contains(hour) || ((5...9).contains(hour) && temperature <= 10)
            if afternoonLift {
                lengthM *= 0.82
                dilution = min(0.7, dilution + 0.1)
            } else if eveningPool {
                halfAngle *= 1.15
                scatter *= 1.12
            }
        }

        let inSteadyBand = !gusty && speed >= steadyMinKmh && speed <= steadyMaxKmh
        if inSteadyBand {
            lengthM = max(lengthM, steadyMinLengthM)
        }

        lengthM = min(maxLengthM, max(minLengthM, lengthM))
        halfAngle = min(maxHalfAngleDeg, max(minHalfAngleDeg, halfAngle))
        dilution = min(0.7, max(0.1, dilution))
        scatter = min(2.5, max(0.5, scatter))
        let standoff = min(560, max(80, lengthM * approachFraction))

        return Plume(
            lengthM: lengthM,
            halfAngleDeg: halfAngle,
            dilution: dilution,
            scatter: scatter,
            approachStandoffM: standoff
        )
    }

    public static func formatWindSpeed(_ kmh: Double, unit: WindUnit) -> String {
        switch unit {
        case .kmh:
            return String(format: "%.0f km/h", kmh)
        case .mph:
            return String(format: "%.0f mph", kmh / 1.609344)
        }
    }

    public static func cardinal(_ degrees: Double) -> String {
        let names = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                     "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
        let index = Int((degrees + 11.25) / 22.5) % 16
        return names[(index + 16) % 16]
    }

    private static func londonHour(epochMs: Int64) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London") ?? .current
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000)
        return calendar.component(.hour, from: date)
    }

    private static func lerp(_ x: Double, _ x0: Double, _ x1: Double, _ y0: Double, _ y1: Double) -> Double {
        guard x1 > x0 else { return y0 }
        let t = min(1, max(0, (x - x0) / (x1 - x0)))
        return y0 + t * (y1 - y0)
    }

    private static let defaultSpeedKmh = 12.0
    private static let gustDeltaKmh = 8.0
    private static let gustAngleFactor = 1.55
    private static let gustLengthFactor = 0.62
    private static let lightMaxKmh = 3.2
    private static let steadyMinKmh = 8.0
    private static let steadyMaxKmh = 16.1
    private static let stiffMinKmh = 24.1
    private static let steadyMinLengthM = 457.2
    private static let minLengthM = 50.0
    private static let maxLengthM = 700.0
    private static let minHalfAngleDeg = 2.5
    private static let maxHalfAngleDeg = 14.0
    private static let approachFraction = 0.85
}
