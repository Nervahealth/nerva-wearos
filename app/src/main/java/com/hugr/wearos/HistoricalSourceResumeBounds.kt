package com.hugr.wearos

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

internal object HistoricalSourceResumeBounds {
    /**
     * A Phone cursor describes durable Phone records, not retained Watch files.
     * Exact ACK deletion can shrink the latter. For a cursor above the retained
     * endpoint, require the existing app-private BUFFERED issuance ledger: one
     * contiguous, single-record entry per index, anchored to verified retained
     * manifests for this session. This is issuance provenance, NOT a hash/ACK
     * ledger and never authorizes segment deletion or verifies deleted bytes.
     */
    fun prepare(journal: SourceJournal, rootDir: File, sessionId: UUID, acceptedIndex: Long): PreparedSourceResumePlan = synchronized(journal) {
        if (!journal.preflight().eligible) throw SourceJournalCorruptionException("Source resume journal is ineligible")
        if (acceptedIndex < 0L || acceptedIndex == Long.MAX_VALUE) {
            throw SourceJournalCorruptionException("Invalid Phone resume cursor")
        }
        val retainedEnd = journal.highestFinalizedRecordIndex(sessionId)
        val historicalBound = if (acceptedIndex <= retainedEnd) retainedEnd else {
            val retained = journal.finalizedManifests(sessionId)
            if (retained.isEmpty()) throw SourceJournalCorruptionException("Historical cursor has no retained session anchor")
            val issued = strictlyRecordedIssuanceBound(rootDir, sessionId)
            retained.forEach { manifest ->
                val file = File(rootDir, manifest.segmentId + ".seg")
                val bytes = file.readBytes()
                val decoded = SourceJournalCodec.decodeAll(bytes)
                val records = decoded.records
                val exactName = "segment_${sessionId}_${manifest.firstRecordIndex}_${manifest.lastRecordIndex}_${manifest.sha256Hex}.seg"
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                if (file.name != exactName || hash != manifest.sha256Hex ||
                    decoded.incompleteTailBytes != 0 || decoded.validBytes.toLong() != manifest.byteCount ||
                    records.isEmpty() || records.size.toLong() != manifest.recordCount ||
                    records.last().recordIndex != manifest.lastRecordIndex ||
                    records.withIndex().any { (n, record) -> record.watchBootSessionId != sessionId ||
                        record.recordIndex != manifest.firstRecordIndex + n.toLong() }) {
                    throw SourceJournalCorruptionException("Historical anchor is not exact contiguous retained source")
                }
            }
            if (retained.zipWithNext().any { (a, b) -> a.lastRecordIndex >= b.firstRecordIndex }) {
                throw SourceJournalCorruptionException("Historical anchors overlap")
            }
            if (retained.any { it.firstRecordIndex < 1L || it.lastRecordIndex > issued }) {
                throw SourceJournalCorruptionException("Retained manifests exceed historical issuance evidence")
            }
            issued
        }
        if (acceptedIndex > historicalBound) {
            throw SourceJournalCorruptionException("Phone resume cursor exceeds justified historical session bound")
        }
        PreparedSourceResumePlan(
            watchBootSessionId = sessionId,
            acceptedRecordIndex = acceptedIndex,
            replayHighWaterRecordIndex = retainedEnd,
            replayBacklogCount = journal.countRecordsAfter(sessionId, acceptedIndex, retainedEnd),
            historicalSessionBound = historicalBound,
        )
    }

    private fun strictlyRecordedIssuanceBound(rootDir: File, sessionId: UUID): Long {
        val file = File(rootDir, "delivery_states.log")
        fun invalid(): Nothing = throw SourceJournalCorruptionException("Historical issuance ledger missing, incomplete or inconsistent")
        if (!file.isFile || file.length() == 0L) invalid()
        RandomAccessFile(file, "r").use { input ->
            input.seek(input.length() - 1L)
            if (input.read() != '\n'.code) invalid()
        }
        var issued = 0L
        file.forEachLine { line ->
            val p = line.split('\t')
            if (p.size != 5) invalid()
            val eventSession = runCatching { UUID.fromString(p[0]) }.getOrElse { invalid() }
            if (eventSession.toString() != p[0]) invalid()
            val first = p[1].toLongOrNull() ?: invalid()
            val last = p[2].toLongOrNull() ?: invalid()
            val state = runCatching { SourceDeliveryState.valueOf(p[3]) }.getOrElse { invalid() }
            val time = p[4].toLongOrNull() ?: invalid()
            if (first <= 0L || last < first || last == Long.MAX_VALUE || time < 0L) invalid()
            if (eventSession == sessionId && state == SourceDeliveryState.BUFFERED) {
                if (first != last || first != issued + 1L) invalid()
                issued = last
            }
        }
        if (issued == 0L) invalid()
        return issued
    }

}
