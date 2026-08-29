package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SourceMtuReadinessGateTest {
    @Test
    fun `connection starts at default MTU and blocks all source construction`() {
        val gate = SourceMtuReadinessGate()
        val lineage = gate.onConnected()

        assertEquals(20, gate.snapshot().attPayloadBytes)
        assertFalse(gate.snapshot().mtuCallbackReceived)
        assertFalse(gate.canConstructSourceFrames(lineage))
        assertNull(gate.takeReleaseIfReady(lineage))
    }

    @Test
    fun `resume before MTU readiness freezes one immutable replay window`() {
        val gate = SourceMtuReadinessGate()
        val lineage = gate.onConnected()
        val frozen = plan(accepted = 100L, highWater = 150L)

        assertEquals(ResumePreparationResult.PREPARED, gate.prepareResume(lineage, frozen))
        repeat(10) {
            assertEquals(PendingLiveReservationResult.ACCEPTED, gate.reservePendingLiveRecord(lineage))
        }

        assertEquals(ResumePreparationResult.DUPLICATE, gate.prepareResume(lineage, frozen))
        assertEquals(ResumePreparationResult.CONFLICT, gate.prepareResume(lineage, plan(accepted = 100L, highWater = 160L)))
        assertEquals(frozen, gate.snapshot().preparedResumePlan)
        assertEquals(10, gate.snapshot().pendingLiveRecordCount)
        assertFalse(gate.canConstructSourceFrames(lineage))
        assertNull(gate.takeReleaseIfReady(lineage))
    }

    @Test
    fun `unsafe MTU never releases or marks the plan released`() {
        val gate = SourceMtuReadinessGate()
        val lineage = gate.onConnected()
        gate.onSourceCccdChanged(lineage, enabled = true)
        gate.prepareResume(lineage, plan(accepted = 0L, highWater = 42L))

        gate.onMtuChanged(lineage, negotiatedMtu = 366)

        assertEquals(363, gate.snapshot().attPayloadBytes)
        assertFalse(gate.canConstructSourceFrames(lineage))
        assertFalse(gate.snapshot().released)
        assertNull(gate.takeReleaseIfReady(lineage))
    }

    @Test
    fun `source-safe MTU releases prepared work exactly once after all predicates`() {
        val gate = SourceMtuReadinessGate()
        val lineage = gate.onConnected()
        val frozen = plan(accepted = 5L, highWater = 55L)
        gate.prepareResume(lineage, frozen)
        repeat(7) { gate.reservePendingLiveRecord(lineage) }
        gate.onMtuChanged(lineage, negotiatedMtu = 367)

        assertFalse(gate.canConstructSourceFrames(lineage))
        assertNull(gate.takeReleaseIfReady(lineage))

        gate.onSourceCccdChanged(lineage, enabled = true)
        assertTrue(gate.canConstructSourceFrames(lineage))
        assertEquals(
            SourceReleaseDecision(lineage, frozen, pendingLiveRecordCount = 7),
            gate.takeReleaseIfReady(lineage),
        )
        assertTrue(gate.snapshot().released)
        assertNull(gate.takeReleaseIfReady(lineage))

        gate.onMtuChanged(lineage, negotiatedMtu = 517)
        gate.onSourceCccdChanged(lineage, enabled = true)
        assertNull(gate.takeReleaseIfReady(lineage))
    }

    @Test
    fun `unsafe MTU after release blocks framing without changing the frozen plan`() {
        val gate = SourceMtuReadinessGate()
        val lineage = gate.onConnected()
        val frozen = plan(accepted = 4L, highWater = 44L)
        gate.prepareResume(lineage, frozen)
        gate.onSourceCccdChanged(lineage, enabled = true)
        gate.onMtuChanged(lineage, negotiatedMtu = 517)
        assertEquals(SourceReleaseDecision(lineage, frozen, 0), gate.takeReleaseIfReady(lineage))

        gate.onMtuChanged(lineage, negotiatedMtu = 23)

        assertFalse(gate.canConstructSourceFrames(lineage))
        assertEquals(frozen, gate.snapshot().preparedResumePlan)
        assertTrue(gate.snapshot().released)
        assertEquals(20, gate.snapshot().attPayloadBytes)
    }

    @Test
    fun `predicate completion order does not change the frozen release`() {
        val frozen = plan(accepted = 9L, highWater = 99L)
        val orders = listOf(
            listOf("mtu", "cccd", "resume"),
            listOf("resume", "mtu", "cccd"),
            listOf("cccd", "resume", "mtu"),
        )

        orders.forEach { order ->
            val gate = SourceMtuReadinessGate()
            val lineage = gate.onConnected()
            order.forEach { event ->
                when (event) {
                    "mtu" -> gate.onMtuChanged(lineage, negotiatedMtu = 517)
                    "cccd" -> gate.onSourceCccdChanged(lineage, enabled = true)
                    "resume" -> assertEquals(ResumePreparationResult.PREPARED, gate.prepareResume(lineage, frozen))
                }
            }
            assertEquals(SourceReleaseDecision(lineage, frozen, 0), gate.takeReleaseIfReady(lineage))
            assertNull(gate.takeReleaseIfReady(lineage))
        }
    }

    @Test
    fun `disconnect invalidates stale callbacks and clears only volatile state`() {
        val gate = SourceMtuReadinessGate()
        val oldLineage = gate.onConnected()
        gate.prepareResume(oldLineage, plan(accepted = 0L, highWater = 10L))
        gate.reservePendingLiveRecord(oldLineage)

        val disconnectedGeneration = gate.onDisconnected()
        gate.onMtuChanged(oldLineage, negotiatedMtu = 517)
        gate.onSourceCccdChanged(oldLineage, enabled = true)

        assertEquals(disconnectedGeneration, gate.snapshot().lineageGeneration)
        assertFalse(gate.snapshot().connected)
        assertEquals(0, gate.snapshot().pendingLiveRecordCount)
        assertNull(gate.snapshot().preparedResumePlan)
        assertNull(gate.takeReleaseIfReady(oldLineage))
        assertEquals(ResumePreparationResult.STALE_LINEAGE, gate.prepareResume(oldLineage, plan(0L, 11L)))
        assertEquals(PendingLiveReservationResult.STALE_LINEAGE, gate.reservePendingLiveRecord(oldLineage))
    }

    @Test
    fun `reconnect requires a new resume and may capture a later high water`() {
        val gate = SourceMtuReadinessGate()
        val oldLineage = gate.onConnected()
        gate.prepareResume(oldLineage, plan(accepted = 0L, highWater = 100L))
        gate.onMtuChanged(oldLineage, negotiatedMtu = 517)
        gate.onSourceCccdChanged(oldLineage, enabled = true)

        gate.onDisconnected()
        val newLineage = gate.onConnected()
        gate.onMtuChanged(newLineage, negotiatedMtu = 517)
        gate.onSourceCccdChanged(newLineage, enabled = true)

        assertNull(gate.takeReleaseIfReady(newLineage))
        val later = plan(accepted = 0L, highWater = 140L)
        assertEquals(ResumePreparationResult.PREPARED, gate.prepareResume(newLineage, later))
        assertEquals(SourceReleaseDecision(newLineage, later, 0), gate.takeReleaseIfReady(newLineage))
        assertNull(gate.takeReleaseIfReady(oldLineage))
    }

    @Test
    fun `pre-release pending live record ceiling is bounded without silent growth`() {
        val gate = SourceMtuReadinessGate(maxPendingLiveRecords = 1_280)
        val lineage = gate.onConnected()

        repeat(1_280) {
            assertEquals(PendingLiveReservationResult.ACCEPTED, gate.reservePendingLiveRecord(lineage))
        }
        assertEquals(PendingLiveReservationResult.CAPACITY_EXCEEDED, gate.reservePendingLiveRecord(lineage))
        assertEquals(1_280, gate.snapshot().pendingLiveRecordCount)
    }

    private fun plan(accepted: Long, highWater: Long) = PreparedSourceResumePlan(
        watchBootSessionId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
        acceptedRecordIndex = accepted,
        replayHighWaterRecordIndex = highWater,
        replayBacklogCount = highWater - accepted,
    )
}
