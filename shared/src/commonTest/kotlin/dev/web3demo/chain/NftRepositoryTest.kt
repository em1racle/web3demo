package dev.web3demo.chain

import com.ionspin.kotlin.bignum.integer.BigInteger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises [NftRepository] end to end with mocked RPC + HTTP transports — no real chain or
 * network needed to cover the actual engineering risk here: unsafe-URL rejection, timeout, and
 * invalid-JSON handling. A real Sepolia ERC-721 contract to live-test against wasn't available at
 * the time this was written (unlike the ERC-20 token, there's no single well-known canonical
 * testnet NFT contract) — see docs/testing.md. */
class NftRepositoryTest {
    private fun rpcClientReturning(vararg hexResults: String): EthereumRpcClient {
        var callIndex = 0
        val engine =
            MockEngine {
                val hex = hexResults[callIndex]
                callIndex++
                respond(
                    content = """{"jsonrpc":"2.0","id":1,"result":"$hex"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return EthereumRpcClient(httpClient = HttpClient(engine))
    }

    private fun abiEncodeAddressResult(address: String): String = "0x" + Abi.encodeAddress(address)

    private fun abiEncodeStringResult(value: String): String {
        val bytes = value.encodeToByteArray()
        val lengthHex = bytes.size.toString(16).padStart(64, '0')
        var dataHex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val paddedLength = ((dataHex.length + 63) / 64) * 64
        dataHex = dataHex.padEnd(paddedLength, '0')
        val offsetHex = "20".padStart(64, '0')
        return "0x$offsetHex$lengthHex$dataHex"
    }

    private val owner = "0xabcdef0123456789abcdef0123456789abcdef01"

    @Test
    fun fetchItem_loadsValidMetadata() =
        runBlocking {
            val rpc =
                rpcClientReturning(
                    abiEncodeAddressResult(owner),
                    abiEncodeStringResult("https://example.com/1.json"),
                )
            val metadataEngine =
                MockEngine {
                    respond(
                        content = """{"name":"Cool NFT","description":"desc","image":"ipfs://QmXYZ"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repository = NftRepository(rpc, HttpClient(metadataEngine))

            val item = repository.fetchItem("0xcontract", BigInteger.ONE)

            assertEquals(NftMetadataState.LOADED, item.metadataState)
            assertEquals("Cool NFT", item.name)
            assertEquals("desc", item.description)
            assertEquals("https://ipfs.io/ipfs/QmXYZ", item.imageUrl)
            assertEquals(owner, item.owner)
        }

    @Test
    fun fetchItem_rejectsUnsafeUrlScheme() =
        runBlocking {
            val rpc =
                rpcClientReturning(
                    abiEncodeAddressResult(owner),
                    abiEncodeStringResult("javascript:alert(1)"),
                )
            val repository = NftRepository(rpc, HttpClient(MockEngine { error("should not be called") }))

            val item = repository.fetchItem("0xcontract", BigInteger.ONE)

            assertEquals(NftMetadataState.UNSAFE_URL, item.metadataState)
        }

    @Test
    fun fetchItem_timesOutOnSlowMetadata() =
        runBlocking {
            val rpc =
                rpcClientReturning(
                    abiEncodeAddressResult(owner),
                    abiEncodeStringResult("https://example.com/slow.json"),
                )
            val metadataEngine =
                MockEngine {
                    delay(500)
                    respond(content = "{}", status = HttpStatusCode.OK)
                }
            val repository = NftRepository(rpc, HttpClient(metadataEngine), metadataTimeoutMillis = 10)

            val item = repository.fetchItem("0xcontract", BigInteger.ONE)

            assertEquals(NftMetadataState.TIMEOUT, item.metadataState)
        }

    @Test
    fun fetchItem_invalidJsonBecomesInvalidMetadataState() =
        runBlocking {
            val rpc =
                rpcClientReturning(
                    abiEncodeAddressResult(owner),
                    abiEncodeStringResult("https://example.com/bad.json"),
                )
            val metadataEngine =
                MockEngine {
                    respond(content = "not json at all", status = HttpStatusCode.OK)
                }
            val repository = NftRepository(rpc, HttpClient(metadataEngine))

            val item = repository.fetchItem("0xcontract", BigInteger.ONE)

            assertEquals(NftMetadataState.INVALID_METADATA, item.metadataState)
        }
}
