package com.hugr.wearos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SourceReplayProtocolTest {
    @Test
    fun `five canonical accelerometer records fit a 509 byte ATT payload`() {
        val records = records(SourceStreamCode.ACCEL, 5, 16)
        val frames = SourceReplayProtocol.buildDataFrames(records, replay = true, maximumAttPayloadBytes = 509)
        assertEquals(1, frames.size)
        assertTrue(frames.single().size <= 509)
        assertEquals(5, SourceReplayProtocol.decodeDataFrame(frames.single()).records.size)
    }

    @Test
    fun `data frame round trip preserves canonical bytes and replay truth`() {
        val records = records(SourceStreamCode.CARDIAC, 3, 25)
        val decoded = SourceReplayProtocol.decodeDataFrame(
            SourceReplayProtocol.buildDataFrames(records, replay = true, maximumAttPayloadBytes = 509).single(),
        )
        assertTrue(decoded.replay)
        records.zip(decoded.records).forEach { (expected, actual) -> assertArrayEquals(expected.canonicalBytes(), actual.canonicalBytes()) }
    }

    @Test
    fun `frame builder preserves global order across stream batches`() {
        val session = UUID.randomUUID()
        val records = listOf(
            record(session, 1, SourceStreamCode.ACCEL, 1),
            record(session, 2, SourceStreamCode.ACCEL, 2),
            record(session, 3, SourceStreamCode.EDA, 1),
            record(session, 4, SourceStreamCode.ACCEL, 3),
        )
        val decoded = SourceReplayProtocol.buildDataFrames(records, true, 509).flatMap { SourceReplayProtocol.decodeDataFrame(it).records }
        assertEquals(listOf(1L, 2L, 3L, 4L), decoded.map { it.recordIndex })
    }

    @Test
    fun `resume and acknowledgement round trip exact identity`() {
        val resume = SourceResumeRequest(UUID.randomUUID(), 42)
        assertEquals(resume, SourceReplayProtocol.decodeResumeRequest(SourceReplayProtocol.encodeResumeRequest(resume)))
        val acknowledgement = SourceSegmentAcknowledgement(UUID.randomUUID(), 1_234, "ab".repeat(32))
        assertEquals(acknowledgement, SourceReplayProtocol.decodeSegmentAcknowledgement(SourceReplayProtocol.encodeSegmentAcknowledgement(acknowledgement)))
    }

    @Test
    fun `manifest round trip preserves equality fields`() {
        val manifest = SourceSegmentManifest(
            segmentId = "ignored",
            watchBootSessionId = UUID.randomUUID(),
            firstRecordIndex = 1,
            lastRecordIndex = 7,
            recordCount = 7,
            byteCount = 500,
            sha256Hex = "cd".repeat(32),
            streamRanges = mapOf(
                SourceStreamCode.ACCEL to SourceStreamRange(5, 1, 5),
                SourceStreamCode.EDA to SourceStreamRange(2, 1, 2),
            ),
        )
        val decoded = SourceReplayProtocol.decodeManifestFrame(SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(manifest))).manifest
        assertEquals(manifest.watchBootSessionId, decoded.watchBootSessionId)
        assertEquals(manifest.firstRecordIndex, decoded.firstRecordIndex)
        assertEquals(manifest.lastRecordIndex, decoded.lastRecordIndex)
        assertEquals(manifest.recordCount, decoded.recordCount)
        assertEquals(manifest.byteCount, decoded.byteCount)
        assertEquals(manifest.sha256Hex, decoded.sha256Hex)
        assertEquals(manifest.streamRanges, decoded.streamRanges)
    }

    @Test
    fun `frame CRC corruption is a hard error`() {
        val bytes = SourceReplayProtocol.buildDataFrames(records(SourceStreamCode.ACCEL, 5, 16), true, 509).single()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(SourceJournalCorruptionException::class.java) { SourceReplayProtocol.decodeDataFrame(bytes) }
    }

    @Test
    fun `source-safe payload is exactly the five accelerometer replay threshold`() {
        val records = records(SourceStreamCode.ACCEL, 5, 16)

        val exact = SourceReplayProtocol.buildDataFrames(
            records,
            replay = true,
            maximumAttPayloadBytes = SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL,
        )
        val oneByteShort = SourceReplayProtocol.buildDataFrames(
            records,
            replay = true,
            maximumAttPayloadBytes = SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL - 1,
        )

        assertEquals(364, SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL)
        assertEquals(1, exact.size)
        assertEquals(364, exact.single().size)
        assertTrue(oneByteShort.size > 1)
    }

    @Test
    fun `source-safe payload also fits the largest single record and maximum manifest`() {
        val session = UUID.randomUUID()
        val deviceHealth = record(
            session = session,
            recordIndex = 1L,
            stream = SourceStreamCode.DEVICE_HEALTH,
            sourceSequence = 1L,
            payload = ByteArray(58),
        )
        val deviceHealthFrame = SourceReplayProtocol.buildDataFrames(
            listOf(deviceHealth),
            replay = false,
            maximumAttPayloadBytes = SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL,
        ).single()
        val maximumManifest = SourceManifestFrame(
            SourceSegmentManifest(
                segmentId = "ignored",
                watchBootSessionId = session,
                firstRecordIndex = 1L,
                lastRecordIndex = 5L,
                recordCount = 5L,
                byteCount = 500L,
                sha256Hex = "ab".repeat(32),
                streamRanges = SourceStreamCode.entries.associateWith { stream ->
                    SourceStreamRange(1L, stream.wireCode.toLong(), stream.wireCode.toLong())
                },
            ),
        )
        val manifestBytes = SourceReplayProtocol.encodeManifestFrame(maximumManifest)

        assertEquals(142, deviceHealthFrame.size)
        assertEquals(149, manifestBytes.size)
        assertTrue(deviceHealthFrame.size <= SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL)
        assertTrue(manifestBytes.size <= SourceReplayProtocol.MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL)
        assertThrows(IllegalArgumentException::class.java) {
            SourceReplayProtocol.buildDataFrames(listOf(deviceHealth), false, deviceHealthFrame.size - 1)
        }
    }

    private fun records(stream: SourceStreamCode, count: Int, payloadBytes: Int): List<WatchSourceRecord> {
        val session = UUID.randomUUID()
        return (1..count).map { index -> record(session, index.toLong(), stream, index.toLong(), ByteArray(payloadBytes) { index.toByte() }) }
    }

    private fun record(session: UUID, recordIndex: Long, stream: SourceStreamCode, sourceSequence: Long, payload: ByteArray = byteArrayOf(1)) =
        WatchSourceRecord(session, recordIndex, stream, sourceSequence, 1_726_000_000_000 + recordIndex, payload)
}
