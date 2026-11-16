import XCTest

/// Drives the installed HunTracK app through its real screens.
///
/// The test does not change the app. It launches uk.hunttrack.app, dismisses
/// system permission alerts, and pauses on each screen until the GitHub job
/// has taken a `simctl` screenshot. The handshake files live in the test
/// runner's temporary directory, which is on the Mac's simulator filesystem.
final class ScreenshotTour: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "uk.hunttrack.app")

    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_GB"]
    }

    func testCaptureTour() throws {
        app.launch()
        dismissSystemAlerts()
        XCTAssertTrue(
            app.tabBars.firstMatch.waitForExistence(timeout: 30)
                || app.buttons["Map"].waitForExistence(timeout: 5),
            "HunTracK did not show its tab bar"
        )
        dismissSystemAlerts()

        waitForText("Field dashboard · live updates", timeout: 20)
        waitForText("IN CASE OF EMERGENCY", timeout: 8)
        // Weather is fetched from Open-Meteo. Keep going if the network is slow;
        // astronomy and the conditions panel are already on screen.
        _ = waitForTextOptional("Temperature", timeout: 30)
        shot("01-home", settle: 1.2)

        tapTab("Map")
        waitForText("Map", timeout: 15)
        shot("02-map", settle: 10)

        tapTab("Weather")
        waitForText("UK forecast · live · Open-Meteo", timeout: 12)
        _ = waitForTextOptional("Humidity", timeout: 12)
        shot("03-weather", settle: 1.5)

        tapTab("More")
        waitForText("Tap any row to open", timeout: 10)
        tapRow("Wind & Scent")
        waitForText("Will animals smell you from downwind?", timeout: 12)
        if app.staticTexts["Waiting for GPS and recent local wind data…"].exists {
            _ = waitForTextOptional("downwind towards", timeout: 12)
        }
        shot("06-wind", settle: 1.5)
        goBack()
        waitForText("Tap any row to open", timeout: 8)

        tapRow("Species")
        waitForText("UK Species", timeout: 12)
        shot("04-species", settle: 1.2)

        tapRow("Red deer")
        waitForText("Cervus elaphus", timeout: 12)
        shot("05-red-deer", settle: 1.5)
    }

    private func shot(_ name: String, settle: TimeInterval) {
        dismissSystemAlerts()
        app.activate()
        if settle > 0 {
            Thread.sleep(forTimeInterval: settle)
        }
        dismissSystemAlerts()
        let directory = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
        let request = directory.appendingPathComponent("huntrack-shot-request-\(name)")
        let done = directory.appendingPathComponent("huntrack-shot-done-\(name)")
        try? FileManager.default.removeItem(at: done)
        do {
            try Data("ready".utf8).write(to: request)
        } catch {
            XCTFail("Could not write screenshot request \(name): \(error)")
            return
        }
        NSLog("%@", "HUNTRACK_SHOT_REQUEST \(name) dir=\(directory.path)" as NSString)
        let deadline = Date().addingTimeInterval(90)
        while Date() < deadline {
            if FileManager.default.fileExists(atPath: done.path) {
                NSLog("%@", "HUNTRACK_SHOT_DONE \(name)" as NSString)
                return
            }
            Thread.sleep(forTimeInterval: 0.25)
        }
        XCTFail("The host did not capture \(name). Request directory: \(directory.path)")
    }

    private func tapTab(_ name: String) {
        let tab = app.tabBars.buttons[name]
        if tab.waitForExistence(timeout: 6) {
            tapHittable(tab, label: name)
            return
        }
        let button = app.buttons[name]
        if button.waitForExistence(timeout: 4) {
            tapHittable(button, label: name)
            return
        }
        failWithHierarchy("Tab \(name) was not on screen")
    }

    private func tapRow(_ title: String) {
        let predicate = NSPredicate(format: "label BEGINSWITH %@", title)
        for _ in 0..<3 {
            let button = app.buttons.matching(predicate).firstMatch
            if button.exists {
                tapHittable(button, label: title)
                return
            }
            let text = app.staticTexts.matching(predicate).firstMatch
            if text.exists {
                tapHittable(text, label: title)
                return
            }
            app.swipeUp()
        }
        failWithHierarchy("Row \(title) was not on screen")
    }

    private func tapHittable(_ element: XCUIElement, label: String) {
        if element.isHittable {
            element.tap()
            return
        }
        element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    private func goBack() {
        let back = app.navigationBars.buttons["Back"]
        if back.waitForExistence(timeout: 3) {
            back.tap()
            return
        }
        if app.buttons["Back"].exists {
            app.buttons["Back"].tap()
            return
        }
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    private func waitForText(_ text: String, timeout: TimeInterval) {
        if !waitForTextOptional(text, timeout: timeout) {
            failWithHierarchy("Timed out waiting for \(text)")
        }
    }

    private func waitForTextOptional(_ text: String, timeout: TimeInterval) -> Bool {
        let exact = app.staticTexts[text]
        if exact.waitForExistence(timeout: timeout) {
            return true
        }
        let button = app.buttons[text]
        if button.waitForExistence(timeout: 1) {
            return true
        }
        let predicate = NSPredicate(format: "label CONTAINS %@", text)
        return app.staticTexts.matching(predicate).firstMatch.waitForExistence(timeout: 1)
            || app.buttons.matching(predicate).firstMatch.waitForExistence(timeout: 1)
    }

    private func dismissSystemAlerts() {
        let labels = ["Allow While Using App", "Allow Once", "Allow", "OK", "Continue"]
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for _ in 0..<5 {
            var tapped = false
            for label in labels {
                let button = springboard.buttons[label]
                if button.exists {
                    button.tap()
                    tapped = true
                    Thread.sleep(forTimeInterval: 0.4)
                }
            }
            for label in ["Allow While Using App", "Allow Once", "Allow"] {
                let button = app.alerts.buttons[label]
                if button.exists {
                    button.tap()
                    tapped = true
                    Thread.sleep(forTimeInterval: 0.4)
                }
            }
            if !tapped {
                break
            }
        }
    }

    private func failWithHierarchy(_ reason: String) -> Never {
        let description = String(app.debugDescription.prefix(7000))
        NSLog("%@", "HUNTRACK_UI \(reason)\n\(description)" as NSString)
        XCTFail(reason)
        fatalError(reason)
    }
}
