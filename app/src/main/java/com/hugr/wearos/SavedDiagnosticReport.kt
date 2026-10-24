package com.hugr.wearos

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/** App-owned operational diagnostics only. Never general Logcat, payloads or Throwable messages. */
internal data class DiagnosticContext(
    val versionCode: Long,
    val versionName: String,
    val processId: UUID,
    val attemptId: UUID? = null,
    val sourceSessionId: UUID? = null,
    val lineage: Long = 0,
    val phoneCursor: Long? = null,
    val retainedEndpoint: Long? = null,
    val historicalBound: Long? = null,
    val mtu: Int = 0,
    val pendingManifestEndpoint: Long? = null,
)

internal enum class DiagnosticStage {
    STARTUP_SELECTION, SERVICE_START, FOREGROUND_PROMOTION, SERVICE_INITIALIZATION,
    RESUME_PREPARE, HISTORICAL_BOUND_VALIDATION, RESUME_APPLY, SOURCE_CONTROL, REPLAY_READ, RECOVERY_STOP,
    SERVICE_DESTROY, UNCAUGHT_EXCEPTION,
}

internal enum class DiagnosticOutcome {
    EXCEPTION, DEADLINE, DISCONNECT, TRANSPORT_ABORT, EXACT_ACK_COMPLETED,
    READINESS_FAILED, INITIALIZATION_FAILED, FOREGROUND_PROMOTION_FAILED,
    INVALID_SOURCE_ID, ATTEMPT_REUSED, SERVICE_START_REJECTED, UNEXPLAINED_DESTROY, OTHER_STOP,
}

internal data class DiagnosticEvent(
    val sequence: Long, val wallMs: Long, val elapsedMs: Long, val lineage: Long,
    val code: String, val reason: Int,
)

/**
 * Payload-free evidence that an ATT callback reached its response attempt but the platform
 * returned false, there was no current GATT server, or the reply was accepted after ten seconds.
 * This is not delivery, radio, Phone-ACK,
 * or retained-source evidence.
 */
internal data class AttResponseOperationDiagnostic(
    val operationKind: String,
    val access: String,
    val pageIndex: Long?,
    val callbackOrdinal: Long,
    val callbackEntryElapsedMs: Long,
    val preResponseElapsedMs: Long,
    val responseElapsedMs: Long,
    val responseOutcome: AttResponseOutcome,
)

internal enum class AttResponseOutcome { TRUE, FALSE, NULL }

internal object SavedDiagnosticFormat {
    const val MAX_REPORT_BYTES = 24 * 1024
    const val MAX_CAUSES = 4
    const val MAX_FRAMES_PER_CAUSE = 24

    fun withTerminal(primary: String, stage: DiagnosticStage, outcome: DiagnosticOutcome, wallMs: Long): String {
        val suffix = "\nterminalStage=${stage.name} terminalOutcome=${outcome.name} terminalWallMs=$wallMs\n--- END REPORT ---\n"
        val prefix = primary.substringBeforeLast("--- END REPORT ---\n").toByteArray(Charsets.UTF_8)
        val budget = MAX_REPORT_BYTES - suffix.toByteArray(Charsets.UTF_8).size
        return prefix.copyOf(minOf(prefix.size, budget)).toString(Charsets.UTF_8) + suffix
    }

    fun render(
        context: DiagnosticContext, stage: DiagnosticStage, outcome: DiagnosticOutcome,
        failure: Throwable?, wallMs: Long, elapsedMs: Long, events: List<DiagnosticEvent> = emptyList(),
    ): String = buildString {
        append("HUGR SAVED DIAGNOSTIC v1\nOperational report — NOT recording verification\n")
        append("wallMs=$wallMs elapsedMs=$elapsedMs\n")
        append("package=com.hugr.wearos versionCode=${context.versionCode} versionName=${safe(context.versionName)}\n")
        append("process=${context.processId} attempt=${context.attemptId ?: "unknown"} sourceSession=${context.sourceSessionId ?: "unknown"}\n")
        append("stage=${stage.name} outcome=${outcome.name} lineage=${context.lineage} mtu=${context.mtu}\n")
        append("C_phone=${context.phoneCursor ?: "unknown"} R_retained=${context.retainedEndpoint ?: "unknown"} H_issuance=${context.historicalBound ?: "unknown"} queuedEndpoint=${context.pendingManifestEndpoint ?: "unknown"}\n")
        append("C/R/H are operational assertions, not deleted-byte or custody proof.\n")
        append("Messages, source payloads, sensor values, filenames/paths, addresses, contact details and credentials are omitted.\n")
        if (failure != null) {
            val visited = java.util.IdentityHashMap<Throwable, Boolean>()
            var cause: Throwable? = failure
            var depth = 0
            while (cause != null && depth < MAX_CAUSES && visited.put(cause, true) == null) {
                append("cause[$depth]=${safe(cause.javaClass.name)}\n")
                // Only project/runtime code locations; never toString() or exception.message.
                cause.stackTrace.take(MAX_FRAMES_PER_CAUSE).forEach { frame ->
                    if (frame.className.startsWith("com.hugr.") || frame.className.startsWith("java.") ||
                        frame.className.startsWith("android.") || frame.className.startsWith("kotlin.")) {
                        append("  at ${safe(frame.className)}.${safe(frame.methodName)}:${frame.lineNumber}\n")
                    } else append("  at [external frame omitted]\n")
                }
                if (cause.stackTrace.size > MAX_FRAMES_PER_CAUSE) append("  [frames truncated]\n")
                cause = cause.cause
                depth++
            }
            if (cause != null) append("[cause chain truncated or cyclic]\n")
        }
        append("--- bounded cached event tail; historical, not readiness ---\n")
        events.takeLast(16).forEach {
            append("#${it.sequence} wall=${it.wallMs} elapsed=${it.elapsedMs} L${it.lineage} ${safe(it.code)} reason=${it.reason}\n")
        }
        append("--- END REPORT ---\n")
    }.let { report ->
        val bytes = report.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_REPORT_BYTES) report else {
            val suffix = "\n[byte budget truncated]\n--- END REPORT ---\n"
            // All serialized fields are ASCII-sanitized; truncation cannot split UTF-8.
            bytes.copyOf(MAX_REPORT_BYTES - suffix.toByteArray(Charsets.UTF_8).size)
                .toString(Charsets.UTF_8) + suffix
        }
    }

    /** Separate from receipt/source reports: deliberately excludes UUID, hash, payload and address. */
    fun renderAttResponseOperation(observation: AttResponseOperationDiagnostic): String = buildString {
        append("HUGR SAVED ATT RESPONSE DIAGNOSTIC v1\n")
        append("Operational response attempt — NOT delivery, radio, Phone-ACK, or recording verification\n")
        append("operation=${safe(observation.operationKind)} access=${safe(observation.access)} pageIndex=")
        append(observation.pageIndex ?: "none")
        append(" callbackOrdinal=${observation.callbackOrdinal}\n")
        append("callbackEntryElapsedMs=${observation.callbackEntryElapsedMs} ")
        append("preResponseElapsedMs=${observation.preResponseElapsedMs} ")
        append("responseElapsedMs=${observation.responseElapsedMs}\n")
        append("responseOutcome=${observation.responseOutcome.name}\n")
        val delayMs = (observation.responseElapsedMs - observation.callbackEntryElapsedMs).coerceAtLeast(0L)
        append("clock=PROCESS_MONOTONIC_NANOTIME callbackToResponseMs=$delayMs lateForPhoneOperation=${delayMs >= 10_000L}\n")
        append("A true platform result after the Phone operation deadline is not timely delivery or Phone acceptance.\n")
        append("Payloads, UUIDs, hashes, source samples, addresses, receipt contents, and exception messages are omitted.\n")
        append("--- END REPORT ---\n")
    }

    private fun safe(value: String): String = value.take(160).map {
        if (it.isLetterOrDigit() && it.code < 128 || it in "._$<>-: ") it else '_'
    }.joinToString("")
}

/**
 * Eight published reports maximum; file fsync + atomic rename + directory sync and exact reopen
 * integrity verification before acknowledgement. Generic operational reports retain their UUID
 * `.txt` name. A frozen incident uses a labelled sibling name so generic cleanup cannot silently
 * rotate it away; the existing viewer reads both.
 */
internal class SavedDiagnosticStore(
    private val root: File,
    private val syncDirectory: (File) -> Unit = { directory -> forceDirectorySync(directory) },
    private val exactReopenVerifier: (File, String) -> Unit = { file, expected ->
        verifyExactReopen(file, expected)
    },
) {
    companion object {
        const val MAX_REPORTS = 8
        private const val TRAILER = "REPORT_SHA256="
        private val GENERIC_NAME = Regex("[0-9a-f-]{36}\\.txt")
        private val INCIDENT_NAME = Regex("[0-9a-f-]{36}\\.incident\\.txt")
        private val ROOT_LOCKS = mutableMapOf<String, Any>()

        private fun sharedRootLock(root: File): Any = synchronized(ROOT_LOCKS) {
            ROOT_LOCKS.getOrPut(root.absoluteFile.normalize().path) { Any() }
        }

        /** Force the parent directory after rename so the namespace entry is durable as well. */
        private fun forceDirectorySync(directory: File) {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel -> channel.force(true) }
        }

        /** Freshly re-read the body and trailer before returning an atomic acknowledgement. */
        private fun verifyExactReopen(file: File, expected: String) {
            val encoded = file.readText(Charsets.UTF_8)
            val at = encoded.lastIndexOf(TRAILER)
            check(at >= 0 && encoded.endsWith('\n')) { "Incomplete diagnostic after publication" }
            val body = encoded.substring(0, at)
            check(body == expected) { "Published diagnostic body changed before acknowledgement" }
            check(encoded.substring(at + TRAILER.length).trim() == sha(body.toByteArray(Charsets.UTF_8))) {
                "Published diagnostic integrity mismatch"
            }
        }

        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private val rootLock = sharedRootLock(root)

    /** Existing generic compatibility entry point; generic rotation cannot delete frozen incidents. */
    fun save(text: String, reportId: UUID = UUID.randomUUID()): File = synchronized(rootLock) {
        val final = File(root, "${reportId}.txt")
        if (!final.exists()) makeRoomForGenericReport()
        writeAtomically(text, final)
        final
    }

    /**
     * Checkpoints one incident under its diagnostic-epoch identity. A later checkpoint replaces
     * only that same incident. A full generic-inclusive store may explicitly displace one generic
     * report; a full incident-only store reports CAPACITY_EXHAUSTED without rotation.
     */
    fun saveIncident(text: String, diagnosticEpoch: UUID): IncidentPublicationResult = synchronized(rootLock) {
        val final = File(root, "${diagnosticEpoch}.incident.txt")
        try {
            var replacedGeneric = false
            if (!final.exists() && reports().size >= MAX_REPORTS) {
                val generic = genericReports().lastOrNull()
                    ?: return@synchronized IncidentPublicationResult.CAPACITY_EXHAUSTED
                check(generic.delete()) { "Generic diagnostic report replacement failed" }
                replacedGeneric = true
            }
            writeAtomically(text, final)
            if (replacedGeneric) IncidentPublicationResult.ACKNOWLEDGED_ATOMIC_REPLACED_GENERIC
            else IncidentPublicationResult.ACKNOWLEDGED_ATOMIC
        } catch (_: Throwable) {
            IncidentPublicationResult.WRITE_FAILED
        }
    }

    fun reports(): List<File> = synchronized(rootLock) {
        root.listFiles()?.filter {
            it.isFile && (it.name.matches(GENERIC_NAME) || it.name.matches(INCIDENT_NAME))
        }?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name }) ?: emptyList()
    }

    fun incidentReports(): List<File> = synchronized(rootLock) { reports().filter(::isIncident) }

    fun read(file: File): String = synchronized(rootLock) {
        require(file.canonicalFile.parentFile == root.canonicalFile && reports().contains(file))
        require(file.length() in 1..(SavedDiagnosticFormat.MAX_REPORT_BYTES + 100L))
        val encoded = file.readText(Charsets.UTF_8)
        val at = encoded.lastIndexOf(TRAILER)
        require(at >= 0 && encoded.endsWith('\n')) { "Incomplete diagnostic" }
        val body = encoded.substring(0, at)
        require(encoded.substring(at + TRAILER.length).trim() == sha(body.toByteArray(Charsets.UTF_8))) {
            "Diagnostic integrity mismatch"
        }
        body
    }

    private fun genericReports(): List<File> = reports().filterNot(::isIncident)

    private fun isIncident(file: File): Boolean = file.name.matches(INCIDENT_NAME)

    private fun makeRoomForGenericReport() {
        if (reports().size < MAX_REPORTS) return
        val generic = genericReports().lastOrNull()
            ?: throw IllegalStateException("Diagnostic report capacity contains only frozen incidents")
        check(generic.delete()) { "Generic diagnostic rotation failed" }
    }

    private fun writeAtomically(text: String, final: File) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= SavedDiagnosticFormat.MAX_REPORT_BYTES)
        check(root.isDirectory || root.mkdirs()) { "Diagnostic directory unavailable" }
        val temporary = File(root, ".${final.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use {
                it.write(bytes)
                it.write("$TRAILER${sha(bytes)}\n".toByteArray(Charsets.UTF_8))
                it.flush()
                it.fd.sync()
            }
            check(temporary.renameTo(final)) { "Diagnostic publication failed" }
            syncDirectory(root)
            exactReopenVerifier(final, text)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
