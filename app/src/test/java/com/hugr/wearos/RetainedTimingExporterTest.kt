package com.hugr.wearos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class RetainedTimingExporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `W1 W2 stable roots export every regular file byte exact without runtime construction or source mutation`() {
        val fixture = stableFixture("stable")
        val before = treeDigest(fixture.root)

        val result = exporter("watch-stable").export(fixture.request)

        assertEquals(RetainedTimingExportStatus.PASS, result.status)
        assertTrue(result.reasons.isEmpty())
        val packageDir = requireNotNull(result.packageDirectory)
        val entries = zipEntries(packageDir)
        val expected = fixture.expectedEntries()
        assertEquals(expected.keys.sorted() + "manifest.json", entries.keys.toList())
        expected.forEach { (path, bytes) ->
            assertArrayEquals(path, bytes, entries.getValue(path))
            val row = result.objects.single { it.relativePath == path }
            assertEquals(bytes.size.toLong(), row.sourceSize)
            assertEquals(sha256(bytes), row.sourceCopySha256)
            assertEquals(row.sourceCopySha256, row.sourceVerificationSha256)
            assertEquals(row.sourceCopySha256, row.packagedSha256)
        }
        assertEquals(before, treeDigest(fixture.root))
        assertStoredLexicalWithManifestLast(packageDir)
        assertEquals(RetainedTimingExportStatus.PASS, RetainedTimingExporter.verifyPackage(packageDir).status)
    }

    @Test
    fun `W3 every between-pass source mutation stops and publishes no package`() {
        val mutations = listOf<(Fixture) -> Unit>(
            { fixture -> File(fixture.journal, "added.tmp").writeBytes(byteArrayOf(9)) },
            { fixture -> File(fixture.journal, "segment_session_1_1.seg").delete() },
            { fixture ->
                val file = File(fixture.journal, "segment_session_1_1.seg")
                file.delete()
                file.writeBytes(byteArrayOf(7, 7, 7))
            },
            { fixture -> File(fixture.recorder, "events_v1.tsv").appendBytes(byteArrayOf(10, 88)) },
        )

        mutations.forEachIndexed { index, mutation ->
            val fixture = stableFixture("unstable-$index")
            val result = exporter("watch-unstable-$index") { mutation(fixture) }.export(fixture.request)
            assertEquals(RetainedTimingExportStatus.STOP, result.status)
            assertTrue(result.reasons.contains(RetainedTimingExportReason.SOURCE_CHANGED_DURING_EXPORT))
            assertNull(result.packageDirectory)
            assertTrue(fixture.destination.listFiles().orEmpty().none { it.name.endsWith(".hugr-export") })
        }
    }

    @Test
    fun `W4 incomplete recorder and unknown files remain raw and explicitly qualified`() {
        val fixture = stableFixture("qualified")
        val openBytes = byteArrayOf(0x48, 0x55, 0x47)
        val partialRecorder = "event\tpartial-without-crc".toByteArray()
        val unknownBytes = byteArrayOf(0, 1, 2, 3, 4)
        File(fixture.journal, "open_session_2.seg").writeBytes(openBytes)
        File(fixture.recorder, "events_v1.tsv").writeBytes(partialRecorder)
        File(fixture.recorder, "unknown.tmp").writeBytes(unknownBytes)

        val result = exporter("watch-qualified").export(fixture.request)

        assertEquals(RetainedTimingExportStatus.QUALIFIED, result.status)
        assertTrue(result.reasons.contains(RetainedTimingExportReason.STABLE_INCOMPLETE_TAIL_PRESERVED))
        assertTrue(result.reasons.contains(RetainedTimingExportReason.RECORDER_CONTENT_UNPARSEABLE_PRESERVED))
        assertTrue(result.reasons.contains(RetainedTimingExportReason.UNKNOWN_SOURCE_OBJECT_PRESERVED))
        val entries = zipEntries(requireNotNull(result.packageDirectory))
        assertArrayEquals(openBytes, entries.getValue("source-journal/open_session_2.seg"))
        assertArrayEquals(partialRecorder, entries.getValue("causal-recorder/events_v1.tsv"))
        assertArrayEquals(unknownBytes, entries.getValue("causal-recorder/unknown.tmp"))
    }

    @Test
    fun `W5 reclaimed segment is explicit and is never reconstructed from delivery rows`() {
        val fixture = stableFixture("reclaimed")
        File(fixture.journal, "segment_session_1_1.seg").delete()
        File(fixture.journal, "delivery_states.log").writeText("session\t1\tREPLAY_CONFIRMED\tsegment_session_1_1.seg\n")

        val result = exporter("watch-reclaimed").export(fixture.request)

        assertEquals(RetainedTimingExportStatus.QUALIFIED, result.status)
        assertTrue(result.reasons.contains(RetainedTimingExportReason.SOURCE_SEGMENT_RECLAIMED_OR_ABSENT))
        val entries = zipEntries(requireNotNull(result.packageDirectory))
        assertFalse(entries.keys.any { it.endsWith("segment_session_1_1.seg") })
        assertArrayEquals(
            File(fixture.journal, "delivery_states.log").readBytes(),
            entries.getValue("source-journal/delivery_states.log"),
        )
    }

    @Test
    fun `W6 destination overlap and source symlink escape stop before publication`() {
        val overlap = stableFixture("overlap")
        val overlapResult = exporter("watch-overlap").export(overlap.request.copy(destinationRoot = overlap.journal))
        assertEquals(RetainedTimingExportStatus.STOP, overlapResult.status)
        assertTrue(overlapResult.reasons.contains(RetainedTimingExportReason.DESTINATION_OVERLAPS_SOURCE))
        assertNull(overlapResult.packageDirectory)

        val linkedDestination = stableFixture("destination-link")
        val link = File(linkedDestination.root, "destination-link")
        Files.createSymbolicLink(link.toPath(), linkedDestination.journal.toPath())
        val linkedResult = exporter("watch-destination-link").export(linkedDestination.request.copy(destinationRoot = link))
        assertEquals(RetainedTimingExportStatus.STOP, linkedResult.status)
        assertTrue(linkedResult.reasons.contains(RetainedTimingExportReason.DESTINATION_OVERLAPS_SOURCE))

        val escapingSource = stableFixture("source-link")
        val outside = File(escapingSource.root, "outside.bin").apply { writeBytes(byteArrayOf(6, 6)) }
        Files.createSymbolicLink(File(escapingSource.journal, "escape.bin").toPath(), outside.toPath())
        val escapeResult = exporter("watch-source-link").export(escapingSource.request)
        assertEquals(RetainedTimingExportStatus.STOP, escapeResult.status)
        assertTrue(escapeResult.reasons.contains(RetainedTimingExportReason.PATH_ESCAPE_REJECTED))
        assertNull(escapeResult.packageDirectory)
    }

    @Test
    fun `W7 altered entry manifest or detached receipt fails offline read back`() {
        val entryPackage = requireNotNull(exporter("watch-tamper-entry").export(stableFixture("tamper-entry").request).packageDirectory)
        rewriteZipEntry(entryPackage, "source-journal/current_session.bin") { it + byteArrayOf(0x55) }
        assertTamperStopped(RetainedTimingExporter.verifyPackage(entryPackage))

        val manifestPackage = requireNotNull(exporter("watch-tamper-manifest").export(stableFixture("tamper-manifest").request).packageDirectory)
        rewriteZipEntry(manifestPackage, "manifest.json") { it + " ".toByteArray() }
        assertTamperStopped(RetainedTimingExporter.verifyPackage(manifestPackage))

        val receiptPackage = requireNotNull(exporter("watch-tamper-receipt").export(stableFixture("tamper-receipt").request).packageDirectory)
        receiptPackage.listFiles().orEmpty().single { it.name.endsWith(".sha256") }.writeText("0".repeat(64) + "  tampered.zip\n")
        assertTamperStopped(RetainedTimingExporter.verifyPackage(receiptPackage))
    }

    private fun exporter(id: String, afterCopyPass: () -> Unit = {}): RetainedTimingExporter = RetainedTimingExporter(
        clockMs = sequenceOf(1_780_000_000_000L, 1_780_000_000_100L).iterator()::next,
        exportId = { id },
        afterCopyPass = afterCopyPass,
    )

    private fun stableFixture(name: String): Fixture {
        val root = temporaryFolder.newFolder(name)
        val journal = File(root, "build45_source_journal").apply { mkdir() }
        val recorder = File(root, "build47_causal_flight_recorder").apply { mkdir() }
        val destination = File(root, "exports").apply { mkdir() }
        File(journal, "current_session.bin").writeBytes(byteArrayOf(1, 2, 3))
        File(journal, "delivery_states.log").writeText("session\t1\tBUFFERED\n")
        File(journal, "journal_anomalies.log").writeText("")
        File(journal, "segment_session_1_1.seg").writeBytes(byteArrayOf(4, 5, 6))
        File(recorder, "events_v1.tsv").writeText("event\tcomplete\tcrc\n")
        File(recorder, "build46_baseline_v1.tsv").writeText("baseline\tcomplete\tcrc\n")
        return Fixture(
            root,
            journal,
            recorder,
            destination,
            RetainedTimingExportRequest(
                journalRoot = journal,
                recorderRoot = recorder,
                destinationRoot = destination,
                sourceIdentity = RetainedTimingSourceIdentity(
                    packageName = "com.hugr.wearos",
                    versionName = "0.51.0-resume-scaling-candidate",
                    versionCode = 51,
                    sourceCommit = "0e1c10808635d6a1ac3fb25ff58451e0521bf805",
                    artifactSha256 = null,
                ),
                selection = null,
            ),
        )
    }

    private fun Fixture.expectedEntries(): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        journal.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { put("source-journal/${it.name}", it.readBytes()) }
        recorder.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { put("causal-recorder/${it.name}", it.readBytes()) }
    }

    private fun treeDigest(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        root.walkTopDown().filter { it.isFile && !it.toPath().startsWith(File(root, "exports").toPath()) }.sortedBy { it.relativeTo(root).path }.forEach {
            digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray())
            digest.update(it.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun zipEntries(packageDir: File): LinkedHashMap<String, ByteArray> {
        val archive = packageDir.listFiles().orEmpty().single { it.extension == "zip" }
        return linkedMapOf<String, ByteArray>().apply {
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().forEach { entry -> put(entry.name, zip.getInputStream(entry).readBytes()) }
            }
        }
    }

    private fun assertStoredLexicalWithManifestLast(packageDir: File) {
        val archive = packageDir.listFiles().orEmpty().single { it.extension == "zip" }
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertEquals("manifest.json", entries.last().name)
            assertEquals(entries.dropLast(1).map { it.name }.sorted(), entries.dropLast(1).map { it.name })
            assertTrue(entries.all { it.method == ZipEntry.STORED })
        }
    }

    private fun rewriteZipEntry(packageDir: File, target: String, transform: (ByteArray) -> ByteArray) {
        val archive = packageDir.listFiles().orEmpty().single { it.extension == "zip" }
        val replacement = File(packageDir, "replacement.zip")
        ZipFile(archive).use { input ->
            ZipOutputStream(replacement.outputStream()).use { output ->
                input.entries().asSequence().forEach { original ->
                    val bytes = input.getInputStream(original).readBytes().let { if (original.name == target) transform(it) else it }
                    val crc = CRC32().apply { update(bytes) }
                    val entry = ZipEntry(original.name).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        this.crc = crc.value
                        time = original.time
                    }
                    output.putNextEntry(entry)
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        Files.move(replacement.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun assertTamperStopped(result: RetainedTimingExportResult) {
        assertEquals(RetainedTimingExportStatus.STOP, result.status)
        assertTrue(
            result.reasons.any {
                it == RetainedTimingExportReason.PACKAGE_ENTRY_HASH_MISMATCH ||
                    it == RetainedTimingExportReason.PACKAGE_SELF_HASH_MISMATCH
            },
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Fixture(
        val root: File,
        val journal: File,
        val recorder: File,
        val destination: File,
        val request: RetainedTimingExportRequest,
    )
}
