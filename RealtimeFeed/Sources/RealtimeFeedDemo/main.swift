import Foundation
import RealtimeFeed

setvbuf(stdout, nil, _IONBF, 0)

let client = PriceFeedClient(symbols: ["btcusdt", "ethusdt"])

Task {
    for await state in client.states {
        print("[state] \(state)")
    }
}

Task {
    for await snapshot in client.snapshots {
        let line = snapshot
            .sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value.price)" }
            .joined(separator: "  ")
        print("[tick] \(line)")
    }
}

await client.start()

// Keep the process alive; Ctrl+C to stop.
try await Task.sleep(nanoseconds: 60 * 1_000_000_000)
await client.stop()
