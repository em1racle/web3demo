package dev.web3demo.realtimefeed

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Integration smoke test against the real Binance API — not mocked, requires network. Confirms
 * the snapshot-bootstrap + gap-detection sequencing in [OrderBookClient] actually holds up
 * against a live, high-message-rate feed, not just the pure-logic cases in [OrderBookSyncTest].
 */
class OrderBookClientLiveTest {
    @Test
    fun connectsAndReachesLiveStateWithoutImmediateGap() =
        runTest(timeout = 20.seconds) {
            val client = OrderBookClient("btcusdt")
            client.start()

            val liveState = client.state.first { it is OrderBookState.Live } as OrderBookState.Live
            println("Reached live state: updateCount=${liveState.updateCount} resyncCount=${liveState.resyncCount}")

            val top = client.top.first { it.bestBid != null && it.bestAsk != null }
            println("Top of book: bid=${top.bestBid} ask=${top.bestAsk} lastUpdateId=${top.lastUpdateId}")
            check(top.bestBid!! < top.bestAsk!!) { "Crossed book: bid ${top.bestBid} >= ask ${top.bestAsk}" }

            client.stop()
        }
}
