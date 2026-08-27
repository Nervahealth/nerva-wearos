package com.hugr.wearos

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.zip.CRC32

internal enum class SourceStreamCode(val wireCode: Int) {
    CARDIAC(1),
    EDA(2),
    ACCEL(3),
    SKIN_TEMP(4),
    DEVICE_HEALTH(5);

    companion object {
        fun fromWireCode(code: Int): SourceStreamCode =
            entries.firstOrNull { it.wireCode == code }
                ?: throw SourceJournalCorruptionException("Unknown source stream code: $code")
    }
}

internal data class WatchSourceRecord(
    val watchBootSessionId: UUID,
    val recordIndex: Long,
    val stream: SourceStreamCode,
    val sourceSequence: Long,
    val sourceTimestampMs: Long,
    val payload: ByteArray,
) {
    fun canonicalBytes(): ByteArray = SourceJournalCodec.encode(this)
}

internal data class SourceStreamRange(
    val count: Long,
    val firstSequence: Long,
    val lastSequence: Long,
)

internal data class SourceSegmentManifest(
    val segmentId: String,
    val watchBootSessionId: UUID,
    val firstRecordIndex: Long,
    val lastRecordIndex: Long,
    val recordCount: Long,
    val byteCount: Long,
    val sha256Hex: String,
    val streamRanges: Map<SourceStreamCode, SourceStreamRange>,
)

internal enum class SourceJournalAnomalyKind {
    INCOMPLETE_TAIL,
    CORRUPT_SEGMENT,
    CAPACITY_REFUSED,
    CAPACITY_EXHAUSTED,
}

internal enum class SourceDeliveryState {
    BUFFERED,
    LIVE_SENT,
    LIVE_CONFIRMED,
    REPLAY_SENT,
    REPLAY_CONFIRMED,
    EXPLICITLY_DROPPED,
}

internal data class SourceDeliveryEvent(
    val watchBootSessionId: UUID,
    val firstRecordIndex: Long,
    val lastRecordIndex: Long,
    val state: SourceDeliveryState,
    val occurredAtWatchMs: Long,
)

internal data class SourceJournalAnomaly(
    val kind: SourceJournalAnomalyKind,
    val segmentName: String?,
    val firstAffectedRecordIndex: Long?,
    val discardedBytes: Long,
    val detail: String,
    val stream: SourceStreamCode? = null,
    val firstAffectedSourceSequence: Long? = null,
    val lastAffectedSourceSequence: Long? = null,
)

internal data class SourceJournalPreflight(
    val eligible: Boolean,
    val availableBytes: Long,
    val requiredBytes: Long,
)

internal data class SourceJournalSnapshot(
    val watchBootSessionId: UUID,
    val nextRecordIndex: Long,
    val activeSegmentBytes: Long,
    val finalizedSegments: List<SourceSegmentManifest>,
    val anomalies: List<SourceJournalAnomaly>,
    val preflight: SourceJournalPreflight,
)

internal class SourceJournalCapacityException(
    message: String,
    val stream: SourceStreamCode? = null,
    val firstAffectedSourceSequence: Long? = null,
    val lastAffectedSourceSequence: Long? = null,
) : IllegalStateException(message)
internal class SourceJournalCorruptionException(message: String) : IllegalStateException(message)

internal object SourceJournalCodec {
    private const val MAGIC = 0x48554752
    private const val VERSION = 1
    private const val FIXED_BYTES_WITHOUT_PAYLOAD_OR_CRC = 44
    private const val CRC_BYTES = 4

    data class DecodeResult(
        val records: List<WatchSourceRecord>,
        val validBytes: Int,
        val incompleteTailBytes: Int,
    )

    fun encode(record: WatchSourceRecord): ByteArray {
        require(record.recordIndex > 0) { "recordIndex must be positive" }
        require(record.sourceSequence in 0..0xFFFF_FFFFL) { "sourceSequence must fit uint32" }
        require(record.payload.size <= 65_535) { "payload exceeds uint16 length" }
        val body = ByteBuffer.allocate(FIXED_BYTES_WITHOUT_PAYLOAD_OR_CRC + record.payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            .put(VERSION.toByte())
            .put(record.stream.wireCode.toByte())
            .putLong(record.watchBootSessionId.mostSignificantBits)
            .putLong(record.watchBootSessionId.leastSignificantBits)
            .putLong(record.recordIndex)
            .putInt(record.sourceSequence.toInt())
            .putLong(record.sourceTimestampMs)
            .putShort(record.payload.size.toShort())
            .put(record.payload)
            .array()
        val crc = CRC32().apply { update(body) }.value
        return ByteBuffer.allocate(body.size + CRC_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(body)
            .putInt(crc.toInt())
            .array()
    }

    fun decodeAll(bytes: ByteArray): DecodeResult {
        val records = mutableListOf<WatchSourceRecord>()
        var offset = 0
        while (offset < bytes.size) {
            val remaining = bytes.size - offset
            if (remaining < FIXED_BYTES_WITHOUT_PAYLOAD_OR_CRC + CRC_BYTES) {
                return DecodeResult(records, offset, remaining)
            }
            val header = ByteBuffer.wrap(bytes, offset, remaining).order(ByteOrder.LITTLE_ENDIAN)
            val magic = header.int
            if (magic != MAGIC) throw SourceJournalCorruptionException("Invalid record magic at byte $offset")
            val version = header.get().toInt() and 0xFF
            if (version != VERSION) throw SourceJournalCorruptionException("Unsupported record version $version")
            val stream = SourceStreamCode.fromWireCode(header.get().toInt() and 0xFF)
            val session = UUID(header.long, header.long)
            val recordIndex = header.long
            val sourceSequence = header.int.toLong() and 0xFFFF_FFFFL
            val sourceTimestampMs = header.long
            val payloadLength = header.short.toInt() and 0xFFFF
            val recordLength = FIXED_BYTES_WITHOUT_PAYLOAD_OR_CRC + payloadLength + CRC_BYTES
            if (remaining < recordLength) return DecodeResult(records, offset, remaining)
            val expectedCrc = ByteBuffer.wrap(bytes, offset + recordLength - CRC_BYTES, CRC_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .toLong() and 0xFFFF_FFFFL
            val actualCrc = CRC32().apply {
                update(bytes, offset, recordLength - CRC_BYTES)
            }.value
            if (actualCrc != expectedCrc) {
                throw SourceJournalCorruptionException("CRC mismatch at record index $recordIndex")
            }
            val payloadOffset = offset + FIXED_BYTES_WITHOUT_PAYLOAD_OR_CRC
            records += WatchSourceRecord(
                watchBootSessionId = session,
                recordIndex = recordIndex,
                stream = stream,
                sourceSequence = sourceSequence,
                sourceTimestampMs = sourceTimestampMs,
                payload = bytes.copyOfRange(payloadOffset, payloadOffset + payloadLength),
            )
            offset += recordLength
        }
        return DecodeResult(records, offset, 0)
    }
}

internal class SourceJournal(
    private val rootDir: File,
    private val bootCount: Int,
    private val availableBytes: () -> Long,
    private val nowWallMs: () -> Long,
    private val requiredFreeBytes: Long = REQUIRED_RETENTION_BYTES,
    private val maxUncommittedMs: Long = 1_000L,
    private val maxSegmentBytes: Long = 1_048_576L,
    private val maxSegmentAgeMs: Long = 60_000L,
) : AutoCloseable {
    companion object {
        const val REQUIRED_RETENTION_BYTES = 510_373_396L
        private const val SESSION_META = "current_session.bin"
        private const val ANOMALY_LOG = "journal_anomalies.log"
        private const val DELIVERY_LOG = "delivery_states.log"
    }

    private data class SessionMeta(val bootCount: Int, val sessionId: UUID)
    private data class ActiveSegment(
        val file: File,
        val firstRecordIndex: Long,
        val openedAtMs: Long,
        val output: FileOutputStream,
        var bytes: Long,
        var lastRecordIndex: Long,
    )

    private val anomalies = mutableListOf<SourceJournalAnomaly>()
    private val newlyFinalizedManifests = ArrayDeque<SourceSegmentManifest>()
    private val preflightResult: SourceJournalPreflight
    private var integrityEligible = true
    val watchBootSessionId: UUID
    private var nextRecordIndex = 1L
    private val nextSourceSequence = SourceStreamCode.entries.associateWith { 1L }.toMutableMap()
    private var active: ActiveSegment? = null
    private lateinit var deliveryOutput: FileOutputStream
    private var lastSyncAtMs = nowWallMs()

    init {
        require(requiredFreeBytes > 0) { "requiredFreeBytes must be positive" }
        require(maxUncommittedMs in 1..1_000L) { "uncommitted tail may not exceed one second" }
        require(maxSegmentBytes > 0) { "maxSegmentBytes must be positive" }
        require(maxSegmentAgeMs > 0) { "maxSegmentAgeMs must be positive" }
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw SourceJournalCapacityException("Unable to create source journal directory")
        }
        loadPersistedAnomalies()
        val available = availableBytes()
        val retainedJournalBytes = rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val effectiveAllocation = if (Long.MAX_VALUE - available < retainedJournalBytes) {
            Long.MAX_VALUE
        } else {
            available + retainedJournalBytes
        }
        preflightResult = SourceJournalPreflight(
            eligible = effectiveAllocation >= requiredFreeBytes,
            availableBytes = available,
            requiredBytes = requiredFreeBytes,
        )
        if (!preflightResult.eligible) {
            recordAnomaly(
                SourceJournalAnomaly(
                    SourceJournalAnomalyKind.CAPACITY_REFUSED,
                    null,
                    null,
                    0,
                    "Available bytes $available below required $requiredFreeBytes",
                ),
            )
        }
        val stored = readSessionMeta()
        watchBootSessionId = if (stored != null && stored.bootCount == bootCount) stored.sessionId else UUID.randomUUID()
        if (stored == null || stored.bootCount != bootCount) writeSessionMeta(SessionMeta(bootCount, watchBootSessionId))
        recoverOrphanedSessions()
        recoverCurrentSession()
        deliveryOutput = FileOutputStream(File(rootDir, DELIVERY_LOG), true)
    }

    @Synchronized
    fun preflight(): SourceJournalPreflight = preflightResult.copy(eligible = preflightResult.eligible && integrityEligible)

    @Synchronized
    fun latestAnomaly(): SourceJournalAnomaly? = anomalies.lastOrNull()

    @Synchronized
    fun latestRecordIndex(): Long = nextRecordIndex - 1L

    @Synchronized
    fun append(stream: SourceStreamCode, sourceTimestampMs: Long, payload: ByteArray): WatchSourceRecord {
        val sequence = nextSourceSequence.getValue(stream)
        if (!preflight().eligible) {
            throw SourceJournalCapacityException(
                "Study mode refused: required source-journal capacity unavailable",
                stream,
                sequence,
                sequence,
            )
        }
        if (availableBytes() < payload.size + 64L) {
            val anomaly = SourceJournalAnomaly(
                kind = SourceJournalAnomalyKind.CAPACITY_EXHAUSTED,
                segmentName = active?.file?.name,
                firstAffectedRecordIndex = nextRecordIndex,
                discardedBytes = 0,
                detail = "Insufficient space for required record",
                stream = stream,
                firstAffectedSourceSequence = sequence,
                lastAffectedSourceSequence = sequence,
            )
            recordAnomaly(anomaly)
            throw SourceJournalCapacityException(anomaly.detail, stream, sequence, sequence)
        }
        val record = WatchSourceRecord(
            watchBootSessionId = watchBootSessionId,
            recordIndex = nextRecordIndex,
            stream = stream,
            sourceSequence = sequence,
            sourceTimestampMs = sourceTimestampMs,
            payload = payload.copyOf(),
        )
        val bytes = record.canonicalBytes()
        rotateBeforeAppendIfNeeded(bytes.size)
        val segment = active ?: openNewSegment(record.recordIndex)
        try {
            segment.output.write(bytes)
            segment.bytes += bytes.size
            segment.lastRecordIndex = record.recordIndex
        } catch (error: Exception) {
            recordAnomaly(
                SourceJournalAnomaly(
                    kind = SourceJournalAnomalyKind.CAPACITY_EXHAUSTED,
                    segmentName = segment.file.name,
                    firstAffectedRecordIndex = record.recordIndex,
                    discardedBytes = bytes.size.toLong(),
                    detail = "Required record append failed: ${error.javaClass.simpleName}",
                    stream = stream,
                    firstAffectedSourceSequence = sequence,
                    lastAffectedSourceSequence = sequence,
                ),
            )
            throw SourceJournalCapacityException("Required source record could not be appended", stream, sequence, sequence)
        }
        nextRecordIndex += 1
        nextSourceSequence[stream] = (sequence + 1L) and 0xFFFF_FFFFL
        appendDeliveryEvent(
            SourceDeliveryEvent(
                watchBootSessionId,
                record.recordIndex,
                record.recordIndex,
                SourceDeliveryState.BUFFERED,
                nowWallMs(),
            ),
        )
        if (nowWallMs() - lastSyncAtMs >= maxUncommittedMs) forceSync()
        return record
    }

    @Synchronized
    fun recordDelivery(records: List<WatchSourceRecord>, state: SourceDeliveryState) {
        if (records.isEmpty()) return
        val session = records.first().watchBootSessionId
        require(records.all { it.watchBootSessionId == session }) { "Delivery event cannot cross watch sessions" }
        val ordered = records.sortedBy { it.recordIndex }
        appendDeliveryEvent(
            SourceDeliveryEvent(
                session,
                ordered.first().recordIndex,
                ordered.last().recordIndex,
                state,
                nowWallMs(),
            ),
        )
    }

    @Synchronized
    fun deliveryEvents(): List<SourceDeliveryEvent> {
        val file = File(rootDir, DELIVERY_LOG)
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 5) return@mapNotNull null
            runCatching {
                SourceDeliveryEvent(
                    watchBootSessionId = UUID.fromString(parts[0]),
                    firstRecordIndex = parts[1].toLong(),
                    lastRecordIndex = parts[2].toLong(),
                    state = SourceDeliveryState.valueOf(parts[3]),
                    occurredAtWatchMs = parts[4].toLong(),
                )
            }.getOrNull()
        }
    }

    @Synchronized
    fun forceSync() {
        active?.let {
            it.output.flush()
            it.output.fd.sync()
        }
        deliveryOutput.flush()
        deliveryOutput.fd.sync()
        lastSyncAtMs = nowWallMs()
    }

    @Synchronized
    fun finalizeActiveSegment(): SourceSegmentManifest? {
        val segment = active ?: return null
        forceSync()
        segment.output.close()
        if (segment.lastRecordIndex < segment.firstRecordIndex) {
            segment.file.delete()
            active = null
            return null
        }
        val manifest = manifestFor(segment.file)
        val finalized = File(
            rootDir,
            "segment_${manifest.watchBootSessionId}_${manifest.firstRecordIndex}_${manifest.lastRecordIndex}_${manifest.sha256Hex}.seg",
        )
        if (!segment.file.renameTo(finalized)) throw IllegalStateException("Unable to finalize source journal segment")
        active = null
        val finalizedManifest = manifest.copy(segmentId = finalized.nameWithoutExtension)
        newlyFinalizedManifests.addLast(finalizedManifest)
        return finalizedManifest
    }

    @Synchronized
    fun drainNewlyFinalizedManifests(): List<SourceSegmentManifest> = buildList {
        while (newlyFinalizedManifests.isNotEmpty()) add(newlyFinalizedManifests.removeFirst())
    }

    @Synchronized
    fun finalizedManifests(): List<SourceSegmentManifest> = finalizedFiles()
        .map(::manifestFor)
        .sortedBy { it.firstRecordIndex }

    @Synchronized
    fun finalizedManifests(sessionId: UUID): List<SourceSegmentManifest> = finalizedFiles()
        .filter { segmentFileIndex(it)?.watchBootSessionId == sessionId }
        .map(::manifestFor)
        .sortedBy { it.firstRecordIndex }

    @Synchronized
    fun retainedSessionIds(): List<UUID> = allSegmentFiles()
        .mapNotNull { file -> segmentFileIndex(file)?.watchBootSessionId?.let { session -> file to session } }
        .sortedWith(
            compareBy<Pair<File, UUID>> { (_, session) -> session == watchBootSessionId }
                .thenBy { (file, _) -> file.lastModified() }
                .thenBy { (file, _) -> file.name },
        )
        .map { (_, session) -> session }
        .distinct()

    @Synchronized
    fun highestRecordIndex(sessionId: UUID): Long {
        val finalizedMaximum = finalizedFiles()
            .mapNotNull(::segmentFileIndex)
            .filter { it.watchBootSessionId == sessionId }
            .maxOfOrNull { it.lastRecordIndex ?: 0L }
            ?: 0L
        val openMaximum = active
            ?.takeIf { watchBootSessionId == sessionId }
            ?.lastRecordIndex
            ?: openFiles(sessionId).maxOfOrNull { file ->
                decodeFile(file).records.lastOrNull()?.recordIndex ?: 0L
            }
            ?: 0L
        return maxOf(finalizedMaximum, openMaximum)
    }

    @Synchronized
    fun recordsAfter(sessionId: UUID, recordIndexExclusive: Long): List<WatchSourceRecord> {
        return readRecordsAfter(sessionId, recordIndexExclusive, Int.MAX_VALUE)
    }

    @Synchronized
    fun readRecordsAfter(
        sessionId: UUID,
        recordIndexExclusive: Long,
        limit: Int,
    ): List<WatchSourceRecord> {
        require(limit > 0) { "limit must be positive" }
        val result = ArrayList<WatchSourceRecord>(minOf(limit, 256))
        val files = allSegmentFiles()
            .mapNotNull { file -> segmentFileIndex(file)?.let { index -> file to index } }
            .filter { (_, index) ->
                index.watchBootSessionId == sessionId &&
                    (index.lastRecordIndex == null || index.lastRecordIndex > recordIndexExclusive)
            }
            .sortedBy { (_, index) -> index.firstRecordIndex }
        for ((file, _) in files) {
            for (record in decodeFile(file).records) {
                if (record.watchBootSessionId != sessionId || record.recordIndex <= recordIndexExclusive) continue
                result += record
                if (result.size >= limit) return result
            }
        }
        return result
    }

    @Synchronized
    fun countRecordsAfter(sessionId: UUID, recordIndexExclusive: Long): Long {
        var count = 0L
        finalizedFiles().mapNotNull(::segmentFileIndex)
            .filter { it.watchBootSessionId == sessionId }
            .forEach { index ->
                val last = requireNotNull(index.lastRecordIndex)
                val firstUnacknowledged = maxOf(index.firstRecordIndex, recordIndexExclusive + 1L)
                if (last >= firstUnacknowledged) count += last - firstUnacknowledged + 1L
            }
        openFiles(sessionId).forEach { file ->
            count += decodeFile(file).records.count { it.recordIndex > recordIndexExclusive }
        }
        return count
    }

    @Synchronized
    fun hasFinalizedSegments(sessionId: UUID): Boolean = finalizedFiles()
        .any { segmentFileIndex(it)?.watchBootSessionId == sessionId }

    @Synchronized
    fun acknowledgeCompletedSegment(sessionId: UUID, cumulativeRecordIndex: Long, completedSegmentSha256: String): Boolean {
        val normalizedHash = completedSegmentSha256.lowercase()
        val file = finalizedFiles().firstOrNull {
            val index = segmentFileIndex(it)
            index?.watchBootSessionId == sessionId &&
                index.lastRecordIndex == cumulativeRecordIndex &&
                index.sha256Hex == normalizedHash
        } ?: return false
        val completed = manifestFor(file)
        if (completed.watchBootSessionId != sessionId ||
            completed.lastRecordIndex != cumulativeRecordIndex ||
            completed.sha256Hex != normalizedHash
        ) return false
        return file.delete()
    }

    @Synchronized
    fun snapshot(): SourceJournalSnapshot = SourceJournalSnapshot(
        watchBootSessionId = watchBootSessionId,
        nextRecordIndex = nextRecordIndex,
        activeSegmentBytes = active?.bytes ?: 0L,
        finalizedSegments = finalizedManifests(),
        anomalies = anomalies.toList(),
        preflight = preflightResult,
    )

    @Synchronized
    override fun close() {
        finalizeActiveSegment()
        deliveryOutput.flush()
        deliveryOutput.fd.sync()
        deliveryOutput.close()
    }

    private fun recoverOrphanedSessions() {
        val orphaned = rootDir.listFiles { file ->
            file.isFile && file.name.startsWith("open_") &&
                !file.name.startsWith("open_${watchBootSessionId}_") && file.name.endsWith(".seg")
        }?.sortedBy { it.lastModified() }.orEmpty()
        orphaned.forEach { file ->
            try {
                val result = decodeFile(file)
                if (result.incompleteTailBytes > 0) {
                    RandomAccessFile(file, "rw").use { it.setLength(result.validBytes.toLong()) }
                    val firstAffected = result.records.lastOrNull()?.recordIndex?.plus(1) ?: firstRecordIndexFromName(file.name)
                    recordAnomaly(
                        SourceJournalAnomaly(
                            SourceJournalAnomalyKind.INCOMPLETE_TAIL,
                            file.name,
                            firstAffected,
                            result.incompleteTailBytes.toLong(),
                            "Recovered and truncated prior-boot active-segment tail",
                        ),
                    )
                }
                if (decodeFile(file).records.isNotEmpty()) finalizeRecoveredFile(file) else file.delete()
            } catch (error: Exception) {
                recordAnomaly(
                    SourceJournalAnomaly(
                        SourceJournalAnomalyKind.CORRUPT_SEGMENT,
                        file.name,
                        null,
                        file.length(),
                        error.message ?: "Prior-boot active segment corrupt",
                    ),
                )
            }
        }
    }

    private fun recoverCurrentSession() {
        var maximumIndex = 0L
        val maximumSequence = SourceStreamCode.entries.associateWith { 0L }.toMutableMap()
        finalizedFiles().forEach { file ->
            try {
                val result = decodeFile(file)
                val expectedHash = file.nameWithoutExtension.substringAfterLast('_')
                val actualHash = sha256Hex(file.readBytes())
                if (expectedHash != actualHash) throw SourceJournalCorruptionException("Finalized segment SHA-256 mismatch")
                result.records.filter { it.watchBootSessionId == watchBootSessionId }.forEach { record ->
                    maximumIndex = maxOf(maximumIndex, record.recordIndex)
                    maximumSequence[record.stream] = maxOf(maximumSequence.getValue(record.stream), record.sourceSequence)
                }
            } catch (error: Exception) {
                recordAnomaly(
                    SourceJournalAnomaly(
                        SourceJournalAnomalyKind.CORRUPT_SEGMENT,
                        file.name,
                        null,
                        file.length(),
                        error.message ?: "Finalized segment corrupt",
                    ),
                )
            }
        }
        val openFiles = rootDir.listFiles { file ->
            file.isFile && file.name.startsWith("open_${watchBootSessionId}_") && file.name.endsWith(".seg")
        }?.sortedBy { firstRecordIndexFromName(it.name) }.orEmpty()
        openFiles.forEachIndexed { index, file ->
            try {
                val result = decodeFile(file)
                if (result.incompleteTailBytes > 0) {
                    RandomAccessFile(file, "rw").use { it.setLength(result.validBytes.toLong()) }
                    val firstAffected = result.records.lastOrNull()?.recordIndex?.plus(1) ?: firstRecordIndexFromName(file.name)
                    recordAnomaly(
                        SourceJournalAnomaly(
                            SourceJournalAnomalyKind.INCOMPLETE_TAIL,
                            file.name,
                            firstAffected,
                            result.incompleteTailBytes.toLong(),
                            "Recovered and truncated incomplete active-segment tail",
                        ),
                    )
                }
                result.records.filter { it.watchBootSessionId == watchBootSessionId }.forEach { record ->
                    maximumIndex = maxOf(maximumIndex, record.recordIndex)
                    maximumSequence[record.stream] = maxOf(maximumSequence.getValue(record.stream), record.sourceSequence)
                }
                if (index < openFiles.lastIndex) {
                    finalizeRecoveredFile(file)
                } else {
                    val first = result.records.firstOrNull()?.recordIndex ?: firstRecordIndexFromName(file.name)
                    val last = result.records.lastOrNull()?.recordIndex ?: first - 1
                    active = ActiveSegment(file, first, nowWallMs(), FileOutputStream(file, true), file.length(), last)
                }
            } catch (error: Exception) {
                recordAnomaly(
                    SourceJournalAnomaly(
                        SourceJournalAnomalyKind.CORRUPT_SEGMENT,
                        file.name,
                        null,
                        file.length(),
                        error.message ?: "Active segment corrupt",
                    ),
                )
            }
        }
        nextRecordIndex = maximumIndex + 1L
        SourceStreamCode.entries.forEach { stream ->
            nextSourceSequence[stream] = (maximumSequence.getValue(stream) + 1L) and 0xFFFF_FFFFL
        }
    }

    private fun rotateBeforeAppendIfNeeded(nextBytes: Int) {
        val segment = active ?: return
        val tooLarge = segment.bytes + nextBytes > maxSegmentBytes
        val tooOld = nowWallMs() - segment.openedAtMs >= maxSegmentAgeMs
        if (tooLarge || tooOld) finalizeActiveSegment()
    }

    private fun openNewSegment(firstRecordIndex: Long): ActiveSegment {
        val file = File(rootDir, "open_${watchBootSessionId}_${firstRecordIndex}.seg")
        val opened = ActiveSegment(file, firstRecordIndex, nowWallMs(), FileOutputStream(file, true), file.length(), firstRecordIndex - 1)
        active = opened
        return opened
    }

    private fun finalizeRecoveredFile(file: File) {
        val manifest = manifestFor(file)
        val finalized = File(
            rootDir,
            "segment_${manifest.watchBootSessionId}_${manifest.firstRecordIndex}_${manifest.lastRecordIndex}_${manifest.sha256Hex}.seg",
        )
        if (!file.renameTo(finalized)) throw IllegalStateException("Unable to finalize recovered segment")
    }

    private fun manifestFor(file: File): SourceSegmentManifest {
        val bytes = file.readBytes()
        val decoded = SourceJournalCodec.decodeAll(bytes)
        if (decoded.incompleteTailBytes != 0 || decoded.records.isEmpty()) {
            throw SourceJournalCorruptionException("Segment is incomplete or empty: ${file.name}")
        }
        val first = decoded.records.first()
        if (decoded.records.any { it.watchBootSessionId != first.watchBootSessionId }) {
            throw SourceJournalCorruptionException("Segment crosses watch boot sessions")
        }
        val ranges = decoded.records.groupBy { it.stream }.mapValues { (_, records) ->
            SourceStreamRange(
                count = records.size.toLong(),
                firstSequence = records.first().sourceSequence,
                lastSequence = records.last().sourceSequence,
            )
        }
        return SourceSegmentManifest(
            segmentId = file.nameWithoutExtension,
            watchBootSessionId = first.watchBootSessionId,
            firstRecordIndex = first.recordIndex,
            lastRecordIndex = decoded.records.last().recordIndex,
            recordCount = decoded.records.size.toLong(),
            byteCount = bytes.size.toLong(),
            sha256Hex = sha256Hex(bytes),
            streamRanges = ranges,
        )
    }

    private fun decodeFile(file: File): SourceJournalCodec.DecodeResult = SourceJournalCodec.decodeAll(file.readBytes())

    private fun finalizedFiles(): List<File> = rootDir.listFiles { file ->
        file.isFile && file.name.startsWith("segment_") && file.name.endsWith(".seg")
    }?.toList().orEmpty()

    private fun allSegmentFiles(): List<File> = rootDir.listFiles { file ->
        file.isFile && (file.name.startsWith("segment_") || file.name.startsWith("open_")) && file.name.endsWith(".seg")
    }?.toList().orEmpty()

    private data class SegmentFileIndex(
        val watchBootSessionId: UUID,
        val firstRecordIndex: Long,
        val lastRecordIndex: Long?,
        val sha256Hex: String?,
    )

    private fun segmentFileIndex(file: File): SegmentFileIndex? {
        val parts = file.name.removeSuffix(".seg").split('_')
        return runCatching {
            when (parts.firstOrNull()) {
                "segment" -> {
                    if (parts.size != 5) return@runCatching null
                    SegmentFileIndex(UUID.fromString(parts[1]), parts[2].toLong(), parts[3].toLong(), parts[4])
                }
                "open" -> {
                    if (parts.size != 3) return@runCatching null
                    SegmentFileIndex(UUID.fromString(parts[1]), parts[2].toLong(), null, null)
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun openFiles(sessionId: UUID): List<File> = rootDir.listFiles { file ->
        file.isFile && file.name.startsWith("open_${sessionId}_") && file.name.endsWith(".seg")
    }?.toList().orEmpty()

    private fun firstRecordIndexFromName(name: String): Long = name.removeSuffix(".seg")
        .split('_')
        .firstNotNullOfOrNull { it.toLongOrNull() }
        ?: Long.MAX_VALUE

    private fun readSessionMeta(): SessionMeta? {
        val file = File(rootDir, SESSION_META)
        if (!file.exists()) return null
        return try {
            DataInputStream(FileInputStream(file)).use { input ->
                SessionMeta(input.readInt(), UUID(input.readLong(), input.readLong()))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeSessionMeta(meta: SessionMeta) {
        val temp = File(rootDir, "$SESSION_META.tmp")
        FileOutputStream(temp).use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(meta.bootCount)
                data.writeLong(meta.sessionId.mostSignificantBits)
                data.writeLong(meta.sessionId.leastSignificantBits)
                data.flush()
                output.fd.sync()
            }
        }
        val target = File(rootDir, SESSION_META)
        if (target.exists() && !target.delete()) throw IllegalStateException("Unable to replace session metadata")
        if (!temp.renameTo(target)) throw IllegalStateException("Unable to persist session metadata")
    }

    private fun recordAnomaly(anomaly: SourceJournalAnomaly) {
        anomalies += anomaly
        integrityEligible = false
        FileOutputStream(File(rootDir, ANOMALY_LOG), true).use { output ->
            output.write(
                listOf(
                    nowWallMs().toString(),
                    anomaly.kind.name,
                    anomaly.segmentName ?: "-",
                    anomaly.firstAffectedRecordIndex?.toString() ?: "-",
                    anomaly.stream?.name ?: "-",
                    anomaly.firstAffectedSourceSequence?.toString() ?: "-",
                    anomaly.lastAffectedSourceSequence?.toString() ?: "-",
                    anomaly.discardedBytes.toString(),
                    anomaly.detail.replace('\n', ' '),
                ).joinToString("\t", postfix = "\n").toByteArray(Charsets.UTF_8),
            )
            output.flush()
            output.fd.sync()
        }
    }

    private fun loadPersistedAnomalies() {
        val file = File(rootDir, ANOMALY_LOG)
        if (!file.exists()) return
        file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size < 6) return@forEachLine
            val anomaly = runCatching {
                if (parts.size >= 9) {
                    SourceJournalAnomaly(
                        kind = SourceJournalAnomalyKind.valueOf(parts[1]),
                        segmentName = parts[2].takeUnless { it == "-" },
                        firstAffectedRecordIndex = parts[3].takeUnless { it == "-" }?.toLong(),
                        stream = parts[4].takeUnless { it == "-" }?.let(SourceStreamCode::valueOf),
                        firstAffectedSourceSequence = parts[5].takeUnless { it == "-" }?.toLong(),
                        lastAffectedSourceSequence = parts[6].takeUnless { it == "-" }?.toLong(),
                        discardedBytes = parts[7].toLong(),
                        detail = parts.drop(8).joinToString("\t"),
                    )
                } else {
                    SourceJournalAnomaly(
                        kind = SourceJournalAnomalyKind.valueOf(parts[1]),
                        segmentName = parts[2].takeUnless { it == "-" },
                        firstAffectedRecordIndex = parts[3].takeUnless { it == "-" }?.toLong(),
                        discardedBytes = parts[4].toLong(),
                        detail = parts.drop(5).joinToString("\t"),
                    )
                }
            }.getOrNull() ?: return@forEachLine
            anomalies += anomaly
            integrityEligible = false
        }
    }

    private fun appendDeliveryEvent(event: SourceDeliveryEvent) {
        deliveryOutput.write(
            listOf(
                event.watchBootSessionId.toString(),
                event.firstRecordIndex.toString(),
                event.lastRecordIndex.toString(),
                event.state.name,
                event.occurredAtWatchMs.toString(),
            ).joinToString("\t", postfix = "\n").toByteArray(Charsets.UTF_8),
        )
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
