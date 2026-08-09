package dev.web3demo.realtimefeed

import kotlinx.serialization.Serializable

@Serializable
data class PriceTick(
    val symbol: String,
    val price: Double,
    val timestampMillis: Long,
)
