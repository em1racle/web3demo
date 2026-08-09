@preconcurrency import ReownAppKit
@preconcurrency import WalletConnectNetworking
import SwiftUI

@main
struct Web3DemoApp: App {
    init() {
        configureAppKit()
    }

    var body: some Scene {
        WindowGroup {
            TabView {
                PriceListView()
                    .tabItem { Label("Swift", systemImage: "swift") }
                KMPPriceListView()
                    .tabItem { Label("KMP", systemImage: "shared.with.you") }
                WalletView()
                    .tabItem { Label("Wallet", systemImage: "faceid") }
                PortfolioView()
                    .tabItem { Label("Portfolio", systemImage: "wallet.pass") }
            }
        }
    }
}

/// Same Reown project ID as the Android app (see androidApp/.../Web3DemoApplication.kt) — one
/// WalletConnect Cloud project covers both platforms.
private func configureAppKit() {
    let metadata = AppMetadata(
        name: "web3demo",
        description: "KMP realtime market data + wallet demo",
        url: "https://github.com/em1racle/web3demo",
        icons: [],
        // Only throws on a malformed scheme; "web3demo://request" is a fixed, valid literal.
        // swiftlint:disable:next force_try
        redirect: try! AppMetadata.Redirect(native: "web3demo://request", universal: nil)
    )

    let projectId = "2bdd3ddddea565928c4498501d613f19"

    Networking.configure(
        groupIdentifier: "group.dev.web3demo",
        projectId: projectId,
        socketFactory: DefaultSocketFactory()
    )

    AppKit.configure(
        projectId: projectId,
        metadata: metadata,
        crypto: DefaultCryptoProvider(),
        authRequestParams: nil,
        coinbaseEnabled: false
    )

    // Sepolia only — this demo never touches mainnet. Built by hand rather than relying on a
    // preset chain id, same reasoning as the Android side.
    let sepolia = Chain(
        chainName: "Sepolia",
        chainNamespace: "eip155",
        chainReference: "11155111",
        requiredMethods: ["eth_sendTransaction", "personal_sign", "eth_signTypedData"],
        optionalMethods: ["eth_accounts", "eth_requestAccounts"],
        events: ["chainChanged", "accountsChanged"],
        token: .init(name: "Sepolia Ether", symbol: "ETH", decimal: 18),
        rpcUrl: "https://ethereum-sepolia-rpc.publicnode.com",
        blockExplorerUrl: "https://sepolia.etherscan.io",
        imageId: ""
    )
    AppKit.instance.addChainPreset(sepolia)
    AppKit.instance.selectChain(sepolia)
}
