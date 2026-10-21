import XCTest

/// The installed app on a simulator, end to end. Controls are found by their test tags (app.onlyshots.TestTags), which
/// Compose shows as accessibility identifiers, never by their words or looks. The tags are repeated here as strings.
///
/// Grant the camera and microphone first (`xcrun simctl privacy booted grant camera dev.parez.onlyshots`, as CI does),
/// so no system prompt stands over the app.
final class AppSmokeTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launch()
        waitFor("screen.start", timeout: 30)
    }

    override func tearDown() {
        app.terminate()
    }

    /// Start → compose a video → Open → back to Start.
    func testOpensComposeAVideoAndComesBack() {
        tap("start.compose")
        waitFor("screen.open")
        waitFor("open.pickVideo")
        tap("back")
        waitFor("screen.start")
    }

    /// Start → record a shot → Home → the sample shot → the record screen, armed with its record button.
    func testTriesTheSampleShot() {
        tap("start.record")
        waitFor("screen.home")
        tap("home.sample")
        waitFor("screen.record")
        waitFor("record.button")
    }

    /// Start → help → privacy → back to Start.
    func testShowsPrivacyAndComesBack() {
        tap("start.help")
        tap("help.privacy")
        waitFor("screen.privacy")
        tap("back")
        waitFor("screen.start")
    }

    private func element(_ tag: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    private func waitFor(_ tag: String, timeout: TimeInterval = 10) {
        XCTAssertTrue(element(tag).waitForExistence(timeout: timeout), "\(tag) not on screen after \(timeout) s")
    }

    private func tap(_ tag: String, timeout: TimeInterval = 10) {
        waitFor(tag, timeout: timeout)
        element(tag).tap()
    }
}
