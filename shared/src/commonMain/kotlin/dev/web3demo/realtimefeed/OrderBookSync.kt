package dev.web3demo.realtimefeed

/**
 * Pure sequencing rules for Binance's documented local-order-book algorithm, kept separate from
 * the WebSocket/HTTP orchestration in [OrderBookClient] so they're unit-testable without a
 * network — same reasoning as [ReconnectPolicy].
 */
internal object OrderBookSync {
    /**
     * While bootstrapping: a REST snapshot only tells you the state as of `lastUpdateId`. Find
     * the first buffered diff whose range straddles that id — everything before it is already
     * reflected in the snapshot; it and everything after must still be applied on top.
     * Returns null if no buffered diff covers `lastUpdateId` yet (caller should keep buffering).
     */
    fun firstApplicableIndex(
        buffer: List<DepthDiff>,
        lastUpdateId: Long,
    ): Int? {
        val index =
            buffer.indexOfFirst {
                it.firstUpdateId <= lastUpdateId + 1 && it.finalUpdateId >= lastUpdateId + 1
            }
        return if (index == -1) null else index
    }

    /**
     * Once live: each diff's `U` must be exactly one past the previous diff's `u`. If it isn't,
     * a message was dropped (or arrived out of order) and the local book can no longer be
     * trusted — the caller must resync from a fresh snapshot rather than silently drift.
     */
    fun isExpectedNext(
        diff: DepthDiff,
        lastAppliedUpdateId: Long,
    ): Boolean = diff.firstUpdateId == lastAppliedUpdateId + 1
}
