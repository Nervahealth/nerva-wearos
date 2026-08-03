package com.hugr.wearos

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.util.Log

class HapticService : Service() {

    companion object {
        private const val TAG = "HUGR-Haptic"
    }

    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        registerHapticReceiver()
        Log.i(TAG, "Haptic service started")
    }

    override fun onDestroy() {
        unregisterReceiver(hapticReceiver)
        vibrator?.cancel()
        super.onDestroy()
    }

    private fun registerHapticReceiver() {
        val filter = IntentFilter(BleGattService.ACTION_HAPTIC_COMMAND)
        registerReceiver(hapticReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    private val hapticReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val patternBytes = intent?.getByteArrayExtra("pattern") ?: return
            executeHapticPattern(patternBytes)
        }
    }

    private fun executeHapticPattern(data: ByteArray) {
        if (data.isEmpty()) return
        val patternId = data[0]
        val intensity = if (data.size > 1) (data[1].toInt() and 0xFF) else 128

        when (patternId.toInt()) {
            1 -> playGentleTap(intensity)
            2 -> playBreathingGuide(intensity)
            3 -> playGroundingPulse(intensity)
            4 -> playAlert(intensity)
            else -> playGentleTap(intensity)
        }
    }

    private fun playGentleTap(intensity: Int) {
        val effect = VibrationEffect.createOneShot(100, intensity.coerceIn(1, 255))
        vibrator?.vibrate(effect)
    }

    private fun playBreathingGuide(intensity: Int) {
        val amp = intensity.coerceIn(1, 255)
        val lowAmp = (amp * 0.3).toInt().coerceIn(1, 255)
        val timings = longArrayOf(0, 500, 100, 500, 100, 500, 100, 500, 1000, 600, 300, 600, 300, 600, 300, 600)
        val amplitudes = intArrayOf(0, lowAmp, 0, (amp * 0.5).toInt(), 0, (amp * 0.75).toInt(), 0, amp, 0, (amp * 0.8).toInt(), 0, (amp * 0.6).toInt(), 0, (amp * 0.4).toInt(), 0, (amp * 0.2).toInt())
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator?.vibrate(effect)
    }

    private fun playGroundingPulse(intensity: Int) {
        val amp = intensity.coerceIn(1, 255)
        val timings = longArrayOf(0, 300, 200, 300, 1200, 300, 200, 300, 1200)
        val amplitudes = intArrayOf(0, amp, 0, (amp * 0.6).toInt().coerceIn(1, 255), 0, amp, 0, (amp * 0.6).toInt().coerceIn(1, 255), 0)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator?.vibrate(effect)
    }

    private fun playAlert(intensity: Int) {
        val amp = intensity.coerceIn(1, 255)
        val timings = longArrayOf(0, 100, 100, 150, 100, 200, 100, 250)
        val amplitudes = intArrayOf(0, (amp * 0.4).toInt().coerceIn(1, 255), 0, (amp * 0.6).toInt().coerceIn(1, 255), 0, (amp * 0.8).toInt().coerceIn(1, 255), 0, amp)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator?.vibrate(effect)
    }
}
