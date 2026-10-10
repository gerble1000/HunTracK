import Foundation

public struct AstronomyInfo: Codable, Equatable {
    public let sunriseEpochMs: Int64
    public let sunsetEpochMs: Int64
    public let moonriseEpochMs: Int64?
    public let moonsetEpochMs: Int64?
    public let moonPhase: Double
    public let moonIllumination: Double
    public let goldenHourStartEpochMs: Int64
    public let goldenHourEndEpochMs: Int64
    public let civilTwilightBeginEpochMs: Int64
    public let civilTwilightEndEpochMs: Int64
}

public enum AstronomyCalculator {
    public static func calculate(latitude: Double, longitude: Double, epochMs: Int64) -> AstronomyInfo {
        let d = toDays(epochMs)
        let sun = sunTimes(d: d, lat: latitude, lng: longitude)
        let moon = moonData(d: d, lat: latitude, lng: longitude)
        let moonT = moonTimes(d: d, lat: latitude, lng: longitude)

        return AstronomyInfo(
            sunriseEpochMs: sun.rise,
            sunsetEpochMs: sun.set,
            moonriseEpochMs: moonT.rise,
            moonsetEpochMs: moonT.set,
            moonPhase: moon.phase,
            moonIllumination: moon.illumination,
            goldenHourStartEpochMs: sun.set - 3600000,
            goldenHourEndEpochMs: sun.set,
            civilTwilightBeginEpochMs: sun.rise - 1800000,
            civilTwilightEndEpochMs: sun.set + 1800000
        )
    }

    private struct SunTimes { let rise: Int64; let set: Int64 }
    private struct MoonTimes { let rise: Int64?; let set: Int64? }
    private struct MoonDataResult { let phase: Double; let illumination: Double }

    private static func toJulian(_ epochMs: Int64) -> Double { Double(epochMs) / 86400000.0 + 2440587.5 }
    private static func toDays(_ epochMs: Int64) -> Double { toJulian(epochMs) - 2451545.0 }
    private static func fromJulian(_ j: Double) -> Int64 { Int64((j - 2440587.5) * 86400000.0) }

    private static func rad(_ d: Double) -> Double { d * .pi / 180.0 }
    private static func obliquity() -> Double { rad(23.4397) }

    private static func solarMeanAnomaly(_ d: Double) -> Double { rad(357.5291) + rad(0.98560028) * d }
    private static func eclipticLongitude(_ m: Double) -> Double {
        let c = rad(1.9148) * sin(m) + rad(0.02) * sin(2 * m) + rad(0.0003) * sin(3 * m)
        let p = rad(102.9372)
        return m + c + p + .pi
    }

    private static func rightAscension(l: Double, b: Double) -> Double {
        atan2(sin(l) * cos(obliquity()) - tan(b) * sin(obliquity()), cos(l))
    }

    private static func declination(l: Double, b: Double) -> Double {
        asin(sin(b) * cos(obliquity()) + cos(b) * sin(obliquity()) * sin(l))
    }

    private static func sunCoords(_ d: Double) -> (Double, Double) {
        let m = solarMeanAnomaly(d)
        let l = eclipticLongitude(m)
        return (rightAscension(l: l, b: 0.0), declination(l: l, b: 0.0))
    }

    private static func siderealTime(d: Double, lw: Double) -> Double {
        rad(280.16) + rad(360.9856235) * d - lw
    }

    private static func julianCycle(d: Double, lw: Double) -> Double {
        floor(d - 0.0009 - lw / (2 * .pi) + 0.5)
    }

    private static func approxTransit(ht: Double, lw: Double, n: Double) -> Double {
        0.0009 + (ht + lw) / (2 * .pi) + n
    }

    private static func solarTransitJ(ds: Double, m: Double, l: Double) -> Double {
        2451545.0 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)
    }

    private static func hourAngle(h: Double, phi: Double, d: Double) -> Double {
        acos(((sin(h) - sin(phi) * sin(d)) / (cos(phi) * cos(d))).clamped(to: -1.0...1.0))
    }

    private static func getSetJ(h: Double, lw: Double, phi: Double, dec: Double, n: Double, m: Double, l: Double) -> Double {
        let w = hourAngle(h: h, phi: phi, d: dec)
        let a = approxTransit(ht: w, lw: lw, n: n)
        return solarTransitJ(ds: a, m: m, l: l)
    }

    private static func sunTimes(d: Double, lat: Double, lng: Double) -> SunTimes {
        let lw = rad(-lng)
        let phi = rad(lat)
        let n = julianCycle(d: d, lw: lw)
        let ds = approxTransit(ht: 0.0, lw: lw, n: n)
        let m = solarMeanAnomaly(ds)
        let l = eclipticLongitude(m)
        let dec = declination(l: l, b: 0.0)
        let jNoon = solarTransitJ(ds: ds, m: m, l: l)
        let jSet = getSetJ(h: rad(-0.833), lw: lw, phi: phi, dec: dec, n: n, m: m, l: l)
        let jRise = jNoon - (jSet - jNoon)
        return SunTimes(rise: fromJulian(jRise), set: fromJulian(jSet))
    }

    private static func moonCoords(_ d: Double) -> (Double, Double, Double) {
        let l = rad(218.316) + rad(13.176396) * d
        let m = rad(134.963) + rad(13.064993) * d
        let f = rad(297.850) + rad(0.1114034) * d
        let lon = l + rad(6.289) * sin(m)
        let lat = rad(5.128) * sin(f)
        let dt = 385001.0 - 20905.0 * cos(m)
        return (rightAscension(l: lon, b: lat), declination(l: lon, b: lat), dt)
    }

    private static func moonData(d: Double, lat: Double, lng: Double) -> MoonDataResult {
        let (sra, sdec) = sunCoords(d)
        let (mra, mdec, dist) = moonCoords(d)
        let phi = acos((sin(sdec) * sin(mdec) + cos(sdec) * cos(mdec) * cos(sra - mra)).clamped(to: -1.0...1.0))
        let sdist = 149598000.0
        let inc = atan2(sdist * sin(phi), dist - sdist * cos(phi))
        let angle = atan2(cos(sdec) * sin(sra - mra), sin(sdec) * cos(mdec) - cos(sdec) * sin(mdec) * cos(sra - mra))
        let illumination = ((1.0 + cos(inc)) / 2.0).clamped(to: 0.0...1.0)
        let phase = 0.5 + 0.5 * inc * (angle < 0 ? -1.0 : 1.0) / .pi
        return MoonDataResult(phase: (phase.truncatingRemainder(dividingBy: 1.0) + 1.0).truncatingRemainder(dividingBy: 1.0), illumination: illumination)
    }

    private static func moonTimes(d: Double, lat: Double, lng: Double) -> MoonTimes {
        var rise: Int64?
        var set: Int64?
        var prevAlt = moonAltitude(d: d - 1.0 / 24.0, lat: lat, lng: lng)
        for i in 0..<48 {
            let di = d + Double(i) / 48.0
            let alt = moonAltitude(d: di, lat: lat, lng: lng)
            if prevAlt < 0 && alt >= 0 && rise == nil {
                rise = fromJulian(2451545.0 + di)
            }
            if prevAlt >= 0 && alt < 0 && set == nil {
                set = fromJulian(2451545.0 + di)
            }
            prevAlt = alt
        }
        return MoonTimes(rise: rise, set: set)
    }

    private static func moonAltitude(d: Double, lat: Double, lng: Double) -> Double {
        let (ra, dec, _) = moonCoords(d)
        let lw = rad(-lng)
        let phi = rad(lat)
        let h = siderealTime(d: d, lw: lw) - ra
        return asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
    }
}

extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        min(max(self, limits.lowerBound), limits.upperBound)
    }
}
