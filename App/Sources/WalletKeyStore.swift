import Foundation
import Security

/// Hardware-backed, biometry-gated signing key store.
///
/// The Secure Enclave only supports the P-256 (secp256r1) curve. Real crypto wallets (BTC/ETH)
/// sign with secp256k1, which the SE cannot hold — so production wallets keep the secp256k1 key
/// in the Keychain (not the SE) behind a `kSecAttrAccessControlBiometryCurrentSet` access
/// control, or use an SE-backed P-256 key as a separate device-binding/session key rather than
/// the actual chain key. This signs with an SE-backed P-256 key to demonstrate the mechanics; a
/// real wallet's chain key needs the Keychain path instead.
///
/// What's protected on a jailbroken device: the private key material itself — it's generated
/// inside the Secure Enclave and never leaves the chip, so root access to the filesystem or
/// process memory can't extract it. What's *not* automatically protected: the biometric gate —
/// jailbreak tooling has historically targeted bypassing LocalAuthentication's evaluation
/// (swizzling/hooking), which is why the access control policy matters as much as the key
/// storage location.
enum WalletKeyStoreBackend: String {
    /// Key material generated and held inside the Secure Enclave chip; never extractable, even
    /// with root. Only supports P-256.
    case secureEnclave
    /// Plain Keychain-resident key, still gated by the same biometric access control, but the
    /// key bytes exist in the Keychain's encrypted database rather than dedicated hardware. This
    /// is where a real secp256k1 wallet key has to live, since the SE can't hold that curve.
    case keychainSoftware
}

enum WalletKeyStoreError: Error {
    case keyGenerationFailed
    case signingFailed(Error?)
    case noKey
}

final class WalletKeyStore {
    private let tag = Data("dev.web3demo.wallet.signingkey".utf8)

    private(set) var lastUsedBackend: WalletKeyStoreBackend?

    func existingPublicKey() -> SecKey? {
        guard let key = privateKey() else { return nil }
        return SecKeyCopyPublicKey(key)
    }

    /// Tries the Secure Enclave first (hardware-backed, P-256 only); if that's unavailable —
    /// as on some simulators/older devices — falls back to a software Keychain key with the
    /// identical biometric access control. Either way, the private key never leaves this call.
    @discardableResult
    func generateKeyIfNeeded() throws -> SecKey {
        if let existing = privateKey() { return existing }

        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            [.privateKeyUsage, .biometryCurrentSet],
            nil
        ) else {
            throw WalletKeyStoreError.keyGenerationFailed
        }

        if let key = makeKey(access: access, useSecureEnclave: true) {
            lastUsedBackend = .secureEnclave
            return key
        }
        if let key = makeKey(access: access, useSecureEnclave: false) {
            lastUsedBackend = .keychainSoftware
            return key
        }
        throw WalletKeyStoreError.keyGenerationFailed
    }

    private func makeKey(access: SecAccessControl, useSecureEnclave: Bool) -> SecKey? {
        var privateKeyAttrs: [String: Any] = [
            kSecAttrIsPermanent as String: true,
            kSecAttrApplicationTag as String: tag,
            kSecAttrAccessControl as String: access
        ]

        var attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256
        ]
        if useSecureEnclave {
            attributes[kSecAttrTokenID as String] = kSecAttrTokenIDSecureEnclave
        } else {
            // Software keys need `kSecAttrIsPermanent` inside a *matching* top-level class too;
            // omitting `kSecAttrTokenID` alone is what routes generation to the Keychain instead
            // of the Secure Enclave.
            privateKeyAttrs[kSecAttrIsPermanent as String] = true
        }
        attributes[kSecPrivateKeyAttrs as String] = privateKeyAttrs

        var error: Unmanaged<CFError>?
        return SecKeyCreateRandomKey(attributes as CFDictionary, &error)
    }

    /// Triggers Face ID/Touch ID automatically — the OS enforces the access control, this code
    /// never sees the biometric result directly, only whether the signature call succeeded.
    func sign(message: Data) throws -> Data {
        guard let key = privateKey() else { throw WalletKeyStoreError.noKey }
        var error: Unmanaged<CFError>?
        guard let signature = SecKeyCreateSignature(
            key,
            .ecdsaSignatureMessageX962SHA256,
            message as CFData,
            &error
        ) else {
            throw WalletKeyStoreError.signingFailed(error?.takeRetainedValue())
        }
        return signature as Data
    }

    private func privateKey() -> SecKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tag,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String: true
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess else { return nil }
        // errSecSuccess + kSecReturnRef guarantees a SecKey here; `as?` on a CF type is always
        // non-nil (the compiler will even warn about it), so it can't replace this check.
        // swiftlint:disable:next force_cast
        return (item as! SecKey)
    }
}
