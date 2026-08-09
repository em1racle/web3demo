import Foundation

public struct PriceTick: Sendable, Equatable {
    public let symbol: String
    public let price: Double
    public let timestamp: Date
}
