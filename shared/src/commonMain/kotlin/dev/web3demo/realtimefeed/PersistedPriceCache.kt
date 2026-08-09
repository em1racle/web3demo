package dev.web3demo.realtimefeed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The "load last-known state on launch so the screen isn't blank before the first WebSocket
 * message arrives" policy — this part (encode/decode, key naming, corrupt-data handling) is
 * genuinely platform-agnostic, so it's the piece worth sharing. Where the bytes actually live
 * (SharedPreferences vs UserDefaults) isn't, hence [KeyValueStore] being injected rather than
 * baked in here.
 */
class PersistedPriceCache(private val store: KeyValueStore) {
    fun save(snapshot: Map<String, PriceTick>) {
        store.putString(KEY, Json.encodeToString(snapshot))
    }

    fun load(): Map<String, PriceTick> {
        val raw = store.getString(KEY) ?: return emptyMap()
        return try {
            Json.decodeFromString<Map<String, PriceTick>>(raw)
        } catch (e: Exception) {
            // Corrupt or stale-schema cache shouldn't crash the app on launch — just start empty,
            // same as a first-ever launch.
            emptyMap()
        }
    }

    private companion object {
        const val KEY = "dev.web3demo.price_cache.v1"
    }
}
