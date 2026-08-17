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

/**
 * HUGR Labs — HealthSensorService (Build 28d)
 *
 * Foreground service with WAKE_LOCK + BODY_SENSORS_BACKGROUND + foregroundServiceType="health"
 * Based on Samsung's official tutorial (April 2026):
 * https://developer.samsung.com/galaxy-watch/blog/en/2026/04/23/continuous-heart-rate-tracking-on-galaxy-watch-even-with-the-screen-off
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
    private var heartRateTracker: HealthTracker? = null
    private var accelerometerTracker: HealthTracker? = null
    private var isConnected = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startForegroundWithNotification()
                acquireWakeLock()
                connectAndStartTracking()
            }
            ACTION_STOP_TRACKING -> {
                stopTrackingAndDisconnect()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                startForegroundWithNotification()
                acquireWakeLock()
                connectAndStartTracking()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTrackingAndDisconnect()
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
            .setContentText("Monitoring EDA, HR, movement")
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

        try {
            val supportedTypes = service.trackingCapability.supportHealthTrackerTypes
            sendStatus("Supported: ${supportedTypes.size} types")

            if (supportedTypes.contains(HealthTrackerType.EDA_CONTINUOUS)) {
                edaTracker = service.getHealthTracker(HealthTrackerType.EDA_CONTINUOUS)
                edaTracker?.setEventListener(edaListener)
                sendStatus("EDA tracker STARTED")
            } else {
                sendStatus("EDA NOT SUPPORTED!")
            }

            if (supportedTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                heartRateTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                heartRateTracker?.setEventListener(heartRateListener)
                sendStatus("HR+IBI tracker STARTED")
            } else {
                sendStatus("HR NOT SUPPORTED!")
            }

            if (supportedTypes.contains(HealthTrackerType.ACCELEROMETER_CONTINUOUS)) {
                accelerometerTracker = service.getHealthTracker(HealthTrackerType.ACCELEROMETER_CONTINUOUS)
                accelerometerTracker?.setEventListener(accelerometerListener)
                sendStatus("Accel tracker STARTED")
            } else {
                sendStatus("Accel NOT SUPPORTED!")
            }

            sendStatus("=== ALL TRACKERS ACTIVE ===")
        } catch (e: Exception) {
            sendStatus("TRACKER ERROR: ${e.message}")
            Log.e(TAG, "Error starting trackers: ${e.message}", e)
        }
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

    private val heartRateListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dp in dataPoints) {
                val hr = dp.getValue(ValueKey.HeartRateSet.HEART_RATE) as? Int ?: 0
                val hrStatus = dp.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS) as? Int ?: -1
                val ibiList = dp.getValue(ValueKey.HeartRateSet.IBI_LIST) as? IntArray ?: intArrayOf()
                val ibiStatusList = dp.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST) as? IntArray ?: intArrayOf()
                val ts = dp.timestamp

                // DEBUG: Log RAW IBI data before filtering (to diagnose IBI=0 issue)
                Log.d(TAG, "HR RAW: hr=$hr status=$hrStatus ibiList=${ibiList.contentToString()} ibiStatusList=${ibiStatusList.contentToString()}")

                val validIbi = mutableListOf<Int>()
                for (i in ibiList.indices) {
                    if (i < ibiStatusList.size && ibiStatusList[i] == 0 && ibiList[i] > 0) {
                        validIbi.add(ibiList[i])
                    }
                }

                // Also show RAW ibi on screen for debugging
                val rawIbiStr = if (ibiList.isEmpty()) "EMPTY" else ibiList.contentToString()
                val rawStatusStr = if (ibiStatusList.isEmpty()) "EMPTY" else ibiStatusList.contentToString()
                val statusStr = if (hrStatus == 1) "OK" else "s=$hrStatus"
                sendStatus("HR: $hr [$statusStr] IBI:${validIbi.joinToString(",")} RAW:$rawIbiStr ST:$rawStatusStr")

                val intent = Intent("com.hugr.wearos.IBI_DATA").apply {
                    setPackage(packageName)
                    putExtra("heartRate", hr)
                    putExtra("ibiValues", validIbi.toIntArray())
                    putExtra("timestamp", ts)
                }
                sendBroadcast(intent)
            }
        }
        override fun onError(error: HealthTracker.TrackerError) {
            sendStatus("HR ERROR: ${error.name}")
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
            heartRateTracker?.unsetEventListener()
            accelerometerTracker?.unsetEventListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error unsetting listeners: ${e.message}")
        }
        edaTracker = null
        heartRateTracker = null
        accelerometerTracker = null
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
}
