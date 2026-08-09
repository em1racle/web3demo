package dev.web3demo.realtimefeed

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Wraps a token-authenticated realtime connection so callers don't have to hand-roll "ensure a
 * fresh token, connect, and if the token expires mid-session or gets rejected, refresh and
 * reconnect" every time. There's no real authenticated backend behind this demo — Binance's
 * public feeds ([PriceFeedClient], [OrderBookClient]) need no auth at all — so this is exercised
 * against a fake [AuthTokenProvider] in tests. It's the same shape a real trading backend's
 * private channels (balances, live orders, price alerts) would need once one exists.
 *
 * Two distinct refresh triggers, both real production concerns:
 *  1. Proactive: a watcher coroutine cancels and restarts the connection *before* the token
 *     expires, so the session never actually goes unauthenticated.
 *  2. Reactive: if the server rejects the token anyway (clock skew, revoked token, etc.), one
 *     refresh-and-retry is attempted before giving up — not looped forever on a truly bad token.
 *
 * [connect] is expected to run until cancelled (a long-lived socket session); returning from it
 * normally is treated as "the caller is done," not as a failure to retry.
 */
class AuthenticatedConnectionGate(
    private val tokenProvider: AuthTokenProvider,
    private val refreshMarginMillis: Long = 30_000,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun run(connect: suspend (token: String) -> Unit) {
        while (true) {
            val token = validToken()
            var rejectedWith: AuthRejectedException? = null
            var proactiveRefreshTriggered = false

            coroutineScope {
                val connectionJob =
                    launch {
                        try {
                            connect(token.value)
                        } catch (e: AuthRejectedException) {
                            rejectedWith = e
                        }
                    }
                val watcherJob =
                    launch {
                        delay(AuthTokenGate.millisUntilRefreshDue(token, now(), refreshMarginMillis))
                        proactiveRefreshTriggered = true
                        connectionJob.cancel()
                    }
                // Without this, coroutineScope waits for the watcher's `delay` to finish even
                // after connect() has already returned or thrown — up to `refreshMarginMillis`
                // of pointless hanging.
                connectionJob.invokeOnCompletion { watcherJob.cancel() }
            }

            if (rejectedWith != null) {
                // Reactive path: refresh once and retry. A second rejection propagates — this
                // deliberately doesn't loop forever on a token that's rejected right after
                // refreshing.
                tokenProvider.refresh()
                val retryToken = validToken()
                connect(retryToken.value)
                return
            }

            if (proactiveRefreshTriggered) continue

            return // connect() completed on its own — nothing left to reconnect for.
        }
    }

    private suspend fun validToken(): AuthToken {
        val existing = tokenProvider.currentToken()
        return if (AuthTokenGate.needsRefresh(existing, now(), refreshMarginMillis)) {
            tokenProvider.refresh()
        } else {
            existing!!
        }
    }
}
