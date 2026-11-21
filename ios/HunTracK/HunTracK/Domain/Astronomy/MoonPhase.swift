import Foundation

/// Moon phase from the mean synodic month, anchored on the US Naval Observatory
/// new moon of 2026-11-09 07:02 UTC. 0 is new, 0.25 first quarter, 0.5 full,
/// 0.75 last quarter. Kept in step with Android `MoonPhase`.
public enum MoonPhaseName: String, Equatable {
    case new
    case waxingCrescent
    case firstQuarter
    case waxingGibbous
    case full
    case waningGibbous
    case lastQuarter
    case waningCrescent

    public var symbolName: String {
        switch self {
        case .new: return "moonphase.new.moon"
        case .waxingCrescent: return "moonphase.waxing.crescent"
        case .firstQuarter: return "moonphase.first.quarter"
        case .waxingGibbous: return "moonphase.waxing.gibbous"
        case .full: return "moonphase.full.moon"
        case .waningGibbous: return "moonphase.waning.gibbous"
        case .lastQuarter: return "moonphase.last.quarter"
        case .waningCrescent: return "moonphase.waning.crescent"
        }
    }
}

public enum MoonPhase {
    /// 2026-11-09 07:02 UTC.
    public static let knownNewMoon = Date(timeIntervalSince1970: 1_794_207_720)

    /// Mean synodic month, in days.
    public static let synodicDays = 29.530588853

    public static func fraction(at date: Date) -> Double {
        let elapsed = date.timeIntervalSince(knownNewMoon)
        let cycles = elapsed / (synodicDays * 86_400)
        let wrapped = cycles.truncatingRemainder(dividingBy: 1)
        return wrapped < 0 ? wrapped + 1 : wrapped
    }

    public static func fraction(epochMs: Int64) -> Double {
        fraction(at: Date(timeIntervalSince1970: Double(epochMs) / 1000))
    }

    public static func name(at date: Date) -> MoonPhaseName {
        name(of: fraction(at: date))
    }

    public static func name(of phase: Double) -> MoonPhaseName {
        var p = phase.truncatingRemainder(dividingBy: 1)
        if p < 0 { p += 1 }
        switch p {
        case ..<0.0625, 0.9375...: return .new
        case ..<0.1875: return .waxingCrescent
        case ..<0.3125: return .firstQuarter
        case ..<0.4375: return .waxingGibbous
        case ..<0.5625: return .full
        case ..<0.6875: return .waningGibbous
        case ..<0.8125: return .lastQuarter
        default: return .waningCrescent
        }
    }

    public static func illumination(at date: Date) -> Double {
        let p = fraction(at: date)
        return (1 - cos(2 * Double.pi * p)) / 2
    }
}
