package dev.web3demo.chain

import com.ionspin.kotlin.bignum.integer.BigInteger

data class TokenMetadata(
    val contract: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
)

data class TokenBalance(
    val raw: BigInteger,
    val decimals: Int,
) {
    val formatted: String get() = formatTokenAmount(raw, decimals)
}

enum class NftMetadataState { LOADING, LOADED, INVALID_METADATA, TIMEOUT, UNSAFE_URL }

data class NftItem(
    val contract: String,
    val tokenId: BigInteger,
    val owner: String,
    val metadataState: NftMetadataState,
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)
