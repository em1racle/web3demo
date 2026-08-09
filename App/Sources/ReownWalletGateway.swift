import Combine
import Foundation
// Reown's SDK predates Swift 6 strict concurrency auditing — `@preconcurrency` treats its
// exposed API as not-yet-Sendable-checked rather than a hard error, the standard escape hatch
// for interop with libraries that haven't adopted strict concurrency yet.
@preconcurrency import ReownAppKit

/// Native Swift mirror of the shared `WalletGateway` design (see docs/adr/ADR-002 and the
/// Android `ReownWalletGateway`) — deliberately *not* made to conform to the Kotlin-exported
/// `WalletGateway` protocol from Shared.xcframework. That would require this class to produce a
/// real Kotlin `StateFlow` instance from Swift for the `session` property, which is significantly
/// more work than consuming one (the direction our existing Flow-bridging code, like
/// `PriceFeedController`, is built for). WalletConnect integration is also inherently
/// platform/SDK-specific and UI-coupled (the connect modal, deep links) — same reasoning
/// `docs/architecture.md` already gives for keeping Secure Enclave/Keystore code native rather
/// than forced through a shared interface.
enum WalletSession: Equatable {
    case disconnected
    case connecting
    case connected(account: String, chainId: String)
}

enum WalletGatewayError: Error {
    case unauthenticated
    case rejected(String)
}

@MainActor
final class ReownWalletGateway: ObservableObject {
    @Published private(set) var session: WalletSession = .disconnected

    private var cancellables = Set<AnyCancellable>()

    init() {
        AppKit.instance.sessionSettlePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] session in self?.apply(session: session) }
            .store(in: &cancellables)

        AppKit.instance.sessionDeletePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.session = .disconnected }
            .store(in: &cancellables)

        if let existing = AppKit.instance.getSessions().first {
            apply(session: existing)
        }
    }

    func disconnect() async {
        guard let topic = AppKit.instance.getSessions().first?.topic else {
            session = .disconnected
            return
        }
        try? await AppKit.instance.disconnect(topic: topic)
        session = .disconnected
    }

    func signMessage(_ message: String) async throws -> String {
        guard case let .connected(account, _) = session else { throw WalletGatewayError.unauthenticated }
        return try await sendRequest(.personal_sign(address: account, message: message))
    }

    func sendTransaction(to: String, valueWei: String, data: String) async throws -> String {
        guard case let .connected(account, chainId) = session else { throw WalletGatewayError.unauthenticated }
        let request = W3MJSONRPC.eth_sendTransaction(
            from: account, to: to, value: valueWei, data: data,
            nonce: nil, gas: nil, gasPrice: nil, maxFeePerGas: nil,
            maxPriorityFeePerGas: nil, gasLimit: nil, chainId: chainId
        )
        return try await sendRequest(request)
    }

    private func apply(session reownSession: Session) {
        guard let account = reownSession.accounts.first else { return }
        session = .connected(account: account.address, chainId: account.blockchainIdentifier)
    }

    private func sendRequest(_ request: W3MJSONRPC) async throws -> String {
        try await AppKit.instance.request(request)

        let box = PendingResponseBox()
        return try await withTaskCancellationHandler(
            operation: {
                try await withCheckedThrowingContinuation { continuation in
                    let cancellable = AppKit.instance.sessionResponsePublisher
                        .first()
                        .sink { response in
                            switch response.result {
                            case .response(let value):
                                box.resume(.success("\(value.value)"))
                            case .error(let error):
                                box.resume(.failure(WalletGatewayError.rejected(error.message)))
                            }
                        }
                    box.setPending(continuation: continuation, cancellable: cancellable)
                }
            },
            onCancel: {
                box.resume(.failure(CancellationError()))
            }
        )
    }
}

/// Bridges a single pending WalletConnect response back to the awaiting Task, resuming exactly
/// once whether the response arrives or the Task is cancelled first. Previously, cancelling the
/// awaiting Task mid-request (e.g. the view disappearing) left the Combine subscription and the
/// suspended continuation alive indefinitely — see docs/review.md #5. Uses a lock rather than
/// actor isolation because `withTaskCancellationHandler`'s `onCancel` closure runs synchronously,
/// possibly off the MainActor, so it can't `await` its way onto one.
private final class PendingResponseBox: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<String, Error>?
    private var cancellable: AnyCancellable?

    func setPending(continuation: CheckedContinuation<String, Error>, cancellable: AnyCancellable) {
        lock.lock()
        defer { lock.unlock() }
        self.continuation = continuation
        self.cancellable = cancellable
    }

    func resume(_ result: Result<String, Error>) {
        lock.lock()
        let pending = continuation
        continuation = nil
        let subscription = cancellable
        cancellable = nil
        lock.unlock()
        subscription?.cancel()
        pending?.resume(with: result)
    }
}
