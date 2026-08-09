import SwiftUI

@main
struct Web3DemoApp: App {
    var body: some Scene {
        WindowGroup {
            TabView {
                PriceListView()
                    .tabItem { Label("Swift", systemImage: "swift") }
                KMPPriceListView()
                    .tabItem { Label("KMP", systemImage: "shared.with.you") }
                WalletView()
                    .tabItem { Label("Wallet", systemImage: "faceid") }
            }
        }
    }
}
