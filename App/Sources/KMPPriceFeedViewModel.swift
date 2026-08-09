import Foundation
import Observation
import Shared

/// Same screen as PriceFeedViewModel, but backed by the Kotlin Multiplatform `shared` module
/// (compiled to Shared.xcframework) instead of the standalone Swift RealtimeFeed package —
/// confirms the shared business logic actually drives a native SwiftUI screen end to end.
@MainActor
@Observable
final class KMPPriceFeedViewModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let symbol: String
        let price: Double
    }

    private(set) var rows: [Row] = []
    private(set) var stateLabel: String = "Offline"
    private(set) var stateColorName: String = "red"

    private let controller: PriceFeedController
    private var started = false

    init(symbols: [String] = ["btcusdt", "ethusdt", "solusdt"]) {
        controller = PriceFeedController(symbols: symbols, cache: UserDefaultsKeyValueStore())
        apply(snapshot: controller.cachedSnapshot())
    }

    func start() {
        guard !started else { return }
        started = true

        controller.start(
            onState: { [weak self] state in
                self?.apply(state: state)
            },
            onSnapshot: { [weak self] snapshot in
                self?.apply(snapshot: snapshot)
            }
        )
    }

    func stop() {
        started = false
        controller.stop()
    }

    private func apply(state: ConnectionState) {
        if let reconnecting = state as? ConnectionState.Reconnecting {
            stateLabel = "Reconnecting #\(reconnecting.attempt)"
            stateColorName = "orange"
        } else if state is ConnectionState.Connected {
            stateLabel = "Live"
            stateColorName = "green"
        } else if state is ConnectionState.Connecting {
            stateLabel = "Connecting…"
            stateColorName = "yellow"
        } else {
            stateLabel = "Offline"
            stateColorName = "red"
        }
    }

    private func apply(snapshot: [String: PriceTick]) {
        rows = snapshot.values
            .sorted { $0.symbol < $1.symbol }
            .map { Row(id: $0.symbol, symbol: $0.symbol, price: $0.price) }
    }
}
