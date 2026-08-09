package dev.web3demo.realtimefeed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}

class PersistedPriceCacheTest {
    @Test
    fun loadReturnsEmptyWhenNothingSavedYet() {
        val cache = PersistedPriceCache(InMemoryKeyValueStore())
        assertTrue(cache.load().isEmpty())
    }

    @Test
    fun saveThenLoadRoundTrips() {
        val store = InMemoryKeyValueStore()
        val cache = PersistedPriceCache(store)
        val snapshot =
            mapOf(
                "BTCUSDT" to PriceTick("BTCUSDT", 65000.0, 1_700_000_000_000),
                "ETHUSDT" to PriceTick("ETHUSDT", 3200.5, 1_700_000_000_001),
            )

        cache.save(snapshot)
        assertEquals(snapshot, cache.load())
    }

    @Test
    fun loadIgnoresCorruptData() {
        val store =
            InMemoryKeyValueStore().apply {
                putString("dev.web3demo.price_cache.v1", "{not valid json")
            }
        val cache = PersistedPriceCache(store)
        assertTrue(cache.load().isEmpty())
    }
}
