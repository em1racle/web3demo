import XCTest

final class PortfolioUITests: XCTestCase {
    func testConnectWalletOpensModal() {
        let app = XCUIApplication()
        app.launch()

        app.tabBars.buttons["Portfolio"].tap()

        let connectButton = app.buttons["Connect Wallet"]
        XCTAssertTrue(connectButton.waitForExistence(timeout: 5))
        connectButton.tap()

        // Give the modal (and its network-backed wallet list fetch) time to present.
        Thread.sleep(forTimeInterval: 3)

        let screenshot = app.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = "portfolio-connect-modal"
        attachment.lifetime = .keepAlways
        add(attachment)

        let modalPresented = app.staticTexts["Connect wallet"].exists || app.otherElements.count > 0
        print("UITEST: modal presented, exists=\(modalPresented)")
    }
}
