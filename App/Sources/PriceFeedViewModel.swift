import Foundation
import Observation
import RealtimeFeed

@MainActor
@Observable
final class PriceFeedViewModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let symbol: String
        let price: Double
    }

    private(set) var rows: [Row] = []
    private(set) var connectionState: ConnectionState = .disconnected

    private let client: PriceFeedClient
    private var snapshotTask: Task<Void, Never>?
    private var stateTask: Task<Void, Never>?

    init(symbols: [String] = ["btcusdt", "ethusdt", "solusdt"]) {
        client = PriceFeedClient(symbols: symbols)
    }

    func start() {
        guard snapshotTask == nil else { return }

        stateTask = Task {
            for await state in client.states {
                connectionState = state
            }
        }

        snapshotTask = Task {
            for await snapshot in client.snapshots {
                rows = snapshot.values
                    .sorted { $0.symbol < $1.symbol }
                    .map { Row(id: $0.symbol, symbol: $0.symbol, price: $0.price) }

                // Render-rate cap: bounds SwiftUI diffing work to ~30fps no matter how fast the
                // feed fires. Combined with the stream's `.bufferingNewest(1)` policy, the next
                // `for await` pickup jumps straight to the latest snapshot — never a backlog.
                try? await Task.sleep(nanoseconds: 33_000_000)
            }
        }

        Task { await client.start() }
    }

    func stop() {
        snapshotTask?.cancel()
        snapshotTask = nil
        stateTask?.cancel()
        stateTask = nil
        Task { await client.stop() }
    }
}
