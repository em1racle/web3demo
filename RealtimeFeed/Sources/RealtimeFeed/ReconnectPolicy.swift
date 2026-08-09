import Foundation

/// Pure exponential backoff with jitter, kept separate from I/O so it's trivially unit-testable.
public struct ReconnectPolicy: Sendable {
    public let baseDelay: TimeInterval
    public let maxDelay: TimeInterval
    public let jitterFraction: Double

    public init(baseDelay: TimeInterval = 1, maxDelay: TimeInterval = 30, jitterFraction: Double = 0.3) {
        self.baseDelay = baseDelay
        self.maxDelay = maxDelay
        self.jitterFraction = jitterFraction
    }

    /// attempt starts at 1 for the first reconnect try.
    public func delay(forAttempt attempt: Int, randomSource: () -> Double = { Double.random(in: 0...1) }) -> TimeInterval {
        let exponential = baseDelay * pow(2, Double(max(0, attempt - 1)))
        let capped = min(maxDelay, exponential)
        let jitter = capped * jitterFraction * randomSource()
        return capped + jitter
    }
}
