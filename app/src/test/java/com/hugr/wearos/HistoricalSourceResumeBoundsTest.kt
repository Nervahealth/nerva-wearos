package com.hugr.wearos

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class HistoricalSourceResumeBoundsTest {
    @get:Rule val temp = TemporaryFolder()
    private fun journal(root: File, boot: Int = 69) = SourceJournal(root, boot, { Long.MAX_VALUE }, { 1000L }, requiredFreeBytes = 1)
    private fun fixture(): Pair<File, UUID> {
        val root = temp.newFolder()
        val j = journal(root)
        repeat(4) { j.append(SourceStreamCode.ACCEL, it.toLong(), byteArrayOf(1)) }
        j.finalizeActiveSegment()
        repeat(2) { j.append(SourceStreamCode.DEVICE_HEALTH, it.toLong(), byteArrayOf(2)) }
        val final = requireNotNull(j.finalizeActiveSegment())
        assertTrue(j.acknowledgeCompletedSegment(j.watchBootSessionId, 6, final.sha256Hex))
        val session = j.watchBootSessionId
        j.close()
        return root to session
    }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("unsupported cursor must fail closed") } catch (_: SourceJournalCorruptionException) { }
    }
    @Test fun `same-session historical issuance justifies cursor after exact deletion and boot restart`() {
        val (root, session) = fixture()
        journal(root, 70).use { j ->
            val plan = HistoricalSourceResumeBounds.prepare(j, root, session, 6)
            assertEquals(6L, plan.acceptedRecordIndex)
            assertEquals(4L, plan.replayHighWaterRecordIndex)
            assertEquals(6L, plan.historicalSessionBound)
            assertEquals(0L, plan.replayBacklogCount)
            assertEquals(0L, j.countRecordsAfter(session, 6, 4))
            assertTrue(j.readRecordsAfter(session, 6, 4, 8).isEmpty())
            rejects { HistoricalSourceResumeBounds.prepare(j, root, session, 7) }
            rejects { HistoricalSourceResumeBounds.prepare(j, root, UUID.randomUUID(), 6) }
            rejects { HistoricalSourceResumeBounds.prepare(j, root, session, -1) }
            rejects { HistoricalSourceResumeBounds.prepare(j, root, session, Long.MAX_VALUE) }
        }
    }
    @Test fun `missing ledger cannot manufacture deleted provenance but retained cursor remains supported`() {
        val (root, session) = fixture()
        File(root, "delivery_states.log").delete()
        journal(root).use { j ->
            rejects { HistoricalSourceResumeBounds.prepare(j, root, session, 6) }
            assertEquals(0L, HistoricalSourceResumeBounds.prepare(j, root, session, 4).replayBacklogCount)
        }
    }
    @Test fun `gapped duplicate range malformed and incomplete issuance logs are rejected without mutation`() {
        for (kind in listOf("gap", "duplicate", "range", "malformed", "tail", "wrong-session")) {
            val (root, session) = fixture()
            val f = File(root, "delivery_states.log")
            val rows = f.readLines().toMutableList()
            when (kind) {
                "gap" -> rows.removeAt(2)
                "duplicate" -> rows.add(2, rows[1])
                "range" -> rows[0] = "$session\t1\t6\tBUFFERED\t1000"
                "malformed" -> rows.add("not-a-log-row")
                "wrong-session" -> rows[5] = "${UUID.randomUUID()}\t6\t6\tBUFFERED\t1000"
            }
            f.writeText(rows.joinToString("\n", postfix = if (kind == "tail") "" else "\n"))
            val before = root.listFiles()!!.filter { it.name.startsWith("segment_") }.associate { it.name to it.readBytes().toList() }
            journal(root).use { j -> rejects { HistoricalSourceResumeBounds.prepare(j, root, session, 6) } }
            assertEquals(before, root.listFiles()!!.filter { it.name.startsWith("segment_") }.associate { it.name to it.readBytes().toList() })
        }
    }
    @Test fun `bad retained anchor and numeric ledger overflow fail closed`() {
        val (root, session) = fixture()
        root.listFiles()!!.first { it.name.startsWith("segment_") }.appendBytes(byteArrayOf(1))
        journal(root).use { j -> rejects { HistoricalSourceResumeBounds.prepare(j, root, session, 6) } }
        val (other, otherSession) = fixture()
        File(other, "delivery_states.log").appendText("$otherSession\t9223372036854775807\t9223372036854775807\tBUFFERED\t1000\n")
        journal(other).use { j -> rejects { HistoricalSourceResumeBounds.prepare(j, other, otherSession, 6) } }
    }
    @Test fun `historical plan still waits for MTU CCCD and rejects a disconnected lineage`() {
        val (root, session) = fixture()
        journal(root).use { j ->
            val plan = HistoricalSourceResumeBounds.prepare(j, root, session, 6)
            val gate = SourceMtuReadinessGate()
            val lineage = gate.onConnected()
            assertEquals(ResumePreparationResult.PREPARED, gate.prepareResume(lineage, plan))
            assertNull(gate.takeReleaseIfReady(lineage))
            gate.onMtuChanged(lineage, 517)
            gate.onSourceCccdChanged(lineage, true)
            assertEquals(plan, gate.takeReleaseIfReady(lineage)?.preparedResumePlan)
            gate.onDisconnected()
            assertEquals(ResumePreparationResult.STALE_LINEAGE, gate.prepareResume(lineage, plan))
            assertFalse(gate.canConstructSourceFrames(lineage))
        }
    }
    @Test fun `historical bound cannot bypass deadline or exact ACK and data-empty is not completion`() {
        val (root, session) = fixture()
        journal(root, 70).use { j ->
            val plan = HistoricalSourceResumeBounds.prepare(j, root, session, 6)
            val manifest = requireNotNull(SourceReplayWindow.nextManifestToQueue(session, 6, 4, null, j.finalizedManifests(session)))
            assertEquals(0L, plan.replayBacklogCount)
            assertTrue(j.hasFinalizedSegments(session))
            val lifetime = FinalizedDeliveryLifetime()
            lifetime.start(1000L)
            assertFalse(lifetime.permitsDelivery(1000L + FinalizedDeliveryLifetime.MAX_DURATION_MS))
            assertTrue(j.hasFinalizedSegments(session))
            assertFalse(j.acknowledgeCompletedSegment(session, 4, "0".repeat(64)))
            assertFalse(j.acknowledgeCompletedSegment(UUID.randomUUID(), 4, manifest.sha256Hex))
            assertFalse(j.acknowledgeCompletedSegment(session, 3, manifest.sha256Hex))
            rejects {
                SourceReplayWindow.validateAcknowledgement(session, 6, 4,
                    SourceSegmentAcknowledgement(session, 6, manifest.sha256Hex), manifest.lastRecordIndex)
            }
            assertTrue(j.hasFinalizedSegments(session))
        }
    }
    @Test fun `both service entries and every downstream backlog use shared validated bounds`() {
        val source = File("src/main/java/com/hugr/wearos/BleGattService.kt").readText()
        val prepare = source.substringAfter("private fun prepareSourceReplay(").substringBefore("private fun applyPreparedSourceReplay")
        val begin = source.substringAfter("private fun beginSourceReplaySession(").substringBefore("private fun enqueueNextManifestForReplayWindow")
        assertTrue(prepare.contains("HistoricalSourceResumeBounds.prepare(sourceJournal, FreshOrdinaryRunScope.journalRoot(filesDir), session, acceptedIndex)"))
        assertTrue(begin.contains("HistoricalSourceResumeBounds.prepare(sourceJournal, FreshOrdinaryRunScope.journalRoot(filesDir), session, acceptedIndex)"))
        assertFalse(source.contains("replayHighWaterRecordIndex - durablePhoneRecordIndex"))
        assertFalse(source.contains("acceptedIndex > highWater"))
        assertFalse(source.contains("acceptedIndex > highestRecordIndex"))
        assertTrue(source.contains("if (!finalizedDeliveryRecoveryOnly) {\n                registerSensorReceivers()"))
        assertTrue(source.contains("if (finalizedDeliveryRecoveryOnly) return"))
        val completion = source.substringAfter("private fun closeBoundedFreshRuntimeAfterDeliveryIfComplete()")
            .substringBefore("val marker = NormalStartupMarkerStore(this).read()")
        assertTrue(completion.indexOf("sourceJournal.hasFinalizedSegments(sourceSessionId)") <
            completion.indexOf("BOUNDED_RUN_DELIVERY_ACKNOWLEDGED"))
    }
}
