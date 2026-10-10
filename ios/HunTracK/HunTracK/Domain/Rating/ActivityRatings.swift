import Foundation

public struct WeatherSnapshot: Codable, Equatable {
    public let latitude: Double
    public let longitude: Double
    public let fetchedAtEpochMs: Int64
    public let temperatureC: Double
    public let feelsLikeC: Double
    public let humidityPercent: Int
    public let pressureHpa: Double
    public let windSpeedKmh: Double
    public let windDirectionDeg: Double
    public let windGustKmh: Double?
    public let rainProbabilityPercent: Int?
    public let visibilityKm: Double?
    public let weatherCode: Int
    public let cloudCoverPercent: Int?
}

public enum HuntingConditionsRating {
    public static func rate(weather: WeatherSnapshot?, astronomy: AstronomyInfo?, nowEpochMs: Int64) -> ActivityRating {
        let isRabbitFox = isRabbitFoxWindow(astronomy: astronomy, nowEpochMs: nowEpochMs)
        if isRabbitFox {
            return RabbitFoxActivityRating.rate(weather: weather, astronomy: astronomy, nowEpochMs: nowEpochMs)
        } else {
            return DeerActivityRating.rate(weather: weather, astronomy: astronomy, nowEpochMs: nowEpochMs)
        }
    }
    
    private static func isRabbitFoxWindow(astronomy: AstronomyInfo?, nowEpochMs: Int64) -> Bool {
        if let a = astronomy {
            return nowEpochMs >= a.goldenHourEndEpochMs || nowEpochMs < a.sunriseEpochMs
        }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London")!
        let hour = calendar.component(.hour, from: Date(timeIntervalSince1970: Double(nowEpochMs) / 1000.0))
        return hour >= 20 || hour < 5
    }
}

public enum DeerActivityRating {
    public static func rate(weather: WeatherSnapshot?, astronomy: AstronomyInfo?, nowEpochMs: Int64) -> ActivityRating {
        guard let w = weather else {
            return ActivityRating(level: .moderate, title: "Deer Activity", reasons: ["Waiting for weather data"])
        }
        var score = 0
        var reasons: [String] = []
        
        if w.windSpeedKmh < 12 {
            score += 2
            reasons.append("Low wind")
        } else if w.windSpeedKmh < 25 {
            score += 1
            reasons.append("Moderate wind")
        } else {
            reasons.append("Strong wind")
        }
        
        if w.temperatureC >= 2.0 && w.temperatureC <= 14.0 {
            score += 2
            reasons.append("Cool temperature")
        } else if w.temperatureC >= 0.0 && w.temperatureC <= 18.0 {
            score += 1
        }
        
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London")!
        let hour = calendar.component(.hour, from: Date(timeIntervalSince1970: Double(nowEpochMs) / 1000.0))
        
        let inEveningNight = isEveningToNight(astronomy: astronomy, nowEpochMs: nowEpochMs, hour: hour)
        let atSunrise = isSunrisePeak(astronomy: astronomy, nowEpochMs: nowEpochMs, hour: hour)
        let atSunset = isSunsetPeak(astronomy: astronomy, nowEpochMs: nowEpochMs)
        
        if atSunrise {
            score += 3
            reasons.append("Sunrise peak activity")
        } else if atSunset {
            score += 3
            reasons.append("Sunset peak activity")
        } else if inEveningNight {
            score += 3
            reasons.append("Evening–night high activity")
        } else if hour >= 5 && hour <= 8 {
            score += 2
            reasons.append("Early morning")
        }
        
        let level: ActivityLevel
        if (inEveningNight || atSunrise || atSunset) && w.windSpeedKmh < 25 {
            level = .high
        } else if score >= 5 {
            level = .high
        } else if score >= 3 {
            level = .moderate
        } else {
            level = .low
        }
        
        return ActivityRating(level: level, title: "Deer Activity", reasons: reasons.isEmpty ? ["Typical conditions"] : reasons)
    }
    
    private static func isEveningToNight(astronomy: AstronomyInfo?, nowEpochMs: Int64, hour: Int) -> Bool {
        if let a = astronomy {
            let eveningStart = a.sunsetEpochMs - 5400000
            let nightEnd = a.sunriseEpochMs - 2700000
            return nowEpochMs >= eveningStart || nowEpochMs < nightEnd
        }
        return hour >= 17 || hour <= 4
    }
    
    private static func isSunrisePeak(astronomy: AstronomyInfo?, nowEpochMs: Int64, hour: Int) -> Bool {
        if let a = astronomy {
            let start = a.sunriseEpochMs - 2700000
            let end = a.sunriseEpochMs + 5400000
            return nowEpochMs >= start && nowEpochMs <= end
        }
        return hour >= 5 && hour <= 8
    }
    
    private static func isSunsetPeak(astronomy: AstronomyInfo?, nowEpochMs: Int64) -> Bool {
        if let a = astronomy {
            return nowEpochMs >= a.goldenHourStartEpochMs && nowEpochMs < a.goldenHourEndEpochMs
        }
        return false
    }
}

public enum RabbitFoxActivityRating {
    public static func rate(weather: WeatherSnapshot?, astronomy: AstronomyInfo?, nowEpochMs: Int64) -> ActivityRating {
        guard let w = weather else {
            return ActivityRating(level: .moderate, title: "Rabbit & Fox Activity", reasons: ["Waiting for weather data"])
        }
        var score = 3
        var reasons = ["Prime nocturnal feeding & movement window"]
        if w.windSpeedKmh < 15 {
            score += 2
            reasons.append("Favourable calm winds for stealth stalk")
        }
        let level: ActivityLevel = score >= 5 ? .high : .moderate
        return ActivityRating(level: level, title: "Rabbit & Fox Activity", reasons: reasons)
    }
}
