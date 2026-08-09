package dev.web3demo.realtimefeed

import kotlin.math.min
import kotlin.math.pow

/**
 * Pure exponential backoff with jitter, kept separate from I/O so it's trivially unit-testable.
 * Mirrors the Swift `ReconnectPolicy` in RealtimeFeed/ 1:1 so both platforms reconnect the same way.
 */
class ReconnectPolicy(
    private val baseDelayMillis: Long = 1_000,
    private val maxDelayMillis: Long = 30_000,
    private val jitterFraction: Double = 0.3,
) {
    /** attempt starts at 1 for the first reconnect try. */
    fun delayMillis(attempt: Int, randomSource: () -> Double = { kotlin.random.Random.nextDouble() }): Long {
        val exponential = baseDelayMillis * 2.0.pow((attempt - 1).coerceAtLeast(0))
        val capped = min(maxDelayMillis.toDouble(), exponential)
        val jitter = capped * jitterFraction * randomSource()
        return (capped + jitter).toLong()
    }
}
