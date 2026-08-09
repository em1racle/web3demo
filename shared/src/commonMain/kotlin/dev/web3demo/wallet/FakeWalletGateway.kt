package dev.web3demo.wallet

import dev.web3demo.chain.AppError
import dev.web3demo.chain.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [WalletGateway] for previews and tests — no network, no real wallet app needed. */
class FakeWalletGateway(
    private val fakeAccount: String = "0x1234567890123456789012345678901234abcd",
    // eip155:11155111 = Sepolia
    private val fakeChainId: String = "eip155:11155111",
    private val shouldRejectConnect: Boolean = false,
) : WalletGateway {
    private val _session = MutableStateFlow<WalletSession>(WalletSession.Disconnected)
    override val session: StateFlow<WalletSession> = _session

    override suspend fun connect(): AppResult<Unit> {
        if (shouldRejectConnect) {
            _session.value = WalletSession.Disconnected
            return AppResult.Err(AppError.WalletRejected("user declined pairing"))
        }
        _session.value = WalletSession.Connecting(pairingUri = "wc:fake-pairing-uri")
        _session.value = WalletSession.Connected(fakeAccount, fakeChainId)
        return AppResult.Ok(Unit)
    }

    override suspend fun reconnect(): AppResult<Unit> = connect()

    override suspend fun disconnect() {
        _session.value = WalletSession.Disconnected
    }

    override suspend fun signMessage(message: String): AppResult<String> {
        val current = _session.value
        if (current !is WalletSession.Connected) return AppResult.Err(AppError.Unauthenticated)
        return AppResult.Ok("0xfakesignature")
    }

    override suspend fun sendTransaction(
        to: String,
        valueWei: String,
        data: String,
    ): AppResult<String> {
        val current = _session.value
        if (current !is WalletSession.Connected) return AppResult.Err(AppError.Unauthenticated)
        return AppResult.Ok("0xfaketransactionhash")
    }
}
