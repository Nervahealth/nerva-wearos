package com.hugr.wearos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class Build50ResumeScalingRedTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `one real segment establishes the selected-unit reference path`() {
        val fixture = realJournalFixture("one-segment", segmentCount = 1, sourceSpacingMs = 1_000L)

        val selected = boundedLookup(fixture.journal, fixture.session)

        assertNotNull(selected)
        assertEquals(1L, selected?.firstRecordIndex)
        assertEquals(1L, selected?.lastRecordIndex)
    }

    @Test
    fun `257 real segments must select the first endpoint without reconstructing unrelated history`() {
        val fixture = realJournalFixture("257-segments", segmentCount = 257, sourceSpacingMs = 60_000L)
        corruptLastFinalizedSegment(fixture.root)

        val selected = boundedLookup(fixture.journal, fixture.session)

        assertNotNull(selected)
        assertEquals(
            "Selecting endpoint 1 must not read, decode or hash unrelated segment 257",
            1L,
            selected?.lastRecordIndex,
        )
    }

    @Test
    fun `synthetic multi-hour corpus must not put unrelated history on resume readiness path`() {
        val fixture = realJournalFixture("multi-hour", segmentCount = 25, sourceSpacingMs = 30 * 60_000L)
        corruptLastFinalizedSegment(fixture.root)

        val selected = boundedLookup(fixture.journal, fixture.session)

        assertNotNull(selected)
        assertEquals(
            "The first eligible endpoint must be selectable without reconstructing twelve hours of later history",
            1L,
            selected?.lastRecordIndex,
        )
    }

    @Test
    fun `selected corrupt segment still fails closed`() {
        val fixture = realJournalFixture("selected-corrupt", segmentCount = 1, sourceSpacingMs = 1_000L)
        corruptLastFinalizedSegment(fixture.root)

        org.junit.Assert.assertThrows(SourceJournalCorruptionException::class.java) {
            boundedLookup(fixture.journal, fixture.session)
        }
    }

    private fun boundedLookup(journal: SourceJournal, session: UUID): SourceSegmentManifest? {
        val startedAtNs = System.nanoTime()
        return try {
            journal.nextFinalizedManifest(
                sessionId = session,
                recordIndexExclusive = 0L,
                recordIndexInclusive = journal.highestFinalizedRecordIndex(session),
            )
        } finally {
            val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000.0
            println("B50_REAL_FILE_CURRENT_LOOKUP elapsedMs=$elapsedMs")
        }
    }

    private fun realJournalFixture(
        name: String,
        segmentCount: Int,
        sourceSpacingMs: Long,
    ): Fixture {
        val root = temporaryFolder.newFolder(name)
        var now = 1_000L
        val journal = SourceJournal(
            rootDir = root,
            bootCount = 50,
            availableBytes = { Long.MAX_VALUE },
            nowWallMs = { now },
            requiredFreeBytes = 1L,
            maxSegmentBytes = Long.MAX_VALUE,
            maxSegmentAgeMs = Long.MAX_VALUE,
        )
        val session = journal.watchBootSessionId
        repeat(segmentCount) { zeroBased ->
            val sourceTimestampMs = 1_780_000_000_000L + (zeroBased * sourceSpacingMs)
            journal.append(
                stream = SourceStreamCode.ACCEL,
                sourceTimestampMs = sourceTimestampMs,
                payload = byteArrayOf((zeroBased and 0xFF).toByte()),
            )
            assertNotNull(journal.finalizeActiveSegment())
            now += 1L
        }
        return Fixture(root, journal, session)
    }

    private fun corruptLastFinalizedSegment(root: File) {
        val last = root.listFiles().orEmpty()
            .filter { it.name.startsWith("segment_") && it.name.endsWith(".seg") }
            .maxByOrNull { file ->
                file.name.removeSuffix(".seg").split('_')[3].toLong()
            }
            ?: error("No finalized segment found")
        RandomAccessFile(last, "rw").use { file ->
            val offset = (file.length() - 1L).coerceAtLeast(0L)
            file.seek(offset)
            val original = file.readByte().toInt()
            file.seek(offset)
            file.writeByte(original xor 0x01)
        }
    }

    private data class Fixture(
        val root: File,
        val journal: SourceJournal,
        val session: UUID,
    )
}
