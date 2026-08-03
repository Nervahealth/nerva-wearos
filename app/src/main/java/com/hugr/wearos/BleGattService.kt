package com.hugr.wearos

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BleGattService : Service() {
    private val TAG = "HUGR-BleGatt"
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    companion object {
        val HUGR_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val EDA_CHARACTERISTIC_UUID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val IBI_CHARACTERISTIC_UUID: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val ACCEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        const val ACTION_HAPTIC_COMMAND = "com.hugr.wearos.HAPTIC_COMMAND"
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleGattService = this@BleGattService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BleGattService created")
        initializeBluetooth()
    }

    private fun initializeBluetooth() {
        try {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth adapter not available")
                return
            }

            if (!bluetoothAdapter!!.isEnabled) {
                Log.w(TAG, "Bluetooth is disabled")
            }

            advertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.e(TAG, "BLE advertiser not available")
                return
            }

            startGattServer()
            startAdvertising()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bluetooth: ${e.message}", e)
        }
    }

    private fun startGattServer() {
        try {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            gattServer = bluetoothManager.openGattServer(this, object : BluetoothGattServerCallback() {
                override fun onConnectionStateChange(device: android.bluetooth.BluetoothDevice?, status: Int, newState: Int) {
                    Log.d(TAG, "Connection state changed: $newState for ${device?.address}")
                }

                override fun onCharacteristicReadRequest(device: android.bluetooth.BluetoothDevice?, requestId: Int, offset: Int, characteristic: android.bluetooth.BluetoothGattCharacteristic?) {
                    Log.d(TAG, "Characteristic read request: ${characteristic?.uuid}")
                }

                override fun onCharacteristicWriteRequest(device: android.bluetooth.BluetoothDevice?, requestId: Int, characteristic: android.bluetooth.BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                    Log.d(TAG, "Characteristic write request: ${characteristic?.uuid}")
                }
            })
            Log.d(TAG, "GATT server started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GATT server: ${e.message}", e)
        }
    }

    private fun startAdvertising() {
        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(HUGR_SERVICE_UUID))
                .build()

            advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "BLE advertising started successfully")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "BLE advertising failed with error code: $errorCode")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising: ${e.message}", e)
        }
    }

    fun broadcastSensorData(dataType: String, data: ByteArray) {
        try {
            when (dataType) {
                "EDA" -> Log.d(TAG, "Broadcasting EDA data: ${data.size} bytes")
                "IBI" -> Log.d(TAG, "Broadcasting IBI data: ${data.size} bytes")
                "ACCEL" -> Log.d(TAG, "Broadcasting accelerometer data: ${data.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting sensor data: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BleGattService destroyed")
        try {
            advertiser?.stopAdvertising(object : AdvertiseCallback() {})
            gattServer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up: ${e.message}", e)
        }
    }
}
