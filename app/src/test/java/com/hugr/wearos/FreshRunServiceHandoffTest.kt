package com.hugr.wearos

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class FreshRunServiceHandoffTest {
    private class Rig {
        val root = Files.createTempDirectory("fresh-handoff-").toFile()
        val store = FreshRunReceiptStore(File(root, "fresh_ordinary_runs_v3"))
        val run = UUID.randomUUID()
        val journal = SourceJournal(store.sourceRoot(run), 7, { Long.MAX_VALUE }, { 1_800_000_000_000L })
        val initial = FreshRunReceipt(journal.watchBootSessionId, run, startWallMs = 1_800_000_000_000L, startElapsedMs = 1000, bootCount = 7)
        val session: FreshRunSession
        init { store.createInExistingNewRun(initial); session = FreshRunSession(journal, store, initial, { 1000 }, { 1_800_000_000_000L }) }
        fun close() { journal.close(); root.deleteRecursively() }
    }
    private fun reject(block: () -> Unit) { try { block(); fail("Expected invalid handoff to fail closed") } catch (_: IllegalStateException) {} }

    @Test fun `admission and begin alone do not acknowledge a timer and arm does not fabricate receipt evidence`() {
        val r = Rig()
        try {
            val before = r.session.snapshot().bytes()
            assertEquals(r.run, r.session.runId)
            assertFalse(r.session.isRecordingStopScheduled())
            reject { r.session.recordingStopScheduled() }
            assertTrue(r.session.begin())
            assertFalse(r.session.isRecordingStopScheduled())
            r.session.recordingStopScheduled()
            assertTrue(r.session.isRecordingStopScheduled())
            assertArrayEquals(before, r.session.snapshot().bytes())
            assertEquals(0L, r.journal.latestRecordIndex())
            reject { r.session.recordingStopScheduled() }
        } finally { r.close() }
    }
    @Test fun `failed or quiesced session cannot acknowledge new timer startup`() {
        val r = Rig()
        try {
            assertTrue(r.session.begin())
            r.session.quiesce()
            reject { r.session.recordingStopScheduled() }
            r.session.fail("HEALTH_START_TIMEOUT")
            reject { r.session.recordingStopScheduled() }
            assertFalse(r.session.isRecordingStopScheduled())
            assertFalse(r.session.acceptsSamples())
            assertEquals("HEALTH_START_TIMEOUT", r.session.snapshot().failure)
            assertEquals(0L, r.journal.latestRecordIndex())
        } finally { r.close() }
    }
    @Test fun `restart cannot inherit in-process timer acknowledgement from saved admission`() {
        val r = Rig()
        try {
            assertTrue(r.session.begin()); r.session.recordingStopScheduled()
            val before = r.session.snapshot()
            val reloaded = FreshRunSession(r.journal, r.store, before, { 1000 }, { 1_800_000_000_000L })
            assertFalse(reloaded.isRecordingStopScheduled())
            assertFalse(reloaded.acceptsSamples())
            assertArrayEquals(before.bytes(), reloaded.snapshot().bytes())
            reject { reloaded.recordingStopScheduled() }
        } finally { r.close() }
    }
}
