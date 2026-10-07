package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Conditional reconstruction, NOT a physical Watch76 diagnosis: Phone showed a
 * morning 8183-8184 ACK, but Watch acceptance of that ACK was not observed.
 * If a later segment were deleted by exact Watch ACK while an earlier segment
 * remained, the current source's high-water-before-resume guard would reject
 * the Phone's durable 8184 index. This test preserves that diagnostic fact.
 */
class SourceResumeIndex8184ScenarioTest {
    @get:Rule val temp = TemporaryFolder()

    private fun journal(root: File): SourceJournal = SourceJournal(
        rootDir = root,
        bootCount = 69,
        availableBytes = { Long.MAX_VALUE },
        nowWallMs = { 1_000L },
        requiredFreeBytes = 1L,
        maxSegmentBytes = 1_048_576L,
    )

    @Test
    fun `hypothetical later exact ACK leaves older retained manifest below Phone index 8184`() {
        val root = temp.newFolder("conditional-out-of-order-ack")
        val source = journal(root)
        repeat(8182) { n -> source.append(SourceStreamCode.CARDIAC, n.toLong(), byteArrayOf(1)) }
        val main = requireNotNull(source.finalizeActiveSegment())
        assertEquals(1L, main.firstRecordIndex)
        assertEquals(8182L, main.lastRecordIndex)
        source.append(SourceStreamCode.DEVICE_HEALTH, 8183L, byteArrayOf(2))
        source.append(SourceStreamCode.DEVICE_HEALTH, 8184L, byteArrayOf(3))
        val health = requireNotNull(source.finalizeActiveSegment())
        assertEquals(8183L, health.firstRecordIndex)
        assertEquals(8184L, health.lastRecordIndex)
        val session = source.watchBootSessionId
        assertEquals(8184L, source.highestFinalizedRecordIndex(session))
        // Scenario only: acceptance cannot be inferred from the Phone's UI ACK.
        assertFalse(source.acknowledgeCompletedSegment(session, 8184L, "0".repeat(64)))
        assertTrue(source.acknowledgeCompletedSegment(session, 8184L, health.sha256Hex))
        source.close()

        val resumed = journal(root)
        assertEquals(session, resumed.watchBootSessionId)
        assertEquals(session, requireNotNull(RetainedFinalizedDeliveryRecovery.select(resumed)).sourceSessionId)
        assertEquals(listOf(main), resumed.finalizedManifests(session))
        assertEquals(8182L, resumed.highestFinalizedRecordIndex(session))
        assertEquals(8182L, resumed.latestRecordIndex())
        // Existing BleGattService.prepareSourceReplay checks acceptedIndex >
        // highestFinalizedRecordIndex and throws before queueing this older
        // manifest. Its exact guard is also source-contract checked below.
        assertTrue(8184L > resumed.highestFinalizedRecordIndex(session))
        val service = File("src/main/java/com/hugr/wearos/BleGattService.kt").readText()
        assertTrue(service.contains("if (acceptedIndex > highWater)"))
        assertTrue(service.contains("Phone resume index exceeds watch journal"))
        assertEquals(main, SourceReplayWindow.nextManifestToQueue(
            session, 8184L, 8182L, null, resumed.finalizedManifests(session),
        ))
        resumed.close()
    }
}
