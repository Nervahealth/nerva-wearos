package com.hugr.wearos

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Passive viewer for the most recent normal-launch stage marker.
 *
 * It does not start MainActivity or any service. It reads only the small marker
 * preference and therefore never traverses, decodes, hashes, writes, exports,
 * acknowledges, prunes, migrates, or otherwise touches retained source data.
 */
class NormalStartupMarkerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(2, 24, 18))
        }
        layout.addView(TextView(this).apply {
            text = "HUGR Startup Readiness\nNormal-launch marker viewer"
            textSize = 14f
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = "READ ONLY — no MainActivity, journal recovery, service, BLE, sensor, permission, export, acknowledgement, cleanup, or payload display."
            textSize = 8f
            setTextColor(Color.YELLOW)
            setPadding(0, 6, 0, 8)
        })
        val resultText = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.WHITE)
            text = render(NormalStartupMarkerStore(this@NormalStartupMarkerActivity).read())
        }
        layout.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(
                resultText,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
    }

    private fun render(marker: NormalStartupMarker?): String = buildString {
        appendLine("MOST RECENT ORDINARY STARTUP MARKER")
        appendLine("runId=${marker?.runId ?: "none"}")
        appendLine("lastStage=${marker?.stage?.name ?: "none"}")
        appendLine("stageEpochMs=${marker?.recordedAtEpochMillis ?: 0L}")
        appendLine()
        appendLine("INTERPRETATION")
        appendLine("This marker identifies only the latest recorded ordinary-launch boundary. It does not prove app survival, journal completeness, sensor operation, BLE connection, participant data, transfer, receipt, verification, or clinical meaning.")
    }
}
