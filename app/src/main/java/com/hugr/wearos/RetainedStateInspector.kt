package com.hugr.wearos

import java.io.File
import java.security.MessageDigest
import java.util.EnumMap
import java.util.zip.CRC32

/**
 * Passive inspection of the already-retained watch journal and causal recorder.
 *
 * This object never constructs SourceJournal/CausalFlightRecorder and never writes, truncates,
 * renames, deletes, exports, acknowledges, starts services, or exposes payload values. It reads
 * existing files directly and reports bounded metadata plus codec/integrity results.
 */
internal enum class RetainedStateOutcome {
    SOURCE_ROOT_ABSENT,
    SOURCE_STORE_EMPTY,
    SOURCE_STORE_UNREADABLE,
    DEVICE_HEALTH_METADATA_ONLY,
    DEVICE_RETAINED_PHYSIOLOGY_PRESENT,
    DEVICE_RETAINED_PHYSIOLOGY_PRESENT_QUALIFIED,
}

internal data class RetainedStateInspection(
    val outcome: RetainedStateOutcome,
    val journalRootState: String,
    val recorderRootState: String,
    val journalObjectCount: Int,
    val journalBytes: Long,
    val segmentObjectCount: Int,
    val decodedRecordCount: Long,
    val physiologicalRecordCount: Long,
    val payloadSchemaValidPhysiologicalRecordCount: Long,
    val streamCounts: Map<SourceStreamCode, Long>,
    val sessionCount: Int,
    val firstRecordIndex: Long?,
    val lastRecordIndex: Long?,
    val incompleteTailBytes: Long,
    val journalUnreadableObjectCount: Int,
    val finalizedHashMismatchCount: Int,
    val causalEventCount: Int,
    val causalInvalidRowCount: Int,
    val causalIntegrity: String,
    val callbackStreams: Set<CausalStreamCode>,
    val appendStreams: Set<CausalStreamCode>,
    val callbackAndAppendStreams: Set<CausalStreamCode>,
    val latestCausalWallTimeMs: Long?,
    val historicalBaseline: Build46SourceBaseline?,
    val baselineReadState: String,
    val sourceMetadataUnchangedDuringInspection: Boolean,
    val recorderMetadataUnchangedDuringInspection: Boolean,
    val scanDurationMs: Long,
)

internal object RetainedStateInspector {
    private const val JOURNAL_DIRECTORY = "build45_source_journal"
    private const val RECORDER_DIRECTORY = "build47_causal_flight_recorder"
    private const val CAUSAL_EVENTS_FILE = "events_v1.tsv"
    private const val EXPECTED_CAUSAL_FIELDS = 17

    fun inspect(
        filesDir: File,
        onProgress: (String) -> Unit = {},
    ): RetainedStateInspection {
        val started = System.nanoTime()
        val journalRoot = File(filesDir, JOURNAL_DIRECTORY)
        val recorderRoot = File(filesDir, RECORDER_DIRECTORY)
        val journalBefore = metadataSnapshot(journalRoot)
        val recorderBefore = metadataSnapshot(recorderRoot)
        val journalObjects = regularFiles(journalRoot)
        val recorderObjects = regularFiles(recorderRoot)
        val segmentObjects = journalObjects.filter { it.name.endsWith(".seg") }

        onProgress(
            "Inventory: journalFiles=${journalObjects.size} " +
                "segments=${segmentObjects.size} bytes=${journalObjects.sumOf { it.length() }}",
        )

        val streamCounts = EnumMap<SourceStreamCode, Long>(SourceStreamCode::class.java).apply {
            SourceStreamCode.entries.forEach { put(it, 0L) }
        }
        val sessions = linkedSetOf<java.util.UUID>()
        var decodedRecordCount = 0L
        var physiologicalRecordCount = 0L
        var schemaValidPhysiologicalRecordCount = 0L
        var firstRecordIndex: Long? = null
        var lastRecordIndex: Long? = null
        var incompleteTailBytes = 0L
        var unreadableObjects = 0
        var finalizedHashMismatches = 0

        segmentObjects.sortedBy { it.name }.forEachIndexed { index, file ->
            if (index == 0 || (index + 1) % 25 == 0 || index == segmentObjects.lastIndex) {
                onProgress("Reading retained segments ${index + 1}/${segmentObjects.size}")
            }
            try {
                val bytes = file.readBytes()
                if (file.name.startsWith("segment_")) {
                    val expected = file.nameWithoutExtension.substringAfterLast('_')
                    if (!expected.matches(Regex("[0-9a-f]{64}")) || sha256(bytes) != expected) {
                        finalizedHashMismatches += 1
                    }
                }
                val decoded = SourceJournalCodec.decodeAll(bytes)
                incompleteTailBytes += decoded.incompleteTailBytes.toLong()
                decoded.records.forEach { record ->
                    decodedRecordCount += 1L
                    sessions += record.watchBootSessionId
                    streamCounts[record.stream] = streamCounts.getValue(record.stream) + 1L
                    firstRecordIndex = firstRecordIndex?.let { minOf(it, record.recordIndex) } ?: record.recordIndex
                    lastRecordIndex = lastRecordIndex?.let { maxOf(it, record.recordIndex) } ?: record.recordIndex
                    if (record.stream != SourceStreamCode.DEVICE_HEALTH) {
                        physiologicalRecordCount += 1L
                        if (payloadSchemaIsValid(record)) schemaValidPhysiologicalRecordCount += 1L
                    }
                }
            } catch (_: Exception) {
                unreadableObjects += 1
            }
        }

        onProgress("Reading causal recorder metadata")
        val causal = inspectCausalFile(File(recorderRoot, CAUSAL_EVENTS_FILE))
        val baselineResult = runCatching { Build46BaselineStore(recorderRoot).load() }
        val baseline = baselineResult.getOrNull()
        val baselineState = when {
            !recorderRoot.isDirectory -> "RECORDER_ROOT_ABSENT"
            baseline != null -> "READABLE_HISTORICAL_BASELINE"
            baselineResult.isFailure -> "BASELINE_UNREADABLE"
            else -> "NO_BASELINE_FILE"
        }

        val physiologicalCausalStreams = streamCounts
            .filter { (stream, count) -> stream != SourceStreamCode.DEVICE_HEALTH && count > 0L }
            .keys
            .mapNotNull(::causalStream)
            .toSet()
        val callbackAndAppend = physiologicalCausalStreams
            .intersect(causal.callbackStreams)
            .intersect(causal.appendStreams)

        val outcome = when {
            !journalRoot.exists() -> RetainedStateOutcome.SOURCE_ROOT_ABSENT
            segmentObjects.isEmpty() -> RetainedStateOutcome.SOURCE_STORE_EMPTY
            decodedRecordCount == 0L && unreadableObjects > 0 -> RetainedStateOutcome.SOURCE_STORE_UNREADABLE
            physiologicalRecordCount > 0L &&
                (unreadableObjects > 0 || incompleteTailBytes > 0L || finalizedHashMismatches > 0) ->
                RetainedStateOutcome.DEVICE_RETAINED_PHYSIOLOGY_PRESENT_QUALIFIED
            physiologicalRecordCount > 0L -> RetainedStateOutcome.DEVICE_RETAINED_PHYSIOLOGY_PRESENT
            decodedRecordCount > 0L -> RetainedStateOutcome.DEVICE_HEALTH_METADATA_ONLY
            else -> RetainedStateOutcome.SOURCE_STORE_EMPTY
        }

        return RetainedStateInspection(
            outcome = outcome,
            journalRootState = rootState(journalRoot),
            recorderRootState = rootState(recorderRoot),
            journalObjectCount = journalObjects.size,
            journalBytes = journalObjects.sumOf { it.length() },
            segmentObjectCount = segmentObjects.size,
            decodedRecordCount = decodedRecordCount,
            physiologicalRecordCount = physiologicalRecordCount,
            payloadSchemaValidPhysiologicalRecordCount = schemaValidPhysiologicalRecordCount,
            streamCounts = streamCounts,
            sessionCount = sessions.size,
            firstRecordIndex = firstRecordIndex,
            lastRecordIndex = lastRecordIndex,
            incompleteTailBytes = incompleteTailBytes,
            journalUnreadableObjectCount = unreadableObjects,
            finalizedHashMismatchCount = finalizedHashMismatches,
            causalEventCount = causal.eventCount,
            causalInvalidRowCount = causal.invalidRowCount,
            causalIntegrity = causal.integrity,
            callbackStreams = causal.callbackStreams,
            appendStreams = causal.appendStreams,
            callbackAndAppendStreams = callbackAndAppend,
            latestCausalWallTimeMs = causal.latestWallTimeMs,
            historicalBaseline = baseline,
            baselineReadState = baselineState,
            sourceMetadataUnchangedDuringInspection = journalBefore == metadataSnapshot(journalRoot),
            recorderMetadataUnchangedDuringInspection = recorderBefore == metadataSnapshot(recorderRoot),
            scanDurationMs = (System.nanoTime() - started) / 1_000_000L,
        )
    }

    private data class CausalInspection(
        val eventCount: Int,
        val invalidRowCount: Int,
        val integrity: String,
        val callbackStreams: Set<CausalStreamCode>,
        val appendStreams: Set<CausalStreamCode>,
        val latestWallTimeMs: Long?,
    )

    private fun inspectCausalFile(file: File): CausalInspection {
        if (!file.isFile) {
            return CausalInspection(0, 0, "ABSENT", emptySet(), emptySet(), null)
        }
        val callbacks = linkedSetOf<CausalStreamCode>()
        val appends = linkedSetOf<CausalStreamCode>()
        var validRows = 0
        var invalidRows = 0
        var latestWallTime: Long? = null
        var previousSequence = 0L
        file.useLines { lines ->
            lines.forEach { line ->
                val row = decodeCausalRow(line)
                if (row == null || row.sequence <= previousSequence) {
                    invalidRows += 1
                } else {
                    validRows += 1
                    previousSequence = row.sequence
                    latestWallTime = latestWallTime?.let { maxOf(it, row.wallTimeMs) } ?: row.wallTimeMs
                    if (row.code == CausalEventCode.FIRST_CALLBACK && row.stream != null) callbacks += row.stream
                    if (row.code == CausalEventCode.FIRST_APPEND && row.stream != null) appends += row.stream
                }
            }
        }
        val integrity = when {
            invalidRows == 0 -> "READABLE"
            validRows > 0 -> "QUALIFIED_INVALID_ROWS"
            else -> "UNREADABLE"
        }
        return CausalInspection(validRows, invalidRows, integrity, callbacks, appends, latestWallTime)
    }

    private data class CausalRow(
        val sequence: Long,
        val wallTimeMs: Long,
        val code: CausalEventCode,
        val stream: CausalStreamCode?,
    )

    private fun decodeCausalRow(line: String): CausalRow? = runCatching {
        val fields = line.split('\t')
        require(fields.size == EXPECTED_CAUSAL_FIELDS)
        val body = fields.dropLast(1).joinToString("\t")
        require(crc32Hex(body) == fields.last().lowercase())
        val streamCode = fields[10].toInt()
        CausalRow(
            sequence = fields[1].toLong().also { require(it > 0L) },
            wallTimeMs = fields[8].toLong(),
            code = CausalEventCode.fromWireCode(fields[9].toInt()),
            stream = if (streamCode == 0) null else CausalStreamCode.fromWireCode(streamCode),
        )
    }.getOrNull()

    private fun payloadSchemaIsValid(record: WatchSourceRecord): Boolean {
        val expectedSize = when (record.stream) {
            SourceStreamCode.EDA -> 8
            SourceStreamCode.ACCEL -> 16
            SourceStreamCode.SKIN_TEMP -> 16
            SourceStreamCode.CARDIAC -> 25
            SourceStreamCode.DEVICE_HEALTH -> 58
        }
        return record.payload.size == expectedSize &&
            record.payload.firstOrNull()?.toInt()?.and(0xFF) == SourcePayloadCodec.VERSION
    }

    private fun causalStream(stream: SourceStreamCode): CausalStreamCode? = when (stream) {
        SourceStreamCode.CARDIAC -> CausalStreamCode.CARDIAC
        SourceStreamCode.EDA -> CausalStreamCode.EDA
        SourceStreamCode.ACCEL -> CausalStreamCode.ACCEL
        SourceStreamCode.SKIN_TEMP -> CausalStreamCode.SKIN_TEMP
        SourceStreamCode.DEVICE_HEALTH -> CausalStreamCode.DEVICE_HEALTH
    }

    private fun regularFiles(root: File): List<File> = if (!root.isDirectory) {
        emptyList()
    } else {
        root.walkTopDown().filter { it.isFile }.toList()
    }

    private fun rootState(root: File): String = when {
        !root.exists() -> "ABSENT"
        root.isDirectory -> "DIRECTORY"
        else -> "NOT_DIRECTORY"
    }

    private data class FileMetadata(val relativePath: String, val size: Long, val modifiedAt: Long)

    private fun metadataSnapshot(root: File): List<FileMetadata> = regularFiles(root)
        .map { FileMetadata(it.relativeTo(root).invariantSeparatorsPath, it.length(), it.lastModified()) }
        .sortedBy { it.relativePath }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun crc32Hex(body: String): String = CRC32().apply {
        update(body.toByteArray(Charsets.UTF_8))
    }.value.toString(16).padStart(8, '0')
}
