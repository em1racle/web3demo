package dev.web3demo.chain

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Minimal ABI encode/decode for the handful of read-only ERC-20/ERC-721 calls this app makes —
 * not a general-purpose ABI library. Their read methods only ever take a fixed number of static
 * params (address, uint256), so the dynamic-type encoding rules for arrays/structs don't apply on
 * the input side. Decoding a `string` return (symbol/name) does need the dynamic-type rules,
 * since Solidity ABI-encodes strings as offset + length + data.
 */
object Abi {
    // Standard 4-byte function selectors — the first 4 bytes of keccak256(signature). Fixed by
    // the ERC-20/ERC-721 standards, not computed at runtime, so no Keccak implementation needed.
    const val SELECTOR_NAME = "0x06fdde03"
    const val SELECTOR_SYMBOL = "0x95d89b41"
    const val SELECTOR_DECIMALS = "0x313ce567"
    const val SELECTOR_BALANCE_OF = "0x70a08231"
    const val SELECTOR_OWNER_OF = "0x6352211e"
    const val SELECTOR_TOKEN_URI = "0xc87b56dd"

    fun encodeCall(
        selector: String,
        vararg params: String,
    ): String = selector + params.joinToString("")

    fun encodeAddress(address: String): String = address.removePrefix("0x").lowercase().padStart(64, '0')

    fun encodeUint256(value: BigInteger): String = value.toString(16).padStart(64, '0')

    fun decodeUint256(hex: String): BigInteger {
        val stripped = stripLeadingZeros(hex)
        return if (stripped.isEmpty()) BigInteger.ZERO else BigInteger.parseString(stripped, 16)
    }

    fun decodeUint8(hex: String): Int = decodeUint256(hex).intValue()

    fun decodeAddress(hex: String): String {
        val body = hex.removePrefix("0x").padStart(64, '0')
        return "0x" + body.takeLast(40)
    }

    /** Single-return-value `string`/`bytes` decode: [offset word][length word][utf8 bytes,
     * right-padded to a multiple of 32 bytes]. The offset is always 0x20 for a single return
     * value, so this skips straight to the length + data words. */
    fun decodeString(hex: String): String {
        val body = hex.removePrefix("0x")
        if (body.length < 128) return ""
        val length = BigInteger.parseString(body.substring(64, 128), 16).intValue()
        if (length <= 0) return ""
        val dataEnd = (128 + length * 2).coerceAtMost(body.length)
        val dataHex = body.substring(128, dataEnd)
        val bytes =
            ByteArray(dataHex.length / 2) { i ->
                dataHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        return bytes.decodeToString()
    }

    private fun stripLeadingZeros(hex: String): String = hex.removePrefix("0x").trimStart('0')
}
