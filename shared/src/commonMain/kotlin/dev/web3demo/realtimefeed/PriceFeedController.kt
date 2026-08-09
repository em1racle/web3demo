package dev.web3demo.realtimefeed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * `StateFlow` doesn't bridge to Swift's `AsyncSequence` without an extra library (SKIE /
 * KMP-NativeCoroutines). This exposes a plain callback API instead — callbacks land on the main
 * thread via `Dispatchers.Main`, so Swift/Compose call sites can mutate UI state directly.
 */
class PriceFeedController(symbols: List<String>, cache: KeyValueStore? = null) {
    private val client = PriceFeedClient(symbols)
    private val priceCache = cache?.let { PersistedPriceCache(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null
    private var snapshotJob: Job? = null

    /** Last-known snapshot from disk, if a [KeyValueStore] was provided — call before [start]
     * so the UI has something to show while the first live message is still in flight. */
    fun cachedSnapshot(): Map<String, PriceTick> = priceCache?.load() ?: emptyMap()

    fun start(
        onState: (ConnectionState) -> Unit,
        onSnapshot: (Map<String, PriceTick>) -> Unit,
    ) {
        stateJob = client.state.onEach { onState(it) }.launchIn(scope)
        snapshotJob =
            client.snapshots.onEach { snapshot ->
                onSnapshot(snapshot)
                priceCache?.save(snapshot)
            }.launchIn(scope)
        client.start()
    }

    fun stop() {
        stateJob?.cancel()
        snapshotJob?.cancel()
        client.stop()
    }
}
