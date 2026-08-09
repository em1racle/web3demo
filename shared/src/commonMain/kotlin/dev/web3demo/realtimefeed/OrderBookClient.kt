package dev.web3demo.realtimefeed

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private class OrderBookGapException(expected: Long, actual: Long) :
    Exception("Order book gap: expected next U=$expected but got U=$actual")

/**
 * Maintains a local order book following Binance's documented "how to manage a local order
 * book" procedure: buffer the live diff stream, bootstrap from a REST snapshot, align the
 * buffer against the snapshot's `lastUpdateId`, then keep applying diffs while checking each
 * one picks up exactly where the last one left off. A gap triggers a full resync rather than
 * silently drifting — see [OrderBookSync] for the pure sequencing rules, unit-tested separately.
 *
 * Deliberately uses the *raw* (non-throttled) `@depth` stream rather than `@depth@100ms`: it's
 * the closer analogue to a real trading app's firehose (hundreds of updates/sec on a busy
 * symbol), which is what actually exercises backpressure — `@depth@100ms` would just hide it.
 */
class OrderBookClient(
    symbol: String,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val httpClient: HttpClient = HttpClient { install(WebSockets) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lowerSymbol = symbol.lowercase()
    private val upperSymbol = symbol.uppercase()

    private val bids = HashMap<Double, Double>()
    private val asks = HashMap<Double, Double>()
    private var lastAppliedUpdateId = -1L

    private var updateCount = 0L
    private var resyncCount = 0

    private val _state = MutableStateFlow<OrderBookState>(OrderBookState.Idle)
    val state: StateFlow<OrderBookState> = _state.asStateFlow()

    private val _top = MutableStateFlow(OrderBookTop(null, null, -1))
    val top: StateFlow<OrderBookTop> = _top.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = OrderBookState.Idle
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                syncAndStream()
                attempt = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: OrderBookGapException) {
                resyncCount += 1
                attempt = 0 // a gap isn't a connectivity problem, retry immediately
            } catch (e: Exception) {
                attempt += 1
                _state.value = OrderBookState.Idle
                delay(reconnectPolicy.delayMillis(attempt))
            }
        }
    }

    private suspend fun syncAndStream() {
        _state.value = OrderBookState.SyncingSnapshot
        bids.clear()
        asks.clear()
        lastAppliedUpdateId = -1

        httpClient.webSocket(urlString = "wss://stream.binance.com:9443/ws/$lowerSymbol@depth") {
            val buffer = mutableListOf<DepthDiff>()
            var synced = false
            var snapshotLastUpdateId: Long? = null

            while (currentCoroutineContext().isActive) {
                val frame = incoming.receive()
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val diff = parseDiff(text) ?: continue

                if (!synced) {
                    buffer += diff
                    if (snapshotLastUpdateId == null) {
                        val snapshot = fetchSnapshot()
                        applySnapshot(snapshot)
                        snapshotLastUpdateId = snapshot.lastUpdateId
                    }
                    // Re-checked on every buffered diff, not just the first: the snapshot's
                    // lastUpdateId can easily be older than every diff buffered so far (the REST
                    // round trip takes time), so the straddling diff may only show up several
                    // messages later. Only fetching the snapshot once but never re-checking
                    // against a growing buffer was the actual bug caught by the live test below.
                    val startIndex = OrderBookSync.firstApplicableIndex(buffer, snapshotLastUpdateId)
                    if (startIndex != null) {
                        for (i in startIndex until buffer.size) {
                            applyDiff(buffer[i])
                            lastAppliedUpdateId = buffer[i].finalUpdateId
                        }
                        updateCount += (buffer.size - startIndex)
                        buffer.clear()
                        synced = true
                        publishLiveState()
                    }
                } else {
                    if (!OrderBookSync.isExpectedNext(diff, lastAppliedUpdateId)) {
                        throw OrderBookGapException(expected = lastAppliedUpdateId + 1, actual = diff.firstUpdateId)
                    }
                    applyDiff(diff)
                    lastAppliedUpdateId = diff.finalUpdateId
                    updateCount += 1
                    publishLiveState()
                }
            }
        }
    }

    private fun applySnapshot(snapshot: OrderBookSnapshot) {
        snapshot.bids.forEach { (price, qty) -> if (qty > 0) bids[price] = qty }
        snapshot.asks.forEach { (price, qty) -> if (qty > 0) asks[price] = qty }
    }

    private fun applyDiff(diff: DepthDiff) {
        diff.bids.forEach { (price, qty) -> if (qty == 0.0) bids.remove(price) else bids[price] = qty }
        diff.asks.forEach { (price, qty) -> if (qty == 0.0) asks.remove(price) else asks[price] = qty }
    }

    private fun publishLiveState() {
        _state.value = OrderBookState.Live(updateCount, resyncCount)
        _top.value = OrderBookTop(
            bestBid = bids.keys.maxOrNull(),
            bestAsk = asks.keys.minOrNull(),
            lastUpdateId = lastAppliedUpdateId,
        )
    }

    private suspend fun fetchSnapshot(): OrderBookSnapshot {
        val response = httpClient.get("https://api.binance.com/api/v3/depth") {
            parameter("symbol", upperSymbol)
            parameter("limit", 1000)
        }
        return parseSnapshot(response.bodyAsText())
    }

    private fun parseDiff(json: String): DepthDiff? = try {
        val obj = Json.parseToJsonElement(json).jsonObject
        DepthDiff(
            firstUpdateId = obj.getValue("U").jsonPrimitive.long,
            finalUpdateId = obj.getValue("u").jsonPrimitive.long,
            bids = obj.getValue("b").jsonArray.map(::parseLevel),
            asks = obj.getValue("a").jsonArray.map(::parseLevel),
        )
    } catch (e: Exception) {
        null
    }

    private fun parseSnapshot(json: String): OrderBookSnapshot {
        val obj = Json.parseToJsonElement(json).jsonObject
        return OrderBookSnapshot(
            lastUpdateId = obj.getValue("lastUpdateId").jsonPrimitive.long,
            bids = obj.getValue("bids").jsonArray.map(::parseLevel),
            asks = obj.getValue("asks").jsonArray.map(::parseLevel),
        )
    }

    private fun parseLevel(element: kotlinx.serialization.json.JsonElement): Pair<Double, Double> {
        val array = element.jsonArray
        return array[0].jsonPrimitive.content.toDouble() to array[1].jsonPrimitive.content.toDouble()
    }
}
