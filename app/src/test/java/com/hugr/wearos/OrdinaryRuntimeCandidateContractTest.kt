package com.hugr.wearos

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Admission contracts for the temporary ordinary-only Watch line.
 *
 * The unavailable exact-readback source is not recreated. The normal path is
 * constrained to its fresh root; the existing egress binary is not exercised
 * by these contracts or by the ordinary recording admission.
 */
class OrdinaryRuntimeCandidateContractTest {
    private val mainSource by lazy { sourceAt("MainActivity.kt") }
    private val healthSource by lazy { sourceAt("HealthSensorService.kt") }
    private val gattSource by lazy { sourceAt("BleGattService.kt") }
    private val runtimeSource by lazy { sourceAt("WatchSourceRuntime.kt") }
    private val scopeSource by lazy { sourceAt("FreshOrdinaryRunScope.kt") }
    private val manifestSource by lazy {
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
            .readText()
    }

    @Test
    fun `ordinary launcher starts standard GATT and bounded health service only`() {
        val normalStartup = between(mainSource, "private fun startAllServices()", "private val causalUpdateReceiver")
        assertTrue(normalStartup.contains("startService(Intent(this, BleGattService::class.java))"))
        assertTrue(normalStartup.contains("HealthSensorService.ACTION_START_BOUNDED_ORDINARY_RUN"))
        assertTrue(normalStartup.contains("BoundedOrdinaryRunPolicy.SHORT_ADMISSION_DURATION_MS"))
        assertTrue(normalStartup.contains("startForegroundService(sensorIntent)"))
        assertFalse(normalStartup.contains("ACTION_START_EGRESS_ONLY"))
        assertFalse(normalStartup.contains("EvidenceEgressActivation"))
    }

    @Test
    fun `ordinary runtime owns v2 fresh roots and excludes legacy frozen roots`() {
        assertTrue(runtimeSource.contains("FreshOrdinaryRunScope.journalRoot"))
        assertTrue(scopeSource.contains("fresh_ordinary_source_journal_v2"))
        assertTrue(scopeSource.contains("fresh_ordinary_causal_flight_recorder_v2"))
        listOf("build45_source_journal", "exact_range_readbacks", "EvidenceEgressActivation").forEach { forbidden ->
            assertFalse("ordinary runtime must exclude $forbidden", runtimeSource.contains(forbidden))
            assertFalse("fresh scope declaration must exclude $forbidden", scopeSource.contains(forbidden))
            assertFalse("ordinary health path must exclude $forbidden", healthSource.contains(forbidden))
        }
    }

    @Test
    fun `bounded lifecycle quiesces then finalizes only the fresh journal`() {
        assertTrue(healthSource.contains("ACTION_START_BOUNDED_ORDINARY_RUN"))
        assertTrue(healthSource.contains("boundedRunGate.requestStop()"))
        assertTrue(healthSource.contains("stopTrackingAndDisconnect()"))
        assertTrue(healthSource.contains("journal.forceSync()"))
        assertTrue(healthSource.contains("journal.finalizeActiveSegment()"))
        assertTrue(healthSource.contains("ACTION_SOURCE_FINALIZED"))
        assertTrue(healthSource.contains("if (!boundedRunGate.acceptsSamples()) return null"))
        assertTrue(gattSource.contains("ACTION_SOURCE_FINALIZED"))
        assertTrue(gattSource.contains("closeBoundedFreshRuntimeAfterDeliveryIfComplete"))
        assertTrue(gattSource.contains("WatchSourceRuntime.closeFreshAfterDelivery()"))

        val finalizer = between(healthSource, "private fun finalizeBoundedOrdinaryRun()", "private fun cancelBoundedRunStop()")
        assertInOrder(
            finalizer,
            "boundedRunGate.requestStop()",
            "stopTrackingAndDisconnect()",
            "journal.forceSync()",
            "journal.finalizeActiveSegment()",
            "NormalStartupStage.BOUNDED_RUN_FINALIZED",
            "ACTION_SOURCE_FINALIZED",
        )
        val closeAfterAck = between(
            gattSource,
            "private fun closeBoundedFreshRuntimeAfterDeliveryIfComplete()",
            "private fun pumpReplay()",
        )
        assertInOrder(
            closeAfterAck,
            "NormalStartupStage.BOUNDED_RUN_DELIVERY_ACKNOWLEDGED",
            "WatchSourceRuntime.closeFreshAfterDelivery()",
            "NormalStartupStage.BOUNDED_RUN_GATT_STOP_REQUESTED",
            "stopSelf()",
        )
        assertTrue(closeAfterAck.contains("if (sourceJournal.finalizedManifests().isNotEmpty()) return"))

        val acknowledgement = between(
            gattSource,
            "private fun handleSourceAcknowledgement",
            "private fun closeBoundedFreshRuntimeAfterDeliveryIfComplete",
        )
        assertInOrder(
            acknowledgement,
            "SourceReplayWindow.validateAcknowledgement",
            "SourceReplayWindow.validateQueuedManifestAcknowledgement",
            "sourceJournal.acknowledgeCompletedSegment",
            "closeBoundedFreshRuntimeAfterDeliveryIfComplete",
        )

        val finalManifestDelivery = between(
            gattSource,
            "private fun enqueueNewlyFinalizedManifests()",
            "private fun handleSourceResume",
        )
        assertTrue(finalManifestDelivery.contains("SourceReplayWindow.planFinalizedManifestDelivery"))
        assertTrue(finalManifestDelivery.contains("enqueueNextManifestForReplayWindow"))
        assertFalse(finalManifestDelivery.contains("notificationQueue.enqueue("))
        assertInOrder(
            finalManifestDelivery,
            "sourceJournal.drainNewlyFinalizedManifests()",
            "SourceReplayWindow.planFinalizedManifestDelivery",
            "replayHighWaterRecordIndex = plan.replayHighWaterRecordIndex",
            "enqueueNextManifestForReplayWindow",
        )
        val replayQueue = between(
            gattSource,
            "private fun enqueueNextManifestForReplayWindow",
            "private fun handleSourceAcknowledgement",
        )
        assertTrue(replayQueue.contains("SourceReplayWindow.nextManifestToQueue"))
        assertTrue(replayQueue.contains("sourceJournal.finalizedManifests(session)"))
        assertFalse(replayQueue.contains("sourceJournal.nextFinalizedManifest("))
    }

    @Test
    fun `standard GATT retains explicit separate egress mode without ordinary activation`() {
        assertTrue(gattSource.contains("EvidenceEgressGattRuntimeMode.STANDARD"))
        assertTrue(gattSource.contains("EvidenceEgressGattRuntimeMode.EGRESS_ONLY"))
        assertTrue(gattSource.contains("EvidenceEgressGattStartPolicy.requestedMode"))
        assertTrue(gattSource.contains("!EvidenceEgressGattStartPolicy.acceptsModeTransition"))
        assertFalse(gattSource.substringBefore("private val gattServerCallback").contains("EvidenceEgressActivation.activate("))
    }

    @Test
    fun `unrecovered exact readback launcher is explicitly absent while surviving protected sources retain digests`() {
        assertFalse(manifestSource.contains(".ExactRangeReadbackActivity"))
        assertFalse(sourceFileOrNull("ExactRangeReadback.kt")?.isFile == true)
        assertEquals(
            "d8239b8ba59367221ff59446fc994b70e2e67835d221785eccc724397ae0fd36",
            sha256(sourceFile("RetainedTimingExporter.kt")),
        )
        assertEquals(
            "96cd6a3c7ae46ab5b1330a36170668e4d91c78d2d8779041ba129403be9de96d",
            sha256(sourceFile("RetainedTimingExportActivity.kt")),
        )
    }

    private fun between(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        check(startIndex >= 0) { "missing source start: $start" }
        val endIndex = source.indexOf(end, startIndex + start.length)
        check(endIndex > startIndex) { "missing source end: $end" }
        return source.substring(startIndex, endIndex)
    }

    private fun assertInOrder(source: String, vararg tokens: String) {
        var previous = -1
        tokens.forEach { token ->
            val found = source.indexOf(token)
            assertTrue("missing lifecycle token: $token", found >= 0)
            assertTrue("out-of-order lifecycle token: $token", found > previous)
            previous = found
        }
    }

    private fun sourceFile(fileName: String): File = sourceFileOrNull(fileName) ?: error("missing source: $fileName")

    private fun sourceFileOrNull(fileName: String): File? = listOf(
        File("src/main/java/com/hugr/wearos/$fileName"),
        File("app/src/main/java/com/hugr/wearos/$fileName"),
    ).firstOrNull { it.isFile }

    private fun sourceAt(fileName: String): String = sourceFile(fileName).readText()

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}
