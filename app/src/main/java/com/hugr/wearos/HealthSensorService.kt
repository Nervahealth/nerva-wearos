package com.hugr.wearos

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

class HealthSensorService : Service() {

    companion object {
        private const val TAG = "HUGR-HealthSensor"
        const val ACTION_START_TRACKING = "com.hugr.wearos.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.hugr.wearos.STOP_TRACKING"
    }

    private var healthTrackingService: HealthTrackingService? = null
    private var edaTracker: HealthTracker? = null
    private var heartRateTracker: HealthTracker? = null
    private var accelerometerTracker: HealthTracker? = null
    private var isConnected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> connectAndStartTracking()
            ACTION_STOP_TRACKING -> stopTrackingAndDisconnect()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTrackingAndDisconnect()
        super.onDestroy()
    }

    private fun connectAndStartTracking() {
        if (isConnected) {
            startAllTrackers()
            return
        }
        try {
            healthTrackingService = HealthTrackingService(connectionListener, applicationContext)
            healthTrackingService?.connectService()
            Log.i(TAG, "Connecting to Health Tracking Service...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
        }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Health Tracking Service connected")
            isConnected = true
            startAllTrackers()
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Health Tracking Service connection ended")
            isConnected = false
            healthTrackingService = null
        }

        override fun onConnectionFailed(error: HealthTrackerException?) {
            Log.e(TAG, "Connection failed: ${error?.message}")
            isConnected = false
            healthTrackingService = null
        }
    }

    private fun startAllTrackers() {
        val service = healthTrackingService ?: return

        val supportedTypes = try {
            service.trackingCapability.supportHealthTrackerTypes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get capabilities: ${e.message}")
            emptyList()
        }

        if (supportedTypes.contains(HealthTrackerType.EDA_CONTINUOUS)) {
            try {
                edaTracker = service.getHealthTracker(HealthTrackerType.EDA_CONTINUOUS)
                edaTracker?.setEventListener(edaListener)
                Log.i(TAG, "EDA tracker started (1Hz)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start EDA tracker: ${e.message}")
            }
        } else {
            Log.w(TAG, "EDA_CONTINUOUS not supported on this device")
        }

        if (supportedTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
            try {
                heartRateTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                heartRateTracker?.setEventListener(heartRateListener)
                Log.i(TAG, "Heart Rate + IBI tracker started (1Hz)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HR tracker: ${e.message}")
            }
        } else {
            Log.w(TAG, "HEART_RATE_CONTINUOUS not supported on this device")
        }

        if (supportedTypes.contains(HealthTrackerType.ACCELEROMETER_CONTINUOUS)) {
            try {
                accelerometerTracker = service.getHealthTracker(HealthTrackerType.ACCELEROMETER_CONTINUOUS)
                accelerometerTracker?.setEventListener(accelerometerListener)
                Log.i(TAG, "Accelerometer tracker started (25Hz)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start accelerometer tracker: ${e.message}")
            }
        } else {
            Log.w(TAG, "ACCELEROMETER_CONTINUOUS not supported on this device")
        }
    }

    private val edaListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dataPoint in dataPoints) {
                val conductance = dataPoint.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE) as? Float
                val timestamp = dataPoint.timestamp
                Log.d(TAG, "EDA: conductance=$conductance, ts=$timestamp")
                val intent = Intent("com.hugr.wearos.EDA_DATA").apply {
                    putExtra("conductance", conductance ?: 0f)
                    putExtra("timestamp", timestamp)
                }
                sendBroadcast(intent)
            }
        }

        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "EDA tracker error: ${error.name}")
        }

        override fun onFlushCompleted() {
            Log.d(TAG, "EDA flush completed")
        }
    }

    private val heartRateListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dataPoint in dataPoints) {
                val heartRate = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE) as? Int
                val ibiList = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST) as? IntArray
                val ibiStatusList = dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST) as? IntArray
                val timestamp = dataPoint.timestamp

                val validIbiValues = mutableListOf<Int>()
                if (ibiList != null && ibiStatusList != null) {
                    for (i in ibiList.indices) {
                        if (i < ibiStatusList.size && ibiStatusList[i] == 0 && ibiList[i] != 0) {
                            validIbiValues.add(ibiList[i])
                        }
                    }
                }

                Log.d(TAG, "HR: bpm=$heartRate, ibi=$validIbiValues, ts=$timestamp")
                val intent = Intent("com.hugr.wearos.IBI_DATA").apply {
                    putExtra("heartRate", heartRate ?: 0)
                    putExtra("ibiValues", validIbiValues.toIntArray())
                    putExtra("timestamp", timestamp)
                }
                sendBroadcast(intent)
            }
        }

        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "Heart rate tracker error: ${error.name}")
        }

        override fun onFlushCompleted() {
            Log.d(TAG, "Heart rate flush completed")
        }
    }

    private val accelerometerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dataPoint in dataPoints) {
                val x = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X) as? Int
                val y = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y) as? Int
                val z = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z) as? Int
                val timestamp = dataPoint.timestamp

                val intent = Intent("com.hugr.wearos.ACCEL_DATA").apply {
                    putExtra("x", x ?: 0)
                    putExtra("y", y ?: 0)
                    putExtra("z", z ?: 0)
                    putExtra("timestamp", timestamp)
                }
                sendBroadcast(intent)
            }
        }

        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "Accelerometer tracker error: ${error.name}")
        }

        override fun onFlushCompleted() {
            Log.d(TAG, "Accelerometer flush completed")
        }
    }

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
        Log.i(TAG, "All trackers stopped, service disconnected")
    }
}
