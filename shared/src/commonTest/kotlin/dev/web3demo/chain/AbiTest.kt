package dev.web3demo.chain

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class AbiTest {
    @Test
    fun encodeAddress_padsTo32Bytes() {
        val encoded = Abi.encodeAddress("0x1234567890123456789012345678901234567890")
        assertEquals(64, encoded.length)
        assertEquals("0000000000000000000000001234567890123456789012345678901234567890".takeLast(64), encoded)
    }

    @Test
    fun encodeCall_concatenatesSelectorAndParams() {
        val call = Abi.encodeCall(Abi.SELECTOR_BALANCE_OF, Abi.encodeAddress("0xabc"))
        assertEquals(Abi.SELECTOR_BALANCE_OF + "0".repeat(61) + "abc", call)
    }

    @Test
    fun decodeUint256_parsesFullWord() {
        val hex = "0x0000000000000000000000000000000000000000000000000000000000002710"
        assertEquals(BigInteger.parseString("10000"), Abi.decodeUint256(hex))
    }

    @Test
    fun decodeUint256_zeroWordIsZero() {
        val hex = "0x" + "0".repeat(64)
        assertEquals(BigInteger.ZERO, Abi.decodeUint256(hex))
    }

    @Test
    fun decodeUint8_parsesSmallValue() {
        val hex = "0x0000000000000000000000000000000000000000000000000000000000000012"
        assertEquals(18, Abi.decodeUint8(hex))
    }

    @Test
    fun decodeAddress_roundTripsWithEncodeAddress() {
        val address = "0xabcdef0123456789abcdef0123456789abcdef01"
        assertEquals(address, Abi.decodeAddress("0x" + Abi.encodeAddress(address)))
    }

    @Test
    fun decodeString_decodesOffsetLengthData() {
        // "Hi" = 0x4869, ABI-encoded as: [offset=0x20][length=2]["Hi" padded to 32 bytes]
        val hex =
            "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "4869000000000000000000000000000000000000000000000000000000000000"
        assertEquals("Hi", Abi.decodeString(hex))
    }

    @Test
    fun decodeString_emptyStringIsEmpty() {
        val hex =
            "0x" +
                "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000000"
        assertEquals("", Abi.decodeString(hex))
    }

    @Test
    fun decodeString_tooShortReturnsEmptyRatherThanCrashing() {
        assertEquals("", Abi.decodeString("0x1234"))
    }

    @Test
    fun roundTrip_encodeThenDecodeUint256() {
        val value = BigInteger.parseString("123456789012345678901234567890")
        assertEquals(value, Abi.decodeUint256("0x" + Abi.encodeUint256(value)))
    }
}
