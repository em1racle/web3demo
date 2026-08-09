import XCTest
@testable import RealtimeFeed

final class ReconnectPolicyTests: XCTestCase {
    func testFirstAttemptUsesBaseDelay() {
        let policy = ReconnectPolicy(baseDelay: 1, maxDelay: 30, jitterFraction: 0)
        XCTAssertEqual(policy.delay(forAttempt: 1), 1)
    }

    func testDelayDoublesEachAttempt() {
        let policy = ReconnectPolicy(baseDelay: 1, maxDelay: 1000, jitterFraction: 0)
        XCTAssertEqual(policy.delay(forAttempt: 1), 1)
        XCTAssertEqual(policy.delay(forAttempt: 2), 2)
        XCTAssertEqual(policy.delay(forAttempt: 3), 4)
        XCTAssertEqual(policy.delay(forAttempt: 4), 8)
    }

    func testDelayNeverExceedsMax() {
        let policy = ReconnectPolicy(baseDelay: 1, maxDelay: 10, jitterFraction: 0)
        XCTAssertEqual(policy.delay(forAttempt: 20), 10)
    }

    func testJitterAddsUpToConfiguredFraction() {
        let policy = ReconnectPolicy(baseDelay: 10, maxDelay: 100, jitterFraction: 0.5)
        let delay = policy.delay(forAttempt: 1, randomSource: { 1.0 })
        XCTAssertEqual(delay, 15)
    }
}
