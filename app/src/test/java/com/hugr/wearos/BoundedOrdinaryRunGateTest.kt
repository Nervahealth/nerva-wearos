package com.hugr.wearos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedOrdinaryRunGateTest {
    @Test
    fun `late samples are refused after the bounded run begins quiescing`() {
        val gate = BoundedOrdinaryRunGate()

        assertTrue(gate.begin())
        assertTrue(gate.acceptsSamples())
        assertTrue(gate.requestStop())
        assertFalse(gate.acceptsSamples())
        assertFalse("a repeated callback cannot reopen collection", gate.begin())
        assertTrue(gate.markFinalized())
        assertFalse(gate.acceptsSamples())
    }

    @Test
    fun `finalization can only follow quiescence and failure remains terminal`() {
        val gate = BoundedOrdinaryRunGate()

        assertFalse(gate.markFinalized())
        assertTrue(gate.begin())
        assertTrue(gate.requestStop())
        assertTrue(gate.markFinalizationFailed())
        assertFalse(gate.acceptsSamples())
        assertFalse(gate.markFinalized())
    }
}
