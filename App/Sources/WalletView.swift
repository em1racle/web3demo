import SwiftUI
import Security

struct WalletView: View {
    @State private var viewModel = WalletViewModel()

    var body: some View {
        NavigationStack {
            Form {
                Section("Hardware-backed signing key") {
                    if let publicKeyHex = viewModel.publicKeyHex {
                        Text(publicKeyHex)
                            .font(.system(.footnote, design: .monospaced))
                            .textSelection(.enabled)
                            .accessibilityIdentifier("publicKeyLabel")
                        if let backendLabel = viewModel.backendLabel {
                            Text(backendLabel)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        Text("No key yet").foregroundStyle(.secondary)
                    }
                    Button("Generate Secure Enclave key") {
                        viewModel.generateKey()
                    }
                    .accessibilityIdentifier("generateKeyButton")
                }

                Section("Sign") {
                    Button("Sign test message (Face ID / Touch ID)") {
                        viewModel.signTestMessage()
                    }
                    .accessibilityIdentifier("signButton")
                    .disabled(viewModel.publicKeyHex == nil)

                    if let signatureHex = viewModel.lastSignatureHex {
                        Text(signatureHex)
                            .font(.system(.footnote, design: .monospaced))
                            .textSelection(.enabled)
                    }
                }

                if let error = viewModel.errorMessage {
                    Section("Status") {
                        Text(error)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("errorLabel")
                    }
                }

                Section("What this proves") {
                    Text("""
                    The private key is generated inside the Secure Enclave and never leaves it — \
                    signing happens on-chip after Face ID/Touch ID approval. Even on a jailbroken \
                    device, the key material can't be extracted from the filesystem or process \
                    memory. Caveat: the Secure Enclave only supports P-256; real BTC/ETH wallets \
                    sign with secp256k1, which has to live in the Keychain instead, gated the same way.
                    """)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Wallet")
        }
        .onAppear { viewModel.refresh() }
    }
}

@MainActor
@Observable
final class WalletViewModel {
    private let store = WalletKeyStore()

    private(set) var publicKeyHex: String?
    private(set) var lastSignatureHex: String?
    private(set) var errorMessage: String?
    private(set) var backendLabel: String?

    func refresh() {
        guard let key = store.existingPublicKey(), let data = externalRepresentation(key) else { return }
        publicKeyHex = hex(data)
    }

    func generateKey() {
        errorMessage = nil
        do {
            _ = try store.generateKeyIfNeeded()
            refresh()
            switch store.lastUsedBackend {
            case .secureEnclave: backendLabel = "Secure Enclave (hardware)"
            case .keychainSoftware: backendLabel = "Keychain (software fallback — no SE on this device/simulator)"
            case nil: backendLabel = nil
            }
        } catch {
            errorMessage = "Key generation failed: \(error)"
        }
    }

    func signTestMessage() {
        errorMessage = nil
        do {
            let message = Data("web3demo:\(Date())".utf8)
            let signature = try store.sign(message: message)
            lastSignatureHex = hex(signature)
        } catch {
            errorMessage = "Signing failed: \(error)"
        }
    }

    private func externalRepresentation(_ key: SecKey) -> Data? {
        var error: Unmanaged<CFError>?
        guard let data = SecKeyCopyExternalRepresentation(key, &error) else { return nil }
        return data as Data
    }

    private func hex(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }
}

#Preview {
    WalletView()
}
