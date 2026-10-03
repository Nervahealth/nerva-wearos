package com.hugr.wearos

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceEgressSourceContractTest {
    private val egressSource by lazy { sourceAt("EvidenceEgress.kt") }
    private val activitySource by lazy { sourceAt("EvidenceEgressActivity.kt") }
    private val gattSource by lazy { sourceAt("BleGattService.kt") }
    private val volatileDiagnosticSource by lazy { sourceAt("EvidenceEgressVolatileDiagnostics.kt") }
    private val gattPolicySource by lazy { sourceAt("EvidenceEgressGattStartPolicy.kt") }
    private val manifestSource by lazy {
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
            .readText()
    }

    @Test
    fun `P1 locator is restricted to completed exact range packages with ZIP and matching sidecar`() {
        assertTrue(egressSource.contains("COMPLETED_ROOT_NAME = \"exact_range_readbacks\""))
        assertTrue(egressSource.contains("val expectedDirectoryName = \"$" + "completedId.exact-range\""))
        assertTrue(egressSource.contains("val archiveName = \"$" + "completedId.zip\""))
        assertTrue(egressSource.contains("val sidecarName = \"$" + "archiveName.sha256\""))
        assertTrue(egressSource.contains("children.size != 2"))
        assertTrue(egressSource.contains("SIDECAR_REJECTED"))
        assertTrue(egressSource.contains("canonicalFile"))
    }

    @Test
    fun `P2 explicit egress consent is a separate activity and only then requests egress-only GATT`() {
        assertTrue(manifestSource.contains("android:name=\".EvidenceEgressActivity\""))
        assertTrue(activitySource.contains("ACTIVATE READ-ONLY EGRESS"))
        assertTrue(activitySource.contains("activateAfterExplicitConsent"))
        assertTrue(activitySource.contains("EvidenceEgressGattStartPolicy.shouldStartEgressGatt(result)"))
        assertTrue(activitySource.contains("Intent(this, BleGattService::class.java)"))
        assertTrue(activitySource.contains("EvidenceEgressGattStartPolicy.ACTION_START_EGRESS_ONLY"))
        assertTrue(activitySource.contains("ContextCompat.startForegroundService("))
        assertTrue(activitySource.contains("PREPARING — dedicated read-only egress GATT starting"))
        assertTrue(activitySource.contains("READY — dedicated read-only egress advertiser available"))
        assertTrue(activitySource.contains("NOT READY —"))
        assertTrue(activitySource.contains("END READ-ONLY EGRESS SESSION"))
        assertTrue(activitySource.contains("EvidenceEgressGattStartPolicy.ACTION_STOP_EGRESS_ONLY"))
        assertFalse(activitySource.contains("MainActivity"))
        assertFalse(activitySource.contains("HealthSensorService"))
        assertFalse(gattSource.substringBefore("private val gattServerCallback").contains("EvidenceEgressActivation.activate("))
        assertFalse(gattSource.contains("onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {\n                    EvidenceEgressActivation.activate"))
    }

    @Test
    fun `activation controls and status remain reachable after a completed ID is entered`() {
        assertTrue(activitySource.contains("ScrollView(this)"))
        assertTrue(activitySource.contains("isFillViewport = true"))
        assertTrue(activitySource.contains("addView(layout)"))
        assertTrue(activitySource.contains("setContentView(scrollRoot)"))
        assertFalse(activitySource.contains("setContentView(layout)"))
        assertTrue(activitySource.contains("ACTIVATE READ-ONLY EGRESS"))
        assertTrue(activitySource.contains("INACTIVE — no egress session"))
        assertTrue(activitySource.contains("activateAfterExplicitConsent()"))
    }

    @Test
    fun `volatile egress diagnostics expose lifecycle markers without a new transport or durable storage path`() {
        assertTrue(egressSource.contains("EvidenceEgressVolatileDiagnostics.beginActivation()"))
        assertTrue(activitySource.contains("EvidenceEgressVolatileDiagnosticFormatter.format"))
        assertTrue(activitySource.contains("refreshVolatileDiagnostic()"))
        assertTrue(activitySource.contains("EvidenceEgressVolatileDiagnostics.observe"))
        assertTrue(activitySource.contains("stopVolatileDiagnosticObservation"))
        listOf(
            "onRuntimeInitialized()",
            "onRuntimeDestroyed()",
            "onGattServiceAdded(status)",
            "beginConnectionAttempt(status, newState)",
            "onConnectionStateChange(status, newState)",
            "onMtuChanged(negotiatedMtu)",
            "onOfferReadEntered(requestId)",
            "onOfferResponse(responseStatus, sent)",
            "onForegroundStarted()",
            "onForegroundStopped()",
            "onAdvertiserStarted()",
            "onAdvertiserStartFailed(",
        ).forEach { required -> assertTrue("missing volatile GATT marker: $required", gattSource.contains(required)) }
        assertTrue(volatileDiagnosticSource.contains("EvidenceEgressVolatileConnectionAttempt"))
        assertTrue(volatileDiagnosticSource.contains("CURRENT EGRESS ATTEMPT"))
        assertTrue(volatileDiagnosticSource.contains("offer callback:"))
        assertTrue(volatileDiagnosticSource.contains("offer response:"))
        assertTrue(volatileDiagnosticSource.contains("last disconnect:"))
        assertTrue(volatileDiagnosticSource.contains("EvidenceEgressReadiness"))
        assertTrue(volatileDiagnosticSource.contains("readiness: ${'$'}readiness"))
        listOf(
            "java.io.File",
            "SharedPreferences",
            "SQLite",
            "writeBytes",
            "readBytes",
            "BluetoothGattCharacteristic",
            "offerBytes()",
            "selectedChunkBytes()",
            "receiptBytes()",
            "startService(",
            "startForegroundService(",
            "Handler",
            "Timer",
        ).forEach { forbidden -> assertFalse("forbidden volatile diagnostic token: $forbidden", volatileDiagnosticSource.contains(forbidden)) }
        assertTrue(volatileDiagnosticSource.contains("Process-memory-only"))
        assertTrue(volatileDiagnosticSource.contains("Not a transfer or verification result"))
    }

    @Test
    fun `egress-only foreground lifetime is connected-device scoped and excludes standard runtime`() {
        assertTrue(manifestSource.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
        assertTrue(manifestSource.contains("android:foregroundServiceType=\"connectedDevice\""))
        assertTrue(gattSource.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE"))
        assertTrue(gattSource.contains("private fun startEgressForegroundLifetime"))
        assertTrue(gattSource.contains("private fun stopEgressForegroundLifetime()"))
        assertTrue(gattSource.contains("if (requestedMode == EvidenceEgressGattRuntimeMode.EGRESS_ONLY)"))
        assertTrue(gattSource.contains("if (isEgressOnlyRuntime()) stopEgressForegroundLifetime()"))
        val egressOnlyInitialization = gattSource.substringAfter("} else {")
            .substringBefore("private fun isEgressOnlyRuntime")
        listOf(
            "WatchSourceRuntime.journal",
            "registerSensorReceivers()",
            "healthHandler.post",
            "transportHandler.post",
            "initializeVibrator()",
            "initializeHapticNotificationChannel()",
        ).forEach { forbidden ->
            assertFalse("egress-only foreground start must exclude $forbidden", egressOnlyInitialization.contains(forbidden))
        }
    }

    @Test
    fun `egress-only GATT registers only dedicated egress characteristics and bypasses standard runtime`() {
        assertTrue(gattPolicySource.contains("ACTION_START_EGRESS_ONLY"))
        assertTrue(gattPolicySource.contains("EvidenceEgressGattRuntimeMode.EGRESS_ONLY"))
        val egressOnlyServiceBranch = gattSource.substringAfter("if (isEgressOnlyRuntime()) {")
            .substringBefore("// EDA Characteristic")
        assertTrue(egressOnlyServiceBranch.contains("addEgressCharacteristics(service)"))
        assertTrue(egressOnlyServiceBranch.contains("registerGattService(service)"))
        assertTrue(egressOnlyServiceBranch.contains("return"))
        listOf(
            "sourceRecordCharacteristic",
            "sourceControlCharacteristic",
            "HAPTIC_CHARACTERISTIC_UUID",
            "EDA_CHARACTERISTIC_UUID",
            "PPG_CHARACTERISTIC_UUID",
            "ACCEL_CHARACTERISTIC_UUID",
            "SKIN_TEMP_CHARACTERISTIC_UUID",
            "STATUS_CHARACTERISTIC_UUID",
        ).forEach { forbidden ->
            assertFalse("egress-only GATT characteristic branch must exclude $forbidden", egressOnlyServiceBranch.contains(forbidden))
        }
        assertTrue(gattSource.contains("Evidence Egress-only BLE GATT runtime initialized"))
        assertTrue(gattSource.contains("Evidence Egress-only phone connected"))
        assertTrue(gattSource.contains("Evidence Egress-only negotiated GATT MTU"))
        val egressOnlyInitialization = gattSource.substringAfter("} else {")
            .substringBefore("private fun isEgressOnlyRuntime")
        listOf(
            "WatchSourceRuntime.journal",
            "registerSensorReceivers()",
            "healthHandler.post",
            "transportHandler.post",
            "initializeVibrator()",
            "initializeHapticNotificationChannel()",
        ).forEach { forbidden ->
            assertFalse("egress-only initialization must exclude $forbidden", egressOnlyInitialization.contains(forbidden))
        }
    }

    @Test
    fun `P3 egress source has no protected generator exporter journal or exact root mutations`() {
        listOf(
            "ExactRangeReadbackGenerator",
            "RetainedTimingExporter",
            "SourceJournal",
            "Files.move",
            ".moveTo(",
            ".renameTo(",
            ".delete(",
            "deleteRecursively(",
            ".mkdir(",
            ".mkdirs(",
            "cleanUp(",
            "migration",
        ).forEach { forbidden ->
            assertFalse("forbidden egress source token: $forbidden", egressSource.contains(forbidden))
            assertFalse("forbidden egress activity token: $forbidden", activitySource.contains(forbidden))
        }
        assertFalse(egressSource.contains("readBytes()"))
        assertTrue(egressSource.contains("sha256File(archive)"))
        assertTrue(egressSource.contains("WATCH_ORIGINAL_MISMATCH"))
    }

    @Test
    fun `P4 dedicated GATT UUIDs sit beside telemetry without source record control reuse`() {
        listOf(
            "EGRESS_OFFER_CHARACTERISTIC_UUID",
            "EGRESS_CHUNK_CHARACTERISTIC_UUID",
            "EGRESS_CONTROL_CHARACTERISTIC_UUID",
            "EGRESS_RECEIPT_CHARACTERISTIC_UUID",
        ).forEach { required -> assertTrue("missing dedicated egress UUID: $required", gattSource.contains(required)) }
        assertTrue(gattSource.contains("Evidence Egress v1 is an explicit pull protocol"))
        assertTrue(gattSource.contains("EGRESS_CONTROL_CHARACTERISTIC_UUID && value != null"))
        assertTrue(gattSource.contains("EGRESS_CHUNK_CHARACTERISTIC_UUID -> EvidenceEgressActivation.selectedChunkBytes()"))
        assertFalse(gattSource.contains("SOURCE_CONTROL_CHARACTERISTIC_UUID && value != null) {\n                // A control write can only select a chunk"))
    }

    @Test
    fun `P5 protocol carries transfer identity roles ordering counts lengths hashes resume and receipt semantics`() {
        listOf(
            "transferId: UUID",
            "EvidenceEgressRole",
            "chunkIndex: Int",
            "chunkCount: Int",
            "artifactByteLength: Long",
            "artifactSha256: String",
            "identityBinding: String",
            "MINIMUM_GATT_MTU = 128",
            "resume(role: EvidenceEgressRole)",
            "DUPLICATE_CHUNK_IDENTICAL",
            "CHUNK_ORDER_REJECTED",
            "PREMATURE_RECEIPT_REJECTED",
            "RECEIPT_MISMATCH_REJECTED",
            "RECEIPT_IDEMPOTENT",
        ).forEach { required -> assertTrue("missing egress protocol contract: $required", egressSource.contains(required)) }
        assertTrue(gattSource.contains("negotiatedMtu < EvidenceEgressContract.MINIMUM_GATT_MTU"))
        assertTrue(egressSource.contains("putIdentityBinding(output, offer.identityBinding)"))
        assertTrue(gattSource.contains("isEgressRead && offset != 0"))
        assertTrue(gattSource.contains("preparedWrite || offset != 0"))
    }

    @Test
    fun `P6 P7 retain pre post opaque hash verification and separate transport receipt`() {
        assertTrue(egressSource.contains("val snapshot = located.snapshot"))
        assertTrue(egressSource.contains("val postTransfer = locator.locate(snapshot.completedId).snapshot"))
        assertTrue(egressSource.contains("sameOpaquePackage(snapshot, postTransfer)"))
        assertTrue(egressSource.contains("RECEIPT_ACCEPTED"))
        assertTrue(egressSource.contains("WATCH_ORIGINAL_MISMATCH"))
        assertTrue(egressSource.contains("sidecarDeclaration[0] != archiveHash"))
    }

    @Test
    fun `egress never materializes a whole Watch artifact in memory`() {
        assertFalse(egressSource.contains(".readBytes()"))
        assertTrue(egressSource.contains("RandomAccessFile(artifact.file, \"r\")"))
        assertTrue(egressSource.contains("ByteArray(payloadLength)"))
        assertTrue(egressSource.contains("val buffer = ByteArray(8 * 1024)"))
    }

    private fun sourceAt(fileName: String): String = listOf(
        File("src/main/java/com/hugr/wearos/$fileName"),
        File("app/src/main/java/com/hugr/wearos/$fileName"),
    ).first { it.isFile }.readText()
}
