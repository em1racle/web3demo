package dev.web3demo.androidapp

import android.content.Context
import dev.web3demo.realtimefeed.KeyValueStore

class AndroidKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("web3demo_cache", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }
}
