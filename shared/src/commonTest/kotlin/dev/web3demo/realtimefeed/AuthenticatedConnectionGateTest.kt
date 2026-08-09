package dev.web3demo.realtimefeed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeAuthTokenProvider(
    private var current: AuthToken?,
    private val nextTokens: MutableList<AuthToken> = mutableListOf(),
) : AuthTokenProvider {
    var refreshCount = 0
        private set

    override suspend fun currentToken(): AuthToken? = current

    override suspend fun refresh(): AuthToken {
        refreshCount++
        val next =
            nextTokens.removeFirstOrNull()
                ?: current!!.copy(expiresAtEpochMillis = current!!.expiresAtEpochMillis + 1_000_000)
        current = next
        return next
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedConnectionGateTest {
    @Test
    fun proactivelyRefreshesAndReconnectsBeforeExpiry() =
        runTest {
            val provider =
                FakeAuthTokenProvider(
                    current = AuthToken("t0", expiresAtEpochMillis = 100_000),
                    nextTokens = mutableListOf(AuthToken("t1", expiresAtEpochMillis = 10_100_000)),
                )
            val gate =
                AuthenticatedConnectionGate(provider, refreshMarginMillis = 30_000, now = { testScheduler.currentTime })
            var connectCount = 0
            val usedTokens = mutableListOf<String>()

            val job =
                launch {
                    gate.run { token ->
                        connectCount++
                        usedTokens += token
                        awaitCancellation()
                    }
                }

            // The first token's refresh is due at 100_000 - 30_000 = 70_000.
            advanceTimeBy(70_001)
            runCurrent()

            assertEquals(2, connectCount)
            assertEquals(listOf("t0", "t1"), usedTokens)
            assertEquals(1, provider.refreshCount)

            job.cancelAndJoin()
        }

    @Test
    fun refreshesOnceAndRetriesAfterRejection() =
        runTest {
            val provider = FakeAuthTokenProvider(current = AuthToken("bad", expiresAtEpochMillis = 10_000_000))
            val gate =
                AuthenticatedConnectionGate(provider, refreshMarginMillis = 30_000, now = { testScheduler.currentTime })
            var attempt = 0

            gate.run { token ->
                attempt++
                if (attempt == 1) throw AuthRejectedException("nope")
                // second attempt just returns normally
            }

            assertEquals(2, attempt)
            assertEquals(1, provider.refreshCount)
        }

    @Test
    fun propagatesRejectionWhenRefreshedTokenIsAlsoRejected() =
        runTest {
            val provider = FakeAuthTokenProvider(current = AuthToken("bad", expiresAtEpochMillis = 10_000_000))
            val gate =
                AuthenticatedConnectionGate(provider, refreshMarginMillis = 30_000, now = { testScheduler.currentTime })

            assertFailsWith<AuthRejectedException> {
                gate.run { throw AuthRejectedException("still nope") }
            }
            assertEquals(1, provider.refreshCount)
        }
}
