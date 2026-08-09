package dev.web3demo.realtimefeed

/** One entry from Binance's diff depth stream. `firstUpdateId`/`finalUpdateId` are the `U`/`u`
 * fields — the sequence range this single message covers. */
data class DepthDiff(
    val firstUpdateId: Long,
    val finalUpdateId: Long,
    val bids: List<Pair<Double, Double>>,
    val asks: List<Pair<Double, Double>>,
)

data class OrderBookSnapshot(
    val lastUpdateId: Long,
    val bids: List<Pair<Double, Double>>,
    val asks: List<Pair<Double, Double>>,
)

data class OrderBookTop(
    val bestBid: Double?,
    val bestAsk: Double?,
    val lastUpdateId: Long,
)

sealed class OrderBookState {
    data object Idle : OrderBookState()
    data object SyncingSnapshot : OrderBookState()
    data class Live(val updateCount: Long, val resyncCount: Int) : OrderBookState()
}
