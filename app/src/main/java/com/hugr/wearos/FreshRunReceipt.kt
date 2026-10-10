package com.hugr.wearos

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

internal enum class FreshRunPhase { STARTED, FINALIZED, COMPLETED, FAILED }
internal data class FreshAcceptedAck(val last: Long, val hash: String, val elapsed: Long)
internal data class FreshRunReceipt(
    val sourceId: UUID, val runId: UUID, val revision: Long = 1,
    val phase: FreshRunPhase = FreshRunPhase.STARTED,
    val durationMs: Long = 300_000L, val startWallMs: Long, val startElapsedMs: Long,
    val finalizedWallMs: Long = 0, val finalizedElapsedMs: Long = 0, val bootCount: Int,
    val supportedMask: Int = 0, val startedMask: Int = 0, val failure: String = "NONE",
    val manifests: List<SourceSegmentManifest> = emptyList(), val acks: List<FreshAcceptedAck> = emptyList(),
) {
    fun bytes(): ByteArray = buildString {
        append("HUGR_RUN_RECEIPT_V1\n")
        append("run\t$sourceId\t$runId\t$revision\t${phase.name}\n")
        append("time\t$durationMs\t$startWallMs\t$startElapsedMs\t$finalizedWallMs\t$finalizedElapsedMs\t$bootCount\n")
        append("scope\t$supportedMask\t$startedMask\t$failure\n")
        manifests.forEach { m ->
            val ranges = m.streamRanges.entries.sortedBy { it.key.wireCode }.joinToString(",") {
                "${it.key.wireCode}:${it.value.count}:${it.value.firstSequence}:${it.value.lastSequence}"
            }
            append("m\t${m.firstRecordIndex}\t${m.lastRecordIndex}\t${m.recordCount}\t${m.byteCount}\t${m.sha256Hex}\t$ranges\n")
        }
        acks.forEach { append("a\t${it.last}\t${it.hash}\t${it.elapsed}\n") }
        append("end\n")
    }.toByteArray(Charsets.US_ASCII).also { require(it.size <= MAX_BYTES) }

    fun completionFailure(): String? {
        if (failure != "NONE") return failure
        if (finalizedElapsedMs - startElapsedMs < durationMs) return "DURATION_SHORT"
        if (bootCount < 0) return "CLOCK_UNKNOWN"
        if (supportedMask != 31) return "SENSORS_UNSUPPORTED"
        if (startedMask != supportedMask) return "SENSOR_START_FAILED"
        if (manifests.isEmpty()) return "NO_SOURCE_RECORDS"
        var next = 1L
        val sequenceEnds = mutableMapOf<SourceStreamCode, Long>()
        manifests.forEach { m ->
            if (m.firstRecordIndex != next) return "MANIFEST_GAP"
            next = m.lastRecordIndex + 1
            m.streamRanges.forEach { (stream, range) ->
                if (range.firstSequence != (sequenceEnds[stream] ?: 0L) + 1 ||
                    range.lastSequence - range.firstSequence + 1 != range.count) return "STREAM_GAP"
                sequenceEnds[stream] = range.lastSequence
            }
        }
        if ((1..5).any { code -> sequenceEnds.keys.none { it.wireCode == code } }) return "STREAM_MISSING"
        if (manifests.none { m -> m.streamRanges.keys.any { it != SourceStreamCode.DEVICE_HEALTH } }) return "NO_SENSOR_RECORDS"
        return null
    }
    companion object {
        const val MAX_BYTES = 65_536
        private val hashPattern = Regex("[0-9a-f]{64}")
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun canonicalUuid(raw: String): UUID = UUID.fromString(raw).also { require(it.toString() == raw) }
        fun parse(bytes: ByteArray): FreshRunReceipt {
            require(bytes.size in 1..MAX_BYTES && bytes.all { it == 9.toByte() || it == 10.toByte() || it.toInt() in 32..126 })
            val lines = bytes.toString(Charsets.US_ASCII).split('\n')
            require(lines.first() == "HUGR_RUN_RECEIPT_V1" && lines.takeLast(2) == listOf("end", ""))
            val r = lines[1].split('\t'); val t = lines[2].split('\t'); val s = lines[3].split('\t')
            require(r.size == 5 && r[0] == "run" && t.size == 7 && t[0] == "time" && s.size == 4 && s[0] == "scope")
            val source = canonicalUuid(r[1]); val run = canonicalUuid(r[2]); val revision = r[3].toLong()
            val phase = FreshRunPhase.valueOf(r[4]); require(revision > 0)
            require(t[1].toLong() == 300_000L && t[2].toLong() > 0 && t[3].toLong() >= 0 && t[4].toLong() >= 0 && t[5].toLong() >= 0 && t[6].toInt() >= -1)
            val supported = s[1].toInt(); val started = s[2].toInt()
            require(supported in 0..31 && started in 0..31 && started and supported == started && Regex("[A-Z][A-Z0-9_]{0,47}").matches(s[3]))
            val manifests = mutableListOf<SourceSegmentManifest>(); val acks = mutableListOf<FreshAcceptedAck>()
            lines.subList(4, lines.size - 2).forEach { line ->
                val p = line.split('\t')
                when (p[0]) {
                    "m" -> {
                        require(acks.isEmpty() && p.size == 7 && hashPattern.matches(p[5]))
                        val first = p[1].toLong(); val last = p[2].toLong(); val count = p[3].toLong(); val size = p[4].toLong()
                        require(first > 0 && last >= first && last < Long.MAX_VALUE && count == last - first + 1 && size >= count * 48)
                        val ranges = linkedMapOf<SourceStreamCode, SourceStreamRange>()
                        var previousCode = 0
                        p[6].split(',').forEach { raw ->
                            val q = raw.split(':'); require(q.size == 4)
                            val code = q[0].toInt(); require(code in 1..5 && code > previousCode); previousCode = code
                            val range = SourceStreamRange(q[1].toLong(), q[2].toLong(), q[3].toLong())
                            require(range.count > 0 && range.firstSequence > 0 && range.lastSequence >= range.firstSequence && range.lastSequence <= 0xffff_ffffL)
                            ranges[SourceStreamCode.fromWireCode(code)] = range
                        }
                        require(ranges.values.sumOf { it.count } == count)
                        require(manifests.lastOrNull()?.lastRecordIndex?.let { first > it } != false)
                        manifests += SourceSegmentManifest("receipt_${first}_${last}", source, first, last, count, size, p[5], ranges)
                    }
                    "a" -> {
                        require(p.size == 4 && hashPattern.matches(p[2]) && p[3].toLong() >= t[3].toLong())
                        val ack = FreshAcceptedAck(p[1].toLong(), p[2], p[3].toLong())
                        require(acks.none { it.last == ack.last } && manifests.any { it.lastRecordIndex == ack.last && it.sha256Hex == ack.hash })
                        acks += ack
                    }
                    else -> error("Invalid receipt field")
                }
            }
            require(manifests.size <= 64 && acks.size <= 64)
            val receipt = FreshRunReceipt(source, run, revision, phase, t[1].toLong(), t[2].toLong(), t[3].toLong(), t[4].toLong(), t[5].toLong(), t[6].toInt(), supported, started, s[3], manifests, acks)
            require(receipt.bytes().contentEquals(bytes))
            if (phase == FreshRunPhase.STARTED) require(receipt.finalizedWallMs == 0L && receipt.finalizedElapsedMs == 0L)
            if (phase == FreshRunPhase.FINALIZED || phase == FreshRunPhase.COMPLETED) require(receipt.finalizedWallMs > 0 && receipt.finalizedElapsedMs >= receipt.startElapsedMs)
            if (phase == FreshRunPhase.FAILED) require(receipt.failure != "NONE") else require(receipt.failure == "NONE")
            if (phase == FreshRunPhase.COMPLETED) require(receipt.completionFailure() == null && acks.size == manifests.size)
            return receipt
        }
    }
}

/** Small app-owned v3 root only. Pending gates prevent old-success fallback on interrupted IO. */
internal class FreshRunReceiptStore(
    private val root: File,
    private val syncDirectory: (File) -> Unit = { dir -> FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } },
    private val fault: (String) -> Unit = {},
) {
    private fun safe(file: File): File {
        var ancestor: File? = file.absoluteFile
        while (ancestor != null) {
            require(!Files.isSymbolicLink(ancestor.toPath())) { "Symbolic receipt path" }
            ancestor = ancestor.parentFile
        }
        require(file.absoluteFile == file.canonicalFile) { "Unsafe receipt path" }
        return file
    }
    private fun ensure(dir: File) { safe(dir); require(dir.isDirectory || dir.mkdirs()); syncDirectory(requireNotNull(dir.parentFile)) }
    private fun runDir(run: UUID) = safe(File(root, run.toString()))
    fun sourceRoot(run: UUID): File = safe(File(runDir(run), "source"))
    private fun envelope(body: ByteArray) = (FreshRunReceipt.digest(body) + "\n").toByteArray(Charsets.US_ASCII) + body
    private fun readEnvelope(file: File, max: Int): ByteArray {
        safe(file); require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.length() in 66L..(max + 65L))
        val bytes = file.readBytes(); require(bytes[64] == 10.toByte())
        val body = bytes.copyOfRange(65, bytes.size)
        require(bytes.copyOfRange(0, 64).toString(Charsets.US_ASCII) == FreshRunReceipt.digest(body))
        return body
    }
    private fun atomic(file: File, body: ByteArray) {
        safe(file); val tmp = safe(File(file.parentFile, "${file.name}.tmp"))
        fault("WRITE")
        FileOutputStream(tmp).use { it.write(envelope(body)); it.flush(); it.fd.sync() }
        fault("RENAME")
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        fault("DIRECTORY_SYNC"); syncDirectory(requireNotNull(file.parentFile))
        fault("READBACK"); require(readEnvelope(file, maxOf(body.size, 64)).contentEquals(body))
    }
    private fun gate(dir: File, name: String, bytes: ByteArray) { atomic(File(dir, name), bytes) }
    private fun clear(dir: File, name: String) { require(File(dir, name).delete()); syncDirectory(dir) }
    @Synchronized fun create(receipt: FreshRunReceipt) {
        ensure(root); require(!File(root, "selection_pending").exists())
        val dir = runDir(receipt.runId); require(!dir.exists()); ensure(dir)
        createInExistingNewRun(receipt)
    }
    @Synchronized fun createInExistingNewRun(receipt: FreshRunReceipt) {
        ensure(root); require(!File(root, "selection_pending").exists())
        val dir = runDir(receipt.runId); ensure(dir)
        require(!File(dir, "receipt").exists() && !File(dir, "publication_pending").exists() && !File(dir, "ack_pending").exists())
        gate(root, "selection_pending", receipt.runId.toString().toByteArray())
        atomic(File(dir, "receipt"), receipt.bytes())
        atomic(File(root, "current"), receipt.runId.toString().toByteArray())
        clear(root, "selection_pending")
    }
    @Synchronized fun current(): FreshRunReceipt? {
        safe(root)
        require(!Files.exists(safe(File(root, "selection_pending")).toPath(), LinkOption.NOFOLLOW_LINKS)) { "Selection publication interrupted" }
        val pointer = safe(File(root, "current")); if (!Files.exists(pointer.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        val run = FreshRunReceipt.canonicalUuid(readEnvelope(pointer, 36).toString(Charsets.US_ASCII))
        return load(run)
    }
    @Synchronized fun load(run: UUID, allowPendingAck: Boolean = false): FreshRunReceipt {
        val dir = runDir(run)
        require(!Files.exists(safe(File(dir, "publication_pending")).toPath(), LinkOption.NOFOLLOW_LINKS)) { "Receipt publication interrupted" }
        require(allowPendingAck || !Files.exists(safe(File(dir, "ack_pending")).toPath(), LinkOption.NOFOLLOW_LINKS)) { "ACK transaction interrupted" }
        return FreshRunReceipt.parse(readEnvelope(File(dir, "receipt"), FreshRunReceipt.MAX_BYTES)).also { require(it.runId == run) }
    }
    @Synchronized fun publish(receipt: FreshRunReceipt) {
        val dir = runDir(receipt.runId); val previous = load(receipt.runId, true)
        require(previous.sourceId == receipt.sourceId && receipt.revision == previous.revision + 1)
        require(previous.phase != FreshRunPhase.COMPLETED && previous.phase != FreshRunPhase.FAILED)
        require(previous.durationMs == receipt.durationMs && previous.startWallMs == receipt.startWallMs &&
            previous.startElapsedMs == receipt.startElapsedMs && previous.bootCount == receipt.bootCount)
        require(previous.manifests.all { old -> receipt.manifests.any { sameInventory(listOf(old), listOf(it)) } })
        require(receipt.acks.take(previous.acks.size) == previous.acks)
        require(receipt.supportedMask or previous.supportedMask == receipt.supportedMask &&
            receipt.startedMask or previous.startedMask == receipt.startedMask)
        if (previous.phase == FreshRunPhase.FINALIZED) require(sameInventory(previous.manifests, receipt.manifests))
        if (previous.phase == FreshRunPhase.FINALIZED) require(receipt.finalizedWallMs == previous.finalizedWallMs &&
            receipt.finalizedElapsedMs == previous.finalizedElapsedMs && receipt.supportedMask == previous.supportedMask &&
            receipt.startedMask == previous.startedMask && receipt.phase != FreshRunPhase.STARTED)
        FreshRunReceipt.parse(receipt.bytes())
        gate(dir, "publication_pending", receipt.revision.toString().toByteArray())
        atomic(File(dir, "receipt"), receipt.bytes())
        clear(dir, "publication_pending")
    }
    @Synchronized fun pendingAck(receipt: FreshRunReceipt, ack: FreshAcceptedAck) {
        val dir = runDir(receipt.runId); require(!File(dir, "ack_pending").exists())
        gate(dir, "ack_pending", "${receipt.sourceId}\t${ack.last}\t${ack.hash}\t${ack.elapsed}\n".toByteArray(Charsets.US_ASCII))
    }
    @Synchronized fun finishAck(run: UUID) = clear(runDir(run), "ack_pending")
    companion object {
        fun sameInventory(a: List<SourceSegmentManifest>, b: List<SourceSegmentManifest>): Boolean = a.size == b.size && a.zip(b).all { (x,y) ->
            x.watchBootSessionId == y.watchBootSessionId && x.firstRecordIndex == y.firstRecordIndex && x.lastRecordIndex == y.lastRecordIndex &&
                x.recordCount == y.recordCount && x.byteCount == y.byteCount && x.sha256Hex == y.sha256Hex && x.streamRanges == y.streamRanges
        }
    }
}

/** A connection owns one snapshot. R1:0 replaces it; nonzero never rereads the store. */
internal class FreshRunReceiptPager(private val latest: () -> FreshRunReceipt?) {
    private var snapshot: ByteArray? = null
    private var offset: Int? = null
    @Synchronized fun reset() { snapshot = null; offset = null }
    @Synchronized fun request(raw: String) {
        require(Regex("R1:(0|[1-9][0-9]{0,5})").matches(raw))
        val requested = raw.substring(3).toInt()
        if (requested == 0) snapshot = latest()?.bytes()?.copyOf() ?: byteArrayOf()
        val bytes = requireNotNull(snapshot) { "No receipt snapshot" }
        require(requested in 0..bytes.size && (requested == 0 || requested < bytes.size))
        offset = requested
    }
    @Synchronized fun frame(mtu: Int = 517): ByteArray {
        val bytes = requireNotNull(snapshot); val start = requireNotNull(offset)
        require(mtu >= 46) { "MTU cannot carry receipt header and payload" }
        val pageSize = minOf(384, mtu - 1 - 44, bytes.size - start)
        val digest = if (bytes.isEmpty()) ByteArray(32) else MessageDigest.getInstance("SHA-256").digest(bytes)
        return ByteBuffer.allocate(44 + pageSize).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x31524748).putInt(bytes.size).putInt(start).put(digest)
            .put(bytes, start, pageSize).array()
    }
    @Synchronized fun confirms(raw: String): Boolean {
        if (!Regex("C1:[0-9a-f-]{36}:[0-9a-f]{64}").matches(raw)) return false
        val parts = raw.split(':'); val bytes = snapshot ?: return false
        if (bytes.isEmpty()) return false
        val selected = FreshRunReceipt.parse(bytes); val current = latest() ?: return false
        return selected.phase == FreshRunPhase.COMPLETED && current.phase == FreshRunPhase.COMPLETED &&
            parts[1] == current.sourceId.toString() && parts[2] == FreshRunReceipt.digest(bytes) && current.bytes().contentEquals(bytes)
    }
}
