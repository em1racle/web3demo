package dev.web3demo.realtimefeed

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Connects to Binance's combined WebSocket stream, mirroring RealtimeFeed/PriceFeedClient.swift
 * so both platforms share the same reconnect/backpressure design even before any UI exists.
 *
 * Subscriptions are encoded directly in the URL (`?streams=...`) rather than sent as a control
 * frame after connecting, same reasoning as the Swift version: sending immediately after the
 * socket opens races the handshake. Reconnecting to the same URL *is* resubscribing.
 *
 * `snapshots` is a StateFlow, which conflates by construction — a slow collector only ever sees
 * the latest full snapshot, never falls behind, and never needs manual buffering-policy tuning
 * the way the Swift AsyncStream side does explicitly.
 */
class PriceFeedClient(
    symbols: List<String>,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val client: HttpClient = HttpClient { install(WebSockets) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val streamsParam = symbols.joinToString("/") { "${it.lowercase()}@trade" }
    private val url = "wss://stream.binance.com:9443/stream?streams=$streamsParam"

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _snapshots = MutableStateFlow<Map<String, PriceTick>>(emptyMap())
    val snapshots: StateFlow<Map<String, PriceTick>> = _snapshots.asStateFlow()

    private var job: Job? = null
    private var reconnectAttempt = 0

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                _state.value =
                    if (reconnectAttempt == 0) {
                        ConnectionState.Connecting
                    } else {
                        ConnectionState.Reconnecting(reconnectAttempt, 0)
                    }

                client.webSocket(urlString = url) {
                    _state.value = ConnectionState.Connected
                    reconnectAttempt = 0
                    for (frame in incoming) {
                        if (frame is Frame.Text) handle(frame.readText())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reconnectAttempt += 1
                val delayMs = reconnectPolicy.delayMillis(reconnectAttempt)
                _state.value = ConnectionState.Reconnecting(reconnectAttempt, delayMs)
                delay(delayMs)
            }
        }
    }

    private fun handle(text: String) {
        val tick = parseTick(text) ?: return
        _snapshots.value = _snapshots.value + (tick.symbol to tick)
    }

    companion object {
        fun parseTick(json: String): PriceTick? =
            try {
                val envelope = Json.parseToJsonElement(json).jsonObject
                val data = envelope["data"]?.jsonObject ?: return null
                val price = data["p"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
                val symbol = data["s"]?.jsonPrimitive?.content ?: return null
                PriceTick(symbol = symbol, price = price, timestampMillis = Clock.System.now().toEpochMilliseconds())
            } catch (e: Exception) {
                null
            }
    }
}
