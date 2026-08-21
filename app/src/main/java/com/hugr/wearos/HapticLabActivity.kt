package com.hugr.wearos

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * HUGR Haptic Lab — Systematic characterization of Galaxy Watch 8 haptic capabilities.
 * Tests every available API path to find the strongest perceptible vibration.
 *
 * Rate each test 0-4:
 * 0 = nothing
 * 1 = detectable if concentrating
 * 2 = clearly detectable
 * 3 = impossible to miss
 * 4 = strong
 */
class HapticLabActivity : ComponentActivity() {

    private lateinit var vibrator: Vibrator
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "HUGR HAPTIC LAB"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#00FF88"))
        })

        // Capability report
        val capReport = buildCapabilityReport()
        layout.addView(TextView(this).apply {
            text = capReport
            textSize = 8f
            setTextColor(android.graphics.Color.YELLOW)
            setPadding(0, 4, 0, 8)
        })

        // ─── Test Buttons ───

        // A: Current HUGR waveform (baseline)
        addTestButton(layout, "A: HUGR Waveform (baseline)") {
            log("A: HUGR createWaveform max amplitude")
            val timings = longArrayOf(0, 25, 60, 25, 60, 40)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }

        // B: Raw max amplitude createOneShot
        addTestButton(layout, "B: OneShot MAX (200ms)") {
            log("B: createOneShot(200, 255)")
            vibrator.vibrate(VibrationEffect.createOneShot(200, 255))
        }

        // C: EFFECT_CLICK
        addTestButton(layout, "C: EFFECT_CLICK") {
            log("C: EFFECT_CLICK")
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } catch (e: Exception) { log("C FAILED: ${e.message}") }
        }

        // D: EFFECT_DOUBLE_CLICK
        addTestButton(layout, "D: EFFECT_DOUBLE_CLICK") {
            log("D: EFFECT_DOUBLE_CLICK")
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } catch (e: Exception) { log("D FAILED: ${e.message}") }
        }

        // E: EFFECT_HEAVY_CLICK *** VERY IMPORTANT ***
        addTestButton(layout, "E: HEAVY_CLICK ★★★") {
            log("E: EFFECT_HEAVY_CLICK")
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } catch (e: Exception) { log("E FAILED: ${e.message}") }
        }

        // F: EFFECT_TICK
        addTestButton(layout, "F: EFFECT_TICK") {
            log("F: EFFECT_TICK")
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } catch (e: Exception) { log("F FAILED: ${e.message}") }
        }

        // G: performHapticFeedback(REJECT) *** VERY IMPORTANT ***
        addTestButton(layout, "G: HapticFB REJECT ★★★") {
            log("G: performHapticFeedback(REJECT)")
            try {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } catch (e: Exception) { log("G FAILED: ${e.message}") }
        }

        // H: performHapticFeedback(CONFIRM)
        addTestButton(layout, "H: HapticFB CONFIRM") {
            log("H: performHapticFeedback(CONFIRM)")
            try {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } catch (e: Exception) { log("H FAILED: ${e.message}") }
        }

        // I: performHapticFeedback(LONG_PRESS)
        addTestButton(layout, "I: HapticFB LONG_PRESS") {
            log("I: performHapticFeedback(LONG_PRESS)")
            try {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (e: Exception) { log("I FAILED: ${e.message}") }
        }

        // J: Samsung-specific constant 101
        addTestButton(layout, "J: Samsung 101 (rotary)") {
            log("J: Samsung-specific haptic 101")
            try {
                window.decorView.performHapticFeedback(101)
            } catch (e: Exception) { log("J FAILED: ${e.message}") }
        }

        // K: Samsung-specific constant 102
        addTestButton(layout, "K: Samsung 102 (rotary)") {
            log("K: Samsung-specific haptic 102")
            try {
                window.decorView.performHapticFeedback(102)
            } catch (e: Exception) { log("K FAILED: ${e.message}") }
        }

        // L: Samsung-specific constant 50107
        addTestButton(layout, "L: Samsung 50107") {
            log("L: Samsung-specific haptic 50107")
            try {
                window.decorView.performHapticFeedback(50107)
            } catch (e: Exception) { log("L FAILED: ${e.message}") }
        }

        // M: Composition with PRIMITIVE_CLICK x5
        addTestButton(layout, "M: Composition 5xCLICK") {
            log("M: Composition PRIMITIVE_CLICK x5")
            try {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 0)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
                    .compose()
                vibrator.vibrate(comp)
            } catch (e: Exception) { log("M FAILED: ${e.message}") }
        }

        // N: Composition with PRIMITIVE_THUD x3
        addTestButton(layout, "N: Composition 3xTHUD") {
            log("N: Composition PRIMITIVE_THUD x3")
            try {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 80)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 80)
                    .compose()
                vibrator.vibrate(comp)
            } catch (e: Exception) { log("N FAILED: ${e.message}") }
        }

        // O: FREQUENCY SWEEP — accelerating then decelerating clicks
        addTestButton(layout, "O: FREQ SWEEP ★★★") {
            log("O: Frequency sweep (accel→decel)")
            Thread {
                try {
                    // Accelerating: gaps shrink from 300ms to 40ms
                    val gaps = listOf(300, 250, 200, 150, 120, 100, 80, 60, 50, 40, 40, 40, 40, 40)
                    // Then decelerating: gaps grow back
                    val decelGaps = listOf(50, 60, 80, 100, 120, 150, 200, 250, 300)
                    val allGaps = gaps + decelGaps

                    for (gap in allGaps) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                        Thread.sleep(gap.toLong())
                    }
                    runOnUiThread { log("O: Sweep complete (${allGaps.size} clicks)") }
                } catch (e: Exception) {
                    runOnUiThread { log("O FAILED: ${e.message}") }
                }
            }.start()
        }

        // P: BREATHING WAVE — frequency-modulated breathing guide
        addTestButton(layout, "P: BREATHE WAVE ★★★") {
            log("P: Breathing wave (freq-modulated)")
            Thread {
                try {
                    // Attention burst: 3 rapid clicks
                    for (i in 1..3) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                        Thread.sleep(60)
                    }
                    Thread.sleep(400) // Pause — "listen..."

                    // Inhale (2.5s): frequency accelerates
                    val inhaleGaps = listOf(250, 200, 160, 130, 100, 80, 60, 50, 40, 40)
                    for (gap in inhaleGaps) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                        Thread.sleep(gap.toLong())
                    }

                    // Exhale (3.5s): frequency decelerates (longer exhale = calming)
                    val exhaleGaps = listOf(40, 50, 60, 80, 100, 130, 160, 200, 250, 300, 350, 400)
                    for (gap in exhaleGaps) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                        Thread.sleep(gap.toLong())
                    }

                    runOnUiThread { log("P: Breathe wave complete") }
                } catch (e: Exception) {
                    runOnUiThread { log("P FAILED: ${e.message}") }
                }
            }.start()
        }

        // Log area
        logText = TextView(this).apply {
            text = ""
            textSize = 7f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 8, 0, 0)
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 200
            )
        }
        scrollView.addView(logText)
        layout.addView(scrollView)

        root.addView(layout)
        setContentView(root)
    }

    private fun buildCapabilityReport(): String {
        val sb = StringBuilder()
        sb.appendLine("─── CAPABILITIES ───")
        sb.appendLine("hasVibrator: ${vibrator.hasVibrator()}")
        sb.appendLine("hasAmplitudeControl: ${vibrator.hasAmplitudeControl()}")

        // Check predefined effects support
        try {
            val effects = intArrayOf(
                VibrationEffect.EFFECT_CLICK,
                VibrationEffect.EFFECT_DOUBLE_CLICK,
                VibrationEffect.EFFECT_HEAVY_CLICK,
                VibrationEffect.EFFECT_TICK
            )
            val names = arrayOf("CLICK", "DBL_CLICK", "HEAVY_CLICK", "TICK")
            val supported = vibrator.areEffectsSupported(*effects)
            for (i in names.indices) {
                val status = when (supported[i]) {
                    Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> "YES ✓"
                    Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> "NO ✗"
                    else -> "UNKNOWN ?"
                }
                sb.appendLine("${names[i]}: $status")
            }
        } catch (e: Exception) {
            sb.appendLine("Effects check failed: ${e.message}")
        }

        // Check primitives support
        try {
            val primitives = intArrayOf(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            )
            val pNames = arrayOf("P_CLICK", "P_THUD", "P_SPIN", "P_Q_RISE", "P_S_RISE", "P_Q_FALL", "P_TICK", "P_LOW_TICK")
            val pSupported = vibrator.arePrimitivesSupported(*primitives)
            for (i in pNames.indices) {
                sb.appendLine("${pNames[i]}: ${if (pSupported[i]) "YES ✓" else "NO ✗"}")
            }
        } catch (e: Exception) {
            sb.appendLine("Primitives check failed: ${e.message}")
        }

        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("SDK: ${Build.VERSION.SDK_INT}")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        return sb.toString()
    }

    private fun addTestButton(layout: LinearLayout, label: String, action: () -> Unit) {
        layout.addView(Button(this).apply {
            text = label
            textSize = 9f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#333333"))
            setPadding(8, 4, 8, 4)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
            layoutParams = params
            setOnClickListener {
                try {
                    action()
                } catch (e: Exception) {
                    log("ERROR: ${e.message}")
                }
            }
        })
    }

    private fun log(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        logText.append("[$time] $msg\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
