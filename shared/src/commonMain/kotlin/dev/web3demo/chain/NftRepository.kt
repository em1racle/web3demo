package dev.web3demo.chain

import com.ionspin.kotlin.bignum.integer.BigInteger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NftRepository(
    private val rpc: EthereumRpcClient,
    private val httpClient: HttpClient = HttpClient(),
    private val metadataTimeoutMillis: Long = 8_000,
) {
    suspend fun fetchOwner(
        contract: String,
        tokenId: BigInteger,
    ): AppResult<String> {
        val call = Abi.encodeCall(Abi.SELECTOR_OWNER_OF, Abi.encodeUint256(tokenId))
        return rpc.call(contract, call).decoded(Abi::decodeAddress)
    }

    suspend fun fetchTokenUri(
        contract: String,
        tokenId: BigInteger,
    ): AppResult<String> {
        val call = Abi.encodeCall(Abi.SELECTOR_TOKEN_URI, Abi.encodeUint256(tokenId))
        return rpc.call(contract, call).decoded(Abi::decodeString)
    }

    /** Combines owner + tokenURI + metadata fetch into one item. Metadata failure modes
     * (unsafe scheme, timeout, invalid JSON) become states on the returned item rather than a
     * thrown error — the owner/tokenId are still worth showing even if metadata isn't. */
    suspend fun fetchItem(
        contract: String,
        tokenId: BigInteger,
    ): NftItem {
        val owner =
            when (val result = fetchOwner(contract, tokenId)) {
                is AppResult.Ok -> result.value
                is AppResult.Err -> return NftItem(contract, tokenId, "unknown", NftMetadataState.INVALID_METADATA)
            }

        val tokenUri =
            when (val result = fetchTokenUri(contract, tokenId)) {
                is AppResult.Ok -> result.value
                is AppResult.Err -> return NftItem(contract, tokenId, owner, NftMetadataState.INVALID_METADATA)
            }

        val resolvedUrl =
            IpfsGateway.normalize(tokenUri)
                ?: return NftItem(contract, tokenId, owner, NftMetadataState.UNSAFE_URL)

        return try {
            withTimeout(metadataTimeoutMillis) {
                val text = httpClient.get(resolvedUrl).bodyAsText()
                val json = Json.parseToJsonElement(text).jsonObject
                val name = json["name"]?.jsonPrimitive?.contentOrNull
                val description = json["description"]?.jsonPrimitive?.contentOrNull
                val imageRaw = json["image"]?.jsonPrimitive?.contentOrNull
                val image = imageRaw?.let { IpfsGateway.normalize(it) }
                NftItem(contract, tokenId, owner, NftMetadataState.LOADED, name, description, image)
            }
        } catch (e: TimeoutCancellationException) {
            NftItem(contract, tokenId, owner, NftMetadataState.TIMEOUT)
        } catch (e: Exception) {
            NftItem(contract, tokenId, owner, NftMetadataState.INVALID_METADATA)
        }
    }
}
