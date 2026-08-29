package com.hugr.wearos

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.EnumMap
import java.util.UUID
import java.util.zip.CRC32

internal enum class CausalComponentCode(val wireCode: Int) {
    RUNTIME(1),
    ACTIVITY(2),
    HEALTH(3),
    BLE(4);

    companion object {
        fun fromWireCode(code: Int): CausalComponentCode =
            entries.firstOrNull { it.wireCode == code }
                ?: throw IllegalArgumentException("Unknown causal component code: $code")
    }
}

internal enum class CausalStreamCode(val wireCode: Int) {
    CARDIAC(1),
    EDA(2),
    PPG(3),
    ACCEL(4),
    SKIN_TEMP(5),
    DEVICE_HEALTH(6);

    companion object {
        fun fromWireCode(code: Int): CausalStreamCode =
            entries.firstOrNull { it.wireCode == code }
                ?: throw IllegalArgumentException("Unknown causal stream code: $code")

        fun fromSourceStream(stream: SourceStreamCode): CausalStreamCode = when (stream) {
            SourceStreamCode.CARDIAC -> CARDIAC
            SourceStreamCode.EDA -> EDA
            SourceStreamCode.ACCEL -> ACCEL
            SourceStreamCode.SKIN_TEMP -> SKIN_TEMP
            SourceStreamCode.DEVICE_HEALTH -> DEVICE_HEALTH
        }
    }
}

internal enum class CausalEventCode(val wireCode: Int) {
    PROCESS_STARTED(1),
    ACTIVITY_CREATED(2),
    PERMISSION_SNAPSHOT(3),
    SERVICES_START_REQUESTED(4),
    B46_BASELINE_SUMMARY(5),
    RECORDER_RECOVERED_TAIL(6),
    RECORDER_DEGRADED(7),
    HEALTH_SERVICE_CREATED(20),
    HEALTH_START_COMMAND(21),
    SOURCE_JOURNAL_READY(22),
    SOURCE_JOURNAL_REFUSED(23),
    SDK_CONNECT_REQUESTED(24),
    SDK_ALREADY_CONNECTED(25),
    SDK_CONNECTED(26),
    SDK_CONNECTION_ENDED(27),
    SDK_CONNECTION_FAILED(28),
    TRACKER_START_ATTEMPT(29),
    TRACKER_STARTED(30),
    TRACKER_UNSUPPORTED(31),
    TRACKER_START_FAILED(32),
    FIRST_CALLBACK(33),
    CALLBACK_ERROR(34),
    FIRST_APPEND(35),
    APPEND_FAILED(36),
    SOURCE_BROADCAST_SENT(37),
    JOURNAL_SYNC_FAILED(38),
    HEALTH_SERVICE_DESTROYED(39),
    BLE_SERVICE_CREATED(60),
    GATT_CONNECTED(61),
    MTU_CHANGED(62),
    SOURCE_CCCD_ENABLED(63),
    SOURCE_CCCD_DISABLED(64),
    RESUME_RECEIVED(65),
    RESUME_DEFERRED(66),
    RESUME_APPLIED(67),
    SOURCE_BROADCAST_RECEIVED(68),
    SOURCE_ENQUEUED(69),
    SOURCE_TRIGGER_RESULT(70),
    SOURCE_NOTIFICATION_COMPLETED(71),
    SOURCE_NOTIFICATION_FAILED(72),
    SOURCE_NOTIFICATION_TIMEOUT(73),
    QUEUE_CRITICAL_FAULT(74),
    ABORT_REQUESTED(75),
    CANCEL_CONNECTION_REQUESTED(76),
    GATT_DISCONNECTED(77),
    BLE_SERVICE_DESTROYED(78),
    TRANSPORT_SNAPSHOT(79);

    companion object {
        fun fromWireCode(code: Int): CausalEventCode =
            entries.firstOrNull { it.wireCode == code }
                ?: throw IllegalArgumentException("Unknown causal event code: $code")
    }
}

internal enum class CausalReasonCode(val code: Int) {
    NONE(0),
    TRIGGERED(1),
    IMMEDIATE_FAILURE(2),
    NOT_SUBSCRIBED(3),
    NO_CONNECTION(4),
    CRITICAL_QUEUE_FAULT(20),
    LIVE_SOURCE_RECORD_REJECTED(21),
    SOURCE_FRAME_ENQUEUE_FAILED(22),
    SOURCE_TRIGGER_DECODE_FAILED(23),
    SOURCE_COMPLETION_DECODE_FAILED(24),
    SOURCE_NOTIFICATION_FAILED(25),
    NOTIFICATION_TIMEOUT(26),
    UNSAFE_MTU_AFTER_RELEASE(27),
    UNKNOWN(255);

    companion object {
        fun fromTrigger(trigger: GattNotificationTrigger): CausalReasonCode = when (trigger) {
            GattNotificationTrigger.TRIGGERED -> TRIGGERED
            GattNotificationTrigger.IMMEDIATE_FAILURE -> IMMEDIATE_FAILURE
            GattNotificationTrigger.NOT_SUBSCRIBED -> NOT_SUBSCRIBED
            GattNotificationTrigger.NO_CONNECTION -> NO_CONNECTION
        }

        fun fromAbortReason(reason: String): CausalReasonCode = when (reason) {
            "critical_queue_fault" -> CRITICAL_QUEUE_FAULT
            "live_source_record_rejected" -> LIVE_SOURCE_RECORD_REJECTED
            "source_frame_enqueue_failed" -> SOURCE_FRAME_ENQUEUE_FAILED
            "source_trigger_decode_failed" -> SOURCE_TRIGGER_DECODE_FAILED
            "source_completion_decode_failed" -> SOURCE_COMPLETION_DECODE_FAILED
            "source_notification_failed" -> SOURCE_NOTIFICATION_FAILED
            "notification_timeout" -> NOTIFICATION_TIMEOUT
            "unsafe_mtu_after_release" -> UNSAFE_MTU_AFTER_RELEASE
            else -> UNKNOWN
        }
    }
}

internal enum class CausalRecorderIntegrity {
    OK,
    RECOVERED_TAIL,
    DEGRADED,
}

internal data class CausalFlightEvent(
    val schemaVersion: Int,
    val eventSequence: Long,
    val watchBootSessionId: UUID,
    val processInstanceId: UUID,
    val component: CausalComponentCode,
    val componentInstanceId: UUID,
    val bleLineage: Long,
    val elapsedRealtimeMs: Long,
    val wallTimeMs: Long,
    val code: CausalEventCode,
    val stream: CausalStreamCode?,
    val recordIndexStart: Long,
    val recordIndexEnd: Long,
    val arg0: Long,
    val arg1: Long,
    val reasonCode: Int,
)

internal class CausalFlightRecorder(
    private val rootDir: File,
    private val watchBootSessionId: UUID,
    private val processInstanceId: UUID,
    private val nowElapsedMs: () -> Long,
    private val nowWallMs: () -> Long,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val retainEvents: Int = DEFAULT_RETAIN_EVENTS,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : AutoCloseable {
    companion object {
        const val EVENTS_FILE_NAME = "events_v1.tsv"
        private const val SCHEMA_VERSION = 1
        private const val EXPECTED_FIELDS_WITH_CRC = 17
        private const val DEFAULT_MAX_EVENTS = 512
        private const val DEFAULT_RETAIN_EVENTS = 384
        private const val DEFAULT_MAX_BYTES = 128L * 1_024L
    }

    private val eventsFile = File(rootDir, EVENTS_FILE_NAME)
    private val recordedEvents = mutableListOf<CausalFlightEvent>()
    private var nextSequence = 1L
    private var integrityState = CausalRecorderIntegrity.OK

    init {
        require(maxEvents > 0) { "maxEvents must be positive" }
        require(retainEvents in 1..maxEvents) { "retainEvents must fit maxEvents" }
        require(maxBytes > 0L) { "maxBytes must be positive" }
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw IllegalStateException("Unable to create causal flight-recorder directory")
        }
        recover()
    }

    @Synchronized
    fun record(
        code: CausalEventCode,
        component: CausalComponentCode,
        componentInstanceId: UUID,
        bleLineage: Long = 0L,
        stream: CausalStreamCode? = null,
        recordIndexStart: Long = 0L,
        recordIndexEnd: Long = 0L,
        arg0: Long = 0L,
        arg1: Long = 0L,
        reasonCode: Int = CausalReasonCode.NONE.code,
    ): CausalFlightEvent? {
        if (integrityState == CausalRecorderIntegrity.DEGRADED) return null
        require(bleLineage >= 0L) { "bleLineage cannot be negative" }
        require(recordIndexStart >= 0L && recordIndexEnd >= 0L) { "record indexes cannot be negative" }
        require(recordIndexEnd == 0L || recordIndexEnd >= recordIndexStart) { "record index range is invalid" }
        val event = CausalFlightEvent(
            schemaVersion = SCHEMA_VERSION,
            eventSequence = nextSequence,
            watchBootSessionId = watchBootSessionId,
            processInstanceId = processInstanceId,
            component = component,
            componentInstanceId = componentInstanceId,
            bleLineage = bleLineage,
            elapsedRealtimeMs = nowElapsedMs(),
            wallTimeMs = nowWallMs(),
            code = code,
            stream = stream,
            recordIndexStart = recordIndexStart,
            recordIndexEnd = recordIndexEnd,
            arg0 = arg0,
            arg1 = arg1,
            reasonCode = reasonCode,
        )
        appendDurably(event)
        recordedEvents += event
        nextSequence += 1L
        compactIfNeeded()
        return event
    }

    @Synchronized
    fun events(): List<CausalFlightEvent> = recordedEvents.toList()

    @Synchronized
    fun integrity(): CausalRecorderIntegrity = integrityState

    @Synchronized
    fun markDegraded() {
        integrityState = CausalRecorderIntegrity.DEGRADED
    }

    @Synchronized
    override fun close() = Unit

    private fun recover() {
        if (!eventsFile.exists()) return
        val bytes = eventsFile.readBytes()
        var offset = 0
        var validEnd = 0
        var lastSequence = 0L
        var recoveredTail = false
        while (offset < bytes.size) {
            val newline = indexOfNewline(bytes, offset)
            if (newline < 0) {
                recoveredTail = true
                break
            }
            val line = bytes.copyOfRange(offset, newline).toString(Charsets.UTF_8)
            val parsed = runCatching { parse(line) }.getOrNull()
            if (parsed == null || parsed.eventSequence <= lastSequence) {
                if (newline == bytes.lastIndex) {
                    recoveredTail = true
                    break
                }
                integrityState = CausalRecorderIntegrity.DEGRADED
                return
            }
            recordedEvents += parsed
            lastSequence = parsed.eventSequence
            validEnd = newline + 1
            offset = newline + 1
        }
        nextSequence = lastSequence + 1L
        if (recoveredTail) {
            RandomAccessFile(eventsFile, "rw").use { it.setLength(validEnd.toLong()) }
            integrityState = CausalRecorderIntegrity.RECOVERED_TAIL
            record(
                CausalEventCode.RECORDER_RECOVERED_TAIL,
                CausalComponentCode.RUNTIME,
                processInstanceId,
                arg0 = (bytes.size - validEnd).toLong(),
            )
        }
    }

    private fun appendDurably(event: CausalFlightEvent) {
        FileOutputStream(eventsFile, true).use { output ->
            output.write(serialize(event).toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    private fun compactIfNeeded() {
        if (recordedEvents.size <= maxEvents && eventsFile.length() <= maxBytes) return
        val baselineAnchor = recordedEvents.firstOrNull { it.code == CausalEventCode.B46_BASELINE_SUMMARY }
        val suffixSize = (retainEvents - if (baselineAnchor == null) 0 else 1).coerceAtLeast(0)
        var retained = (listOfNotNull(baselineAnchor) + recordedEvents.takeLast(suffixSize))
            .distinctBy { it.eventSequence }
            .sortedBy { it.eventSequence }
        while (retained.size > 1 && serializedBytes(retained) > maxBytes) {
            val removalIndex = if (baselineAnchor != null && retained.first().eventSequence == baselineAnchor.eventSequence) 1 else 0
            retained = retained.toMutableList().also { it.removeAt(removalIndex) }
        }
        val temp = File(rootDir, "$EVENTS_FILE_NAME.tmp")
        FileOutputStream(temp).use { output ->
            retained.forEach { output.write(serialize(it).toByteArray(Charsets.UTF_8)) }
            output.flush()
            output.fd.sync()
        }
        if (eventsFile.exists() && !eventsFile.delete()) throw IllegalStateException("Unable to replace causal flight-recorder file")
        if (!temp.renameTo(eventsFile)) throw IllegalStateException("Unable to persist compacted causal flight recorder")
        recordedEvents.clear()
        recordedEvents.addAll(retained)
    }

    private fun serializedBytes(events: List<CausalFlightEvent>): Long =
        events.sumOf { serialize(it).toByteArray(Charsets.UTF_8).size.toLong() }

    private fun serialize(event: CausalFlightEvent): String {
        val body = listOf(
            event.schemaVersion,
            event.eventSequence,
            event.watchBootSessionId,
            event.processInstanceId,
            event.component.wireCode,
            event.componentInstanceId,
            event.bleLineage,
            event.elapsedRealtimeMs,
            event.wallTimeMs,
            event.code.wireCode,
            event.stream?.wireCode ?: 0,
            event.recordIndexStart,
            event.recordIndexEnd,
            event.arg0,
            event.arg1,
            event.reasonCode,
        ).joinToString("\t")
        return "$body\t${crc32Hex(body)}\n"
    }

    private fun parse(line: String): CausalFlightEvent {
        val fields = line.split('\t')
        require(fields.size == EXPECTED_FIELDS_WITH_CRC) { "Invalid causal event field count" }
        val body = fields.dropLast(1).joinToString("\t")
        require(crc32Hex(body) == fields.last().lowercase()) { "Causal event CRC mismatch" }
        val streamCode = fields[10].toInt()
        return CausalFlightEvent(
            schemaVersion = fields[0].toInt().also { require(it == SCHEMA_VERSION) },
            eventSequence = fields[1].toLong().also { require(it > 0L) },
            watchBootSessionId = UUID.fromString(fields[2]),
            processInstanceId = UUID.fromString(fields[3]),
            component = CausalComponentCode.fromWireCode(fields[4].toInt()),
            componentInstanceId = UUID.fromString(fields[5]),
            bleLineage = fields[6].toLong().also { require(it >= 0L) },
            elapsedRealtimeMs = fields[7].toLong().also { require(it >= 0L) },
            wallTimeMs = fields[8].toLong(),
            code = CausalEventCode.fromWireCode(fields[9].toInt()),
            stream = if (streamCode == 0) null else CausalStreamCode.fromWireCode(streamCode),
            recordIndexStart = fields[11].toLong().also { require(it >= 0L) },
            recordIndexEnd = fields[12].toLong().also { require(it >= 0L) },
            arg0 = fields[13].toLong(),
            arg1 = fields[14].toLong(),
            reasonCode = fields[15].toInt(),
        )
    }

    private fun indexOfNewline(bytes: ByteArray, start: Int): Int {
        for (index in start until bytes.size) if (bytes[index] == '\n'.code.toByte()) return index
        return -1
    }

    private fun crc32Hex(body: String): String = CRC32().apply {
        update(body.toByteArray(Charsets.UTF_8))
    }.value.toString(16).padStart(8, '0')
}

internal data class Build46SourceBaseline(
    val retainedSessionCount: Int,
    val latestRecordIndex: Long,
    val deliveryCounts: Map<SourceDeliveryState, Long>,
) {
    companion object {
        fun from(journal: SourceJournal): Build46SourceBaseline {
            val counts = EnumMap<SourceDeliveryState, Long>(SourceDeliveryState::class.java).apply {
                SourceDeliveryState.entries.forEach { put(it, 0L) }
            }
            journal.deliveryEvents().forEach { event ->
                val count = (event.lastRecordIndex - event.firstRecordIndex + 1L).coerceAtLeast(0L)
                counts[event.state] = counts.getValue(event.state) + count
            }
            return Build46SourceBaseline(
                retainedSessionCount = journal.retainedSessionIds().size,
                latestRecordIndex = journal.latestRecordIndex(),
                deliveryCounts = counts,
            )
        }
    }
}

internal object Build46BaselineFormatter {
    fun format(baseline: Build46SourceBaseline): String = buildString {
        append("B46 sessions=${baseline.retainedSessionCount} latest=${baseline.latestRecordIndex}\n")
        append("BUF=${baseline.deliveryCounts[SourceDeliveryState.BUFFERED] ?: 0L} ")
        append("LS=${baseline.deliveryCounts[SourceDeliveryState.LIVE_SENT] ?: 0L} ")
        append("LC=${baseline.deliveryCounts[SourceDeliveryState.LIVE_CONFIRMED] ?: 0L} ")
        append("RS=${baseline.deliveryCounts[SourceDeliveryState.REPLAY_SENT] ?: 0L} ")
        append("RC=${baseline.deliveryCounts[SourceDeliveryState.REPLAY_CONFIRMED] ?: 0L} ")
        append("DROP=${baseline.deliveryCounts[SourceDeliveryState.EXPLICITLY_DROPPED] ?: 0L}")
    }
}

internal class Build46BaselineStore(private val rootDir: File) {
    companion object {
        private const val FILE_NAME = "build46_baseline_v1.tsv"
        private const val SCHEMA_VERSION = 1
    }

    private val file = File(rootDir, FILE_NAME)

    @Synchronized
    fun captureOnce(capture: () -> Build46SourceBaseline): Build46SourceBaseline {
        load()?.let { return it }
        val baseline = capture()
        if (!rootDir.exists() && !rootDir.mkdirs()) throw IllegalStateException("Unable to create baseline directory")
        val body = buildList {
            add(SCHEMA_VERSION.toString())
            add(baseline.retainedSessionCount.toString())
            add(baseline.latestRecordIndex.toString())
            SourceDeliveryState.entries.forEach { add((baseline.deliveryCounts[it] ?: 0L).toString()) }
        }.joinToString("\t")
        val temp = File(rootDir, "$FILE_NAME.tmp")
        FileOutputStream(temp).use { output ->
            output.write("$body\t${crc32(body)}\n".toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) throw IllegalStateException("Unable to replace baseline file")
        if (!temp.renameTo(file)) throw IllegalStateException("Unable to persist baseline file")
        return baseline
    }

    @Synchronized
    fun load(): Build46SourceBaseline? {
        if (!file.exists()) return null
        val line = file.readLines().singleOrNull() ?: throw IllegalStateException("Invalid baseline row count")
        val fields = line.split('\t')
        require(fields.size == 4 + SourceDeliveryState.entries.size) { "Invalid baseline field count" }
        val body = fields.dropLast(1).joinToString("\t")
        require(fields.last().lowercase() == crc32(body)) { "Baseline CRC mismatch" }
        require(fields[0].toInt() == SCHEMA_VERSION) { "Unknown baseline schema" }
        val counts = EnumMap<SourceDeliveryState, Long>(SourceDeliveryState::class.java)
        SourceDeliveryState.entries.forEachIndexed { index, state -> counts[state] = fields[3 + index].toLong() }
        return Build46SourceBaseline(
            retainedSessionCount = fields[1].toInt(),
            latestRecordIndex = fields[2].toLong(),
            deliveryCounts = counts,
        )
    }

    private fun crc32(body: String): String = CRC32().apply {
        update(body.toByteArray(Charsets.UTF_8))
    }.value.toString(16).padStart(8, '0')
}

internal class FirstCausalEventGate {
    private data class Key(
        val componentInstanceId: UUID,
        val eventCode: CausalEventCode,
        val streamCode: CausalStreamCode?,
    )

    private val recorded = mutableSetOf<Key>()

    @Synchronized
    fun shouldRecord(componentInstanceId: UUID, eventCode: CausalEventCode, streamCode: CausalStreamCode?): Boolean =
        recorded.add(Key(componentInstanceId, eventCode, streamCode))
}

internal data class CausalDisconnectEvidence(
    val bleLineage: Long,
    val gattStatus: Int,
    val localCancelRequested: Boolean,
    val abortReasonCode: Int,
)

internal class CausalLineageState {
    private var lineage = 0L
    private var localCancelRequested = false
    private var abortReasonCode = CausalReasonCode.NONE.code

    @Synchronized
    fun onConnected(): Long {
        lineage += 1L
        localCancelRequested = false
        abortReasonCode = CausalReasonCode.NONE.code
        return lineage
    }

    @Synchronized
    fun currentLineage(): Long = lineage

    @Synchronized
    fun markAbort(reasonCode: Int) {
        abortReasonCode = reasonCode
    }

    @Synchronized
    fun markCancelRequested() {
        localCancelRequested = true
    }

    @Synchronized
    fun onDisconnected(gattStatus: Int): CausalDisconnectEvidence = CausalDisconnectEvidence(
        bleLineage = lineage,
        gattStatus = gattStatus,
        localCancelRequested = localCancelRequested,
        abortReasonCode = abortReasonCode,
    ).also {
        localCancelRequested = false
        abortReasonCode = CausalReasonCode.NONE.code
    }
}

internal object CausalFlightFormatter {
    fun format(events: List<CausalFlightEvent>, limit: Int = 20): List<String> {
        require(limit > 0) { "limit must be positive" }
        val visible = events.takeLast(limit)
        val origin = visible.firstOrNull()?.elapsedRealtimeMs ?: 0L
        return visible.map { event ->
            val stream = event.stream?.name ?: "-"
            val range = if (event.recordIndexStart > 0L) {
                " ${event.recordIndexStart}-${event.recordIndexEnd.coerceAtLeast(event.recordIndexStart)}"
            } else {
                ""
            }
            "#${event.eventSequence} +${(event.elapsedRealtimeMs - origin).coerceAtLeast(0L)} L${event.bleLineage} " +
                "${event.code.name} $stream$range a=${event.arg0},${event.arg1} r=${event.reasonCode}"
        }.map { if (it.length <= 120) it else it.take(120) }
    }
}

internal object WatchCausalRuntime {
    const val ACTION_CAUSAL_EVENT_UPDATE = "com.hugr.wearos.CAUSAL_EVENT_UPDATE"
    private const val DIRECTORY_NAME = "build47_causal_flight_recorder"

    val processInstanceId: UUID = UUID.randomUUID()

    @Volatile
    private var recorder: CausalFlightRecorder? = null

    @Synchronized
    fun recorder(context: Context): CausalFlightRecorder {
        recorder?.let { return it }
        val applicationContext = context.applicationContext
        val journal = WatchSourceRuntime.journal(applicationContext)
        return CausalFlightRecorder(
            rootDir = File(applicationContext.filesDir, DIRECTORY_NAME),
            watchBootSessionId = journal.watchBootSessionId,
            processInstanceId = processInstanceId,
            nowElapsedMs = { SystemClock.elapsedRealtime() },
            nowWallMs = { System.currentTimeMillis() },
        ).also { created ->
            recorder = created
            created.record(
                CausalEventCode.PROCESS_STARTED,
                CausalComponentCode.RUNTIME,
                processInstanceId,
            )
        }
    }

    fun record(
        context: Context,
        code: CausalEventCode,
        component: CausalComponentCode,
        componentInstanceId: UUID,
        bleLineage: Long = 0L,
        stream: CausalStreamCode? = null,
        recordIndexStart: Long = 0L,
        recordIndexEnd: Long = 0L,
        arg0: Long = 0L,
        arg1: Long = 0L,
        reasonCode: Int = CausalReasonCode.NONE.code,
    ): CausalFlightEvent? {
        val activeRecorder = runCatching { recorder(context) }.getOrNull() ?: return null
        return try {
            activeRecorder.record(
                code,
                component,
                componentInstanceId,
                bleLineage,
                stream,
                recordIndexStart,
                recordIndexEnd,
                arg0,
                arg1,
                reasonCode,
            )?.also {
                context.sendBroadcast(Intent(ACTION_CAUSAL_EVENT_UPDATE).setPackage(context.packageName))
            }
        } catch (_: Exception) {
            activeRecorder.markDegraded()
            runCatching {
                context.sendBroadcast(Intent(ACTION_CAUSAL_EVENT_UPDATE).setPackage(context.packageName))
            }
            null
        }
    }

    @Synchronized
    fun resetForTests() {
        recorder?.close()
        recorder = null
    }
}
