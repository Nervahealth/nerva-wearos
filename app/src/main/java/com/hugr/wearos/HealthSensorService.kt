package com.hugr.wearos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import com.samsung.android.service.health.tracking.data.PpgType
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import java.util.Timer
import java.util.TimerTask

/**
 * HUGR Labs — HealthSensorService (Build 36w — PPG_CONTINUOUS + Skin Temp + Format Byte)
 *
 * Foreground service with WAKE_LOCK + BODY_SENSORS_BACKGROUND + foregroundServiceType="health"
 * Sensors: EDA_CONTINUOUS (1 Hz) + PPG_CONTINUOUS (25 Hz, Green+IR+Red) + ACCELEROMETER_CONTINUOUS (25 Hz) + SKIN_TEMPERATURE_CONTINUOUS
 * PPG replaces HEART_RATE_CONTINUOUS — provides raw waveform for IBI, HRV, SpO2, respiratory rate, PTT
 * Flush timer every 30s ensures data delivery when screen is off (Samsung SDK batching behaviour)
 * Skin temperature enables circadian phase estimation, stress/exercise disambiguation (Clusters 33, 43, 48)
 */
class HealthSensorService : Service() {

    companion object {
        private const val TAG = "HUGR-HealthSensor"
        const val ACTION_START_TRACKING = "com.hugr.wearos.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.hugr.wearos.STOP_TRACKING"
        const val ACTION_STATUS_UPDATE = "com.hugr.wearos.STATUS_UPDATE"
        private const val CHANNEL_ID = "hugr_sensor_channel"
        private const val NOTIFICATION_ID = 1
        private const val FLUSH_INTERVAL_MS = 30000L
    }

    private var healthTrackingService: HealthTrackingService? = null
    private var edaTracker: HealthTracker? = null
    private var ppgTracker: HealthTracker? = null
    private var accelerometerTracker: HealthTracker? = null
    private var skinTempTracker: HealthTracker? = null
    private var hrTracker: HealthTracker? = null  // Dual-stream: hardware IBI alongside PPG
    private var isConnected = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var flushTimer: Timer? = null
    private var isScreenOn = true

    // Screen state receiver — tracks when screen goes on/off for metadata tagging
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.d(TAG, "Screen ON — switching to real-time delivery")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Log.d(TAG, "Screen OFF — flush timer active for batched delivery")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startForegroundWithNotification()
                acquireWakeLock()
                registerScreenReceiver()
                startFlushTimer()
                connectAndStartTracking()
            }
            ACTION_STOP_TRACKING -> {
                stopTrackingAndDisconnect()
                stopFlushTimer()
                unregisterScreenReceiver()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                startForegroundWithNotification()
                acquireWakeLock()
                registerScreenReceiver()
                startFlushTimer()
                connectAndStartTracking()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTrackingAndDisconnect()
        stopFlushTimer()
        unregisterScreenReceiver()
        releaseWakeLock()
        super.onDestroy()
    }

    // ─── Foreground Service (Samsung-documented pattern) ────────────────────────

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HUGR Sensor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous physiological monitoring"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("HUGR Active")
            .setContentText("Monitoring EDA, PPG, Temp, movement")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        // CRITICAL: Use FOREGROUND_SERVICE_TYPE_HEALTH (required for sensor access in background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "Foreground service started with HEALTH type")
        sendStatus("Foreground service active (health type)")
    }

    // ─── Wake Lock (keeps CPU active when screen off) ──────────────────────────

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HUGR::SensorWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max (safety timeout)
        }
        Log.i(TAG, "Wake lock acquired")
        sendStatus("Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    // ─── Samsung SDK Connection ────────────────────────────────────────────────

    private fun connectAndStartTracking() {
        if (isConnected) {
            sendStatus("Already connected, starting trackers...")
            startAllTrackers()
            return
        }
        try {
            sendStatus("Connecting to Samsung Health SDK...")
            healthTrackingService = HealthTrackingService(connectionListener, applicationContext)
            healthTrackingService?.connectService()
            Log.i(TAG, "Connecting to Health Tracking Service...")
        } catch (e: Exception) {
            sendStatus("ERROR connecting: ${e.message}")
            Log.e(TAG, "Failed to connect: ${e.message}", e)
        }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Health Tracking Service connected")
            sendStatus("=== SDK CONNECTED ===")
            isConnected = true
            startAllTrackers()
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Health Tracking Service connection ended")
            sendStatus("SDK connection ENDED")
            isConnected = false
            healthTrackingService = null
        }

        override fun onConnectionFailed(error: HealthTrackerException?) {
            Log.e(TAG, "Connection failed: ${error?.message}")
            sendStatus("SDK FAILED: ${error?.message}")
            isConnected = false
            healthTrackingService = null
        }
    }

    private fun startAllTrackers() {
        val service = healthTrackingService ?: return

        val supportedTypes = service.trackingCapability.supportHealthTrackerTypes
        sendStatus("Supported: ${supportedTypes.size} types: ${supportedTypes.joinToString(", ") { it.name }}")

        // EDA tracker — independent try/catch
        try {
            if (supportedTypes.contains(HealthTrackerType.EDA_CONTINUOUS)) {
                edaTracker = service.getHealthTracker(HealthTrackerType.EDA_CONTINUOUS)
                edaTracker?.setEventListener(edaListener)
                sendStatus("EDA tracker STARTED")
            } else {
                sendStatus("EDA NOT SUPPORTED!")
            }
        } catch (e: Exception) {
            sendStatus("EDA ERROR: ${e.message}")
            Log.e(TAG, "EDA tracker failed: ${e.message}", e)
        }

        // PPG tracker — independent try/catch (falls back to HR)
        try {
            if (supportedTypes.contains(HealthTrackerType.PPG_CONTINUOUS)) {
                // SDK v1.4.1 REQUIRES PpgType set for PPG_CONTINUOUS
                ppgTracker = service.getHealthTracker(
                    HealthTrackerType.PPG_CONTINUOUS,
                    setOf(PpgType.GREEN, PpgType.IR, PpgType.RED)
                )
                ppgTracker?.setEventListener(ppgListener)
                sendStatus("PPG tracker STARTED (25 Hz, Green+IR+Red)")
            } else {
                sendStatus("PPG NOT SUPPORTED! Falling back to HR...")
                if (supportedTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                    ppgTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                    ppgTracker?.setEventListener(heartRateFallbackListener)
                    sendStatus("HR fallback tracker STARTED")
                }
            }
        } catch (e: Exception) {
            sendStatus("PPG/HR ERROR: ${e.message} — trying HR fallback...")
            Log.e(TAG, "PPG tracker failed: ${e.message}", e)
            // If PPG threw an exception (policy issue), try HR fallback
            try {
                if (supportedTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                    ppgTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                    ppgTracker?.setEventListener(heartRateFallbackListener)
                    sendStatus("HR fallback tracker STARTED (after PPG error)")
                }
            } catch (e2: Exception) {
                sendStatus("HR FALLBACK ALSO FAILED: ${e2.message}")
                Log.e(TAG, "HR fallback also failed: ${e2.message}", e2)
            }
        }

        // Accelerometer tracker — independent try/catch
        try {
            if (supportedTypes.contains(HealthTrackerType.ACCELEROMETER_CONTINUOUS)) {
                accelerometerTracker = service.getHealthTracker(HealthTrackerType.ACCELEROMETER_CONTINUOUS)
                accelerometerTracker?.setEventListener(accelerometerListener)
                sendStatus("Accel tracker STARTED")
            } else {
                sendStatus("Accel NOT SUPPORTED!")
            }
        } catch (e: Exception) {
            sendStatus("ACCEL ERROR: ${e.message}")
            Log.e(TAG, "Accel tracker failed: ${e.message}", e)
        }

        // Skin Temperature tracker — independent try/catch (Cluster 33, 43, 48)
        try {
            if (supportedTypes.contains(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)) {
                skinTempTracker = service.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
                skinTempTracker?.setEventListener(skinTempListener)
                sendStatus("Skin Temp tracker STARTED (continuous)")
            } else {
                sendStatus("Skin Temp CONTINUOUS not supported on this device")
            }
        } catch (e: Exception) {
            sendStatus("SKIN_TEMP ERROR: ${e.message}")
            Log.e(TAG, "Skin temp tracker failed: ${e.message}", e)
        }

        sendStatus("=== TRACKER INIT (PPG+HR dual) ===")

        // HR dual-stream: runs ALONGSIDE PPG for hardware-derived IBI (Option C)
        try {
            if (supportedTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                hrTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                hrTracker?.setEventListener(hrDualStreamListener)
                sendStatus("HR dual-stream STARTED (hardware IBI alongside PPG)")
            }
        } catch (e: Exception) {
            sendStatus("HR dual-stream ERROR: ${e.message}")
            Log.e(TAG, "HR dual-stream failed: ${e.message}", e)
        }

        sendStatus("=== BUILD 36w INIT COMPLETE ===")
    }

    // ─── Sensor Listeners ──────────────────────────────────────────────────────

    private val edaListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dp in dataPoints) {
                val conductance = dp.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE) as? Float ?: 0f
                val ts = dp.timestamp
                sendStatus("EDA: ${String.format("%.4f", conductance)} µS")
                val intent = Intent("com.hugr.wearos.EDA_DATA").apply {
                    setPackage(packageName)
                    putExtra("conductance", conductance)
                    putExtra("timestamp", ts)
                }
                sendBroadcast(intent)
            }
        }
        override fun onError(error: HealthTracker.TrackerError) {
            sendStatus("EDA ERROR: ${error.name}")
        }
        override fun onFlushCompleted() {}
    }

    private val ppgListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            val batchSize = dataPoints.size
            val deliveryMode = if (isScreenOn) "REALTIME" else "FLUSH"
            for (dp in dataPoints) {
                val ppgGreen = dp.getValue(ValueKey.PpgSet.PPG_GREEN) as? Int ?: 0
                val ppgIR = dp.getValue(ValueKey.PpgSet.PPG_IR) as? Int ?: 0
                val ppgRed = dp.getValue(ValueKey.PpgSet.PPG_RED) as? Int ?: 0
                val ts = dp.timestamp

                // Broadcast PPG data to BleGattService
                val intent = Intent("com.hugr.wearos.PPG_DATA").apply {
                    setPackage(packageName)
                    putExtra("ppgGreen", ppgGreen)
                    putExtra("ppgIR", ppgIR)
                    putExtra("ppgRed", ppgRed)
                    putExtra("timestamp", ts)
                    putExtra("deliveryMode", deliveryMode)
                    putExtra("batchSize", batchSize)
                    putExtra("screenOn", isScreenOn)
                }
                sendBroadcast(intent)
            }
            // Log summary (not every point — 25 Hz would flood the log)
            if (dataPoints.isNotEmpty()) {
                val firstGreen = dataPoints[0].getValue(ValueKey.PpgSet.PPG_GREEN) as? Int ?: 0
                sendStatus("PPG: G=$firstGreen [$deliveryMode|b=$batchSize]")
            }
        }
        override fun onError(error: HealthTracker.TrackerError) {
            sendStatus("PPG ERROR: ${error.name}")
        }
        override fun onFlushCompleted() {}
    }

    // Fallback listener if PPG_CONTINUOUS is not available (uses HEART_RATE_CONTINUOUS)
    private val heartRateFallbackListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            if (dataPoints.isEmpty()) return
            
            // Samsung docs: "IBI values stored in the FIRST data point. Others contain NULL."
            val firstDp = dataPoints[0]
            val hr = firstDp.getValue(ValueKey.HeartRateSet.HEART_RATE) as? Int ?: 0
            val hrStatus = firstDp.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS) as? Int ?: -1
            val ts = firstDp.timestamp
            
            // Extract IBI from FIRST data point only
            var ibiMs = 0
            try {
                val ibiList = firstDp.getValue(ValueKey.HeartRateSet.IBI_LIST) as? List<*>
                val ibiStatusList = firstDp.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST) as? List<*>
                val rawIbiCount = ibiList?.size ?: 0
                sendStatus("HR: $hr [st=$hrStatus] IBI_RAW: $rawIbiCount items")
                
                if (ibiList != null && ibiList.isNotEmpty()) {
                    for (i in ibiList.indices) {
                        val ibiVal = (ibiList[i] as? Number)?.toInt() ?: 0
                        val ibiSt = if (i < (ibiStatusList?.size ?: 0)) (ibiStatusList!![i] as? Number)?.toInt() ?: -1 else -1
                        if (ibiVal > 0) {
                            ibiMs = ibiVal
                            sendStatus("  IBI[$i]: ${ibiVal}ms (status=$ibiSt)")
                            break // Take first valid IBI
                        }
                    }
                }
            } catch (e: Exception) {
                sendStatus("IBI extract error: ${e.message}")
            }
            
            // Send HR + IBI via PPG_DATA broadcast (phone will parse)
            // ppgGreen = HR, ppgIR = IBI, ppgRed = hrStatus
            val intent = Intent("com.hugr.wearos.PPG_DATA").apply {
                setPackage(packageName)
                putExtra("ppgGreen", hr)
                putExtra("ppgIR", ibiMs)
                putExtra("ppgRed", hrStatus)
                putExtra("timestamp", ts)
                putExtra("deliveryMode", "FALLBACK")
                putExtra("batchSize", dataPoints.size)
                putExtra("screenOn", isScreenOn)
            }
            sendBroadcast(intent)
        }
        override fun onError(error: HealthTracker.TrackerError) {
            sendStatus("HR FALLBACK ERROR: ${error.name}")
        }
        override fun onFlushCompleted() {}
    }

    // HR dual-stream listener — hardware-derived HR + IBI with accurate timestamps
    // Phone uses THIS for HRV calculation. PPG is stored for offline analysis.
    private val hrDualStreamListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            if (dataPoints.isEmpty()) return
            val firstDp = dataPoints[0]
            val hr = firstDp.getValue(ValueKey.HeartRateSet.HEART_RATE) as? Int ?: 0
            val hrStatus = firstDp.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS) as? Int ?: -1
            val ts = firstDp.timestamp
            var ibiMs = 0
            try {
                val ibiList = firstDp.getValue(ValueKey.HeartRateSet.IBI_LIST) as? List<*>
                if (ibiList != null && ibiList.isNotEmpty()) {
                    for (i in ibiList.indices) {
                        val ibiVal = (ibiList[i] as? Number)?.toInt() ?: 0
                        if (ibiVal > 0) { ibiMs = ibiVal; break }
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "IBI extract: ${e.message}") }
            if (hr > 0) sendStatus("HR(hw): $hr bpm IBI: ${ibiMs}ms")
            val intent = Intent("com.hugr.wearos.HR_DATA").apply {
                setPackage(packageName)
                putExtra("heartRate", hr)
                putExtra("ibiMs", ibiMs)
                putExtra("hrStatus", hrStatus)
                putExtra("timestamp", ts)
            }
            sendBroadcast(intent)
        }
        override fun onError(error: HealthTracker.TrackerError) { sendStatus("HR dual ERROR: ${error.name}") }
        override fun onFlushCompleted() {}
    }

    // Skin Temperature listener (Clusters 33, 43, 48 — circadian, disambiguation, sports)
    private val skinTempListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dp in dataPoints) {
                val objectTemp = dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE) as? Float ?: 0f
                val ambientTemp = dp.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE) as? Float ?: 0f
                val status = dp.getValue(ValueKey.SkinTemperatureSet.STATUS) as? Int ?: -1
                val ts = dp.timestamp

                sendStatus("Temp: skin=${String.format("%.2f", objectTemp)}°C amb=${String.format("%.1f", ambientTemp)}°C [st=$status]")

                val intent = Intent("com.hugr.wearos.TEMP_DATA").apply {
                    setPackage(packageName)
                    putExtra("skinTemp", objectTemp)
                    putExtra("ambientTemp", ambientTemp)
                    putExtra("status", status)
                    putExtra("timestamp", ts)
                }
                sendBroadcast(intent)
            }
        }
        override fun onError(error: HealthTracker.TrackerError) {
            sendStatus("SKIN_TEMP ERROR: ${error.name}")
            Log.e(TAG, "Skin temp error: ${error.name}")
        }
        override fun onFlushCompleted() {}
    }

    private val accelerometerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dp in dataPoints) {
                val x = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X) as? Int ?: 0
                val y = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y) as? Int ?: 0
                val z = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z) as? Int ?: 0
                val ts = dp.timestamp

                val intent = Intent("com.hugr.wearos.ACCEL_DATA").apply {
                    setPackage(packageName)
                    putExtra("x", x)
                    putExtra("y", y)
                    putExtra("z", z)
                    putExtra("timestamp", ts)
                }
                sendBroadcast(intent)
            }
        }
        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "Accel error: ${error.name}")
        }
        override fun onFlushCompleted() {}
    }

    // ─── Cleanup ───────────────────────────────────────────────────────────────

    private fun stopTrackingAndDisconnect() {
        try {
            edaTracker?.unsetEventListener()
            ppgTracker?.unsetEventListener()
            accelerometerTracker?.unsetEventListener()
            skinTempTracker?.unsetEventListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error unsetting listeners: ${e.message}")
        }
        edaTracker = null
        ppgTracker = null
        accelerometerTracker = null
        skinTempTracker = null
        try {
            healthTrackingService?.disconnectService()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
        healthTrackingService = null
        isConnected = false
    }

    private fun sendStatus(message: String) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra("status", message)
        }
        sendBroadcast(intent)
        Log.i(TAG, "STATUS: $message")
    }

    // ─── Flush Timer (forces Samsung SDK to deliver batched data every 30s) ───

    private fun startFlushTimer() {
        flushTimer = Timer("HUGR-Flush", true)
        flushTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (!isScreenOn) {
                    // Only flush when screen is off (when SDK batches data)
                    try {
                        edaTracker?.flush()
                        ppgTracker?.flush()
                        accelerometerTracker?.flush()
                        skinTempTracker?.flush()
                        Log.d(TAG, "Flush triggered (screen off)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Flush error: ${e.message}")
                    }
                }
            }
        }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS)
        Log.i(TAG, "Flush timer started (${FLUSH_INTERVAL_MS}ms interval)")
    }

    private fun stopFlushTimer() {
        flushTimer?.cancel()
        flushTimer = null
    }

    // ─── Screen State Receiver ────────────────────────────────────────────────

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
        Log.i(TAG, "Screen state receiver registered")
    }

    private fun unregisterScreenReceiver() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering screen receiver: ${e.message}")
        }
    }
}
