import XCTest

/// Same dates as the Android test. US Naval Observatory: new moon 2026-11-09 07:02 UTC,
/// full moon 2026-11-24 14:53 UTC.
final class MoonPhaseTests: XCTestCase {
    func testNewMoonOn9November2026() {
        let date = Date(timeIntervalSince1970: 1_794_207_720)
        XCTAssertEqual(MoonPhase.fraction(at: date), 0, accuracy: 1e-9)
        XCTAssertEqual(MoonPhase.name(at: date), .new)
        XCTAssertLessThan(MoonPhase.illumination(at: date), 0.01)
    }

    func testFullMoonOn24November2026() {
        var components = DateComponents()
        components.calendar = Calendar(identifier: .gregorian)
        components.timeZone = TimeZone(secondsFromGMT: 0)
        components.year = 2026
        components.month = 11
        components.day = 24
        components.hour = 14
        components.minute = 53
        let date = components.date!
        let phase = MoonPhase.fraction(at: date)
        XCTAssertEqual(MoonPhase.name(at: date), .full)
        XCTAssertGreaterThan(phase, 0.47)
        XCTAssertLessThan(phase, 0.53)
        XCTAssertGreaterThan(MoonPhase.illumination(at: date), 0.95)
    }

    func testThreeDaysAfterNewMoonIsAWaxingCrescent() {
        let date = Date(timeIntervalSince1970: 1_794_207_720 + 86_400 * 3)
        XCTAssertEqual(MoonPhase.name(at: date), .waxingCrescent)
    }
}
