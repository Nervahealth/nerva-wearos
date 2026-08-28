package com.hugr.wearos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayStartLineageGuardTest {
    @Test
    fun `delayed replay token is invalid after disconnect or reconnect lineage advances`() {
        val guard = ReplayStartLineageGuard()
        guard.advanceLineage()
        val connectedLineage = guard.capture()

        assertTrue(guard.isCurrent(connectedLineage))

        guard.advanceLineage()
        assertFalse(guard.isCurrent(connectedLineage))

        val reconnectedLineage = guard.advanceLineage()
        assertTrue(guard.isCurrent(reconnectedLineage))
        assertFalse(guard.isCurrent(connectedLineage))
    }
}
