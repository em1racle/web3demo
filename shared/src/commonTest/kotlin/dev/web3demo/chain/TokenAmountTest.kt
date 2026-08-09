package dev.web3demo.chain

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenAmountTest {
    @Test
    fun formatTokenAmount_wholeNumberWithNoRemainder() {
        assertEquals("5", formatTokenAmount(BigInteger.parseString("5000000000000000000"), 18))
    }

    @Test
    fun formatTokenAmount_fractionalAmount() {
        assertEquals("1.5", formatTokenAmount(BigInteger.parseString("1500000000000000000"), 18))
    }

    @Test
    fun formatTokenAmount_zero() {
        assertEquals("0", formatTokenAmount(BigInteger.ZERO, 18))
    }

    @Test
    fun formatTokenAmount_smallerThanOneUnit() {
        assertEquals("0.000000000000000001", formatTokenAmount(BigInteger.ONE, 18))
    }

    @Test
    fun formatTokenAmount_zeroDecimals() {
        assertEquals("42", formatTokenAmount(BigInteger.parseString("42"), 0))
    }

    @Test
    fun formatTokenAmount_largeValueDoesNotLosePrecision() {
        // 2^60, well past Double's 2^53 exact-integer limit — this would silently round with Double.
        val raw = BigInteger.parseString("1152921504606846976000000000000000000")
        assertEquals("1152921504606846976", formatTokenAmount(raw, 18))
    }

    @Test
    fun formatTokenAmount_trimsTrailingZerosButKeepsSignificantDigits() {
        assertEquals("1.23", formatTokenAmount(BigInteger.parseString("1230000000000000000"), 18))
    }
}
