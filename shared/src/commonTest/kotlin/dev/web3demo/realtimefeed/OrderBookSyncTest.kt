package dev.web3demo.realtimefeed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderBookSyncTest {
    private fun diff(
        first: Long,
        last: Long,
    ) = DepthDiff(first, last, emptyList(), emptyList())

    @Test
    fun firstApplicableIndex_findsDiffStraddlingSnapshot() {
        val buffer = listOf(diff(150, 155), diff(156, 160), diff(161, 165))
        // snapshot lastUpdateId=158 -> need U<=159<=u, so the diff covering 156..160 qualifies
        assertEquals(1, OrderBookSync.firstApplicableIndex(buffer, lastUpdateId = 158))
    }

    @Test
    fun firstApplicableIndex_returnsNullWhenBufferDoesNotCoverSnapshotYet() {
        val buffer = listOf(diff(150, 155))
        assertNull(OrderBookSync.firstApplicableIndex(buffer, lastUpdateId = 500))
    }

    @Test
    fun firstApplicableIndex_matchesExactBoundary() {
        val buffer = listOf(diff(100, 100))
        assertEquals(0, OrderBookSync.firstApplicableIndex(buffer, lastUpdateId = 99))
    }

    @Test
    fun isExpectedNext_trueWhenContiguous() {
        val next = diff(161, 165)
        assertEquals(true, OrderBookSync.isExpectedNext(next, lastAppliedUpdateId = 160))
    }

    @Test
    fun isExpectedNext_falseOnGap() {
        val next = diff(162, 165)
        assertEquals(false, OrderBookSync.isExpectedNext(next, lastAppliedUpdateId = 160))
    }

    @Test
    fun isExpectedNext_falseWhenDuplicateOrStaleMessageArrives() {
        val stale = diff(155, 158)
        assertEquals(false, OrderBookSync.isExpectedNext(stale, lastAppliedUpdateId = 160))
    }
}
