package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Synthetic interoperability seam. Run producer -> Phone test -> verifier with the
 * controlled scripts/hugr_integrated_wire_regression.py. The default Watch suite
 * skips this external-input case; it never uses or exports device/private data.
 */
class WatchPhoneWireIntegrationTest {
    private fun root(): File {
        val raw = System.getenv("HUGR_WIRE_WORKSPACE")
        assumeTrue("Cross-device fixture workspace not set", !raw.isNullOrBlank())
        return File(raw!!).canonicalFile.also { assertTrue(it.isDirectory) }
    }

    private fun journal(dir: File): SourceJournal = SourceJournal(
        rootDir = dir,
        bootCount = 69,
        availableBytes = { Long.MAX_VALUE },
        nowWallMs = { 1_000L },
        requiredFreeBytes = 1,
        maxSegmentBytes = 10_000,
    )

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun parse(file: File): Map<String, String> = file.readLines().filter { it.isNotBlank() }
        .associate { row -> row.substringBefore('=') to row.substringAfter('=') }

    @Test
    fun producer() {
        val workspace = root()
        if (System.getenv("HUGR_WIRE_PHASE") != "produce") return
        val store = File(workspace, "synthetic_journal").apply { assertTrue(mkdirs()) }
        val source = journal(store)
        fun accel(n: Int): ByteArray = SourcePayloadCodec.accel(n * 10, n * 20, n * 30, "REALTIME", 1, true)
        source.append(SourceStreamCode.ACCEL, 1_000L, accel(1))
        source.append(SourceStreamCode.ACCEL, 1_001L, accel(2))
        val main = requireNotNull(source.finalizeActiveSegment())
        source.append(SourceStreamCode.ACCEL, 1_002L, accel(3))
        source.append(SourceStreamCode.ACCEL, 1_003L, accel(4))
        val sensorFinal = requireNotNull(source.finalizeActiveSegment())
        fun health(): ByteArray = SourcePayloadCodec.deviceHealth(
            batteryPercent = 80, flags = 0, activeSensorMask = 0, sdkStatus = 0,
            buildVersionCode = 69, totalSourceRecords = 4, flushCount = 0,
            transportCompletedCount = 0, transportFailedCount = 0,
            transportTimeoutCount = 0, transportCoalescedAccelCount = 0,
            negotiatedMtu = 517, replayBacklogCount = 0, dataLoss = false,
            dataLossStreamCode = 0, dataLossFirstSequence = 0,
            dataLossLastSequence = 0, dataLossReasonCode = 0,
        )
        source.append(SourceStreamCode.DEVICE_HEALTH, 1_004L, health())
        source.append(SourceStreamCode.DEVICE_HEALTH, 1_005L, health())
        val postRunHealth = requireNotNull(source.finalizeActiveSegment())
        assertEquals(212L, postRunHealth.byteCount) // two canonical 106-byte health records
        assertEquals(listOf(main, sensorFinal, postRunHealth), source.finalizedManifests(source.watchBootSessionId))
        val records = source.readRecordsAfter(source.watchBootSessionId, 0, 6, 6)
        assertEquals(6, records.size)
        source.close()
        val lines = mutableListOf(
            "session=${main.watchBootSessionId}",
            "durable=4",
            "highWater=6",
        )
        listOf(main, sensorFinal, postRunHealth).forEachIndexed { index, manifest ->
            lines += "manifest${index + 1}=${b64(SourceReplayProtocol.encodeManifestFrame(SourceManifestFrame(manifest)))}"
        }
        records.forEachIndexed { index, record ->
            lines += "record${index + 1}=${b64(record.canonicalBytes())}"
        }
        val replayFrame = SourceReplayProtocol.buildDataFrames(records.drop(4), true, 514)
        assertEquals(1, replayFrame.size) // two health source records in a valid GATT frame
        lines += "data=${b64(replayFrame.single())}"
        File(workspace, "watch_wire.tsv").writeText(lines.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun verifier() {
        val workspace = root()
        if (System.getenv("HUGR_WIRE_PHASE") != "verify") return
        val input = parse(File(workspace, "watch_wire.tsv"))
        val phone = parse(File(workspace, "phone_acks.tsv"))
        val source = journal(File(workspace, "synthetic_journal"))
        val session = source.watchBootSessionId
        assertEquals(input.getValue("session"), session.toString())
        val highWater = input.getValue("highWater").toLong()
        val durablePhoneIndex = input.getValue("durable").toLong()
        assertEquals(6L, source.latestRecordIndex())
        assertEquals(3, source.finalizedManifests(session).size)
        assertEquals(3, phone.getValue("count").toInt())
        listOf(2L, 4L, 6L).forEachIndexed { index, endpoint ->
            val pending = source.finalizedManifests(session)
            assertEquals(3 - index, pending.size)
            val manifest = requireNotNull(SourceReplayWindow.nextManifestToQueue(
                session, durablePhoneIndex, highWater, null, pending,
            ))
            assertEquals(endpoint, manifest.lastRecordIndex)
            val wireAck = Base64.getDecoder().decode(phone.getValue("ack${index + 1}"))
            assertEquals(59, wireAck.size)
            val ack = SourceReplayProtocol.decodeSegmentAcknowledgement(wireAck)
            SourceReplayWindow.validateAcknowledgement(session, durablePhoneIndex, highWater, ack, endpoint)
            SourceReplayWindow.validateQueuedManifestAcknowledgement(endpoint, ack)
            assertEquals(session, ack.watchBootSessionId)
            assertEquals(endpoint, ack.cumulativeRecordIndex)
            assertEquals(manifest.sha256Hex, ack.completedSegmentSha256)
            assertFalse(source.acknowledgeCompletedSegment(session, endpoint, "0".repeat(64)))
            assertTrue(source.acknowledgeCompletedSegment(session, endpoint, ack.completedSegmentSha256))
            assertFalse(source.acknowledgeCompletedSegment(session, endpoint, ack.completedSegmentSha256))
            if (index < 2) assertTrue(source.hasFinalizedSegments(session))
        }
        assertFalse(source.hasFinalizedSegments(session))
        assertEquals(6L, source.latestRecordIndex()) // recovery/verification created no new samples
        assertEquals(null, SourceReplayWindow.nextManifestToQueue(session, durablePhoneIndex, highWater, null, source.finalizedManifests(session)))
        source.close()
    }
}
