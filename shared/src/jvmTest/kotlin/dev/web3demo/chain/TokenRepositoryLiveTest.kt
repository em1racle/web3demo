package dev.web3demo.chain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration smoke test against the real Sepolia network via a public, unauthenticated RPC
 * endpoint — no account, no API key. Confirms the ABI encode/decode in [Abi] actually holds up
 * against a real contract's response, not just hand-built fixtures.
 */
class TokenRepositoryLiveTest {
    // Chainlink's official Sepolia testnet LINK token — verified live before hardcoding here.
    private val linkContract = "0x779877A7B0D9E8603169DdbD7836e478b4624789"

    @Test
    fun fetchesRealMetadataFromSepolia() =
        runTest(timeout = 20.seconds) {
            val repository = TokenRepository(EthereumRpcClient())
            val result = repository.fetchMetadata(linkContract)

            assertTrue(result is AppResult.Ok, "expected Ok, got $result")
            val metadata = (result as AppResult.Ok).value
            assertEquals("LINK", metadata.symbol)
            assertEquals(18, metadata.decimals)
            println("Live token metadata: $metadata")
        }

    @Test
    fun fetchesRealBalanceForAnArbitraryAddress() =
        runTest(timeout = 20.seconds) {
            val repository = TokenRepository(EthereumRpcClient())
            // Not asserting a specific balance value — it can change over time. Just confirms the
            // call round-trips against a real contract without erroring.
            val result =
                repository.fetchBalance(
                    linkContract,
                    "0x0000000000000000000000000000000000dEaD",
                    decimals = 18,
                )

            assertTrue(result is AppResult.Ok, "expected Ok, got $result")
            println("Live balance: ${(result as AppResult.Ok).value.formatted}")
        }
}
