package com.hugr.wearos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream

class SourceJournalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `canonical records round trip with stable identity and bytes`() {
        val session = java.util.UUID.randomUUID()
        val record = WatchSourceRecord(
            watchBootSessionId = session,
            recordIndex = 7,
            stream = SourceStreamCode.ACCEL,
            sourceSequence = 11,
            sourceTimestampMs = 1_726_000_000_123,
            payload = byteArrayOf(1, 2, 3, 4),
        )

        val first = record.canonicalBytes()
        val second = record.canonicalBytes()
        val decoded = SourceJournalCodec.decodeAll(first)

        assertArrayEquals(first, second)
        assertEquals(1, decoded.records.size)
        assertEquals(record.watchBootSessionId, decoded.records[0].watchBootSessionId)
        assertEquals(record.recordIndex, decoded.records[0].recordIndex)
        assertEquals(record.stream, decoded.records[0].stream)
        assertEquals(record.sourceSequence, decoded.records[0].sourceSequence)
        assertEquals(record.sourceTimestampMs, decoded.records[0].sourceTimestampMs)
        assertArrayEquals(record.payload, decoded.records[0].payload)
    }

    @Test
    fun `service recreation preserves boot session record index and per stream sequences`() {
        val root = temporaryFolder.newFolder("journal")
        var now = 1_000L
        val first = journal(root, bootCount = 12) { now }
        val session = first.watchBootSessionId
        val a1 = first.append(SourceStreamCode.ACCEL, 100, byteArrayOf(1))
        val e1 = first.append(SourceStreamCode.EDA, 101, byteArrayOf(2))
        first.forceSync()

        now += 100
        val recreated = journal(root, bootCount = 12) { now }
        val a2 = recreated.append(SourceStreamCode.ACCEL, 102, byteArrayOf(3))
        val e2 = recreated.append(SourceStreamCode.EDA, 103, byteArrayOf(4))

        assertEquals(session, recreated.watchBootSessionId)
        assertEquals(1L, a1.recordIndex)
        assertEquals(2L, e1.recordIndex)
        assertEquals(3L, a2.recordIndex)
        assertEquals(4L, e2.recordIndex)
        assertEquals(1L, a1.sourceSequence)
        assertEquals(2L, a2.sourceSequence)
        assertEquals(1L, e1.sourceSequence)
        assertEquals(2L, e2.sourceSequence)
        recreated.close()
    }

    @Test
    fun `new physical boot rotates session and restarts indices without deleting old segments`() {
        val root = temporaryFolder.newFolder("journal")
        val first = journal(root, bootCount = 2) { 1_000L }
        first.append(SourceStreamCode.EDA, 100, byteArrayOf(1))
        first.close()
        val firstSession = first.watchBootSessionId

        val second = journal(root, bootCount = 3) { 2_000L }
        val record = second.append(SourceStreamCode.EDA, 200, byteArrayOf(2))

        assertNotEquals(firstSession, second.watchBootSessionId)
        assertEquals(1L, record.recordIndex)
        assertEquals(1L, record.sourceSequence)
        assertTrue(root.listFiles().orEmpty().any { it.name.contains(firstSession.toString()) })
        second.close()
    }

    @Test
    fun `new physical boot finalizes valid prior open segment and enumerates sessions oldest first`() {
        val root = temporaryFolder.newFolder("journal")
        val first = journal(root, bootCount = 20) { 1_000L }
        val firstSession = first.watchBootSessionId
        first.append(SourceStreamCode.ACCEL, 100, byteArrayOf(1))
        first.forceSync()

        val second = journal(root, bootCount = 21) { 2_000L }
        val secondSession = second.watchBootSessionId
        second.append(SourceStreamCode.EDA, 200, byteArrayOf(2))

        assertEquals(listOf(firstSession, secondSession), second.retainedSessionIds())
        assertEquals(1L, second.highestRecordIndex(firstSession))
        assertEquals(1L, second.highestRecordIndex(secondSession))
        assertTrue(second.finalizedManifests().any { it.watchBootSessionId == firstSession })
        second.close()
    }

    @Test
    fun `incomplete active tail is truncated and reported rather than inferred complete`() {
        val root = temporaryFolder.newFolder("journal")
        val first = journal(root, bootCount = 5) { 1_000L }
        val record = first.append(SourceStreamCode.ACCEL, 100, byteArrayOf(1, 2, 3))
        first.forceSync()
        val openFile = root.listFiles().orEmpty().single { it.name.startsWith("open_") }
        FileOutputStream(openFile, true).use { it.write(byteArrayOf(9, 9, 9, 9, 9)) }

        val recreated = journal(root, bootCount = 5) { 2_000L }
        val decoded = SourceJournalCodec.decodeAll(openFile.readBytes())

        assertEquals(1, decoded.records.size)
        assertEquals(record.recordIndex, decoded.records[0].recordIndex)
        assertEquals(0, decoded.incompleteTailBytes)
        assertTrue(recreated.snapshot().anomalies.any { it.kind == SourceJournalAnomalyKind.INCOMPLETE_TAIL })
        assertFalse(recreated.preflight().eligible)
        recreated.close()
    }

    @Test
    fun `finalized manifest hashes canonical bytes and reports per stream ranges`() {
        val root = temporaryFolder.newFolder("journal")
        val journal = journal(root, bootCount = 6) { 1_000L }
        journal.append(SourceStreamCode.ACCEL, 100, byteArrayOf(1))
        journal.append(SourceStreamCode.ACCEL, 101, byteArrayOf(2))
        journal.append(SourceStreamCode.EDA, 102, byteArrayOf(3))

        val manifest = requireNotNull(journal.finalizeActiveSegment())

        assertEquals(1L, manifest.firstRecordIndex)
        assertEquals(3L, manifest.lastRecordIndex)
        assertEquals(3L, manifest.recordCount)
        assertEquals(2L, manifest.streamRanges.getValue(SourceStreamCode.ACCEL).count)
        assertEquals(1L, manifest.streamRanges.getValue(SourceStreamCode.ACCEL).firstSequence)
        assertEquals(2L, manifest.streamRanges.getValue(SourceStreamCode.ACCEL).lastSequence)
        assertEquals(64, manifest.sha256Hex.length)
        assertEquals(manifest, journal.finalizedManifests().single())
        assertEquals(listOf(manifest), journal.drainNewlyFinalizedManifests())
        assertTrue(journal.drainNewlyFinalizedManifests().isEmpty())
    }

    @Test
    fun `automatic segment rotation exposes the finalized manifest for live delivery`() {
        val root = temporaryFolder.newFolder("journal")
        val journal = SourceJournal(
            rootDir = root,
            bootCount = 6,
            availableBytes = { Long.MAX_VALUE },
            nowWallMs = { 1_000L },
            requiredFreeBytes = 1,
            maxSegmentBytes = 55,
            maxSegmentAgeMs = 60_000,
        )
        journal.append(SourceStreamCode.EDA, 100, byteArrayOf(1))
        journal.append(SourceStreamCode.EDA, 101, byteArrayOf(2))

        val announced = journal.drainNewlyFinalizedManifests()
        assertEquals(1, announced.size)
        assertEquals(1L, announced.single().firstRecordIndex)
        assertEquals(1L, announced.single().lastRecordIndex)
        journal.close()
    }

    @Test
    fun `segment deletion requires exact cumulative endpoint and hash`() {
        val root = temporaryFolder.newFolder("journal")
        val journal = journal(root, bootCount = 7) { 1_000L }
        journal.append(SourceStreamCode.CARDIAC, 100, byteArrayOf(1))
        val manifest = requireNotNull(journal.finalizeActiveSegment())

        assertFalse(journal.acknowledgeCompletedSegment(manifest.watchBootSessionId, manifest.lastRecordIndex, "0".repeat(64)))
        assertEquals(1, journal.finalizedManifests().size)
        assertFalse(journal.acknowledgeCompletedSegment(java.util.UUID.randomUUID(), manifest.lastRecordIndex, manifest.sha256Hex))
        assertTrue(journal.acknowledgeCompletedSegment(manifest.watchBootSessionId, manifest.lastRecordIndex, manifest.sha256Hex))
        assertTrue(journal.finalizedManifests().isEmpty())
    }

    @Test
    fun `acknowledging a later segment cannot delete an earlier segment without its own hash equality`() {
        val root = temporaryFolder.newFolder("journal")
        val journal = journal(root, bootCount = 7) { 1_000L }
        journal.append(SourceStreamCode.CARDIAC, 100, byteArrayOf(1))
        val first = requireNotNull(journal.finalizeActiveSegment())
        journal.append(SourceStreamCode.ACCEL, 101, byteArrayOf(2))
        val second = requireNotNull(journal.finalizeActiveSegment())

        assertTrue(journal.acknowledgeCompletedSegment(second.watchBootSessionId, second.lastRecordIndex, second.sha256Hex))
        assertEquals(listOf(first), journal.finalizedManifests())
        assertTrue(journal.acknowledgeCompletedSegment(first.watchBootSessionId, first.lastRecordIndex, first.sha256Hex))
        assertTrue(journal.finalizedManifests().isEmpty())
    }

    @Test
    fun `bounded page reads preserve record order without loading the full journal`() {
        val root = temporaryFolder.newFolder("journal")
        val journal = journal(root, bootCount = 8) { 1_000L }
        repeat(20) { index ->
            journal.append(SourceStreamCode.ACCEL, 100 + index.toLong(), byteArrayOf(index.toByte()))
        }
        journal.forceSync()

        val firstPage = journal.readRecordsAfter(journal.watchBootSessionId, 0, 7)
        val secondPage = journal.readRecordsAfter(journal.watchBootSessionId, firstPage.last().recordIndex, 7)

        assertEquals((1L..7L).toList(), firstPage.map { it.recordIndex })
        assertEquals((8L..14L).toList(), secondPage.map { it.recordIndex })
        assertEquals(20L, journal.countRecordsAfter(journal.watchBootSessionId, 0))
        journal.close()
    }

    @Test
    fun `Build 45 reproduction - current replay paging crosses the resume-time high water`() {
        val root = temporaryFolder.newFolder("replay-race")
        val journal = journal(root, bootCount = 46) { 1_000L }
        repeat(100) { index ->
            journal.append(SourceStreamCode.ACCEL, 100 + index.toLong(), byteArrayOf(index.toByte()))
        }
        val replayHighWaterAtResume = journal.highestRecordIndex(journal.watchBootSessionId)
        assertEquals(100L, replayHighWaterAtResume)

        repeat(100) { index ->
            journal.append(SourceStreamCode.CARDIAC, 1_000 + index.toLong(), byteArrayOf(index.toByte()))
        }

        val boundedBuild46ReplayPage = journal.readRecordsAfter(
            journal.watchBootSessionId,
            recordIndexExclusive = 0L,
            recordIndexInclusive = replayHighWaterAtResume,
            limit = 256,
        )

        assertEquals(
            "Build 45 replay must not cross the record index present at resume",
            (1L..replayHighWaterAtResume).toList(),
            boundedBuild46ReplayPage.map { it.recordIndex },
        )
        assertEquals(100L, journal.countRecordsAfter(journal.watchBootSessionId, 0L, replayHighWaterAtResume))
        assertEquals(0L, journal.countRecordsAfter(journal.watchBootSessionId, replayHighWaterAtResume, replayHighWaterAtResume))
        assertEquals(200L, journal.countRecordsAfter(journal.watchBootSessionId, 0L))

        val nextResumeHighWater = journal.highestRecordIndex(journal.watchBootSessionId)
        val retainedForNextResume = journal.readRecordsAfter(
            journal.watchBootSessionId,
            recordIndexExclusive = replayHighWaterAtResume,
            recordIndexInclusive = nextResumeHighWater,
            limit = 256,
        )
        assertEquals((101L..200L).toList(), retainedForNextResume.map { it.recordIndex })
        assertEquals((1L..100L).toList(), retainedForNextResume.map { it.sourceSequence })
        assertEquals((1_000L..1_099L).toList(), retainedForNextResume.map { it.sourceTimestampMs })
        journal.close()
    }

    @Test
    fun `delivery ledger distinguishes buffered live and replay transitions without changing canonical bytes`() {
        val root = temporaryFolder.newFolder("journal")
        var now = 1_000L
        val journal = journal(root, bootCount = 9) { now }
        val records = listOf(
            journal.append(SourceStreamCode.ACCEL, 100, byteArrayOf(1)),
            journal.append(SourceStreamCode.ACCEL, 101, byteArrayOf(2)),
        )
        val canonicalBefore = records.map { it.canonicalBytes() }
        now = 1_100L
        journal.recordDelivery(records, SourceDeliveryState.LIVE_SENT)
        now = 1_200L
        journal.recordDelivery(records, SourceDeliveryState.LIVE_CONFIRMED)
        now = 1_300L
        journal.recordDelivery(records, SourceDeliveryState.REPLAY_SENT)
        now = 1_400L
        journal.recordDelivery(records, SourceDeliveryState.REPLAY_CONFIRMED)

        assertEquals(
            listOf(
                SourceDeliveryState.BUFFERED,
                SourceDeliveryState.BUFFERED,
                SourceDeliveryState.LIVE_SENT,
                SourceDeliveryState.LIVE_CONFIRMED,
                SourceDeliveryState.REPLAY_SENT,
                SourceDeliveryState.REPLAY_CONFIRMED,
            ),
            journal.deliveryEvents().map { it.state },
        )
        records.zip(canonicalBefore).forEach { (record, before) -> assertArrayEquals(before, record.canonicalBytes()) }
        journal.close()
    }

    @Test
    fun `capacity preflight refuses study mode without silent overwrite`() {
        val root = temporaryFolder.newFolder("journal")
        val journal = SourceJournal(
            rootDir = root,
            bootCount = 1,
            availableBytes = { 100L },
            nowWallMs = { 1_000L },
            requiredFreeBytes = 101L,
        )

        assertFalse(journal.preflight().eligible)
        assertTrue(journal.snapshot().anomalies.any { it.kind == SourceJournalAnomalyKind.CAPACITY_REFUSED })
        val error = assertThrows(SourceJournalCapacityException::class.java) {
            journal.append(SourceStreamCode.EDA, 100, byteArrayOf(1))
        }
        assertEquals(SourceStreamCode.EDA, error.stream)
        assertEquals(1L, error.firstAffectedSourceSequence)
        assertEquals(1L, error.lastAffectedSourceSequence)
    }

    @Test
    fun `persisted anomaly keeps study eligibility removed after service recreation`() {
        val root = temporaryFolder.newFolder("journal")
        val refused = SourceJournal(
            rootDir = root,
            bootCount = 1,
            availableBytes = { 100L },
            nowWallMs = { 1_000L },
            requiredFreeBytes = 101L,
        )
        assertFalse(refused.preflight().eligible)
        refused.close()

        val recreated = SourceJournal(
            rootDir = root,
            bootCount = 1,
            availableBytes = { Long.MAX_VALUE },
            nowWallMs = { 2_000L },
            requiredFreeBytes = 1L,
        )
        assertFalse(recreated.preflight().eligible)
        assertTrue(recreated.snapshot().anomalies.any { it.kind == SourceJournalAnomalyKind.CAPACITY_REFUSED })
        recreated.close()
    }

    @Test
    fun `retained journal bytes count toward restart retention allocation`() {
        val root = temporaryFolder.newFolder("journal")
        java.io.File(root, "retained.bin").writeBytes(ByteArray(80))
        val journal = SourceJournal(
            rootDir = root,
            bootCount = 1,
            availableBytes = { 20L },
            nowWallMs = { 1_000L },
            requiredFreeBytes = 100L,
        )

        assertTrue(journal.preflight().eligible)
        journal.close()
    }

    @Test
    fun `CRC corruption is a hard error`() {
        val record = WatchSourceRecord(
            java.util.UUID.randomUUID(),
            1,
            SourceStreamCode.EDA,
            1,
            100,
            byteArrayOf(1, 2, 3),
        )
        val bytes = record.canonicalBytes()
        bytes[bytes.lastIndex - 1] = (bytes[bytes.lastIndex - 1].toInt() xor 0x01).toByte()

        assertThrows(SourceJournalCorruptionException::class.java) {
            SourceJournalCodec.decodeAll(bytes)
        }
    }

    private fun journal(root: java.io.File, bootCount: Int, now: () -> Long): SourceJournal = SourceJournal(
        rootDir = root,
        bootCount = bootCount,
        availableBytes = { Long.MAX_VALUE },
        nowWallMs = now,
        requiredFreeBytes = 1,
        maxSegmentBytes = 10_000,
        maxSegmentAgeMs = 60_000,
    )
}
