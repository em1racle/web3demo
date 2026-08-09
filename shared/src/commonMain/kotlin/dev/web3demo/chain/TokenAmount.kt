package dev.web3demo.chain

import com.ionspin.kotlin.bignum.integer.BigInteger

/** Formats a raw token quantity as a decimal string using only integer/string arithmetic —
 * `Double` can't exactly represent 18-decimal token amounts (or anything beyond ~2^53), so
 * dividing by 10^decimals as a Double silently loses precision on real-sized balances. */
fun formatTokenAmount(
    raw: BigInteger,
    decimals: Int,
): String {
    require(decimals >= 0) { "decimals must be >= 0" }
    if (decimals == 0) return raw.toString(10)

    val digits = raw.toString(10)
    val padded = digits.padStart(decimals + 1, '0')
    val whole = padded.substring(0, padded.length - decimals)
    val fraction = padded.substring(padded.length - decimals).trimEnd('0')

    return if (fraction.isEmpty()) whole else "$whole.$fraction"
}
