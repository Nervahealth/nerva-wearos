package com.hugr.wearos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.app.NotificationCompat

/**
 * HUGR Haptic Lab 2 — System Path Investigation
 * 
 * Goal: Find a way to achieve Samsung Gallop-level vibration intensity
 * from a third-party app by exploiting notification channels, Samsung
 * vendor effects, and system event paths.
 *
 * Key hypothesis: Samsung's notification system uses a privileged
 * vibration driver path that produces stronger output than the
 * generic Vibrator API.
 */
class HapticLab2Activity : ComponentActivity() {

    private lateinit var vibrator: Vibrator
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_CUSTOM_WAVE = "hugr_haptic_custom"
        const val CHANNEL_DEFAULT = "hugr_haptic_default"
        const val CHANNEL_HIGH = "hugr_haptic_high"
        const val CHANNEL_ALARM = "hugr_haptic_alarm"
        const val CHANNEL_GALLOP_USER = "hugr_haptic_gallop"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create all notification channels
        createNotificationChannels()

        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "HAPTIC LAB 2\nSYSTEM PATH"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FF6600"))
        })

        // Info
        layout.addView(TextView(this).apply {
            text = "Compare ALL to Gallop (Settings)\nGallop = 4/4 reference"
            textSize = 8f
            setTextColor(android.graphics.Color.YELLOW)
            setPadding(0, 4, 0, 8)
        })

        // ─── TEST 1: NotificationChannel with custom waveform ───
        addTestButton(layout, "1: Notif + Custom Wave") {
            log("1: NotificationChannel with custom vibration pattern")
            fireNotification(CHANNEL_CUSTOM_WAVE, "HUGR Test 1", "Custom waveform via notification", 1001)
        }

        // ─── TEST 2: Default notification vibration (Samsung chooses) ───
        addTestButton(layout, "2: Notif + Default Vib") {
            log("2: Default notification vibration (Samsung decides)")
            fireNotification(CHANNEL_DEFAULT, "HUGR Test 2", "Default vibration", 1002)
        }

        // ─── TEST 3: HIGH importance notification ───
        addTestButton(layout, "3: HIGH Importance Notif ★") {
            log("3: HIGH importance notification")
            fireNotification(CHANNEL_HIGH, "HUGR Test 3", "High importance", 1003)
        }

        // ─── TEST 4: ALARM-class notification (max priority) ───
        addTestButton(layout, "4: ALARM Class ★★") {
            log("4: ALARM-class notification (max system priority)")
            fireNotification(CHANNEL_ALARM, "HUGR ALERT", "Alarm class notification", 1004)
        }

        // ─── TEST 5: Channel for user to assign Gallop ★★★ ───
        addTestButton(layout, "5: USER GALLOP Channel ★★★") {
            log("5: Notification on user-configurable channel")
            log("   → Go to Watch Settings → Apps → HUGR")
            log("   → Notifications → HUGR Gallop")
            log("   → Set vibration to GALLOP")
            log("   → Then press this button again")
            fireNotification(CHANNEL_GALLOP_USER, "HUGR Intervention", "Gallop channel test", 1005)
        }

        // ─── TEST 6: Rapid-fire notifications (simulate temporal pattern) ───
        addTestButton(layout, "6: 5x Rapid Notifs ★★") {
            log("6: 5 rapid notifications (temporal via system)")
            Thread {
                try {
                    for (i in 1..5) {
                        val id = 2000 + i
                        runOnUiThread {
                            fireNotification(CHANNEL_HIGH, "HUGR $i", "Rapid $i", id)
                        }
                        Thread.sleep(300)
                    }
                    runOnUiThread { log("6: 5 notifications fired") }
                } catch (e: Exception) {
                    runOnUiThread { log("6 FAILED: ${e.message}") }
                }
            }.start()
        }

        // ─── TEST 7: Samsung vendor effect IDs (probe) ───
        addTestButton(layout, "7: Samsung Effects 1-30") {
            log("7: Probing Samsung vendor effect IDs 1-30...")
            Thread {
                try {
                    for (effectId in 1..30) {
                        try {
                            val effect = VibrationEffect.createPredefined(effectId)
                            vibrator.vibrate(effect)
                            runOnUiThread { log("   Effect $effectId: PLAYED") }
                            Thread.sleep(500)
                        } catch (e: Exception) {
                            // Skip silently — most will fail
                        }
                    }
                    runOnUiThread { log("7: Probe complete") }
                } catch (e: Exception) {
                    runOnUiThread { log("7 FAILED: ${e.message}") }
                }
            }.start()
        }

        // ─── TEST 8: Samsung HapticFeedback constants 50000-50200 ───
        addTestButton(layout, "8: Samsung FB 50000-50200") {
            log("8: Probing Samsung haptic feedback 50000-50200...")
            Thread {
                var found = 0
                try {
                    for (id in 50000..50200 step 10) {
                        try {
                            val result = window.decorView.performHapticFeedback(id)
                            if (result) {
                                found++
                                runOnUiThread { log("   FB $id: SUCCESS ✓") }
                                Thread.sleep(400)
                            }
                        } catch (e: Exception) { /* skip */ }
                    }
                    runOnUiThread { log("8: Found $found working Samsung FB constants") }
                } catch (e: Exception) {
                    runOnUiThread { log("8 FAILED: ${e.message}") }
                }
            }.start()
        }

        // ─── TEST 9: Accessibility haptic ───
        addTestButton(layout, "9: Accessibility Haptic") {
            log("9: Accessibility haptic feedback")
            try {
                // GESTURE_START and GESTURE_END are accessibility-related
                window.decorView.performHapticFeedback(12) // GESTURE_START
                Thread.sleep(200)
                window.decorView.performHapticFeedback(13) // GESTURE_END
            } catch (e: Exception) { log("9 FAILED: ${e.message}") }
        }

        // ─── TEST 10: G (REJECT) x5 rapid as temporal pattern ───
        addTestButton(layout, "10: 5x REJECT rapid ★★") {
            log("10: 5x performHapticFeedback(REJECT) rapid-fire")
            Thread {
                try {
                    for (i in 1..5) {
                        runOnUiThread {
                            window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        }
                        Thread.sleep(200)
                    }
                    runOnUiThread { log("10: 5x REJECT complete") }
                } catch (e: Exception) {
                    runOnUiThread { log("10 FAILED: ${e.message}") }
                }
            }.start()
        }

        // ─── REFERENCE: Samsung Gallop reminder ───
        layout.addView(TextView(this).apply {
            text = "\n→ REFERENCE: Go to Settings →\nSound & Vibration → Varselsvibrering\n→ Gallop = 4/4"
            textSize = 8f
            setTextColor(android.graphics.Color.parseColor("#00FFFF"))
            setPadding(0, 8, 0, 4)
        })

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

    private fun createNotificationChannels() {
        // Channel 1: Custom waveform
        val ch1 = NotificationChannel(CHANNEL_CUSTOM_WAVE, "HUGR Custom Wave", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 300)
            description = "Custom vibration pattern test"
        }

        // Channel 2: Default vibration (let Samsung decide)
        val ch2 = NotificationChannel(CHANNEL_DEFAULT, "HUGR Default", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(true)
            description = "Default system vibration"
        }

        // Channel 3: HIGH importance
        val ch3 = NotificationChannel(CHANNEL_HIGH, "HUGR High Priority", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300)
            description = "High importance notification"
        }

        // Channel 4: ALARM class (maximum system priority)
        val ch4 = NotificationChannel(CHANNEL_ALARM, "HUGR Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            description = "Alarm-class notification"
            setBypassDnd(true)
        }

        // Channel 5: USER-CONFIGURABLE — user assigns Gallop to this channel
        val ch5 = NotificationChannel(CHANNEL_GALLOP_USER, "HUGR Gallop", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            // Don't set vibrationPattern — let user configure it in watch settings
            description = "User-configurable: assign Gallop vibration in Watch Settings"
        }

        notificationManager.createNotificationChannel(ch1)
        notificationManager.createNotificationChannel(ch2)
        notificationManager.createNotificationChannel(ch3)
        notificationManager.createNotificationChannel(ch4)
        notificationManager.createNotificationChannel(ch5)

        log("5 notification channels created")
    }

    private fun fireNotification(channelId: String, title: String, text: String, notifId: Int) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
        log("   → Notification fired (id=$notifId, channel=$channelId)")
    }

    private fun addTestButton(layout: LinearLayout, label: String, action: () -> Unit) {
        layout.addView(Button(this).apply {
            text = label
            textSize = 9f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#442200"))
            setPadding(8, 4, 8, 4)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
            layoutParams = params
            setOnClickListener {
                try { action() } catch (e: Exception) { log("ERROR: ${e.message}") }
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
