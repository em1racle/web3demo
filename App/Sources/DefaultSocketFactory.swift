import Foundation
import Starscream
import WalletConnectRelay

/// Bridges Starscream 4.x's unified `onEvent` callback to the separate onConnect/onDisconnect/
/// onText callbacks `WebSocketConnecting` expects — the SDK's own example code (in the reown-swift
/// repo's Example/ folder) targets an older Starscream API shape that doesn't match the version
/// SPM actually resolved here.
private final class StarscreamWebSocketAdapter: WebSocketConnecting {
    private let socket: Starscream.WebSocket
    private var connected = false

    var isConnected: Bool { connected }
    var onConnect: (() -> Void)?
    var onDisconnect: ((Error?) -> Void)?
    var onText: ((String) -> Void)?
    var request: URLRequest {
        get { socket.request }
        set { socket.request = newValue }
    }

    init(request: URLRequest) {
        socket = Starscream.WebSocket(request: request)
        socket.onEvent = { [weak self] event in
            guard let self else { return }
            switch event {
            case .connected:
                connected = true
                onConnect?()
            case .disconnected, .cancelled, .peerClosed:
                connected = false
                onDisconnect?(nil)
            case .error(let error):
                connected = false
                onDisconnect?(error)
            case .text(let text):
                onText?(text)
            default:
                break
            }
        }
    }

    func connect() { socket.connect() }
    func disconnect() { socket.disconnect() }
    func write(string: String, completion: (() -> Void)?) {
        socket.write(string: string, completion: completion)
    }
}

struct DefaultSocketFactory: WebSocketFactory {
    func create(with url: URL) -> WebSocketConnecting {
        StarscreamWebSocketAdapter(request: URLRequest(url: url))
    }
}
