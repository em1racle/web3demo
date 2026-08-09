import CryptoSwift
import Foundation
import WalletConnectSigner

/// Required by Reown's Sign SDK for `personal_sign` verification (SIWE). This demo doesn't use
/// SIWE — `AppKit.configure` is called with `authRequestParams: nil` — so `recoverPubKey` is
/// never actually invoked; it's left unimplemented rather than pulling in a secp256k1 library
/// for a code path this demo doesn't exercise. `keccak256` alone (needed by the SDK internally
/// regardless of SIWE) is implemented for real via CryptoSwift, since Apple's CryptoKit doesn't
/// offer Keccak.
struct DefaultCryptoProvider: CryptoProvider {
    enum Error: Swift.Error {
        /// Would mean SIWE (or some other pubkey-recovery path) got enabled without this being
        /// implemented — a real, catchable error instead of crashing the process, since the
        /// method is already `throws` and a caller might reasonably want to recover from this.
        case pubKeyRecoveryNotImplemented
    }

    func recoverPubKey(signature: EthereumSignature, message: Data) throws -> Data {
        throw Error.pubKeyRecoveryNotImplemented
    }

    func keccak256(_ data: Data) -> Data {
        Data(SHA3(variant: .keccak256).calculate(for: [UInt8](data)))
    }
}
