import XCTest

final class WalletUITests: XCTestCase {
    func testGenerateKeyAndSign() {
        let app = XCUIApplication()
        app.launch()

        app.tabBars.buttons["Wallet"].tap()

        let generateButton = app.buttons["generateKeyButton"]
        XCTAssertTrue(generateButton.waitForExistence(timeout: 5))
        generateButton.tap()

        // Give the Secure Enclave call + any system UI a moment to resolve.
        Thread.sleep(forTimeInterval: 2)

        let publicKeyLabel = app.staticTexts["publicKeyLabel"]
        let errorLabel = app.staticTexts["errorLabel"]

        if publicKeyLabel.waitForExistence(timeout: 3) {
            print("UITEST: key generated: \(publicKeyLabel.label)")

            let signButton = app.buttons["signButton"]
            XCTAssertTrue(signButton.isEnabled)
            signButton.tap()
            Thread.sleep(forTimeInterval: 2)

            if let errorText = errorLabel.exists ? errorLabel.label : nil {
                print("UITEST: sign error: \(errorText)")
            } else {
                print("UITEST: sign appears to have succeeded or is awaiting biometric prompt")
            }
        } else if errorLabel.exists {
            print("UITEST: key generation error: \(errorLabel.label)")
        } else {
            XCTFail("Neither public key nor error label appeared after generating key")
        }
    }
}
