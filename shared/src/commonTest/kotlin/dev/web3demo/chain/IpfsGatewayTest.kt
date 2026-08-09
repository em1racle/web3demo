package dev.web3demo.chain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IpfsGatewayTest {
    @Test
    fun normalize_resolvesIpfsScheme() {
        assertEquals(
            "https://ipfs.io/ipfs/QmABC/1.json",
            IpfsGateway.normalize("ipfs://QmABC/1.json"),
        )
    }

    @Test
    fun normalize_handlesDoubleIpfsPrefix() {
        // "ipfs://ipfs/<cid>" is a common malformed-but-real pattern from some minting tools.
        assertEquals(
            "https://ipfs.io/ipfs/QmABC/1.json",
            IpfsGateway.normalize("ipfs://ipfs/QmABC/1.json"),
        )
    }

    @Test
    fun normalize_passesThroughHttps() {
        assertEquals("https://example.com/1.json", IpfsGateway.normalize("https://example.com/1.json"))
    }

    @Test
    fun normalize_passesThroughHttp() {
        assertEquals("http://example.com/1.json", IpfsGateway.normalize("http://example.com/1.json"))
    }

    @Test
    fun normalize_rejectsJavascriptScheme() {
        assertNull(IpfsGateway.normalize("javascript:alert(1)"))
    }

    @Test
    fun normalize_rejectsDataScheme() {
        assertNull(IpfsGateway.normalize("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun normalize_rejectsFileScheme() {
        assertNull(IpfsGateway.normalize("file:///etc/passwd"))
    }

    @Test
    fun normalize_rejectsEmptyIpfsPath() {
        assertNull(IpfsGateway.normalize("ipfs://"))
    }

    @Test
    fun normalize_usesCustomGateway() {
        assertEquals(
            "https://custom.gateway/ipfs/QmABC",
            IpfsGateway.normalize("ipfs://QmABC", gateway = "https://custom.gateway/ipfs/"),
        )
    }

    @Test
    fun normalize_trimsWhitespace() {
        assertEquals("https://example.com/1.json", IpfsGateway.normalize("  https://example.com/1.json  "))
    }
}
