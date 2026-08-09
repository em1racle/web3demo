package dev.web3demo.androidapp

import android.app.Application
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.presets.AppKitChainsPresets

/**
 * Initializes Reown AppKit (WalletConnect) once, at process start — every screen just reads
 * AppKit's state afterward. The project ID identifies *this app* to the WalletConnect relay
 * network; it's a client-side identifier (like a Firebase app ID), not a secret.
 */
class Web3DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val projectId = "2bdd3ddddea565928c4498501d613f19"
        val appMetaData =
            Core.Model.AppMetaData(
                name = "web3demo",
                description = "KMP realtime market data + wallet demo",
                url = "https://github.com/em1racle/web3demo",
                icons = emptyList(),
                redirect = "web3demo://request",
                appLink = null,
                linkMode = false,
                verifyUrl = null,
            )

        CoreClient.initialize(
            application = this,
            projectId = projectId,
            metaData = appMetaData,
            connectionType = ConnectionType.AUTOMATIC,
            telemetryEnabled = false,
            onError = {
                    error: Core.Model.Error ->
                android.util.Log.e("Web3Demo", "CoreClient init failed", error.throwable)
            },
        )

        AppKit.initialize(
            init = Modal.Params.Init(core = CoreClient),
            onSuccess = {
                // Sepolia only — this demo never touches mainnet. Built by hand rather than
                // pulled from AppKitChainsPresets.ethChains so the chain id isn't a guessed
                // preset-map key.
                val sepolia =
                    Modal.Model.Chain(
                        chainName = "Sepolia",
                        chainNamespace = "eip155",
                        chainReference = "11155111",
                        requiredMethods = listOf("eth_sendTransaction", "personal_sign", "eth_signTypedData"),
                        optionalMethods = listOf("eth_accounts", "eth_requestAccounts"),
                        events = listOf("chainChanged", "accountsChanged"),
                        token = AppKitChainsPresets.ethToken,
                        chainImage = null,
                        rpcUrl = "https://ethereum-sepolia-rpc.publicnode.com",
                        blockExplorerUrl = "https://sepolia.etherscan.io",
                    )
                AppKit.setChains(listOf(sepolia))
            },
            onError = {
                    error: Modal.Model.Error ->
                android.util.Log.e("Web3Demo", "AppKit init failed", error.throwable)
            },
        )
    }
}
