package com.hugr.wearos

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Admission contracts for the prospective ordinary-runtime/Gate-1 Watch line.
 *
 * These contracts do not start a service, access Bluetooth, touch an evidence
 * root, or exercise a package. They pin the normal and explicit-egress paths
 * to their separately governed source responsibilities.
 */
class OrdinaryRuntimeCandidateContractTest {
    private val mainSource by lazy { sourceAt("MainActivity.kt") }
    private val healthSource by lazy { sourceAt("HealthSensorService.kt") }
    private val gattSource by lazy { sourceAt("BleGattService.kt") }
    private val egressActivitySource by lazy { sourceAt("EvidenceEgressActivity.kt") }
    private val egressPolicySource by lazy { sourceAt("EvidenceEgressGattStartPolicy.kt") }
    private val manifestSource by lazy {
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
            .readText()
    }

    @Test
    fun `O3 W1 ordinary launcher starts the standard GATT and health services only`() {
        val normalStartup = between(mainSource, "private fun startAllServices()", "private val causalUpdateReceiver")
        assertTrue(normalStartup.contains("startService(Intent(this, BleGattService::class.java))"))
        assertTrue(normalStartup.contains("HealthSensorService.ACTION_START_TRACKING"))
        assertTrue(normalStartup.contains("startForegroundService(sensorIntent)"))
        assertFalse(normalStartup.contains("ACTION_START_EGRESS_ONLY"))
        assertFalse(normalStartup.contains("EvidenceEgressActivity"))
        assertFalse(normalStartup.contains("EvidenceEgressActivation"))
        assertFalse(normalStartup.contains("startForegroundService(Intent(this, BleGattService"))
    }

    @Test
    fun `O3 W2 standard and egress GATT modes remain explicit and mutually exclusive`() {
        assertTrue(egressPolicySource.contains("STANDARD,"))
        assertTrue(egressPolicySource.contains("EGRESS_ONLY,"))
        assertTrue(egressPolicySource.contains("current == null || current == requested"))
        assertTrue(egressPolicySource.contains("ACTION_START_EGRESS_ONLY"))

        val gattStartup = between(gattSource, "override fun onStartCommand", "private fun initializeRuntime")
        assertTrue(gattStartup.contains("requestedMode = EvidenceEgressGattStartPolicy.requestedMode"))
        assertTrue(gattStartup.contains("!EvidenceEgressGattStartPolicy.acceptsModeTransition"))
        assertTrue(gattStartup.contains("requestedMode == EvidenceEgressGattRuntimeMode.EGRESS_ONLY"))
        assertTrue(gattStartup.contains("startEgressForegroundLifetime()"))
        assertFalse(gattStartup.contains("runtimeMode = EvidenceEgressGattRuntimeMode.EGRESS_ONLY\n            initializeRuntime"))
    }

    @Test
    fun `O3 W3 egress activation is separate explicit consent and cannot enter sensing`() {
        assertTrue(manifestSource.contains("android:name=\".EvidenceEgressActivity\""))
        assertTrue(egressActivitySource.contains("ACTIVATE READ-ONLY EGRESS"))
        assertTrue(egressActivitySource.contains("EvidenceEgressContract.isCompletedId"))
        assertTrue(egressActivitySource.contains("EvidenceEgressActivation.activate(filesDir, completedId)"))
        assertTrue(egressActivitySource.contains("EvidenceEgressGattStartPolicy.ACTION_START_EGRESS_ONLY"))
        listOf(
            "MainActivity",
            "HealthSensorService",
            "ACTION_START_TRACKING",
            "WatchSourceRuntime",
            "registerSensorReceivers",
            "startAllServices",
        ).forEach { forbidden ->
            assertFalse("explicit egress activity must exclude ordinary runtime token: $forbidden", egressActivitySource.contains(forbidden))
        }
    }

    @Test
    fun `O3 W4 standard execution and health paths exclude the frozen evidence root`() {
        val standardInitialization = between(gattSource, "if (mode == EvidenceEgressGattRuntimeMode.STANDARD)", "} else {")
        assertTrue(standardInitialization.contains("WatchSourceRuntime.journal(this)"))
        assertTrue(standardInitialization.contains("registerSensorReceivers()"))
        assertTrue(standardInitialization.contains("healthHandler.post(healthTicker)"))
        assertTrue(standardInitialization.contains("transportHandler.post(transportTicker)"))
        listOf(
            "exact_range_readbacks",
            "EvidenceEgressActivation",
            "EvidenceEgressContract",
            "EvidenceEgressRole",
            "selectedChunkBytes",
            "receiptBytes",
        ).forEach { forbidden ->
            assertFalse("standard GATT initialization must exclude $forbidden", standardInitialization.contains(forbidden))
            assertFalse("health service must exclude $forbidden", healthSource.contains(forbidden))
            assertFalse("ordinary launcher must exclude $forbidden", mainSource.contains(forbidden))
        }
    }

    @Test
    fun `O3 W4a ordinary runtime owns fresh roots and leaves legacy evidence paths to their dedicated tools`() {
        val runtime = sourceAt("WatchSourceRuntime.kt")
        val causal = sourceAt("CausalFlightRecorder.kt")
        val scope = sourceAt("FreshOrdinaryRunScope.kt")

        assertTrue(runtime.contains("FreshOrdinaryRunScope.journalRoot"))
        assertTrue(causal.contains("FreshOrdinaryRunScope.causalRoot"))
        listOf("build45_source_journal", "exact_range_readbacks", "EvidenceEgressActivation").forEach { forbidden ->
            assertFalse("ordinary journal runtime must exclude $forbidden", runtime.contains(forbidden))
            assertFalse("ordinary causal runtime must exclude $forbidden", causal.contains(forbidden))
            assertFalse("fresh scope declaration must exclude $forbidden", scope.contains(forbidden))
        }
        assertTrue(egressActivitySource.contains("EvidenceEgressActivation.activate(filesDir, completedId)"))
    }

    @Test
    fun `O3 W5 protected frozen-evidence sources retain their admitted digests`() {
        assertEquals(
            "5464fac5cd399fa1627071f139e0724a8135bff5d40a34e9d34fb994c4448ca6",
            sha256(sourceFile("ExactRangeReadback.kt")),
        )
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

    private fun sourceFile(fileName: String): File = listOf(
        File("src/main/java/com/hugr/wearos/$fileName"),
        File("app/src/main/java/com/hugr/wearos/$fileName"),
    ).first { it.isFile }

    private fun sourceAt(fileName: String): String = sourceFile(fileName).readText()

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}
