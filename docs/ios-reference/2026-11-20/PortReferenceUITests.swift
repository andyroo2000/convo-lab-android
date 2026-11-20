import XCTest

/// Capture-only harness for the Android port. Does not alter the reference app.
final class PortReferenceUITests: XCTestCase {
    @MainActor private func launch(_ fixture: String = "mode-navigation", flags: [String] = []) -> XCUIApplication {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments = ["-ui-test-fixture", fixture, "-journal-beta-visibility",
            "-AppleInterfaceStyle", "Light", "-journal.typography.font", "klee", "-journal.typography.size", "20"] + flags
        app.launch()
        return app
    }

    @MainActor private func capture(_ app: XCUIApplication, _ name: String) {
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = name
        image.lifetime = .keepAlways
        add(image)
    }

    @MainActor private func menu(_ app: XCUIApplication, _ item: String) {
        app.buttons["app-account-menu"].tap()
        app.buttons[item].tap()
    }

    @MainActor func testAuthenticationReference() {
        let app = launch("login-screen")
        XCTAssertTrue(app.buttons["Create an account"].waitForExistence(timeout: 10))
        capture(app, "01-login")
        app.buttons["Create an account"].tap()
        XCTAssertTrue(app.textFields["Invite code"].waitForExistence(timeout: 5))
        capture(app, "02-registration")
        app.buttons["Cancel"].tap()
        app.buttons["Forgot password?"].tap()
        XCTAssertTrue(app.buttons["Send reset link"].waitForExistence(timeout: 5))
        capture(app, "03-password-reset")
    }

    @MainActor func testNotebookAndSettingsReference() {
        let app = launch()
        XCTAssertTrue(app.textViews["Journal entry"].waitForExistence(timeout: 15))
        capture(app, "04-empty-notebook")
        app.buttons["app-account-menu"].tap()
        capture(app, "05-account-menu")
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["SettingsClose-Settings"].waitForExistence(timeout: 5))
        capture(app, "06-settings")
        app.buttons["Edit Profile"].tap()
        XCTAssertTrue(app.buttons["SettingsSaveProfile"].waitForExistence(timeout: 5))
        capture(app, "07-profile")
        app.buttons["SettingsClose-Edit profile"].tap()
        app.buttons["SettingsChapter-Connections"].tap()
        app.buttons["WaniKani"].tap()
        capture(app, "08-wanikani")
        app.buttons["SettingsClose-Settings"].tap()
        menu(app, "JournalAppearance")
        XCTAssertTrue(app.staticTexts["JournalAppearancePreview"].waitForExistence(timeout: 5))
        capture(app, "09-appearance")
    }

    @MainActor func testReviewedEntryReference() {
        let app = launch()
        XCTAssertTrue(app.buttons["Contents"].waitForExistence(timeout: 15))
        app.buttons["Contents"].tap()
        capture(app, "10-contents")
        let row = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "A movie night")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()
        XCTAssertTrue(app.buttons["Draft"].waitForExistence(timeout: 5))
        app.buttons["Draft"].tap()
        capture(app, "11-draft-feedback")
        app.buttons["Final"].tap()
        capture(app, "12-final-narration")
        app.buttons["More journal options"].firstMatch.tap()
        app.buttons["Edit final"].tap()
        XCTAssertTrue(app.textViews["Final text"].waitForExistence(timeout: 5))
        capture(app, "13-final-editor-keyboard")
        app.buttons["Cancel"].tap()
        app.buttons["More journal options"].firstMatch.tap()
        app.buttons["JournalVoicePicker"].tap()
        capture(app, "14-narration-voices")
    }

    @MainActor func testWritingAndClarificationReference() {
        let app = launch(flags: ["-journal-clarification-fixture"])
        let field = app.textViews["Journal entry"]
        XCTAssertTrue(field.waitForExistence(timeout: 15))
        field.tap()
        field.typeText("今日は友達と映画を見ました。")
        capture(app, "15-writing-keyboard")
        app.buttons["Close"].tap()
        capture(app, "21-close-draft")
        app.terminate()
        app.launch()
        app.buttons["Contents"].tap()
        let row = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "A movie night")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()
        let tutor = app.buttons["Hana"]
        for _ in 0..<3 where !tutor.isHittable { app.swipeUp() }
        tutor.tap()
        let answer = app.textViews["Your answer"]
        XCTAssertTrue(answer.waitForExistence(timeout: 5))
        for _ in 0..<3 where !answer.isHittable { app.swipeUp() }
        capture(app, "20-tutor-conversation")
        answer.tap()
        answer.typeText("I drove while Molly looked up the movie.")
        app.buttons["Done"].tap()
        app.buttons["Submit"].tap()
        let prompt = app.staticTexts["Who was driving?"]
        XCTAssertTrue(prompt.waitForExistence(timeout: 10))
        capture(app, "16-clarification")
        app.buttons["I did"].tap()
        app.buttons["Answer"].tap()
        XCTAssertTrue(app.staticTexts["Who looked up the movie?"].waitForExistence(timeout: 5))
        capture(app, "17-clarification-next")
    }
    @MainActor func testKeyboardAndTutorReference() {
        let app = launch(flags: ["-journal-keyboard-onboarding"])
        XCTAssertTrue(app.buttons["JournalKeyboardConfirm"].waitForExistence(timeout: 10))
        capture(app, "18-keyboard-choice")
        app.buttons["JournalKeyboard-device"].tap()
        app.buttons["JournalKeyboardConfirm"].tap()
        menu(app, "JournalTutor")
        XCTAssertTrue(app.buttons["Hana"].waitForExistence(timeout: 5))
        capture(app, "19-tutor-picker")
    }

}
