package com.hugr.wearos

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Temporary Watch57 diagnostic metadata.
 *
 * The store is separate from retained HUGR evidence. Watch57 reads the prior Watch56 launch request
 * and startup snapshot without rewriting them, then passively inspects the existing journal and
 * recorder through RetainedStateInspector.
 */
internal enum class StartupBreadcrumbStage {
    ACTIVITY_ONCREATE_ENTER,
    FIRST_FRAME_LAYOUT_ATTACHED,
    FIRST_FRAME_PRESENTATION_SET,
    FIRST_DRAW_OBSERVED,
    RECORDER_INITIALIZATION_ENTER,
    RECORDER_INITIALIZATION_FAILURE,
    BASELINE_INITIALIZATION_ENTER,
    BASELINE_INITIALIZATION_FAILURE,
    PERMISSION_PATH_ENTER,
}

internal object StartupBreadcrumbPlan {
    const val CANDIDATE_ID = "watch57w-retained-state-inspector"
    const val EXTRA_DIAGNOSTIC_LAUNCH_ID = "com.hugr.wearos.extra.DIAGNOSTIC_LAUNCH_ID"

    val normalPath = listOf(
        StartupBreadcrumbStage.ACTIVITY_ONCREATE_ENTER,
        StartupBreadcrumbStage.FIRST_FRAME_LAYOUT_ATTACHED,
        StartupBreadcrumbStage.FIRST_FRAME_PRESENTATION_SET,
        StartupBreadcrumbStage.FIRST_DRAW_OBSERVED,
        StartupBreadcrumbStage.RECORDER_INITIALIZATION_ENTER,
        StartupBreadcrumbStage.BASELINE_INITIALIZATION_ENTER,
        StartupBreadcrumbStage.PERMISSION_PATH_ENTER,
    )
}

internal data class StartupBreadcrumbSnapshot(
    val candidateId: String,
    val runId: String,
    val stage: StartupBreadcrumbStage,
    val recordedAtEpochMillis: Long,
)

internal data class StartupLaunchRequest(
    val candidateId: String,
    val launchId: String,
    val requestedAtEpochMillis: Long,
)

internal class StartupBreadcrumbStore private constructor(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    fun recordLaunchRequest(launchId: String) {
        preferences.edit()
            .putString(KEY_REQUEST_CANDIDATE_ID, StartupBreadcrumbPlan.CANDIDATE_ID)
            .putString(KEY_REQUEST_ID, launchId)
            .putLong(KEY_REQUESTED_AT_EPOCH_MILLIS, System.currentTimeMillis())
            .commit()
    }

    fun record(stage: StartupBreadcrumbStage, runId: String) {
        preferences.edit()
            .putString(KEY_CANDIDATE_ID, StartupBreadcrumbPlan.CANDIDATE_ID)
            .putString(KEY_RUN_ID, runId)
            .putString(KEY_STAGE, stage.name)
            .putLong(KEY_RECORDED_AT_EPOCH_MILLIS, System.currentTimeMillis())
            .commit()
    }

    fun readLaunchRequest(): StartupLaunchRequest? = readAnyCandidateLaunchRequest()
        ?.takeIf { it.candidateId == StartupBreadcrumbPlan.CANDIDATE_ID }

    fun read(): StartupBreadcrumbSnapshot? = readAnyCandidate()
        ?.takeIf { it.candidateId == StartupBreadcrumbPlan.CANDIDATE_ID }

    fun readAnyCandidateLaunchRequest(): StartupLaunchRequest? {
        val candidateId = preferences.getString(KEY_REQUEST_CANDIDATE_ID, null) ?: return null
        val launchId = preferences.getString(KEY_REQUEST_ID, null) ?: return null
        return StartupLaunchRequest(
            candidateId = candidateId,
            launchId = launchId,
            requestedAtEpochMillis = preferences.getLong(KEY_REQUESTED_AT_EPOCH_MILLIS, 0L),
        )
    }

    fun readAnyCandidate(): StartupBreadcrumbSnapshot? {
        val candidateId = preferences.getString(KEY_CANDIDATE_ID, null) ?: return null
        val runId = preferences.getString(KEY_RUN_ID, null) ?: return null
        val stageName = preferences.getString(KEY_STAGE, null) ?: return null
        val stage = runCatching { StartupBreadcrumbStage.valueOf(stageName) }.getOrNull() ?: return null
        return StartupBreadcrumbSnapshot(
            candidateId = candidateId,
            runId = runId,
            stage = stage,
            recordedAtEpochMillis = preferences.getLong(KEY_RECORDED_AT_EPOCH_MILLIS, 0L),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "hugr_startup_breadcrumb_diagnostic"
        const val KEY_CANDIDATE_ID = "candidate_id"
        const val KEY_RUN_ID = "run_id"
        const val KEY_STAGE = "stage"
        const val KEY_RECORDED_AT_EPOCH_MILLIS = "recorded_at_epoch_millis"
        const val KEY_REQUEST_CANDIDATE_ID = "request_candidate_id"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_REQUESTED_AT_EPOCH_MILLIS = "requested_at_epoch_millis"
    }
}

/**
 * Passive Watch57 viewer. Opening this activity does not launch MainActivity or construct any HUGR
 * runtime. It reads only existing diagnostic metadata, canonical source-segment bytes, causal rows,
 * and the historical baseline file. Retained payload values are never rendered.
 */
class StartupBreadcrumbDiagnosticActivity : ComponentActivity() {
    private lateinit var resultText: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(2, 24, 18))
        }
        layout.addView(TextView(this).apply {
            text = "HUGR Retained State Check\nWatch57 passive inspector"
            textSize = 14f
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = "READ ONLY — no MainActivity, services, BLE, sensors, permissions, export, acknowledgement, cleanup, or payload display."
            textSize = 8f
            setTextColor(Color.YELLOW)
            setPadding(0, 6, 0, 8)
        })
        resultText = TextView(this).apply {
            text = "Preparing passive inventory…"
            textSize = 8f
            setTextColor(Color.WHITE)
        }
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                resultText,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        layout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)

        val store = StartupBreadcrumbStore(this)
        val previousRequest = store.readAnyCandidateLaunchRequest()
        val previousSnapshot = store.readAnyCandidate()
        Thread {
            val inspection = runCatching {
                RetainedStateInspector.inspect(filesDir) { progress ->
                    runOnUiThread {
                        resultText.text = "PASSIVE READ IN PROGRESS\n$progress"
                    }
                }
            }
            runOnUiThread {
                resultText.text = inspection.fold(
                    onSuccess = { render(previousRequest, previousSnapshot, it) },
                    onFailure = { "PASSIVE INSPECTION STOP\nFailure class: ${it.javaClass.simpleName}\nNo HUGR runtime was started." },
                )
                scrollView.post { scrollView.fullScroll(View.FOCUS_UP) }
            }
        }.start()
    }

    private fun render(
        request: StartupLaunchRequest?,
        snapshot: StartupBreadcrumbSnapshot?,
        inspection: RetainedStateInspection,
    ): String {
        val tokenMatch = request != null && snapshot != null &&
            request.candidateId == snapshot.candidateId && request.launchId == snapshot.runId
        val streamCounts = SourceStreamCode.entries.joinToString(" ") {
            "${it.name}=${inspection.streamCounts[it] ?: 0L}"
        }
        val callbacks = inspection.callbackStreams.map { it.name }.sorted().joinToString().ifBlank { "none" }
        val appends = inspection.appendStreams.map { it.name }.sorted().joinToString().ifBlank { "none" }
        val paired = inspection.callbackAndAppendStreams.map { it.name }.sorted().joinToString().ifBlank { "none" }
        val baseline = inspection.historicalBaseline
        return buildString {
            appendLine("PRE-INSTALL STARTUP SNAPSHOT")
            appendLine("requestCandidate=${request?.candidateId ?: "none"}")
            appendLine("snapshotCandidate=${snapshot?.candidateId ?: "none"}")
            appendLine("tokenMatch=${if (tokenMatch) "YES" else "NO"}")
            appendLine("lastDurableStage=${snapshot?.stage?.name ?: "none"}")
            appendLine("stageEpochMs=${snapshot?.recordedAtEpochMillis ?: 0L}")
            appendLine()
            appendLine("PASSIVE RETAINED-STATE RESULT")
            appendLine("outcome=${inspection.outcome.name}")
            appendLine("journalRoot=${inspection.journalRootState} recorderRoot=${inspection.recorderRootState}")
            appendLine("journalObjects=${inspection.journalObjectCount} bytes=${inspection.journalBytes}")
            appendLine("segmentObjects=${inspection.segmentObjectCount}")
            appendLine("canonicalRecords=${inspection.decodedRecordCount}")
            appendLine("physiologyRecords=${inspection.physiologicalRecordCount}")
            appendLine("schemaValidPhysiology=${inspection.payloadSchemaValidPhysiologicalRecordCount}")
            appendLine("sessions=${inspection.sessionCount} recordRange=${inspection.firstRecordIndex ?: 0L}-${inspection.lastRecordIndex ?: 0L}")
            appendLine(streamCounts)
            appendLine("incompleteTailBytes=${inspection.incompleteTailBytes}")
            appendLine("unreadableSegments=${inspection.journalUnreadableObjectCount}")
            appendLine("finalizedHashMismatches=${inspection.finalizedHashMismatchCount}")
            appendLine()
            appendLine("CAUSAL RECORDER")
            appendLine("integrity=${inspection.causalIntegrity} validEvents=${inspection.causalEventCount} invalidRows=${inspection.causalInvalidRowCount}")
            appendLine("firstCallbackStreams=$callbacks")
            appendLine("firstAppendStreams=$appends")
            appendLine("callbackPlusAppendForRetainedStreams=$paired")
            appendLine("latestCausalWallMs=${inspection.latestCausalWallTimeMs ?: 0L}")
            appendLine()
            appendLine("HISTORICAL BASELINE")
            appendLine("state=${inspection.baselineReadState}")
            if (baseline != null) appendLine(Build46BaselineFormatter.format(baseline))
            appendLine()
            appendLine("NON-MUTATION CHECK")
            appendLine("journalMetadataUnchanged=${yesNo(inspection.sourceMetadataUnchangedDuringInspection)}")
            appendLine("recorderMetadataUnchanged=${yesNo(inspection.recorderMetadataUnchangedDuringInspection)}")
            appendLine("scanDurationMs=${inspection.scanDurationMs}")
            appendLine()
            appendLine("CLAIM BOUNDARY")
            appendLine("Canonical records here prove retained bytes and codec validity in this installed app store, not physiological truth, participant identity, completeness, clinical meaning, receiver durability, or deterministic reconstruction.")
        }
    }

    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"
}
