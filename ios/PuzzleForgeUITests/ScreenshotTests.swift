import XCTest

@MainActor
class ScreenshotTests: XCTestCase {
    let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        setupSnapshot(app)
        app.launch()
    }

    func testScreenshots() {
        // 1. Home / Categories
        sleep(2) // Wait for app to fully load
        snapshot("01_Home_Categories")

        // TODO: Navigate to Puzzle Gameplay
        // Tap on a puzzle category then select a puzzle to play
        // app.cells.firstMatch.tap()
        // sleep(1)
        // app.cells.firstMatch.tap()
        // sleep(2)
        // snapshot("02_Puzzle_Gameplay")

        // TODO: Navigate to Leaderboard
        // Go back to home and tap the leaderboard tab or button
        // app.navigationBars.buttons.element(boundBy: 0).tap()
        // sleep(1)
        // app.tabBars.buttons["Leaderboard"].tap()
        // sleep(2)
        // snapshot("03_Leaderboard")

        // TODO: Navigate to Daily Rewards
        // Tap the daily rewards button or banner
        // app.buttons["Daily Rewards"].tap()
        // sleep(1)
        // snapshot("04_Daily_Rewards")

        // TODO: Navigate to AI Create
        // Tap the AI create tab to show AI-generated puzzle creation
        // app.tabBars.buttons["Create"].tap()
        // sleep(2)
        // snapshot("05_AI_Create")
    }
}
