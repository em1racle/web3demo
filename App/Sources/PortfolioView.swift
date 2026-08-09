@preconcurrency import ReownAppKit
import Shared
import SwiftUI

// Chainlink's official Sepolia testnet LINK token — same contract verified live in
// shared/src/jvmTest/.../TokenRepositoryLiveTest.
private let linkContract = "0x779877A7B0D9E8603169DdbD7836e478b4624789"

// Holds the one TokenRepository the view needs, boxed in an ObservableObject so `@StateObject`
// keeps it alive across SwiftUI re-rendering PortfolioView's struct — a plain `let` property on
// the View struct would be reconstructed (and its HttpClient re-leaked) on every re-render, not
// just on session changes. See docs/review.md #3.
@MainActor
private final class PortfolioServices: ObservableObject {
    let repository = TokenRepository(rpc: EthereumRpcClient.companion.default())
}

struct PortfolioView: View {
    @StateObject private var gateway = ReownWalletGateway()
    @StateObject private var services = PortfolioServices()
    @State private var balanceText: String?
    @State private var balanceError: String?

    var body: some View {
        NavigationStack {
            Form {
                switch gateway.session {
                case .disconnected:
                    Section {
                        Text("No wallet connected.")
                        Button("Connect Wallet") {
                            AppKit.present()
                        }
                    }
                case .connecting:
                    Section {
                        Text("Connecting…")
                    }
                case .connected(let account, let chainId):
                    Section("Wallet") {
                        Text(account).font(.system(.footnote, design: .monospaced))
                        Text(chainId).font(.caption).foregroundStyle(.secondary)
                    }
                    Section("LINK balance (Sepolia)") {
                        if let error = balanceError {
                            Text(error).foregroundStyle(.red)
                        } else if let balanceText {
                            Text(balanceText)
                        } else {
                            Text("Loading…").foregroundStyle(.secondary)
                        }
                    }
                    Section {
                        Button("Disconnect", role: .destructive) {
                            Task { await gateway.disconnect() }
                        }
                    }
                }
            }
            .navigationTitle("Portfolio")
        }
        .task(id: gateway.session) {
            guard case .connected(let account, _) = gateway.session else { return }
            balanceError = nil
            balanceText = nil
            let metadataResult = try? await services.repository.fetchMetadata(contract: linkContract)
            guard let metadataOk = metadataResult as? AppResultOk<TokenMetadata>,
                  let metadata = metadataOk.value else {
                balanceError = "Token metadata read failed"
                return
            }
            let balanceResult = try? await services.repository.fetchBalance(
                contract: linkContract, owner: account, decimals: metadata.decimals
            )
            if let balanceOk = balanceResult as? AppResultOk<TokenBalance>, let balance = balanceOk.value {
                balanceText = "\(balance.formatted) \(metadata.symbol)"
            } else {
                balanceError = "Balance read failed"
            }
        }
    }
}
