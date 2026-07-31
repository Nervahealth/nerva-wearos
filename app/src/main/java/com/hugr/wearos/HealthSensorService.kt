package com.hugr.wearos

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.samsung.android.sdk.healthdata.HealthConstants
import com.samsung.android.sdk.healthdata.HealthDataService
import com.samsung.android.sdk.healthdata.HealthDataStore
import com.samsung.android.sdk.healthdata.HealthDataStore.ConnectionListener
import com.samsung.android.sdk.healthdata.HealthResultHolder
import com.samsung.android.sdk.healthdata.HealthTrackerEventListener
import java.util.concurrent.CopyOnWriteArrayList

class HealthSensorService : Service() {
    private val TAG = "HUGR-HealthSensor"
    private val binder = LocalBinder()
    private lateinit var mHealthDataStore: HealthDataStore
    private val listeners = CopyOnWriteArrayList<SensorDataListener>()
    
    // Trackers
    private var edaTracker: HealthTrackerEventListener? = null
    private var ibiTracker: HealthTrackerEventListener? = null
    private var accelTracker: HealthTrackerEventListener? = null

    interface SensorDataListener {
        fun onEdaData(value: Float, timestamp: Long)
        fun onIbiData(rr: Int, timestamp: Long)
        fun onAccelData(x: Float, y: Float, z: Float, timestamp: Long)
    }

    inner class LocalBinder : Binder() {
        fun getService(): HealthSensorService = this@HealthSensorService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HealthSensorService created")
        initializeHealthDataStore()
    }

    private fun initializeHealthDataStore() {
        try {
            mHealthDataStore = HealthDataStore(this, object : ConnectionListener {
                override fun onConnected() {
                    Log.d(TAG, "HealthDataStore connected")
                    startListeningToSensors()
                }

                override fun onConnectionFailed(error: HealthDataStore.ConnectionError?) {
                    Log.e(TAG, "HealthDataStore connection failed: $error")
                }

                override fun onDisconnected() {
                    Log.d(TAG, "HealthDataStore disconnected")
                }
            })
            mHealthDataStore.connectService()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing HealthDataStore: ${e.message}", e)
        }
    }

    private fun startListeningToSensors() {
        Log.d(TAG, "Starting sensor listeners")
        
        // EDA_CONTINUOUS (1Hz)
        startEdaListener()
        
        // IBI (beat-to-beat intervals)
        startIbiListener()
        
        // Accelerometer (25Hz)
        startAccelListener()
    }

    private fun startEdaListener() {
        try {
            Log.d(TAG, "Starting EDA listener")
            edaTracker = HealthTrackerEventListener { trackerId, result ->
                if (result.hasData()) {
                    val edaList = result.getList(HealthConstants.ElectrodermalActivity.ELECTRODERMAL_ACTIVITY)
                    for (eda in edaList) {
                        val value = eda.getFloat(HealthConstants.ElectrodermalActivity.ELECTRODERMAL_ACTIVITY)
                        val timestamp = eda.getLong(HealthConstants.ElectrodermalActivity.START_TIME)
                        Log.d(TAG, "EDA: $value at $timestamp")
                        notifyEdaData(value, timestamp)
                    }
                }
            }
            
            val trackerResult = mHealthDataStore.getConnectedTrackers(HealthConstants.ElectrodermalActivity.TRACKER_NAME)
            if (trackerResult.hasData()) {
                val trackerList = trackerResult.getList(HealthConstants.ElectrodermalActivity.TRACKER_NAME)
                if (trackerList.isNotEmpty()) {
                    val trackerId = trackerList[0].getString(HealthConstants.TRACKER_ID)
                    mHealthDataStore.addTrackerEventListener(trackerId, edaTracker)
                    Log.d(TAG, "EDA listener attached to tracker: $trackerId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting EDA listener: ${e.message}", e)
        }
    }

    private fun startIbiListener() {
        try {
            Log.d(TAG, "Starting IBI listener")
            ibiTracker = HealthTrackerEventListener { trackerId, result ->
                if (result.hasData()) {
                    val ibiList = result.getList(HealthConstants.HeartRate.HEART_RATE)
                    for (ibi in ibiList) {
                        val rr = ibi.getInt(HealthConstants.HeartRate.HEART_RATE)
                        val timestamp = ibi.getLong(HealthConstants.HeartRate.START_TIME)
                        Log.d(TAG, "IBI: $rr ms at $timestamp")
                        notifyIbiData(rr, timestamp)
                    }
                }
            }
            
            val trackerResult = mHealthDataStore.getConnectedTrackers(HealthConstants.HeartRate.TRACKER_NAME)
            if (trackerResult.hasData()) {
                val trackerList = trackerResult.getList(HealthConstants.HeartRate.TRACKER_NAME)
                if (trackerList.isNotEmpty()) {
                    val trackerId = trackerList[0].getString(HealthConstants.TRACKER_ID)
                    mHealthDataStore.addTrackerEventListener(trackerId, ibiTracker)
                    Log.d(TAG, "IBI listener attached to tracker: $trackerId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting IBI listener: ${e.message}", e)
        }
    }

    private fun startAccelListener() {
        try {
            Log.d(TAG, "Starting accelerometer listener")
            accelTracker = HealthTrackerEventListener { trackerId, result ->
                if (result.hasData()) {
                    val accelList = result.getList(HealthConstants.Accelerometer.ACCELEROMETER)
                    for (accel in accelList) {
                        val x = accel.getFloat(HealthConstants.Accelerometer.X)
                        val y = accel.getFloat(HealthConstants.Accelerometer.Y)
                        val z = accel.getFloat(HealthConstants.Accelerometer.Z)
                        val timestamp = accel.getLong(HealthConstants.Accelerometer.START_TIME)
                        Log.d(TAG, "Accel: [$x, $y, $z] at $timestamp")
                        notifyAccelData(x, y, z, timestamp)
                    }
                }
            }
            
            val trackerResult = mHealthDataStore.getConnectedTrackers(HealthConstants.Accelerometer.TRACKER_NAME)
            if (trackerResult.hasData()) {
                val trackerList = trackerResult.getList(HealthConstants.Accelerometer.TRACKER_NAME)
                if (trackerList.isNotEmpty()) {
                    val trackerId = trackerList[0].getString(HealthConstants.TRACKER_ID)
                    mHealthDataStore.addTrackerEventListener(trackerId, accelTracker)
                    Log.d(TAG, "Accelerometer listener attached to tracker: $trackerId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting accelerometer listener: ${e.message}", e)
        }
    }

    private fun notifyEdaData(value: Float, timestamp: Long) {
        for (listener in listeners) {
            listener.onEdaData(value, timestamp)
        }
    }

    private fun notifyIbiData(rr: Int, timestamp: Long) {
        for (listener in listeners) {
            listener.onIbiData(rr, timestamp)
        }
    }

    private fun notifyAccelData(x: Float, y: Float, z: Float, timestamp: Long) {
        for (listener in listeners) {
            listener.onAccelData(x, y, z, timestamp)
        }
    }

    fun addSensorDataListener(listener: SensorDataListener) {
        listeners.add(listener)
        Log.d(TAG, "Sensor listener added")
    }

    fun removeSensorDataListener(listener: SensorDataListener) {
        listeners.remove(listener)
        Log.d(TAG, "Sensor listener removed")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HealthSensorService destroyed")
        try {
            edaTracker?.let { mHealthDataStore.removeTrackerEventListener(it) }
            ibiTracker?.let { mHealthDataStore.removeTrackerEventListener(it) }
            accelTracker?.let { mHealthDataStore.removeTrackerEventListener(it) }
            mHealthDataStore.disconnectService()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up: ${e.message}", e)
        }
    }
}
