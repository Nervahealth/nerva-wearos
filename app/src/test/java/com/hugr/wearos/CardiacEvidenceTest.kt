package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardiacEvidenceTest {
    @Test
    fun preservesEveryPointAndEveryIbiStatus() {
        val records = flattenCardiacBatch(
            17,
            listOf(
                SamsungCardiacPoint(1000L, 80, 1, listOf(750, 760), listOf(0, 1)),
                SamsungCardiacPoint(2000L, 82, 1, listOf(730), listOf(0))
            )
        )
        assertEquals(5, records.size)
        assertEquals(listOf(1, 2, 2, 1, 2), records.map { it.kind.wireCode })
        assertEquals(listOf(1000L, 1000L, 1000L, 2000L, 2000L), records.map { it.sourceTimestamp })
        assertEquals(listOf(-1, 0, 1, -1, 0), records.map { it.listIndex })
        assertEquals(listOf(1, 0, 1, 1, 0), records.map { it.status })
        assertTrue(records.all { it.callbackId == 17 && it.pointCount == 2 })
        assertFalse(records.any { it.contractAnomaly })
    }

    @Test
    fun preservesStatusShapeMismatchAsExplicitAnomaly() {
        val records = flattenCardiacBatch(
            3,
            listOf(SamsungCardiacPoint(3000L, 79, 1, listOf(760, 770), listOf(0)))
        )
        assertEquals(3, records.size)
        assertTrue(records.all { it.contractAnomaly })
        assertEquals(-1, records.last().status)
        assertEquals(770, records.last().value)
    }
}
