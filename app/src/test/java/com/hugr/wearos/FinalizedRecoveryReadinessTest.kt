package com.hugr.wearos

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizedRecoveryReadinessTest {
    @Test fun `historical ready and a destroyed service cannot make a new attempt ready`() {
        val status = FinalizedRecoveryReadinessStore(leaseMs = 2_000L)
        val oldAttempt = UUID.randomUUID()
        val oldService = UUID.randomUUID()
        status.request(oldAttempt)
        status.enter(oldAttempt, oldService)
        status.foreground(oldAttempt, oldService, 100L)
        status.advertising(oldAttempt, oldService, 101L)
        assertEquals(FinalizedRecoveryDisplay.READY, status.snapshot(oldAttempt, 102L).display)
        status.destroyed(oldAttempt, oldService)
        assertEquals(FinalizedRecoveryDisplay.STOPPED, status.snapshot(oldAttempt, 103L).display)
        assertEquals(FinalizedRecoveryStopReason.UNEXPLAINED_DESTROY, status.snapshot(oldAttempt, 103L).reason)

        val newAttempt = UUID.randomUUID()
        status.request(newAttempt)
        assertEquals(FinalizedRecoveryDisplay.PREPARING, status.snapshot(newAttempt, 104L).display)
        status.advertising(oldAttempt, oldService, 105L)
        assertFalse(status.snapshot(newAttempt, 105L).display == FinalizedRecoveryDisplay.READY)
    }

    @Test fun `readiness requires actual foreground promotion advertising and a fresh service heartbeat`() {
        val status = FinalizedRecoveryReadinessStore(leaseMs = 2_000L)
        val attempt = UUID.randomUUID()
        val service = UUID.randomUUID()
        status.request(attempt)
        status.enter(attempt, service)
        status.advertising(attempt, service, 99L)
        assertEquals(FinalizedRecoveryDisplay.PREPARING, status.snapshot(attempt, 100L).display)
        status.foreground(attempt, service, 100L)
        assertEquals(FinalizedRecoveryDisplay.READY, status.snapshot(attempt, 100L).display)
        status.heartbeat(attempt, service, 101L)
        assertEquals(FinalizedRecoveryDisplay.READY, status.snapshot(attempt, 2_101L).display)
        assertEquals(FinalizedRecoveryDisplay.NOT_CONFIRMED, status.snapshot(attempt, 2_102L).display)
        assertEquals(FinalizedRecoveryDisplay.NOT_CONFIRMED, FinalizedRecoveryReadinessStore().snapshot(attempt, 2_102L).display)
    }

    @Test fun `first explicit stop reason is retained before destruction and never mislabels Android cause`() {
        val status = FinalizedRecoveryReadinessStore()
        val attempt = UUID.randomUUID()
        val service = UUID.randomUUID()
        status.request(attempt)
        status.enter(attempt, service)
        assertTrue(status.stop(attempt, service, FinalizedRecoveryStopReason.GATT_READINESS_FAILED))
        assertFalse(status.stop(attempt, service, FinalizedRecoveryStopReason.DEADLINE))
        status.destroyed(attempt, service)
        assertEquals(FinalizedRecoveryDisplay.STOPPED, status.snapshot(attempt, 4L).display)
        assertEquals(FinalizedRecoveryStopReason.GATT_READINESS_FAILED, status.snapshot(attempt, 4L).reason)
        assertFalse(status.advertising(attempt, service, 5L))
        assertEquals(
            FinalizedRecoveryStopReason.UNEXPLAINED_DESTROY,
            FinalizedRecoveryStopReason.fromCode(FinalizedRecoveryStopReason.UNEXPLAINED_DESTROY.code),
        )
    }

    @Test fun `duplicate service under one attempt fails closed and old callbacks cannot affect a new attempt`() {
        val status = FinalizedRecoveryReadinessStore()
        val attempt = UUID.randomUUID()
        val old = UUID.randomUUID()
        val current = UUID.randomUUID()
        status.request(attempt)
        assertTrue(status.enter(attempt, old))
        assertFalse(status.enter(attempt, current))
        assertEquals(FinalizedRecoveryDisplay.STOPPED, status.snapshot(attempt, 1L).display)
        assertEquals(FinalizedRecoveryStopReason.ATTEMPT_REUSED, status.snapshot(attempt, 1L).reason)
        val newAttempt = UUID.randomUUID()
        status.request(newAttempt)
        assertTrue(status.enter(newAttempt, current))
        assertFalse(status.stop(attempt, old, FinalizedRecoveryStopReason.PHONE_DISCONNECTED))
        assertFalse(status.advertising(attempt, old, 1L))
        status.foreground(newAttempt, current, 2L)
        status.advertising(newAttempt, current, 3L)
        assertEquals(FinalizedRecoveryDisplay.READY, status.snapshot(newAttempt, 3L).display)
    }

    @Test fun `explicit stop marker and destruction reason survive recorder restart without free text`() {
        val root = Files.createTempDirectory("hugr-finalized-readiness-test-").toFile()
        val boot = UUID.randomUUID()
        val service = UUID.randomUUID()
        val recorder = CausalFlightRecorder(root, boot, UUID.randomUUID(), { 10L }, { 20L })
        requireNotNull(recorder.record(
            CausalEventCode.FINALIZED_RECOVERY_STOP_REQUESTED,
            CausalComponentCode.BLE,
            service,
            reasonCode = FinalizedRecoveryStopReason.FOREGROUND_PROMOTION_FAILED.code,
        ))
        requireNotNull(recorder.record(
            CausalEventCode.BLE_SERVICE_DESTROYED,
            CausalComponentCode.BLE,
            service,
            reasonCode = FinalizedRecoveryStopReason.FOREGROUND_PROMOTION_FAILED.code,
        ))
        recorder.close()
        val reopened = CausalFlightRecorder(root, boot, UUID.randomUUID(), { 30L }, { 40L })
        assertEquals(CausalRecorderIntegrity.OK, reopened.integrity())
        assertEquals(listOf(86, 78), reopened.events().map { it.code.wireCode })
        assertTrue(reopened.events().all {
            FinalizedRecoveryStopReason.fromCode(it.reasonCode) ==
                FinalizedRecoveryStopReason.FOREGROUND_PROMOTION_FAILED
        })
        assertTrue(File(root, CausalFlightRecorder.EVENTS_FILE_NAME).readText().contains("86"))
    }

    @Test fun `each explicit source stop label has a bounded reason rather than free text`() {
        listOf(
            "DEADLINE", "DEADLINE_BEFORE_CONNECTION", "INITIALIZATION_FAILED",
            "GATT_SERVICE_REGISTER_REJECTED", "GATT_SERVICE_FAILED", "ADVERTISING_FAILED",
            "GATT_READINESS_FAILED", "PHONE_DISCONNECTED", "RESUME_AFTER_DEADLINE_OR_DISCONNECT",
            "ACK_AFTER_DEADLINE_OR_DISCONNECT",
        ).forEach { label ->
            assertEquals(label, FinalizedRecoveryStopReason.fromStopLabel(label).name)
        }
        assertEquals(FinalizedRecoveryStopReason.TRANSPORT_ABORT,
            FinalizedRecoveryStopReason.fromStopLabel("notification_timeout"))
    }

    @Test fun `service start rejection without a service instance cannot leave ready behind`() {
        val status = FinalizedRecoveryReadinessStore()
        val attempt = UUID.randomUUID()
        status.request(attempt)
        assertTrue(status.rejectRequest(attempt))
        assertFalse(status.enter(attempt, UUID.randomUUID()))
        assertEquals(FinalizedRecoveryDisplay.STOPPED, status.snapshot(attempt, 1L).display)
        assertEquals(FinalizedRecoveryStopReason.SERVICE_START_REJECTED, status.snapshot(attempt, 1L).reason)
    }

    @Test fun `source wiring never reports historic readiness as live or completion before exact ACK`() {
        val main = source("MainActivity.kt")
        val gatt = source("BleGattService.kt")
        val render = main.substringAfter("private fun renderEvidence()").substringBefore("private fun Long?.orZero()")
        assertTrue(main.contains("BleGattService.EXTRA_FINALIZED_RECOVERY_ATTEMPT_ID"))
        assertTrue(render.contains("FinalizedRecoveryReadiness.snapshot"))
        assertTrue(render.contains("HISTORY — NOT LIVE READINESS"))
        assertTrue(gatt.contains("FinalizedRecoveryReadiness.heartbeat"))
        assertTrue(gatt.contains("CausalEventCode.FINALIZED_RECOVERY_STOP_REQUESTED"))
        assertTrue(gatt.contains("FinalizedRecoveryStopReason.EXACT_ACK_COMPLETED"))
        assertTrue(gatt.contains("stopFinalizedRecoveryWithoutAcknowledgement(\"ATTEMPT_REUSED\")"))
        val completion = gatt.substringAfter("private fun closeBoundedFreshRuntimeAfterDeliveryIfComplete()")
            .substringBefore("private fun pumpReplay()")
        assertTrue(completion.indexOf("NormalStartupStage.BOUNDED_RUN_DELIVERY_ACKNOWLEDGED") <
            completion.indexOf("FinalizedRecoveryStopReason.EXACT_ACK_COMPLETED"))
        val recoveryBranch = render.substringAfter("val gattReadiness = if (finalizedDeliveryRecoveryOnly) {")
            .substringBefore("} else when {")
        assertFalse(recoveryBranch.contains("latest(CausalEventCode.GATT_ADVERTISING_READY)"))
    }

    private fun source(name: String) = listOf(
        File("src/main/java/com/hugr/wearos/$name"),
        File("app/src/main/java/com/hugr/wearos/$name"),
    ).first { it.isFile }.readText()
}
