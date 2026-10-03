package com.hugr.wearos

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedNormalStartupGateTest {
    @Test
    fun `ordinary permissions and services remain blocked while fresh scope is deferred or active`() {
        val gate = BoundedNormalStartupGate()

        assertEquals(BoundedNormalStartupGate.Phase.FRESH_SCOPE_DEFERRED, gate.deferFreshScope())
        assertFalse(gate.permitsPermissionPath())
        assertFalse(gate.permitsServiceStart())

        assertEquals(BoundedNormalStartupGate.Phase.FRESH_SCOPE_PREPARING, gate.beginFreshScope())
        assertFalse(gate.permitsPermissionPath())
        assertFalse(gate.permitsServiceStart())
    }

    @Test
    fun `only a fresh eligible scope opens the ordinary permission and service paths`() {
        val gate = BoundedNormalStartupGate()
        gate.deferFreshScope()
        gate.beginFreshScope()

        assertEquals(BoundedNormalStartupGate.Phase.FRESH_SCOPE_READY, gate.markFreshScopeReady())
        assertTrue(gate.permitsPermissionPath())
        assertTrue(gate.permitsServiceStart())
    }

    @Test
    fun `failed fresh scope fails closed and cannot start ordinary services`() {
        val gate = BoundedNormalStartupGate()
        gate.deferFreshScope()
        gate.beginFreshScope()

        assertEquals(BoundedNormalStartupGate.Phase.FAILED, gate.markFailed())
        assertFalse(gate.permitsPermissionPath())
        assertFalse(gate.permitsServiceStart())
    }

    @Test
    fun `invalid state transitions cannot silently grant startup readiness`() {
        val gate = BoundedNormalStartupGate()

        assertThrows(IllegalStateException::class.java) { gate.markFreshScopeReady() }
        gate.deferFreshScope()
        assertThrows(IllegalStateException::class.java) { gate.markFreshScopeReady() }
        gate.beginFreshScope()
        gate.markFailed()
        assertThrows(IllegalStateException::class.java) { gate.markFreshScopeReady() }
        assertFalse(gate.permitsServiceStart())
    }

    @Test
    fun `normal main start prepares an isolated journal in background before permissions and services`() {
        val source = sourceAt("MainActivity.kt")
        val begin = source.substring(
            source.indexOf("private fun beginStartupAfterFirstFrame()"),
            source.indexOf("private fun prepareFreshOrdinaryScopeInBackground()"),
        )
        val freshPreparation = source.substring(
            source.indexOf("private fun prepareFreshOrdinaryScopeInBackground()"),
            source.indexOf("private fun completeFreshOrdinaryScopeFailure"),
        )
        val serviceStart = source.substring(
            source.indexOf("private fun startAllServices()"),
            source.indexOf("private val causalUpdateReceiver"),
        )
        val permissionResult = source.substring(
            source.indexOf("override fun onRequestPermissionsResult("),
            source.indexOf("private fun startAllServices()"),
        )

        assertTrue(begin.contains("startupRecoveryGate.deferFreshScope()"))
        assertTrue(begin.contains("Thread({"))
        assertTrue(begin.contains("prepareFreshOrdinaryScopeInBackground()"))
        assertFalse(begin.contains("WatchCausalRuntime.recorder("))
        assertTrue(freshPreparation.contains("startupRecoveryGate.beginFreshScope()"))
        assertTrue(freshPreparation.contains("WatchSourceRuntime.journal(applicationContext)"))
        assertTrue(freshPreparation.contains("WatchCausalRuntime.recorder(applicationContext)"))
        assertTrue(freshPreparation.contains("!journal.preflight().eligible"))
        assertTrue(freshPreparation.contains("completeFreshOrdinaryScopeFailure"))
        assertTrue(freshPreparation.contains("startupRecoveryGate.markFreshScopeReady()"))
        assertTrue(freshPreparation.contains("NormalStartupStage.FRESH_SCOPE_READY"))
        assertTrue(freshPreparation.contains("requestForegroundPermissions()"))
        assertFalse(freshPreparation.contains("startAllServices()"))
        assertTrue(serviceStart.contains("!startupRecoveryGate.permitsServiceStart()"))
        assertTrue(serviceStart.contains("NormalStartupStage.SERVICE_START_BLOCKED"))
        assertTrue(permissionResult.contains("allPermissionsGranted(grantResults)"))
        assertTrue(permissionResult.contains("else onPermissionDenied()"))
        assertTrue(permissionResult.contains("NormalStartupStage.PERMISSION_DENIED"))
        assertFalse(permissionResult.contains("BACKGROUND_PERMISSION_REQUEST_CODE -> startAllServices()"))
    }

    @Test
    fun `frozen evidence boundaries remain unchanged`() {
        assertEquals(
            "24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361",
            sha256(sourceFile("SourceJournal.kt")),
        )

        val main = sourceAt("MainActivity.kt")
        val health = sourceAt("HealthSensorService.kt")
        val egress = sourceAt("EvidenceEgressActivity.kt")
        listOf("exact_range_readbacks", "EvidenceEgressActivation", "RetainedTimingExporter").forEach { forbidden ->
            assertFalse("ordinary main path must exclude $forbidden", main.contains(forbidden))
            assertFalse("health path must exclude $forbidden", health.contains(forbidden))
        }
        listOf("BoundedNormalStartupGate", "WatchSourceRuntime", "HealthSensorService", "MainActivity").forEach { forbidden ->
            assertFalse("explicit egress path must exclude $forbidden", egress.contains(forbidden))
        }
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
