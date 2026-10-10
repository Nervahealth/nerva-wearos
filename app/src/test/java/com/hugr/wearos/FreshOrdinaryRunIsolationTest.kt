package com.hugr.wearos

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshOrdinaryRunIsolationTest {
    @Test
    fun `fresh ordinary journal appends independently without changing legacy or frozen roots`() {
        val filesDir = Files.createTempDirectory("hugr-fresh-ordinary-root-").toFile()
        try {
            val legacyJournal = File(filesDir, "build45_source_journal").apply { mkdirs() }
            File(legacyJournal, "legacy-sentinel.seg").writeBytes(ByteArray(4_096) { 0x5a })
            val frozenPackage = File(filesDir, "exact_range_readbacks").apply { mkdirs() }
            File(frozenPackage, "frozen-sentinel.zip").writeBytes(ByteArray(8_192) { 0x23 })
            val legacyBefore = treeDigest(legacyJournal)
            val frozenBefore = treeDigest(frozenPackage)

            val freshRoot = FreshOrdinaryRunScope.journalRoot(filesDir)
            val journal = SourceJournal(
                rootDir = freshRoot,
                bootCount = 701,
                availableBytes = { Long.MAX_VALUE },
                nowWallMs = { 1_726_000_000_000L },
            )
            val record = journal.append(SourceStreamCode.ACCEL, 11L, byteArrayOf(1, 2, 3))
            journal.forceSync()

            assertEquals(1L, record.recordIndex)
            assertTrue(freshRoot.exists())
            assertNotEquals(legacyJournal.canonicalPath, freshRoot.canonicalPath)
            assertFalse(freshRoot.canonicalPath.startsWith(legacyJournal.canonicalPath + File.separator))
            assertEquals(legacyBefore, treeDigest(legacyJournal))
            assertEquals(frozenBefore, treeDigest(frozenPackage))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `fresh scope names do not alias retained or frozen evidence roots`() {
        assertEquals("fresh_ordinary_source_journal_v2", FreshOrdinaryRunScope.JOURNAL_DIRECTORY)
        assertEquals("fresh_ordinary_causal_flight_recorder_v2", FreshOrdinaryRunScope.CAUSAL_DIRECTORY)
        assertNotEquals("fresh_ordinary_source_journal_v1", FreshOrdinaryRunScope.JOURNAL_DIRECTORY)
        assertNotEquals("fresh_ordinary_causal_flight_recorder_v1", FreshOrdinaryRunScope.CAUSAL_DIRECTORY)
        assertNotEquals("build45_source_journal", FreshOrdinaryRunScope.JOURNAL_DIRECTORY)
        assertNotEquals("build47_causal_flight_recorder", FreshOrdinaryRunScope.CAUSAL_DIRECTORY)
        assertNotEquals("exact_range_readbacks", FreshOrdinaryRunScope.JOURNAL_DIRECTORY)
        assertNotEquals("exact_range_readbacks", FreshOrdinaryRunScope.CAUSAL_DIRECTORY)
    }

    @Test
    fun `ordinary startup names fresh scope and excludes full retained recovery`() {
        val main = sourceAt("MainActivity.kt")
        val runtime = sourceAt("WatchSourceRuntime.kt")
        val causal = sourceAt("CausalFlightRecorder.kt")
        val scope = sourceAt("FreshOrdinaryRunScope.kt")
        val freshPreparation = main.substring(
            main.indexOf("private fun prepareFreshOrdinaryScopeInBackground()"),
            main.indexOf("private fun completeFreshOrdinaryScopeFailure"),
        )

        assertTrue(main.contains("Thread({ prepareFreshOrdinaryScopeInBackground() }"))
        assertFalse(freshPreparation.contains("WatchSourceRuntime.journal("))
        assertFalse(freshPreparation.contains("WatchCausalRuntime.recorder("))
        assertTrue(main.contains("if (!freshRequested) return"))
        assertTrue(main.contains("FreshRunRuntime.admit(applicationContext)"))
        val freshRuntime = sourceAt("FreshRunRuntime.kt")
        assertTrue(freshRuntime.contains("noBackupFilesDir"))
        assertTrue(freshRuntime.contains("fresh_ordinary_runs_v3"))
        assertFalse(freshRuntime.contains("FreshOrdinaryRunScope"))
        assertTrue(freshPreparation.contains("requestForegroundPermissions()"))
        listOf(
            "Build46BaselineStore",
            "Build46SourceBaseline.from",
            "build45_source_journal",
            "build47_causal_flight_recorder",
            "exact_range_readbacks",
            "ExactRangeReadback",
            "RetainedTimingExporter",
        ).forEach { forbidden ->
            assertFalse("ordinary fresh preparation must exclude $forbidden", freshPreparation.contains(forbidden))
        }
        assertTrue(runtime.contains("FreshOrdinaryRunScope.journalRoot"))
        assertTrue(causal.contains("FreshOrdinaryRunScope.causalRoot"))
        listOf("build45_source_journal", "exact_range_readbacks").forEach { forbidden ->
            assertFalse("new ordinary runtime root must exclude $forbidden", runtime.contains(forbidden))
            assertFalse("new causal root must exclude $forbidden", causal.contains(forbidden))
            assertFalse("scope declaration must exclude $forbidden", scope.contains(forbidden))
        }
    }

    @Test
    fun `frozen source journal implementation remains byte-identical`() {
        assertEquals(
            "24d369f82440781b5846f5b609f459df7a5d7cb49bd794726ff7f15cc50d0361",
            sha256(sourceFile("SourceJournal.kt")),
        )
    }

    private fun treeDigest(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        root.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(root).path }.forEach { file ->
            digest.update(file.relativeTo(root).path.toByteArray())
            digest.update(0)
            digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
