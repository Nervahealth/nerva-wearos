package com.hugr.wearos

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

/** Reads only already-saved operational reports. No journal, sensor, BLE or replay initialization. */
class SavedDiagnosticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = WatchDiagnosticRuntime.store(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 28)
            setBackgroundColor(Color.BLACK)
        }
        val heading = TextView(this).apply { text = "HUGR Saved Diagnostics\nNOT recording verification"; setTextColor(Color.WHITE); textSize = 12f }
        layout.addView(heading)
        val content = TextView(this).apply { setTextColor(Color.WHITE); textSize = 10f; setTextIsSelectable(true) }
        val files = store.reports()
        var index = 0
        var selected: File? = null
        fun show() {
            selected = files.getOrNull(index)
            content.text = selected?.let { file ->
                runCatching { store.read(file) }.getOrElse { "Saved report unreadable or integrity mismatch. No recovery was started." }
            } ?: "No saved operational reports. No recovery was started."
            heading.text = "HUGR Saved Diagnostics ${if (files.isEmpty()) "0/0" else "${index + 1}/${files.size}"}\nNOT recording verification"
        }
        layout.addView(Button(this).apply {
            text = "Next saved report"
            setOnClickListener { if (files.isNotEmpty()) { index = (index + 1) % files.size; show() } }
        })
        layout.addView(Button(this).apply {
            text = "Share this diagnostic"
            setOnClickListener {
                val file = selected ?: return@setOnClickListener
                try {
                    store.read(file) // refuse damaged reports
                    val uri = FileProvider.getUriForFile(this@SavedDiagnosticActivity, "$packageName.diagnostics", file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newRawUri("HUGR diagnostic", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "Share saved operational diagnostic"))
                } catch (_: Exception) {
                    Toast.makeText(this@SavedDiagnosticActivity, "No compatible sharing app, or report unavailable. Report remains saved; view/record it here.", Toast.LENGTH_LONG).show()
                }
            }
        })
        // One outer scroll includes controls and report; no fixed buttons squeeze the text.
        layout.addView(content, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this).apply { addView(layout) }
        show()
        setContentView(scroll)
    }
}
