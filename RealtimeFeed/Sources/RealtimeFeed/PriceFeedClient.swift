import Foundation

/// Connects to Binance's combined WebSocket stream, resubscribes on every reconnect,
/// and exposes a conflated snapshot stream so a slow UI consumer never falls behind
/// a high-message-rate feed — it just gets dropped intermediate snapshots and always
/// renders the latest known price per symbol.
public actor PriceFeedClient {
    public let symbols: [String]

    private let session: URLSession
    // Subscriptions are encoded directly in the URL (Binance's "combined streams" form) rather
    // than sent as a control frame after connecting — sending immediately after `resume()` races
    // the WebSocket handshake (URLSessionWebSocketTask gives no "now connected" signal without a
    // delegate) and fails with "Socket is not connected". Encoding subscriptions in the URL also
    // makes resubscribe-on-reconnect trivial: reconnecting to the same URL *is* resubscribing.
    private let endpoint: URL
    private let reconnectPolicy: ReconnectPolicy

    private var task: URLSessionWebSocketTask?
    private var isRunning = false
    private var reconnectAttempt = 0
    private var latestBySymbol: [String: PriceTick] = [:]

    private var stateContinuation: AsyncStream<ConnectionState>.Continuation?
    private var snapshotContinuation: AsyncStream<[String: PriceTick]>.Continuation?

    // `nonisolated` because AsyncStream<T> is Sendable and these are set once in init —
    // callers need to iterate them from outside the actor without hopping isolation.
    public nonisolated let states: AsyncStream<ConnectionState>
    public nonisolated let snapshots: AsyncStream<[String: PriceTick]>

    public init(symbols: [String], reconnectPolicy: ReconnectPolicy = ReconnectPolicy(), session: URLSession = URLSession(configuration: .default)) {
        let lowercased = symbols.map { $0.lowercased() }
        self.symbols = lowercased
        self.reconnectPolicy = reconnectPolicy
        self.session = session

        let streams = lowercased.map { "\($0)@trade" }.joined(separator: "/")
        self.endpoint = URL(string: "wss://stream.binance.com:9443/stream?streams=\(streams)")!

        var stateCont: AsyncStream<ConnectionState>.Continuation!
        self.states = AsyncStream(bufferingPolicy: .bufferingNewest(1)) { stateCont = $0 }
        self.stateContinuation = stateCont

        var snapCont: AsyncStream<[String: PriceTick]>.Continuation!
        self.snapshots = AsyncStream(bufferingPolicy: .bufferingNewest(1)) { snapCont = $0 }
        self.snapshotContinuation = snapCont
    }

    public func start() {
        guard !isRunning else { return }
        isRunning = true
        Task { await runLoop() }
    }

    public func stop() {
        isRunning = false
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        stateContinuation?.yield(.disconnected)
    }

    private func runLoop() async {
        while isRunning {
            do {
                try await connectAndSubscribe()
                reconnectAttempt = 0
                try await receiveUntilFailure()
            } catch {
                guard isRunning else { break }
                FileHandle.standardError.write(Data("[PriceFeedClient] error: \(error)\n".utf8))
                reconnectAttempt += 1
                let delay = reconnectPolicy.delay(forAttempt: reconnectAttempt)
                stateContinuation?.yield(.reconnecting(attempt: reconnectAttempt, delay: delay))
                try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            }
        }
        stateContinuation?.yield(.disconnected)
    }

    private func connectAndSubscribe() async throws {
        stateContinuation?.yield(reconnectAttempt == 0 ? .connecting : .reconnecting(attempt: reconnectAttempt, delay: 0))

        let webSocketTask = session.webSocketTask(with: endpoint)
        webSocketTask.resume()
        self.task = webSocketTask

        stateContinuation?.yield(.connected)
    }

    private func receiveUntilFailure() async throws {
        while isRunning, let task {
            let message = try await task.receive()
            switch message {
            case .string(let text):
                handle(text: text)
            case .data(let raw):
                if let text = String(data: raw, encoding: .utf8) {
                    handle(text: text)
                }
            @unknown default:
                break
            }
        }
    }

    private func handle(text: String) {
        guard let tick = Self.parseTick(json: text) else { return }
        latestBySymbol[tick.symbol] = tick
        snapshotContinuation?.yield(latestBySymbol)
    }

    static func parseTick(json: String) -> PriceTick? {
        guard let data = json.data(using: .utf8),
              let envelope = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let payload = envelope["data"] as? [String: Any],
              let priceString = payload["p"] as? String,
              let price = Double(priceString),
              let symbol = payload["s"] as? String
        else { return nil }
        return PriceTick(symbol: symbol, price: price, timestamp: Date())
    }
}
