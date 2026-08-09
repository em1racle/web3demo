package dev.web3demo.chain

/** Resolves `ipfs://` URIs to an HTTP gateway URL, and rejects anything that isn't http(s)/ipfs —
 * NFT metadata is attacker-controlled (anyone can mint a token with a malicious `tokenURI`), so
 * schemes like `javascript:`/`file:`/`data:` are refused rather than handed to an image loader. */
object IpfsGateway {
    const val DEFAULT_GATEWAY = "https://ipfs.io/ipfs/"

    /** Returns null for anything that isn't a safe, resolvable http(s)/ipfs URI. */
    fun normalize(
        uri: String,
        gateway: String = DEFAULT_GATEWAY,
    ): String? {
        val trimmed = uri.trim()
        return when {
            trimmed.startsWith("ipfs://", ignoreCase = true) -> {
                val path = trimmed.removePrefix("ipfs://").removePrefix("ipfs/")
                if (path.isBlank()) null else gateway + path
            }
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            else -> null
        }
    }
}
