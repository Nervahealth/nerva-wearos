package com.hugr.wearos

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Disposable Linux fixture reproduces user-zero app-directory aliases, not physical Android storage. */
class FreshRunAndroidPathTest {
    @Test fun `raw Android app alias fails receipt lookup before source admission`() {
        val fixture = Files.createTempDirectory("fresh-android-path-before-").toFile()
        try {
            val data = File(fixture, "data/data").apply { mkdirs() }
            val users = File(fixture, "data/user").apply { mkdirs() }
            Files.createSymbolicLink(File(users, "0").toPath(), data.toPath())
            val app = File(users, "0/com.hugr.wearos/no_backup").apply { mkdirs() }
            val store = FreshRunReceiptStore(File(app, FreshRunRuntime.DIRECTORY))
            try { store.current(); fail("Raw aliased Context path must reproduce strict rejection") }
            catch (e: IllegalArgumentException) { assertEquals("Symbolic receipt path", e.message) }
            assertFalse(File(app, FreshRunRuntime.DIRECTORY).exists())
        } finally { fixture.deleteRecursively() }
    }
    @Test fun `resolved trusted anchor admits real journal and reopens same receipt without accepting child symlinks`() {
        val fixture = Files.createTempDirectory("fresh-android-path-after-").toFile()
        try {
            val data = File(fixture, "data/data").apply { mkdirs() }
            val users = File(fixture, "data/user").apply { mkdirs() }
            Files.createSymbolicLink(File(users, "0").toPath(), data.toPath())
            val app = File(users, "0/com.hugr.wearos/no_backup").apply { mkdirs() }
            val root = FreshRunRuntime.receiptRootAtTrustedAnchor(app)
            assertEquals(File(app.canonicalFile, FreshRunRuntime.DIRECTORY), root)
            val store = FreshRunReceiptStore(root)
            assertNull(store.current())
            val run = UUID.randomUUID()
            val journal = SourceJournal(store.sourceRoot(run), 10, { Long.MAX_VALUE }, { 1_800_000_000_000L })
            try {
                val initial = FreshRunReceipt(journal.watchBootSessionId, run,
                    startWallMs = 1_800_000_000_000L, startElapsedMs = 10L, bootCount = 10)
                store.createInExistingNewRun(initial)
                assertArrayEquals(initial.bytes(), FreshRunReceiptStore(root).current()!!.bytes())
                assertEquals(0L, journal.latestRecordIndex())
            } finally { journal.close() }
            val foreign = File(fixture, "foreign").apply { mkdirs() }
            val child = File(root, UUID.randomUUID().toString())
            Files.createSymbolicLink(child.toPath(), foreign.toPath())
            try { store.sourceRoot(UUID.fromString(child.name)); fail("Child symlink accepted") }
            catch (_: IllegalArgumentException) {}
            val noBackupLink = File(fixture, "no-backup-link")
            Files.createSymbolicLink(noBackupLink.toPath(), app.canonicalFile.toPath())
            try { FreshRunRuntime.receiptRootAtTrustedAnchor(noBackupLink); fail("Direct anchor symlink accepted") }
            catch (_: IllegalArgumentException) {}
        } finally { fixture.deleteRecursively() }
    }
}
