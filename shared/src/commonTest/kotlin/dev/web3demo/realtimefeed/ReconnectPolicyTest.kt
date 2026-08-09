package dev.web3demo.realtimefeed

import kotlin.test.Test
import kotlin.test.assertEquals

class ReconnectPolicyTest {
    @Test
    fun firstAttemptUsesBaseDelay() {
        val policy = ReconnectPolicy(baseDelayMillis = 1000, maxDelayMillis = 30_000, jitterFraction = 0.0)
        assertEquals(1000, policy.delayMillis(1))
    }

    @Test
    fun delayDoublesEachAttempt() {
        val policy = ReconnectPolicy(baseDelayMillis = 1000, maxDelayMillis = 1_000_000, jitterFraction = 0.0)
        assertEquals(1000, policy.delayMillis(1))
        assertEquals(2000, policy.delayMillis(2))
        assertEquals(4000, policy.delayMillis(3))
        assertEquals(8000, policy.delayMillis(4))
    }

    @Test
    fun delayNeverExceedsMax() {
        val policy = ReconnectPolicy(baseDelayMillis = 1000, maxDelayMillis = 10_000, jitterFraction = 0.0)
        assertEquals(10_000, policy.delayMillis(20))
    }

    @Test
    fun jitterAddsUpToConfiguredFraction() {
        val policy = ReconnectPolicy(baseDelayMillis = 10_000, maxDelayMillis = 100_000, jitterFraction = 0.5)
        val delay = policy.delayMillis(1) { 1.0 }
        assertEquals(15_000, delay)
    }
}
