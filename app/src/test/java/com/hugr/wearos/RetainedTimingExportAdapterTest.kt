package com.hugr.wearos

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RetainedTimingExportAdapterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val identity = RetainedTimingSourceIdentity(
        packageName = "com.hugr.wearos",
        versionName = "0.52.0-retained-timing-export-candidate",
        versionCode = 52,
        sourceCommit = "0123456789abcdef0123456789abcdef01234567",
        artifactSha256 = null,
    )

    // The sole compile-time production reference. All behavioral assertions
    // exercise that class reflectively, keeping RED to one missing symbol.
    @Suppress("unused")
    private fun missingProductionSymbol(): Class<*> = WatchRetainedTimingExportAdapter::class.java

    @Test
    fun `WAT1 construction and consent presentation perform zero export or acquisition actions`() {
        val fixture = fixture()
        val snapshot = call(fixture.adapter, "open")

        assertEquals("AWAITING_FIRST_CONFIRMATION", property(snapshot, "state").toString())
        assertEquals(0, fixture.coreCalls)
        assertEquals(0, fixture.copyCalls)
        assertSourceHashesUnchanged(fixture)
    }

    @Test
    fun `WAT2 first acknowledgement cannot export and second confirmation permits exactly one core call`() {
        val fixture = fixture()
        call(fixture.adapter, "open")
        val first = call(fixture.adapter, "confirmIntent")
        assertEquals("AWAITING_SECOND_CONFIRMATION", property(first, "state").toString())
        assertEquals(0, fixture.coreCalls)

        val second = call(fixture.adapter, "confirmExport")
        assertEquals("READY_FOR_TRANSFER", property(second, "state").toString())
        assertEquals(1, fixture.coreCalls)
        call(fixture.adapter, "confirmExport")
        assertEquals(1, fixture.coreCalls)
        assertEquals(0, fixture.copyCalls)
    }

    @Test
    fun `WAT3 confirmed request uses exact watch roots runtime identity and full retained store selection`() {
        val fixture = fixture()
        ready(fixture)
        val request = requireNotNull(fixture.lastRequest)

        assertEquals(fixture.journalRoot.canonicalFile, request.journalRoot.canonicalFile)
        assertEquals(fixture.recorderRoot.canonicalFile, request.recorderRoot.canonicalFile)
        assertEquals(fixture.destinationRoot.canonicalFile, request.destinationRoot.canonicalFile)
        assertEquals(identity, request.sourceIdentity)
        assertEquals("FULL_RETAINED_WATCH_STORE", request.selection)
        assertFalse(request.destinationRoot.canonicalFile.toPath().startsWith(request.journalRoot.canonicalFile.toPath()))
        assertFalse(request.destinationRoot.canonicalFile.toPath().startsWith(request.recorderRoot.canonicalFile.toPath()))
    }

    @Test
    fun `WAT4 core stop transfers zero objects and preserves every exact reason`() {
        val reasons = setOf(RetainedTimingExportReason.SOURCE_ROOT_MISSING, RetainedTimingExportReason.SOURCE_OBJECT_UNREADABLE)
        val fixture = fixture(coreStatus = RetainedTimingExportStatus.STOP, coreReasons = reasons)
        val stopped = ready(fixture)
        call(fixture.adapter, "copyToHost")

        assertEquals("CORE_STOP", property(stopped, "state").toString())
        assertEquals("STOP", property(stopped, "coreStatus").toString())
        assertEquals(reasons, property(stopped, "reasons"))
        assertEquals(0, fixture.copyCalls)
        assertTrue(property(stopped, "transferFiles") is List<*>)
        assertTrue((property(stopped, "transferFiles") as List<*>).isEmpty())
    }

    @Test
    fun `WAT5 qualified remains qualified and the receipt preserves every reason`() {
        val reasons = setOf(RetainedTimingExportReason.UNKNOWN_SOURCE_OBJECT_PRESERVED, RetainedTimingExportReason.STABLE_INCOMPLETE_TAIL_PRESERVED)
        val fixture = fixture(coreStatus = RetainedTimingExportStatus.QUALIFIED, coreReasons = reasons)
        val ready = ready(fixture)

        assertEquals("READY_FOR_TRANSFER", property(ready, "state").toString())
        assertEquals("QUALIFIED", property(ready, "coreStatus").toString())
        assertEquals(reasons, property(ready, "reasons"))
        assertFalse(property(ready, "coreStatus").toString() == "PASS")
    }

    @Test
    fun `WAT6 pass or qualified exposes exactly one zip and one sidecar under one completed export directory`() {
        listOf(RetainedTimingExportStatus.PASS, RetainedTimingExportStatus.QUALIFIED).forEach { status ->
            val fixture = fixture(coreStatus = status)
            val ready = ready(fixture)
            val files = property(ready, "transferFiles") as List<*>

            assertEquals(2, files.size)
            assertEquals(listOf("watch-export.zip", "watch-export.zip.sha256"), files.map { (it as File).name }.sorted())
            files.map { it as File }.forEach { assertEquals(fixture.packageDirectory.canonicalFile, it.parentFile.canonicalFile) }
        }
    }

    @Test
    fun `WAT7 source and completed staging inventories remain byte identical through planning and copy failure`() {
        val fixture = fixture(copySucceeds = false)
        val sourcesBefore = sourceHashes(fixture)
        val stagingBefore = packageHashes(fixture)
        ready(fixture)
        val failed = call(fixture.adapter, "copyToHost")

        assertEquals("COPY_OR_HASH_STOP", property(failed, "state").toString())
        assertEquals(1, fixture.copyCalls)
        assertEquals(sourcesBefore, sourceHashes(fixture))
        assertEquals(stagingBefore, packageHashes(fixture))
        call(fixture.adapter, "copyToHost")
        assertEquals(1, fixture.copyCalls)
    }

    @Test
    fun `WAT8 source and manifest scope contain no automatic service ble ack replay haptic network or boot trigger`() {
        val source = locateSource("app/src/main/java/com/hugr/wearos/RetainedTimingExportActivity.kt")
        val manifest = locateSource("app/src/main/AndroidManifest.xml").readText()
        val text = source.readText()

        assertTrue(text.contains("WatchRetainedTimingExportAdapter"))
        assertTrue(text.contains("FULL_RETAINED_WATCH_STORE"))
        listOf("startService(", "startForegroundService(", "WatchSourceService", "SourceDeliveryService", "Bluetooth", "ACK", "replay", "Vibrator", "Http", "Socket", "BOOT_COMPLETED").forEach {
            assertFalse("forbidden watch adapter token: $it", text.contains(it, ignoreCase = true))
        }
        assertFalse(manifest.substringAfter("RetainedTimingExportActivity").substringBefore("</activity>").contains("BOOT_COMPLETED"))
    }

    private fun ready(fixture: WatchFixture): Any {
        call(fixture.adapter, "open")
        call(fixture.adapter, "confirmIntent")
        return call(fixture.adapter, "confirmExport")
    }

    private fun fixture(
        coreStatus: RetainedTimingExportStatus = RetainedTimingExportStatus.PASS,
        coreReasons: Set<RetainedTimingExportReason> = emptySet(),
        copySucceeds: Boolean = true,
    ): WatchFixture {
        val journalRoot = temporaryFolder.newFolder("journal-${System.nanoTime()}")
        val recorderRoot = temporaryFolder.newFolder("recorder-${System.nanoTime()}")
        val destinationRoot = temporaryFolder.newFolder("destination-${System.nanoTime()}")
        File(journalRoot, "sealed_1.seg").writeBytes(byteArrayOf(1, 2, 3))
        File(recorderRoot, "events_v1.tsv").writeText("event\n")
        val packageDirectory = temporaryFolder.newFolder("watch-package-${System.nanoTime()}")
        File(packageDirectory, "watch-export.zip").writeBytes(byteArrayOf(9, 8, 7))
        File(packageDirectory, "watch-export.zip.sha256").writeText("hash  watch-export.zip\n")
        val fixture = WatchFixture(journalRoot, recorderRoot, destinationRoot, packageDirectory)
        val core: (RetainedTimingExportRequest) -> RetainedTimingExportResult = { request ->
            fixture.coreCalls += 1
            fixture.lastRequest = request
            RetainedTimingExportResult(
                status = coreStatus,
                reasons = coreReasons,
                packageDirectory = if (coreStatus == RetainedTimingExportStatus.STOP) null else packageDirectory,
            )
        }
        val copier: (List<File>) -> Boolean = { files ->
            fixture.copyCalls += 1
            fixture.lastCopied = files.toList()
            copySucceeds
        }
        fixture.adapter = construct(
            "com.hugr.wearos.WatchRetainedTimingExportAdapter",
            core,
            journalRoot,
            recorderRoot,
            destinationRoot,
            identity,
            copier,
        )
        fixture.initialSourceHashes = sourceHashes(fixture)
        return fixture
    }

    private class WatchFixture(
        val journalRoot: File,
        val recorderRoot: File,
        val destinationRoot: File,
        val packageDirectory: File,
    ) {
        lateinit var adapter: Any
        var coreCalls = 0
        var copyCalls = 0
        var lastRequest: RetainedTimingExportRequest? = null
        var lastCopied: List<File> = emptyList()
        var initialSourceHashes: Map<String, String> = emptyMap()
    }

    private fun sourceHashes(fixture: WatchFixture): Map<String, String> = hashTrees(fixture.journalRoot, fixture.recorderRoot)
    private fun packageHashes(fixture: WatchFixture): Map<String, String> = hashTrees(fixture.packageDirectory)
    private fun assertSourceHashesUnchanged(fixture: WatchFixture) = assertEquals(fixture.initialSourceHashes, sourceHashes(fixture))

    private fun hashTrees(vararg roots: File): Map<String, String> = roots.flatMap { root ->
        root.walkTopDown().filter { it.isFile }.map { file ->
            "${root.name}/${file.relativeTo(root).invariantSeparatorsPath}" to sha256(file.readBytes())
        }.toList()
    }.toMap()

    private fun construct(className: String, vararg arguments: Any): Any {
        val type = Class.forName(className)
        val constructor = type.declaredConstructors.single { it.parameterCount == arguments.size }
        constructor.isAccessible = true
        return constructor.newInstance(*arguments)
    }

    private fun call(target: Any, name: String, vararg arguments: Any): Any {
        val method = target.javaClass.methods.single { it.name == name && it.parameterCount == arguments.size }
        return requireNotNull(method.invoke(target, *arguments))
    }

    private fun property(target: Any, name: String): Any {
        val getter = "get" + name.replaceFirstChar { it.uppercase() }
        return requireNotNull(target.javaClass.methods.single { it.name == getter && it.parameterCount == 0 }.invoke(target))
    }

    private fun locateSource(relative: String): File {
        var cursor = File(System.getProperty("user.dir")).canonicalFile
        repeat(8) {
            val candidate = File(cursor, relative)
            if (candidate.isFile) return candidate
            cursor = cursor.parentFile ?: return@repeat
        }
        error("source file not found: $relative")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
