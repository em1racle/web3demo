package dev.web3demo.realtimefeed

sealed class ConnectionState {
    data object Disconnected : ConnectionState()

    data object Connecting : ConnectionState()

    data object Connected : ConnectionState()

    data class Reconnecting(val attempt: Int, val delayMillis: Long) : ConnectionState()
}
