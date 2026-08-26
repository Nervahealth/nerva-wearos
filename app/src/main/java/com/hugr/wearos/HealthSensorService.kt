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
 * HUGR Labs — HealthSensorService (Build 43w cardiac evidence candidate)
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
        const val ACTION_DEVICE_HEALTH_UPDATE = "com.hugr.wearos.DEVICE_HEALTH_UPDATE"
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
    private var activeSensorMask = 0
    private var sdkStatus = 0
    private var flushCount = 0L
    private var cardiacCallbackId = 0

    // Screen state receiver — tracks when screen goes on/off for metadata tagging
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.d(TAG, "Screen ON — switching to real-time delivery")
                    sendDeviceHealthMetadata()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Log.d(TAG, "Screen OFF — flush timer active for batched delivery")
                    sendDeviceHealthMetadata()
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
            sdkStatus = 1
            sendDeviceHealthMetadata()
            startAllTrackers()
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Health Tracking Service connection ended")
            sendStatus("SDK connection ENDED")
            isConnected = false
            sdkStatus = 2
            activeSensorMask = 0
            sendDeviceHealthMetadata()
            healthTrackingService = null
        }

        override fun onConnectionFailed(error: HealthTrackerException?) {
            Log.e(TAG, "Connection failed: ${error?.message}")
            sendStatus("SDK FAILED: ${error?.message}")
            isConnected = false
            sdkStatus = 3
            activeSensorMask = 0
            sendDeviceHealthMetadata()
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
                activeSensorMask = activeSensorMask or 0x01
                sendStatus("EDA tracker STARTED")
            } else {
                sendStatus("EDA NOT SUPPORTED!")
            }
        } catch (e: Exception) {
            sendStatus("EDA ERROR: ${e.message}")
            Log.e(TAG, "EDA tracker failed: ${e.message}", e)
        }

        // Raw PPG tracker — independent evidence stream. It never substitutes for HR.
        try {
            if (supportedTypes.contains(HealthTrackerType.PPG_CONTINUOUS)) {
                // SDK v1.4.1 REQUIRES PpgType set for PPG_CONTINUOUS
                ppgTracker = service.getHealthTracker(
                    HealthTrackerType.PPG_CONTINUOUS,
                    setOf(PpgType.GREEN, PpgType.IR, PpgType.RED)
                )
                ppgTracker?.setEventListener(ppgListener)
                activeSensorMask = activeSensorMask or 0x04
                sendStatus("PPG tracker STARTED (25 Hz, Green+IR+Red)")
            } else {
                sendStatus("PPG NOT SUPPORTED — raw PPG stream unavailable")
            }
        } catch (e: Exception) {
            sendStatus("PPG ERROR: ${e.message}")
            Log.e(TAG, "PPG tracker failed: ${e.message}", e)
        }

        // Accelerometer tracker — independent try/catch
        try {
            if (supportedTypes.contains(HealthTrackerType.ACCELEROMETER_CONTINUOUS)) {
                accelerometerTracker = service.getHealthTracker(HealthTrackerType.ACCELEROMETER_CONTINUOUS)
                accelerometerTracker?.setEventListener(accelerometerListener)
                activeSensorMask = activeSensorMask or 0x08
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
                activeSensorMask = activeSensorMask or 0x10
                sendStatus("Skin Temp tracker STARTED (continuous)")
            } else {
                sendStatus("Skin Temp CONTINUOUS not supported on this device")
            }
        } catch (e: Exception) {
            sendStatus("SKIN_TEMP ERROR: ${e.message}")
            Log.e(TAG, "Skin temp tracker failed: ${e.message}", e)
        }

        sendStatus("=== TRACKER INIT (typed cardiac evidence) ===")

        // Exactly one authoritative hardware HR tracker. Raw PPG remains separate.
        try {
            if (supportedTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                hrTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                hrTracker?.setEventListener(cardiacEvidenceListener)
                activeSensorMask = activeSensorMask or 0x02
                sendStatus("HR evidence tracker STARTED")
            } else {
                sendStatus("HEART_RATE_CONTINUOUS NOT SUPPORTED")
            }
        } catch (e: Exception) {
            sendStatus("HR evidence tracker ERROR: ${e.message}")
            Log.e(TAG, "HR evidence tracker failed: ${e.message}", e)
        }

        sendDeviceHealthMetadata()
        sendStatus("=== TRACKER INIT COMPLETE ===")
    }

    // ─── Sensor Listeners ──────────────────────────────────────────────────────

    private val edaListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            val batchSize = dataPoints.size
            val deliveryMode = if (isScreenOn) "REALTIME" else "FLUSH"
            for (dp in dataPoints) {
                val conductance = dp.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE) as? Float ?: 0f
                val ts = dp.timestamp
                sendStatus("EDA: ${String.format("%.4f", conductance)} µS")
                val intent = Intent("com.hugr.wearos.EDA_DATA").apply {
                    setPackage(packageName)
                    putExtra("conductance", conductance)
                    putExtra("timestamp", ts)
                    putExtra("deliveryMode", deliveryMode)
                    putExtra("batchSize", batchSize)
                    putExtra("screenOn", isScreenOn)
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

    private val cardiacEvidenceListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            if (dataPoints.isEmpty()) return
            cardiacCallbackId = (cardiacCallbackId + 1) and 0x7FFF_FFFF
            val points = dataPoints.map { point ->
                val ibiValues = (point.getValue(ValueKey.HeartRateSet.IBI_LIST) as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toInt() }
                    ?: emptyList()
                val ibiStatuses = (point.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST) as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toInt() }
                    ?: emptyList()
                SamsungCardiacPoint(
                    sourceTimestamp = point.timestamp,
                    heartRate = (point.getValue(ValueKey.HeartRateSet.HEART_RATE) as? Number)?.toInt() ?: 0,
                    heartRateStatus = (point.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS) as? Number)?.toInt() ?: -1,
                    ibiValuesMs = ibiValues,
                    ibiStatuses = ibiStatuses
                )
            }
            val records = flattenCardiacBatch(cardiacCallbackId, points)
            val deliveryMode = if (isScreenOn) "REALTIME" else "FLUSH"
            records.forEach { record ->
                val intent = Intent("com.hugr.wearos.CARDIAC_EVIDENCE_DATA").apply {
                    setPackage(packageName)
                    putExtra("kind", record.kind.wireCode)
                    putExtra("value", record.value)
                    putExtra("status", record.status)
                    putExtra("timestamp", record.sourceTimestamp)
                    putExtra("callbackId", record.callbackId)
                    putExtra("pointIndex", record.pointIndex)
                    putExtra("pointCount", record.pointCount)
                    putExtra("listIndex", record.listIndex)
                    putExtra("listCount", record.listCount)
                    putExtra("contractAnomaly", record.contractAnomaly)
                    putExtra("deliveryMode", deliveryMode)
                    putExtra("batchSize", dataPoints.size)
                    putExtra("screenOn", isScreenOn)
                }
                sendBroadcast(intent)
            }
            sendStatus("Cardiac callback ${cardiacCallbackId}: ${dataPoints.size} points → ${records.size} evidence records")
        }
        override fun onError(error: HealthTracker.TrackerError) {
            sendStatus("HR evidence ERROR: ${error.name}")
        }
        override fun onFlushCompleted() {}
    }

    // Skin Temperature listener (Clusters 33, 43, 48 — circadian, disambiguation, sports)
    private val skinTempListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            val batchSize = dataPoints.size
            val deliveryMode = if (isScreenOn) "REALTIME" else "FLUSH"
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
                    putExtra("deliveryMode", deliveryMode)
                    putExtra("batchSize", batchSize)
                    putExtra("screenOn", isScreenOn)
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
            val batchSize = dataPoints.size
            val deliveryMode = if (isScreenOn) "REALTIME" else "FLUSH"
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
                    putExtra("deliveryMode", deliveryMode)
                    putExtra("batchSize", batchSize)
                    putExtra("screenOn", isScreenOn)
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
            hrTracker?.unsetEventListener()
            accelerometerTracker?.unsetEventListener()
            skinTempTracker?.unsetEventListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error unsetting listeners: ${e.message}")
        }
        edaTracker = null
        ppgTracker = null
        hrTracker = null
        accelerometerTracker = null
        skinTempTracker = null
        try {
            healthTrackingService?.disconnectService()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
        healthTrackingService = null
        isConnected = false
        sdkStatus = 2
        activeSensorMask = 0
        sendDeviceHealthMetadata()
    }

    private fun sendStatus(message: String) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra("status", message)
        }
        sendBroadcast(intent)
        Log.i(TAG, "STATUS: $message")
    }

    private fun sendDeviceHealthMetadata() {
        val intent = Intent(ACTION_DEVICE_HEALTH_UPDATE).apply {
            setPackage(packageName)
            putExtra("sdkConnected", isConnected)
            putExtra("sdkStatus", sdkStatus)
            putExtra("activeSensorMask", activeSensorMask)
            putExtra("flushCount", flushCount)
            putExtra("screenOn", isScreenOn)
        }
        sendBroadcast(intent)
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
                        hrTracker?.flush()
                        accelerometerTracker?.flush()
                        skinTempTracker?.flush()
                        flushCount += 1
                        sendDeviceHealthMetadata()
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
