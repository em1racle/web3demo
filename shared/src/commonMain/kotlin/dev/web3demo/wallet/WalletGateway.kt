package dev.web3demo.wallet

import dev.web3demo.chain.AppResult
import kotlinx.coroutines.flow.StateFlow

sealed class WalletSession {
    data object Disconnected : WalletSession()

    data class Connecting(val pairingUri: String?) : WalletSession()

    data class Connected(val account: String, val chainId: String) : WalletSession()
}

/**
 * SDK-agnostic boundary in front of the wallet connection provider (Reown AppKit). No Reown type
 * appears in this interface's signature — see ADR-002 — so call sites, tests, and previews never
 * need a real SDK instance, and the concrete provider can be swapped without touching them.
 *
 * `session` folds account-changed/chain-changed events into itself rather than exposing them as
 * separate callback streams: a wallet switching accounts or networks is just a new `Connected`
 * value, consistent with how every other realtime state in this app is modeled (see
 * PriceFeedClient's StateFlow-based design).
 */
interface WalletGateway {
    val session: StateFlow<WalletSession>

    suspend fun connect(): AppResult<Unit>

    suspend fun reconnect(): AppResult<Unit>

    suspend fun disconnect()

    /** SIWE-style message signing — the returned signature is what proves account ownership,
     * not the address alone (see the security note in docs/architecture.md). */
    suspend fun signMessage(message: String): AppResult<String>

    /** Returns the transaction hash once the external wallet has broadcast it. This app never
     * constructs a raw signed transaction itself — the external wallet signs and sends. */
    suspend fun sendTransaction(
        to: String,
        valueWei: String,
        data: String,
    ): AppResult<String>
}
