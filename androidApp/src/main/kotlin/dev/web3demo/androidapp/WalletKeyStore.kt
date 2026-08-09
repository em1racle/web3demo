package dev.web3demo.androidapp

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Android counterpart to iOS's WalletKeyStore — hardware-backed, biometry-gated signing key
 * store using AndroidKeyStore.
 *
 * Tries StrongBox (dedicated secure chip, like the iPhone's Secure Enclave) first; falls back to
 * the TEE-backed AndroidKeyStore if StrongBox isn't present — most emulators and many devices
 * don't have a StrongBox module, so exercising this fallback here is the realistic path, same as
 * the Secure Enclave fallback on the iOS simulator.
 *
 * What's protected on a rooted device either way: the key never leaves AndroidKeyStore — signing
 * happens inside the TEE/StrongBox, so root access to app storage can't extract the key bytes.
 * What root *can* attack: a poorly-configured key that doesn't require authentication per use, or
 * an app that caches the signature/derived secret in regular storage after signing.
 */
enum class WalletKeyBackend { STRONGBOX, TEE, UNKNOWN }

class WalletKeyStore {
    private val alias = "dev.web3demo.wallet.signingkey"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    var lastUsedBackend: WalletKeyBackend = WalletKeyBackend.UNKNOWN
        private set

    fun existingPublicKey(): PublicKey? = keyStore.getCertificate(alias)?.publicKey

    fun privateKey(): PrivateKey? = keyStore.getKey(alias, null) as? PrivateKey

    fun generateKeyIfNeeded(): PublicKey {
        existingPublicKey()?.let { return it }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        return try {
            generator.initialize(buildSpec(strongBox = true))
            val key = generator.generateKeyPair().public
            lastUsedBackend = WalletKeyBackend.STRONGBOX
            key
        } catch (e: StrongBoxUnavailableException) {
            generator.initialize(buildSpec(strongBox = false))
            val key = generator.generateKeyPair().public
            lastUsedBackend = WalletKeyBackend.TEE
            key
        }
    }

    private fun buildSpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }
}
