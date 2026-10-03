package com.hugr.wearos

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class RetainedTimingExportStatus { PASS, QUALIFIED, STOP }

enum class RetainedTimingExportReason {
    SOURCE_IDENTITY_UNVERIFIED,
    SOURCE_ROOT_MISSING,
    DESTINATION_OVERLAPS_SOURCE,
    PATH_ESCAPE_REJECTED,
    SOURCE_CHANGED_DURING_EXPORT,
    SOURCE_OBJECT_UNREADABLE,
    STABLE_INCOMPLETE_TAIL_PRESERVED,
    SOURCE_SEGMENT_RECLAIMED_OR_ABSENT,
    UNKNOWN_SOURCE_OBJECT_PRESERVED,
    RECORDER_CONTENT_UNPARSEABLE_PRESERVED,
    PACKAGE_ENTRY_HASH_MISMATCH,
    PACKAGE_SELF_HASH_MISMATCH,
}

data class RetainedTimingSourceIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val sourceCommit: String,
    val artifactSha256: String?,
)

data class RetainedTimingExportRequest(
    val journalRoot: File,
    val recorderRoot: File,
    val destinationRoot: File,
    val sourceIdentity: RetainedTimingSourceIdentity,
    val selection: String?,
)

data class RetainedTimingExportObject(
    val relativePath: String,
    val sourceSize: Long,
    val sourceCopySha256: String,
    val sourceVerificationSha256: String,
    val packagedSha256: String,
    val inclusionStatus: String,
    val reason: String?,
)

data class RetainedTimingExportResult(
    val status: RetainedTimingExportStatus,
    val reasons: Set<RetainedTimingExportReason>,
    val packageDirectory: File?,
    val objects: List<RetainedTimingExportObject> = emptyList(),
)

class RetainedTimingExporter(
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val exportId: () -> String,
    private val afterCopyPass: () -> Unit = {},
) {
    fun export(request: RetainedTimingExportRequest): RetainedTimingExportResult {
        val identity = request.sourceIdentity
        if (
            identity.packageName.isBlank() || identity.versionName.isBlank() ||
            identity.versionCode <= 0 || !identity.sourceCommit.matches(Regex("[0-9a-f]{40}"))
        ) {
            return stopped(RetainedTimingExportReason.SOURCE_IDENTITY_UNVERIFIED)
        }

        val roots = listOf(request.journalRoot to SOURCE_JOURNAL_PREFIX, request.recorderRoot to RECORDER_PREFIX)
        if (roots.any { !it.first.isDirectory }) return stopped(RetainedTimingExportReason.SOURCE_ROOT_MISSING)

        val destination = request.destinationRoot.canonicalFile
        val sourceRoots = roots.map { it.first.canonicalFile }
        if (sourceRoots.any { destination.isWithin(it) }) {
            return stopped(RetainedTimingExportReason.DESTINATION_OVERLAPS_SOURCE)
        }

        val inventoryA = mutableListOf<SourceObject>()
        for ((root, prefix) in roots) {
            when (val scan = scanRoot(root, prefix)) {
                is RootScan.Stop -> return stopped(scan.reason)
                is RootScan.Pass -> inventoryA += scan.objects
            }
        }
        inventoryA.sortBy { it.relativePath }

        val copiedBytes = linkedMapOf<String, ByteArray>()
        for (source in inventoryA) {
            val bytes = try {
                source.file.readBytes()
            } catch (_: Exception) {
                return stopped(RetainedTimingExportReason.SOURCE_OBJECT_UNREADABLE)
            }
            if (bytes.size.toLong() != source.size || sha256(bytes) != source.sha256) {
                return stopped(RetainedTimingExportReason.SOURCE_CHANGED_DURING_EXPORT)
            }
            copiedBytes[source.relativePath] = bytes
        }

        afterCopyPass()

        val inventoryB = mutableListOf<SourceObject>()
        for ((root, prefix) in roots) {
            when (val scan = scanRoot(root, prefix)) {
                is RootScan.Stop -> return stopped(scan.reason)
                is RootScan.Pass -> inventoryB += scan.objects
            }
        }
        inventoryB.sortBy { it.relativePath }
        if (inventoryA.map { it.signature } != inventoryB.map { it.signature }) {
            return stopped(RetainedTimingExportReason.SOURCE_CHANGED_DURING_EXPORT)
        }

        val reasons = linkedSetOf<RetainedTimingExportReason>()
        copiedBytes.forEach { (path, bytes) ->
            val name = path.substringAfterLast('/')
            if (path.startsWith(SOURCE_JOURNAL_PREFIX) && name.startsWith("open_") && name.endsWith(".seg")) {
                reasons += RetainedTimingExportReason.STABLE_INCOMPLETE_TAIL_PRESERVED
            }
            if (path == "$RECORDER_PREFIX/events_v1.tsv" && bytes.isNotEmpty() && bytes.last() != '\n'.code.toByte()) {
                reasons += RetainedTimingExportReason.RECORDER_CONTENT_UNPARSEABLE_PRESERVED
            }
            if (!isKnownObject(path)) reasons += RetainedTimingExportReason.UNKNOWN_SOURCE_OBJECT_PRESERVED
        }

        copiedBytes["$SOURCE_JOURNAL_PREFIX/delivery_states.log"]?.toString(Charsets.UTF_8)?.let { delivery ->
            val observed = copiedBytes.keys.map { it.substringAfterLast('/') }.toSet()
            SEGMENT_REFERENCE.findAll(delivery).map { it.value }.forEach { referenced ->
                if (referenced !in observed) reasons += RetainedTimingExportReason.SOURCE_SEGMENT_RECLAIMED_OR_ABSENT
            }
        }

        val startedAt = clockMs()
        val id = exportId()
        if (id.isBlank() || id.contains('/') || id.contains('\\')) {
            return stopped(RetainedTimingExportReason.SOURCE_IDENTITY_UNVERIFIED)
        }
        request.destinationRoot.mkdirs()
        val temporaryDirectory = File(request.destinationRoot, ".$id.hugr-export.tmp")
        val finalDirectory = File(request.destinationRoot, "$id.hugr-export")
        if (temporaryDirectory.exists()) temporaryDirectory.deleteRecursively()
        if (finalDirectory.exists()) return stopped(RetainedTimingExportReason.SOURCE_IDENTITY_UNVERIFIED)
        if (!temporaryDirectory.mkdirs()) return stopped(RetainedTimingExportReason.SOURCE_OBJECT_UNREADABLE)

        val archive = File(temporaryDirectory, "$id.zip")
        val objects = inventoryA.map { first ->
            val bytes = copiedBytes.getValue(first.relativePath)
            val hash = sha256(bytes)
            RetainedTimingExportObject(
                relativePath = first.relativePath,
                sourceSize = bytes.size.toLong(),
                sourceCopySha256 = hash,
                sourceVerificationSha256 = inventoryB.single { it.relativePath == first.relativePath }.sha256,
                packagedSha256 = hash,
                inclusionStatus = "INCLUDED",
                reason = classification(first.relativePath, reasons),
            )
        }
        val completedAt = clockMs()
        val status = if (reasons.isEmpty()) RetainedTimingExportStatus.PASS else RetainedTimingExportStatus.QUALIFIED
        val manifest = manifestBytes(request, id, archive.name, startedAt, completedAt, status, reasons, objects)

        try {
            ZipOutputStream(archive.outputStream().buffered()).use { output ->
                copiedBytes.toSortedMap().forEach { (path, bytes) -> output.writeStored(path, bytes) }
                output.writeStored(MANIFEST_NAME, manifest)
            }
            val archiveDigest = sha256(archive.readBytes())
            File(temporaryDirectory, "${archive.name}.sha256").writeText("$archiveDigest  ${archive.name}\n")
            val verified = verifyPackage(temporaryDirectory)
            if (verified.status != RetainedTimingExportStatus.PASS) {
                temporaryDirectory.deleteRecursively()
                return verified
            }
            try {
                Files.move(temporaryDirectory.toPath(), finalDirectory.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temporaryDirectory.toPath(), finalDirectory.toPath())
            }
        } catch (_: Exception) {
            temporaryDirectory.deleteRecursively()
            return stopped(RetainedTimingExportReason.SOURCE_OBJECT_UNREADABLE)
        }

        return RetainedTimingExportResult(status, reasons, finalDirectory, objects)
    }

    private fun scanRoot(root: File, prefix: String): RootScan {
        val canonicalRoot = root.canonicalFile
        val objects = mutableListOf<SourceObject>()
        for (file in root.walkTopDown().filter { it != root }) {
            val path = file.toPath()
            if (Files.isSymbolicLink(path) && !file.canonicalFile.isWithin(canonicalRoot)) {
                return RootScan.Stop(RetainedTimingExportReason.PATH_ESCAPE_REJECTED)
            }
            if (!file.isFile) continue
            val canonical = file.canonicalFile
            if (!canonical.isWithin(canonicalRoot)) return RootScan.Stop(RetainedTimingExportReason.PATH_ESCAPE_REJECTED)
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val bytes = try {
                file.readBytes()
            } catch (_: Exception) {
                return RootScan.Stop(RetainedTimingExportReason.SOURCE_OBJECT_UNREADABLE)
            }
            objects += SourceObject("$prefix/$relative", file, bytes.size.toLong(), sha256(bytes))
        }
        return RootScan.Pass(objects.sortedBy { it.relativePath })
    }

    private fun isKnownObject(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return if (path.startsWith(SOURCE_JOURNAL_PREFIX)) {
            name == "current_session.bin" || name == "delivery_states.log" || name == "journal_anomalies.log" ||
                (name.startsWith("segment_") && name.endsWith(".seg")) ||
                (name.startsWith("open_") && name.endsWith(".seg"))
        } else {
            name == "events_v1.tsv" || name == "build46_baseline_v1.tsv"
        }
    }

    private fun classification(path: String, reasons: Set<RetainedTimingExportReason>): String? {
        val name = path.substringAfterLast('/')
        return when {
            name.startsWith("open_") && name.endsWith(".seg") -> RetainedTimingExportReason.STABLE_INCOMPLETE_TAIL_PRESERVED.name
            path == "$RECORDER_PREFIX/events_v1.tsv" && RetainedTimingExportReason.RECORDER_CONTENT_UNPARSEABLE_PRESERVED in reasons -> RetainedTimingExportReason.RECORDER_CONTENT_UNPARSEABLE_PRESERVED.name
            !isKnownObject(path) -> RetainedTimingExportReason.UNKNOWN_SOURCE_OBJECT_PRESERVED.name
            else -> null
        }
    }

    private fun manifestBytes(
        request: RetainedTimingExportRequest,
        id: String,
        archiveName: String,
        startedAt: Long,
        completedAt: Long,
        status: RetainedTimingExportStatus,
        reasons: Set<RetainedTimingExportReason>,
        objects: List<RetainedTimingExportObject>,
    ): ByteArray {
        val identity = request.sourceIdentity
        val objectJson = objects.joinToString(",") { row ->
            "{" +
                "\"inclusionStatus\":\"${row.inclusionStatus}\"," +
                "\"packagedSha256\":\"${row.packagedSha256}\"," +
                "\"reason\":${row.reason?.let { "\"${escapeJson(it)}\"" } ?: "null"}," +
                "\"relativePath\":\"${escapeJson(row.relativePath)}\"," +
                "\"sourceCopySha256\":\"${row.sourceCopySha256}\"," +
                "\"sourceSize\":${row.sourceSize}," +
                "\"sourceVerificationSha256\":\"${row.sourceVerificationSha256}\"}"
        }
        val reasonJson = reasons.map { it.name }.sorted().joinToString(",") { "\"$it\"" }
        val json = "{" +
            "\"archiveDigestAlgorithm\":\"SHA-256\"," +
            "\"archiveName\":\"${escapeJson(archiveName)}\"," +
            "\"captureCompletedAtMs\":$completedAt," +
            "\"captureStartedAtMs\":$startedAt," +
            "\"exportId\":\"${escapeJson(id)}\"," +
            "\"exporterSide\":\"WATCH\"," +
            "\"exporterVersion\":\"1\"," +
            "\"objects\":[$objectJson]," +
            "\"packageSchema\":\"$PACKAGE_SCHEMA\"," +
            "\"packageStatus\":\"${status.name}\"," +
            "\"reasons\":[$reasonJson]," +
            "\"selection\":${request.selection?.let { "\"${escapeJson(it)}\"" } ?: "null"}," +
            "\"sourceAppIdentity\":{" +
            "\"artifactSha256\":${identity.artifactSha256?.let { "\"${escapeJson(it)}\"" } ?: "null"}," +
            "\"packageName\":\"${escapeJson(identity.packageName)}\"," +
            "\"sourceCommit\":\"${escapeJson(identity.sourceCommit)}\"," +
            "\"versionCode\":${identity.versionCode}," +
            "\"versionName\":\"${escapeJson(identity.versionName)}\"}," +
            "\"sourceRoots\":[\"${escapeJson(request.journalRoot.canonicalPath)}\",\"${escapeJson(request.recorderRoot.canonicalPath)}\"]}"
        return json.toByteArray(Charsets.UTF_8)
    }

    private fun stopped(reason: RetainedTimingExportReason) = RetainedTimingExportResult(
        status = RetainedTimingExportStatus.STOP,
        reasons = setOf(reason),
        packageDirectory = null,
    )

    private data class SourceObject(val relativePath: String, val file: File, val size: Long, val sha256: String) {
        val signature: String get() = "$relativePath\u0000$size\u0000$sha256"
    }

    private sealed class RootScan {
        data class Pass(val objects: List<SourceObject>) : RootScan()
        data class Stop(val reason: RetainedTimingExportReason) : RootScan()
    }

    companion object {
        private const val PACKAGE_SCHEMA = "hugr-retained-timing-export-v1"
        private const val SOURCE_JOURNAL_PREFIX = "source-journal"
        private const val RECORDER_PREFIX = "causal-recorder"
        private const val MANIFEST_NAME = "manifest.json"
        private val SEGMENT_REFERENCE = Regex("segment_[^\\s\\t]+\\.seg")

        fun verifyPackage(packageDirectory: File): RetainedTimingExportResult {
            return try {
                val archive = packageDirectory.listFiles().orEmpty().single { it.extension == "zip" }
                val receipt = packageDirectory.listFiles().orEmpty().single { it.name == "${archive.name}.sha256" }
                val expected = receipt.readText().trim().split(Regex("\\s+"), limit = 2).firstOrNull()
                if (expected == null || expected != sha256(archive.readBytes())) {
                    return RetainedTimingExportResult(
                        RetainedTimingExportStatus.STOP,
                        setOf(RetainedTimingExportReason.PACKAGE_SELF_HASH_MISMATCH),
                        null,
                    )
                }
                ZipFile(archive).use { zip ->
                    val entries = zip.entries().asSequence().toList()
                    if (entries.isEmpty() || entries.last().name != MANIFEST_NAME || entries.any { it.method != ZipEntry.STORED }) {
                        return RetainedTimingExportResult(
                            RetainedTimingExportStatus.STOP,
                            setOf(RetainedTimingExportReason.PACKAGE_ENTRY_HASH_MISMATCH),
                            null,
                        )
                    }
                    entries.forEach { entry -> zip.getInputStream(entry).use { it.readBytes() } }
                }
                RetainedTimingExportResult(RetainedTimingExportStatus.PASS, emptySet(), packageDirectory)
            } catch (_: Exception) {
                RetainedTimingExportResult(
                    RetainedTimingExportStatus.STOP,
                    setOf(RetainedTimingExportReason.PACKAGE_ENTRY_HASH_MISMATCH),
                    null,
                )
            }
        }

        private fun ZipOutputStream.writeStored(name: String, bytes: ByteArray) {
            val crc = CRC32().apply { update(bytes) }
            val entry = ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc.value
                time = 0L
            }
            putNextEntry(entry)
            write(bytes)
            closeEntry()
        }

        private fun File.isWithin(root: File): Boolean {
            val candidatePath = canonicalFile.toPath()
            val rootPath = root.canonicalFile.toPath()
            return candidatePath == rootPath || candidatePath.startsWith(rootPath)
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        private fun escapeJson(value: String): String = buildString {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                }
            }
        }
    }
}
