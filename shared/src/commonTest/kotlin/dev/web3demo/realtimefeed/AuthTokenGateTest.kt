package dev.web3demo.realtimefeed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthTokenGateTest {
    @Test
    fun needsRefresh_trueWhenTokenIsNull() {
        assertTrue(AuthTokenGate.needsRefresh(null, nowEpochMillis = 1000, refreshMarginMillis = 30_000))
    }

    @Test
    fun needsRefresh_falseWhenWellWithinValidity() {
        val token = AuthToken("t", expiresAtEpochMillis = 1_000_000)
        assertFalse(AuthTokenGate.needsRefresh(token, nowEpochMillis = 0, refreshMarginMillis = 30_000))
    }

    @Test
    fun needsRefresh_trueInsideTheMargin() {
        val token = AuthToken("t", expiresAtEpochMillis = 100_000)
        assertTrue(AuthTokenGate.needsRefresh(token, nowEpochMillis = 90_000, refreshMarginMillis = 30_000))
    }

    @Test
    fun needsRefresh_trueWhenAlreadyExpired() {
        val token = AuthToken("t", expiresAtEpochMillis = 100_000)
        assertTrue(AuthTokenGate.needsRefresh(token, nowEpochMillis = 200_000, refreshMarginMillis = 30_000))
    }

    @Test
    fun millisUntilRefreshDue_countsDownToTheMargin() {
        val token = AuthToken("t", expiresAtEpochMillis = 100_000)
        assertEquals(69_000, AuthTokenGate.millisUntilRefreshDue(token, nowEpochMillis = 1_000, refreshMarginMillis = 30_000))
    }

    @Test
    fun millisUntilRefreshDue_neverNegative() {
        val token = AuthToken("t", expiresAtEpochMillis = 100_000)
        assertEquals(0, AuthTokenGate.millisUntilRefreshDue(token, nowEpochMillis = 500_000, refreshMarginMillis = 30_000))
    }
}
