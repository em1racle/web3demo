package dev.web3demo.realtimefeed

/**
 * Pure timing rules for token refresh, kept separate from I/O for the same reason as
 * [ReconnectPolicy] and [OrderBookSync]. Deliberately refreshes *before* expiry rather than
 * waiting to be rejected — a socket auth'd with a token that expires mid-session should get a
 * fresh one proactively, not wait for the server to kick it off first.
 */
internal object AuthTokenGate {
    fun needsRefresh(token: AuthToken?, nowEpochMillis: Long, refreshMarginMillis: Long): Boolean {
        if (token == null) return true
        return token.expiresAtEpochMillis - nowEpochMillis <= refreshMarginMillis
    }

    /** How long until this token should be proactively refreshed — 0 if it's already due. */
    fun millisUntilRefreshDue(token: AuthToken, nowEpochMillis: Long, refreshMarginMillis: Long): Long =
        (token.expiresAtEpochMillis - refreshMarginMillis - nowEpochMillis).coerceAtLeast(0)
}
