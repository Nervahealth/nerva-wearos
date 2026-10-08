package com.hugr.wearos

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class SavedDiagnosticReportTest {
    private fun context() = DiagnosticContext(77, "0.77.0-durable-diagnostics-candidate", UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID(), 7, 8184, 8182, 8184, 517, 4096)
    private fun render(error: Throwable? = null) = SavedDiagnosticFormat.render(context(), DiagnosticStage.HISTORICAL_BOUND_VALIDATION,
        DiagnosticOutcome.EXCEPTION, error, 12345, 999)
    private fun source(name: String) = File("src/main/java/com/hugr/wearos/$name").readText()
    @Test fun `saved diagnostic survives store recreation with exact identity and checksum`() {
        val root = Files.createTempDirectory("diagnostic-test").toFile()
        try {
            val text = render(IllegalStateException("private payload not to be copied"))
            val file = SavedDiagnosticStore(root).save(text)
            assertEquals(text, SavedDiagnosticStore(root).read(file))
            assertTrue(text.contains("C_phone=8184 R_retained=8182 H_issuance=8184"))
            assertTrue(text.contains("stage=HISTORICAL_BOUND_VALIDATION"))
            assertTrue(text.contains("java.lang.IllegalStateException"))
        } finally { root.deleteRecursively() }
    }
    @Test fun `exception messages paths contacts tokens and sensor values never enter report`() {
        val failure = IllegalStateException("email a@b.com phone +4712345678 GH_TOKEN=secret /private/source HR=72")
        failure.stackTrace = arrayOf(StackTraceElement("com.hugr.wearos.BleGattService", "prepareSourceReplay", "/private/source/GH_TOKEN=secret", 1715))
        val text = render(failure)
        for (secret in listOf("a@b.com", "+4712345678", "GH_TOKEN", "/private/source", "HR=72")) assertFalse(text.contains(secret))
        assertTrue(text.contains("prepareSourceReplay:1715"))
    }
    @Test fun `cause and frame limits are explicit and bounded`() {
        var e: Throwable = IllegalArgumentException("hidden")
        repeat(10) { e = RuntimeException("hidden", e); e.stackTrace = Array(100) { StackTraceElement("com.hugr.wearos.A", "f", "file", it) } }
        val text = render(e)
        assertTrue(text.contains("cause chain truncated"))
        assertTrue(text.contains("frames truncated"))
        assertFalse(text.contains("hidden"))
        assertTrue(text.toByteArray().size <= SavedDiagnosticFormat.MAX_REPORT_BYTES)
    }
    @Test fun `maximum long frames stay in budget and still persist`() {
        var e: Throwable = RuntimeException("private")
        repeat(5) {
            e = RuntimeException("private", e)
            e.stackTrace = Array(100) { StackTraceElement("com.hugr." + "A".repeat(200), "m".repeat(200), "private", Int.MAX_VALUE) }
        }
        val text = render(e)
        assertTrue(text.contains("byte budget truncated"))
        assertTrue(text.toByteArray().size <= SavedDiagnosticFormat.MAX_REPORT_BYTES)
        val root = Files.createTempDirectory("diagnostic-max").toFile()
        try {
            val store = SavedDiagnosticStore(root)
            assertEquals(text, store.read(store.save(text)))
        } finally { root.deleteRecursively() }
    }
    @Test fun `generic stop and destroy preserve primary exception in one attempt report`() {
        val root = Files.createTempDirectory("diagnostic-primary").toFile()
        try {
            val store = SavedDiagnosticStore(root)
            repeat(8) {
                val id = UUID.randomUUID()
                val primary = render(IllegalArgumentException("hidden"))
                store.save(primary, id)
                store.save(SavedDiagnosticFormat.withTerminal(primary, DiagnosticStage.RECOVERY_STOP, DiagnosticOutcome.TRANSPORT_ABORT, 10), id)
                store.save(SavedDiagnosticFormat.withTerminal(primary, DiagnosticStage.SERVICE_DESTROY, DiagnosticOutcome.TRANSPORT_ABORT, 11), id)
            }
            assertEquals(8, store.reports().size)
            store.reports().forEach {
                val text = store.read(it)
                assertTrue(text.contains("java.lang.IllegalArgumentException"))
                assertTrue(text.contains("stage=HISTORICAL_BOUND_VALIDATION"))
                assertTrue(text.contains("terminalStage=SERVICE_DESTROY"))
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun `only diagnostic root excluded from cloud backup and transfer`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("@xml/diagnostic_backup_rules"))
        assertTrue(manifest.contains("@xml/diagnostic_extraction_rules"))
        for (f in listOf("diagnostic_backup_rules.xml", "diagnostic_extraction_rules.xml")) {
            val text = File("src/main/res/xml/$f").readText()
            assertTrue(text.contains("hugr_operational_diagnostics_v1/"))
            assertFalse(text.contains("build45_source_journal"))
            assertFalse(text.contains("frozen"))
        }
    }
    @Test fun `rotation touches only isolated completed reports not source or pending files`() {
        val root = Files.createTempDirectory("diagnostic-rotation").toFile()
        try {
            val untouched = File(root, "source.bin").apply { writeText("source preserved") }
            val pending = File(root, "${UUID.randomUUID()}.tmp").apply { writeText("incomplete") }
            repeat(12) { SavedDiagnosticStore(root).save(render()) }
            assertEquals(8, SavedDiagnosticStore(root).reports().size)
            assertEquals("source preserved", untouched.readText())
            assertEquals("incomplete", pending.readText())
            SavedDiagnosticStore(root).reports().forEach { SavedDiagnosticStore(root).read(it) }
        } finally { root.deleteRecursively() }
    }
    @Test fun `truncated or changed published report rejected without modifying it`() {
        val root = Files.createTempDirectory("diagnostic-corrupt").toFile()
        try {
            val store = SavedDiagnosticStore(root)
            val file = store.save(render())
            file.appendText("tampered")
            val before = file.readBytes()
            assertTrue(runCatching { store.read(file) }.isFailure)
            assertArrayEquals(before, file.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun `unpublished temporary file is never exposed as complete report`() {
        val root = Files.createTempDirectory("diagnostic-pending").toFile()
        try {
            File(root, "${UUID.randomUUID()}.tmp").writeText("half report")
            assertTrue(SavedDiagnosticStore(root).reports().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun `all stop outcomes distinguish completed ACK from failures`() {
        DiagnosticOutcome.entries.forEach { outcome ->
            val text = SavedDiagnosticFormat.render(context(), DiagnosticStage.RECOVERY_STOP, outcome, null, 0, 0)
            assertTrue(text.contains("outcome=${outcome.name}"))
            assertTrue(text.contains("NOT recording verification"))
        }
    }
    @Test fun `viewer and report runtime never open journals services or network`() {
        for (name in listOf("SavedDiagnosticActivity.kt", "WatchDiagnosticRuntime.kt", "SavedDiagnosticReport.kt")) {
            val s = source(name)
            listOf("WatchSourceRuntime", "WatchCausalRuntime", "startService(", "startForegroundService(", "HttpURLConnection", "SmsManager", "mailto:", "readRecordsAfter", "delivery_states.log").forEach {
                assertFalse("$name must exclude $it", s.contains(it))
            }
        }
        val viewer = source("SavedDiagnosticActivity.kt")
        assertFalse(viewer.contains("fullScroll"))
        assertTrue(viewer.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        val paths = File("src/main/res/xml/diagnostic_paths.xml").readText()
        assertTrue(paths.contains("path=\"hugr_operational_diagnostics_v1/\""))
        assertFalse(paths.contains("root-path"))
        assertFalse(paths.contains("path=\".\""))
    }
    @Test fun `failure capture precedes abort and stop without suppressing original exception termination`() {
        val gatt = source("BleGattService.kt")
        val failure = gatt.substringAfter("private fun failSourceResumePreparation").substringBefore("private fun prepareSourceReplay")
        assertTrue(failure.indexOf("saveDiagnostic(") < failure.indexOf("abortTransportLineage("))
        val stop = gatt.substringAfter("private fun markFinalizedRecoveryStop").substringBefore("private fun stopFinalizedRecoveryForegroundLifetime")
        assertTrue(stop.contains("saveDiagnostic(DiagnosticStage.RECOVERY_STOP"))
        assertTrue(gatt.contains("DiagnosticStage.RESUME_APPLY"))
        assertTrue(gatt.contains("DiagnosticStage.HISTORICAL_BOUND_VALIDATION"))
        assertTrue(gatt.contains("DiagnosticStage.REPLAY_READ"))
        val runtime = source("WatchDiagnosticRuntime.kt")
        assertTrue(runtime.contains("previous.uncaughtException(thread, failure)"))
        assertTrue(runtime.contains("original failure handling continues"))
    }
}
