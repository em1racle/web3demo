package dev.web3demo.androidapp

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.security.PrivateKey
import java.security.Signature
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun WalletScreen(activity: FragmentActivity) {
    val store = remember { WalletKeyStore() }
    var publicKeyHex by remember { mutableStateOf<String?>(store.existingPublicKey()?.encoded?.toHexString()) }
    var backendLabel by remember { mutableStateOf<String?>(null) }
    var signatureHex by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wallet", style = MaterialTheme.typography.headlineMedium)

        Text("Hardware-backed signing key", style = MaterialTheme.typography.titleSmall)
        Text(publicKeyHex ?: "No key yet", style = MaterialTheme.typography.bodySmall)
        backendLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall) }

        Button(onClick = {
            errorMessage = null
            try {
                val key = store.generateKeyIfNeeded()
                publicKeyHex = key.encoded.toHexString()
                backendLabel = when (store.lastUsedBackend) {
                    WalletKeyBackend.STRONGBOX -> "StrongBox (dedicated secure chip)"
                    WalletKeyBackend.TEE -> "TEE (AndroidKeyStore, software fallback — no StrongBox on this device/emulator)"
                    WalletKeyBackend.UNKNOWN -> null
                }
            } catch (e: Exception) {
                errorMessage = "Key generation failed: ${e.message}"
            }
        }) {
            Text("Generate Keystore key")
        }

        Button(
            onClick = {
                val key = store.privateKey() ?: return@Button
                scope.launch {
                    try {
                        val message = "web3demo:${System.currentTimeMillis()}".toByteArray()
                        val signed = authenticateAndSign(activity, key, message)
                        signatureHex = signed.toHexString()
                        errorMessage = null
                    } catch (e: Exception) {
                        errorMessage = "Signing failed: ${e.message}"
                    }
                }
            },
            enabled = publicKeyHex != null,
        ) {
            Text("Sign test message (biometric)")
        }

        signatureHex?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text(
            "The private key is generated inside AndroidKeyStore (StrongBox or TEE) and never " +
                "leaves it — signing happens on-chip after biometric approval. Even on a rooted " +
                "device, the key material can't be extracted from app storage.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private suspend fun authenticateAndSign(
    activity: FragmentActivity,
    privateKey: PrivateKey,
    message: ByteArray,
): ByteArray = suspendCancellableCoroutine { cont ->
    val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey) }
    val cryptoObject = BiometricPrompt.CryptoObject(signature)

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                try {
                    val sig = result.cryptoObject?.signature
                    sig?.update(message)
                    cont.resume(sig?.sign() ?: ByteArray(0))
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                cont.resumeWithException(RuntimeException("Biometric error $errorCode: $errString"))
            }
        },
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Sign wallet message")
        .setSubtitle("Authenticate to sign with your hardware-backed key")
        .setNegativeButtonText("Cancel")
        .build()

    prompt.authenticate(promptInfo, cryptoObject)

    cont.invokeOnCancellation { prompt.cancelAuthentication() }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
