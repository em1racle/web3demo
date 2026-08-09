package dev.web3demo.androidapp

import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request
import dev.web3demo.chain.AppError
import dev.web3demo.chain.AppResult
import dev.web3demo.wallet.WalletGateway
import dev.web3demo.wallet.WalletSession
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * Bridges Reown AppKit's callback-based [AppKit.ModalDelegate] to the platform-agnostic
 * [WalletGateway] StateFlow — see ADR-002/architecture.md for why no Reown type appears in
 * [WalletGateway]'s own signature.
 *
 * The actual "open the connect UI" trigger lives in Compose (`openAppKit(navController)`, part
 * of AppKit's own pre-built pairing/QR modal) rather than in [connect] here — AppKit's primary
 * integration path is UI-driven, and reimplementing pairing/namespace negotiation by hand would
 * just be reinventing what the modal already does correctly.
 *
 * `AppKit.request(...)`'s own callback only confirms the request was *dispatched* — the actual
 * signed result arrives later, asynchronously, via `onSessionRequestResponse`. [sendRequest]
 * bridges that back to a suspend function by holding one pending continuation at a time (this
 * demo never has two wallet requests in flight simultaneously).
 */
class ReownWalletGateway : WalletGateway {
    private val _session = MutableStateFlow<WalletSession>(WalletSession.Disconnected)
    override val session: StateFlow<WalletSession> = _session

    private var pendingRequest: CancellableContinuation<AppResult<String>>? = null

    init {
        AppKit.setDelegate(
            object : AppKit.ModalDelegate {
                override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
                    refreshSessionFromAccount()
                }

                override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
                    _session.value = WalletSession.Disconnected
                }

                override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
                    refreshSessionFromAccount()
                }

                override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {}

                override fun onSessionEvent(event: Modal.Model.Event) {}

                override fun onSessionExtend(session: Modal.Model.Session) {}

                override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
                    _session.value = WalletSession.Disconnected
                }

                override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
                    val pending = pendingRequest ?: return
                    pendingRequest = null
                    if (!pending.isActive) return
                    when (val result = response.result) {
                        is Modal.Model.JsonRpcResponse.JsonRpcResult ->
                            pending.resume(AppResult.Ok(result.result))
                        is Modal.Model.JsonRpcResponse.JsonRpcError ->
                            pending.resume(AppResult.Err(AppError.WalletRejected(result.message)))
                        else ->
                            pending.resume(AppResult.Err(AppError.InvalidData("unrecognized wallet response")))
                    }
                }

                override fun onSessionAuthenticateResponse(response: Modal.Model.SessionAuthenticateResponse) {}

                override fun onSIWEAuthenticationResponse(response: Modal.Model.SIWEAuthenticateResponse) {}

                override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {}

                override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {}

                override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {}

                override fun onError(error: Modal.Model.Error) {
                    android.util.Log.e("Web3Demo", "AppKit delegate error", error.throwable)
                }
            },
        )

        refreshSessionFromAccount()
    }

    /** No pre-existing session and no UI context here — the Compose layer drives pairing via
     * `openAppKit(navController)`. This just reflects whatever AppKit already knows. */
    override suspend fun connect(): AppResult<Unit> = reconnect()

    override suspend fun reconnect(): AppResult<Unit> {
        if (!refreshSessionFromAccount()) return AppResult.Err(AppError.Unauthenticated)
        return AppResult.Ok(Unit)
    }

    override suspend fun disconnect() {
        suspendCancellableCoroutine<Unit> { continuation ->
            AppKit.disconnect(
                onSuccess = { if (continuation.isActive) continuation.resume(Unit) },
                onError = { if (continuation.isActive) continuation.resume(Unit) },
            )
        }
        _session.value = WalletSession.Disconnected
    }

    override suspend fun signMessage(message: String): AppResult<String> {
        val current = _session.value
        if (current !is WalletSession.Connected) return AppResult.Err(AppError.Unauthenticated)
        // Proper JSON encoding, not string interpolation — a message containing `"` or `\` would
        // otherwise produce malformed (or attacker-shifted) params. See docs/review.md #2.
        val params =
            buildJsonArray {
                add(JsonPrimitive(message))
                add(JsonPrimitive(current.account))
            }.toString()
        return sendRequest(Request(method = "personal_sign", params = params, expiry = null))
    }

    override suspend fun sendTransaction(
        to: String,
        valueWei: String,
        data: String,
    ): AppResult<String> {
        val current = _session.value
        if (current !is WalletSession.Connected) return AppResult.Err(AppError.Unauthenticated)
        val params =
            buildJsonArray {
                addJsonObject {
                    put("from", JsonPrimitive(current.account))
                    put("to", JsonPrimitive(to))
                    put("value", JsonPrimitive(valueWei))
                    put("data", JsonPrimitive(data))
                }
            }.toString()
        return sendRequest(Request(method = "eth_sendTransaction", params = params, expiry = null))
    }

    private suspend fun sendRequest(request: Request): AppResult<String> =
        suspendCancellableCoroutine { continuation ->
            pendingRequest = continuation
            AppKit.request(
                request = request,
                onSuccess = fun() { /* dispatched only — real result arrives via onSessionRequestResponse */ },
                onError = { throwable: Throwable ->
                    if (pendingRequest === continuation) {
                        pendingRequest = null
                        if (continuation.isActive) {
                            continuation.resume(
                                AppResult.Err(AppError.WalletRejected(throwable.message ?: "request failed")),
                            )
                        }
                    }
                },
            )
            continuation.invokeOnCancellation {
                if (pendingRequest === continuation) pendingRequest = null
            }
        }

    /** Returns true if a session was found. */
    private fun refreshSessionFromAccount(): Boolean {
        val account = AppKit.getAccount() ?: return false
        val chainId = "${account.chain?.chainNamespace}:${account.chain?.chainReference}"
        _session.value = WalletSession.Connected(account.address, chainId)
        return true
    }
}
