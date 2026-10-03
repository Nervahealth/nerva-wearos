package com.hugr.wearos

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.security.MessageDigest
import java.util.UUID

enum class WatchRetainedTimingExportState {
    IDLE,
    AWAITING_FIRST_CONFIRMATION,
    AWAITING_SECOND_CONFIRMATION,
    READY_FOR_TRANSFER,
    CORE_STOP,
    TRANSFER_COMPLETE,
    COPY_OR_HASH_STOP,
}

data class WatchRetainedTimingExportSnapshot(
    val state: WatchRetainedTimingExportState,
    val coreStatus: RetainedTimingExportStatus?,
    val reasons: Set<RetainedTimingExportReason>,
    val transferFiles: List<File>,
    val retainedObjectCount: Int,
)

class WatchRetainedTimingExportAdapter(
    private val core: (RetainedTimingExportRequest) -> RetainedTimingExportResult,
    private val journalRoot: File,
    private val recorderRoot: File,
    private val destinationRoot: File,
    private val sourceIdentity: RetainedTimingSourceIdentity,
    private val copier: (List<File>) -> Boolean,
) {
    private var state = WatchRetainedTimingExportState.IDLE
    private var result: RetainedTimingExportResult? = null
    private var coreInvoked = false
    private var copyAttempted = false

    fun open(): WatchRetainedTimingExportSnapshot {
        if (state == WatchRetainedTimingExportState.IDLE) {
            state = WatchRetainedTimingExportState.AWAITING_FIRST_CONFIRMATION
        }
        return snapshot()
    }

    fun confirmIntent(): WatchRetainedTimingExportSnapshot {
        if (state == WatchRetainedTimingExportState.AWAITING_FIRST_CONFIRMATION) {
            state = WatchRetainedTimingExportState.AWAITING_SECOND_CONFIRMATION
        }
        return snapshot()
    }

    fun confirmExport(): WatchRetainedTimingExportSnapshot {
        if (state != WatchRetainedTimingExportState.AWAITING_SECOND_CONFIRMATION || coreInvoked) {
            return snapshot()
        }
        coreInvoked = true
        result = core(
            RetainedTimingExportRequest(
                journalRoot = journalRoot,
                recorderRoot = recorderRoot,
                destinationRoot = destinationRoot,
                sourceIdentity = sourceIdentity,
                selection = FULL_RETAINED_WATCH_STORE,
            ),
        )
        state = if (result?.status == RetainedTimingExportStatus.STOP) {
            WatchRetainedTimingExportState.CORE_STOP
        } else {
            WatchRetainedTimingExportState.READY_FOR_TRANSFER
        }
        return snapshot()
    }

    fun copyToHost(): WatchRetainedTimingExportSnapshot {
        if (state != WatchRetainedTimingExportState.READY_FOR_TRANSFER || copyAttempted) {
            return snapshot()
        }
        copyAttempted = true
        state = if (copier(transferFiles())) {
            WatchRetainedTimingExportState.TRANSFER_COMPLETE
        } else {
            WatchRetainedTimingExportState.COPY_OR_HASH_STOP
        }
        return snapshot()
    }

    private fun snapshot(): WatchRetainedTimingExportSnapshot = WatchRetainedTimingExportSnapshot(
        state = state,
        coreStatus = result?.status,
        reasons = result?.reasons.orEmpty(),
        transferFiles = transferFiles(),
        retainedObjectCount = result?.objects?.size ?: 0,
    )

    private fun transferFiles(): List<File> {
        val directory = result?.packageDirectory ?: return emptyList()
        return directory.listFiles().orEmpty()
            .filter { it.isFile && (it.extension == "zip" || it.name.endsWith(".zip.sha256")) }
            .sortedBy { it.name }
    }

    companion object {
        const val FULL_RETAINED_WATCH_STORE = "FULL_RETAINED_WATCH_STORE"
    }
}

class RetainedTimingExportActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var adapter: WatchRetainedTimingExportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val journalRoot = File(filesDir, "build45_source_journal")
        val recorderRoot = File(filesDir, "build47_causal_flight_recorder")
        val destinationRoot = File(filesDir, "retained_timing_exports")
        val exporter = RetainedTimingExporter(exportId = ::nextExportId)
        adapter = WatchRetainedTimingExportAdapter(
            core = exporter::export,
            journalRoot = journalRoot,
            recorderRoot = recorderRoot,
            destinationRoot = destinationRoot,
            sourceIdentity = runtimeIdentity(),
            copier = { false },
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(TextView(this).apply {
            text = "HUGR Evidence Export\nFull retained watch store\nDestination: staged for separately authorized host copy"
            setTextColor(Color.WHITE)
            textSize = 11f
        })
        statusText = TextView(this).apply {
            setTextColor(Color.GREEN)
            textSize = 9f
        }
        layout.addView(ScrollView(this).apply { addView(statusText) })

        val acknowledge = Button(this).apply {
            text = "I understand the export scope"
            setOnClickListener { render(adapter.confirmIntent()) }
        }
        val generate = Button(this).apply {
            text = "Generate package"
            setOnClickListener { render(adapter.confirmExport()) }
        }
        layout.addView(acknowledge)
        layout.addView(generate)
        setContentView(layout)

        render(adapter.open(), incompleteNames(destinationRoot))
    }

    private fun render(snapshot: WatchRetainedTimingExportSnapshot, incomplete: List<String> = emptyList()) {
        val files = snapshot.transferFiles.joinToString { it.name }
        val zipHash = snapshot.transferFiles.firstOrNull { it.extension == "zip" }
            ?.let { sha256(it.readBytes()).take(12) }
            ?: "none"
        statusText.text = buildString {
            appendLine("state=${snapshot.state}")
            appendLine("coreStatus=${snapshot.coreStatus ?: "not-run"}")
            appendLine("reasons=${snapshot.reasons.map { it.name }.sorted()}")
            appendLine("retainedObjects=${snapshot.retainedObjectCount}")
            appendLine("packageObjects=$files")
            appendLine("zipSha256Prefix=$zipHash")
            appendLine("incompleteStaging=$incomplete")
        }
    }

    private fun incompleteNames(root: File): List<String> = root.listFiles().orEmpty()
        .filter { it.name.startsWith(".") || it.name.endsWith(".tmp") }
        .map { it.name }
        .sorted()

    private fun runtimeIdentity(): RetainedTimingSourceIdentity {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val commit = applicationInfo.metaData?.getString(SOURCE_COMMIT_METADATA).orEmpty()
        return RetainedTimingSourceIdentity(
            packageName = packageName,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode.toInt(),
            sourceCommit = commit,
            artifactSha256 = null,
        )
    }

    private fun nextExportId(): String = "watch-${System.currentTimeMillis()}-${UUID.randomUUID()}"

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val SOURCE_COMMIT_METADATA = "health.hugr.sourceCommit"
    }
}
