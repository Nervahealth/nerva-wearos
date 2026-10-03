package com.hugr.wearos

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32

internal data class SourceDataFrame(
    val watchBootSessionId: UUID,
    val replay: Boolean,
    val records: List<WatchSourceRecord>,
)

internal data class SourceResumeRequest(
    val watchBootSessionId: UUID?,
    val cumulativeRecordIndex: Long,
)

internal data class SourceSegmentAcknowledgement(
    val watchBootSessionId: UUID,
    val cumulativeRecordIndex: Long,
    val completedSegmentSha256: String,
)

internal data class SourceManifestFrame(
    val manifest: SourceSegmentManifest,
)

internal object SourceReplayProtocol {
    const val MARKER = 0xA5
    const val VERSION = 1
    const val DATA_FRAME = 1
    const val RESUME_REQUEST = 2
    const val SEGMENT_ACKNOWLEDGEMENT = 3
    const val MANIFEST_FRAME = 4
    const val MIN_ATT_PAYLOAD_FOR_FIVE_ACCEL = 364

    private const val DATA_HEADER_BYTES = 30
    private const val DATA_CRC_BYTES = 4
    private const val RESUME_BYTES = 27
    private const val ACK_BYTES = 59
    private const val MANIFEST_FIXED_BYTES = 84

    fun buildDataFrames(
        records: List<WatchSourceRecord>,
        replay: Boolean,
        maximumAttPayloadBytes: Int,
    ): List<ByteArray> {
        require(maximumAttPayloadBytes >= DATA_HEADER_BYTES + DATA_CRC_BYTES + 2) {
            "Negotiated ATT payload is too small for source records"
        }
        if (records.isEmpty()) return emptyList()
        val result = mutableListOf<ByteArray>()
        var cursor = 0
        while (cursor < records.size) {
            val first = records[cursor]
            val frameRecords = mutableListOf<WatchSourceRecord>()
            var encodedBytes = DATA_HEADER_BYTES + DATA_CRC_BYTES
            while (cursor < records.size) {
                val candidate = records[cursor]
                if (candidate.watchBootSessionId != first.watchBootSessionId) break
                if (candidate.stream != first.stream) break
                val candidateBytes = candidate.canonicalBytes().size + 2
                if (encodedBytes + candidateBytes > maximumAttPayloadBytes) break
                frameRecords += candidate
                encodedBytes += candidateBytes
                cursor += 1
            }
            if (frameRecords.isEmpty()) {
                throw IllegalArgumentException("Canonical source record does not fit negotiated ATT payload")
            }
            result += encodeDataFrame(SourceDataFrame(first.watchBootSessionId, replay, frameRecords))
        }
        return result
    }

    fun encodeDataFrame(frame: SourceDataFrame): ByteArray {
        require(frame.records.isNotEmpty()) { "Source data frame must contain records" }
        require(frame.records.size <= 65_535) { "Source data frame record count exceeds uint16" }
        val stream = frame.records.first().stream
        require(frame.records.all {
            it.watchBootSessionId == frame.watchBootSessionId && it.stream == stream
        }) { "A source data frame may contain only one session and one stream" }
        val recordBytes = frame.records.map(WatchSourceRecord::canonicalBytes)
        val size = DATA_HEADER_BYTES + recordBytes.sumOf { it.size + 2 } + DATA_CRC_BYTES
        val withoutCrc = ByteBuffer.allocate(size - DATA_CRC_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MARKER.toByte())
            .put(VERSION.toByte())
            .put(DATA_FRAME.toByte())
            .put(if (frame.replay) 0x01 else 0x00)
            .putLong(frame.watchBootSessionId.mostSignificantBits)
            .putLong(frame.watchBootSessionId.leastSignificantBits)
            .putLong(frame.records.first().recordIndex)
            .putShort(frame.records.size.toShort())
        recordBytes.forEach { bytes ->
            withoutCrc.putShort(bytes.size.toShort())
            withoutCrc.put(bytes)
        }
        val body = withoutCrc.array()
        val crc = CRC32().apply { update(body) }.value
        return ByteBuffer.allocate(size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(body)
            .putInt(crc.toInt())
            .array()
    }

    fun decodeDataFrame(bytes: ByteArray): SourceDataFrame {
        validateFrameCrc(bytes, DATA_HEADER_BYTES + DATA_CRC_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        requireHeader(buffer, DATA_FRAME)
        val replay = (buffer.get().toInt() and 0x01) != 0
        val session = UUID(buffer.long, buffer.long)
        val firstRecordIndex = buffer.long
        val recordCount = buffer.short.toInt() and 0xFFFF
        if (recordCount == 0) throw SourceJournalCorruptionException("Source data frame is empty")
        val records = ArrayList<WatchSourceRecord>(recordCount)
        repeat(recordCount) {
            if (buffer.remaining() < 2 + DATA_CRC_BYTES) throw SourceJournalCorruptionException("Source frame record length missing")
            val length = buffer.short.toInt() and 0xFFFF
            if (buffer.remaining() < length + DATA_CRC_BYTES) throw SourceJournalCorruptionException("Source frame record truncated")
            val recordBytes = ByteArray(length)
            buffer.get(recordBytes)
            val decoded = SourceJournalCodec.decodeAll(recordBytes)
            if (decoded.records.size != 1 || decoded.validBytes != recordBytes.size) {
                throw SourceJournalCorruptionException("Source frame contains a non-canonical record")
            }
            records += decoded.records.single()
        }
        if (buffer.position() != bytes.size - DATA_CRC_BYTES) {
            throw SourceJournalCorruptionException("Source frame has trailing bytes before CRC")
        }
        if (records.first().recordIndex != firstRecordIndex) {
            throw SourceJournalCorruptionException("Source frame first-record index mismatch")
        }
        if (records.any { it.watchBootSessionId != session || it.stream != records.first().stream }) {
            throw SourceJournalCorruptionException("Source frame session/stream mismatch")
        }
        return SourceDataFrame(session, replay, records)
    }

    fun encodeResumeRequest(request: SourceResumeRequest): ByteArray {
        val session = request.watchBootSessionId ?: UUID(0L, 0L)
        return ByteBuffer.allocate(RESUME_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MARKER.toByte())
            .put(VERSION.toByte())
            .put(RESUME_REQUEST.toByte())
            .putLong(session.mostSignificantBits)
            .putLong(session.leastSignificantBits)
            .putLong(request.cumulativeRecordIndex)
            .array()
    }

    fun decodeResumeRequest(bytes: ByteArray): SourceResumeRequest {
        if (bytes.size != RESUME_BYTES) throw SourceJournalCorruptionException("Malformed resume request (${bytes.size} bytes)")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        requireHeader(buffer, RESUME_REQUEST)
        val session = UUID(buffer.long, buffer.long)
        val index = buffer.long
        return SourceResumeRequest(if (session == UUID(0L, 0L)) null else session, index)
    }

    fun encodeSegmentAcknowledgement(acknowledgement: SourceSegmentAcknowledgement): ByteArray {
        val hash = acknowledgement.completedSegmentSha256.lowercase()
        if (!hash.matches(Regex("[0-9a-f]{64}"))) throw IllegalArgumentException("Completed segment SHA-256 is malformed")
        val hashBytes = ByteArray(32) { index -> hash.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        return ByteBuffer.allocate(ACK_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MARKER.toByte())
            .put(VERSION.toByte())
            .put(SEGMENT_ACKNOWLEDGEMENT.toByte())
            .putLong(acknowledgement.watchBootSessionId.mostSignificantBits)
            .putLong(acknowledgement.watchBootSessionId.leastSignificantBits)
            .putLong(acknowledgement.cumulativeRecordIndex)
            .put(hashBytes)
            .array()
    }

    fun decodeSegmentAcknowledgement(bytes: ByteArray): SourceSegmentAcknowledgement {
        if (bytes.size != ACK_BYTES) throw SourceJournalCorruptionException("Malformed source acknowledgement (${bytes.size} bytes)")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        requireHeader(buffer, SEGMENT_ACKNOWLEDGEMENT)
        val session = UUID(buffer.long, buffer.long)
        val index = buffer.long
        val hashBytes = ByteArray(32)
        buffer.get(hashBytes)
        return SourceSegmentAcknowledgement(
            watchBootSessionId = session,
            cumulativeRecordIndex = index,
            completedSegmentSha256 = hashBytes.joinToString("") { "%02x".format(it) },
        )
    }

    fun encodeManifestFrame(frame: SourceManifestFrame): ByteArray {
        val manifest = frame.manifest
        val hash = manifest.sha256Hex.lowercase()
        if (!hash.matches(Regex("[0-9a-f]{64}"))) throw IllegalArgumentException("Manifest SHA-256 is malformed")
        if (manifest.streamRanges.size > 255) throw IllegalArgumentException("Manifest contains too many streams")
        val hashBytes = ByteArray(32) { index -> hash.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        val buffer = ByteBuffer.allocate(MANIFEST_FIXED_BYTES + manifest.streamRanges.size * 13)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(MARKER.toByte())
            .put(VERSION.toByte())
            .put(MANIFEST_FRAME.toByte())
            .putLong(manifest.watchBootSessionId.mostSignificantBits)
            .putLong(manifest.watchBootSessionId.leastSignificantBits)
            .putLong(manifest.firstRecordIndex)
            .putLong(manifest.lastRecordIndex)
            .putLong(manifest.recordCount)
            .putLong(manifest.byteCount)
            .put(hashBytes)
            .put(manifest.streamRanges.size.toByte())
        manifest.streamRanges.toSortedMap(compareBy { it.wireCode }).forEach { (stream, range) ->
            require(range.count in 0..0xFFFF_FFFFL) { "Manifest stream count exceeds uint32" }
            buffer.put(stream.wireCode.toByte())
            buffer.putInt(range.count.toInt())
            buffer.putInt(range.firstSequence.toInt())
            buffer.putInt(range.lastSequence.toInt())
        }
        return buffer.array()
    }

    fun decodeManifestFrame(bytes: ByteArray): SourceManifestFrame {
        if (bytes.size < MANIFEST_FIXED_BYTES) throw SourceJournalCorruptionException("Malformed source manifest (${bytes.size} bytes)")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        requireHeader(buffer, MANIFEST_FRAME)
        val session = UUID(buffer.long, buffer.long)
        val firstIndex = buffer.long
        val lastIndex = buffer.long
        val recordCount = buffer.long
        val byteCount = buffer.long
        val hashBytes = ByteArray(32)
        buffer.get(hashBytes)
        val rangeCount = buffer.get().toInt() and 0xFF
        if (buffer.remaining() != rangeCount * 13) throw SourceJournalCorruptionException("Source manifest range length mismatch")
        val ranges = mutableMapOf<SourceStreamCode, SourceStreamRange>()
        repeat(rangeCount) {
            val stream = SourceStreamCode.fromWireCode(buffer.get().toInt() and 0xFF)
            if (ranges.containsKey(stream)) throw SourceJournalCorruptionException("Source manifest duplicates stream ${stream.name}")
            ranges[stream] = SourceStreamRange(
                count = buffer.int.toLong() and 0xFFFF_FFFFL,
                firstSequence = buffer.int.toLong() and 0xFFFF_FFFFL,
                lastSequence = buffer.int.toLong() and 0xFFFF_FFFFL,
            )
        }
        return SourceManifestFrame(
            SourceSegmentManifest(
                segmentId = "segment_${session}_${firstIndex}_${lastIndex}_${hashBytes.joinToString("") { "%02x".format(it) }}",
                watchBootSessionId = session,
                firstRecordIndex = firstIndex,
                lastRecordIndex = lastIndex,
                recordCount = recordCount,
                byteCount = byteCount,
                sha256Hex = hashBytes.joinToString("") { "%02x".format(it) },
                streamRanges = ranges,
            ),
        )
    }

    private fun validateFrameCrc(bytes: ByteArray, minimumLength: Int) {
        if (bytes.size < minimumLength) throw SourceJournalCorruptionException("Source frame is too short")
        val expected = ByteBuffer.wrap(bytes, bytes.size - DATA_CRC_BYTES, DATA_CRC_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
            .toLong() and 0xFFFF_FFFFL
        val actual = CRC32().apply { update(bytes, 0, bytes.size - DATA_CRC_BYTES) }.value
        if (actual != expected) throw SourceJournalCorruptionException("Source frame CRC mismatch")
    }

    private fun requireHeader(buffer: ByteBuffer, expectedType: Int) {
        val marker = buffer.get().toInt() and 0xFF
        val version = buffer.get().toInt() and 0xFF
        val type = buffer.get().toInt() and 0xFF
        if (marker != MARKER || version != VERSION || type != expectedType) {
            throw SourceJournalCorruptionException("Unsupported source protocol header")
        }
    }
}
