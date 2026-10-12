@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.samsung.android.service.health.tracking

import android.app.Activity
import android.content.Context
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType

/**
 * TEST-CLASSPATH ONLY — Watch79 FreshRunAndroidServiceFlowTest Samsung SDK substitute.
 *
 * This source is deliberately under the exact Samsung package names used by
 * HealthSensorService so JVM test classes load it before the malformed vendor AAR. It supplies
 * only the API exercised by that production service: connection, capability lookup, tracker
 * listener registration, explicitly requested callback delivery, and injected capability failure.
 * It is never compiled into the application APK/DEX and is not a Samsung hardware claim.
 */
object FreshRunAndroidServiceFlowTestOnlySamsungSdk {
    @Volatile var supportedTypes: Set<HealthTrackerType> = emptySet()
    @Volatile var capabilityFailure: Throwable? = null
    @Volatile var connectFailure: HealthTrackerException? = null

    private val trackers = linkedMapOf<HealthTrackerType, HealthTracker>()
    @Volatile private var latestService: HealthTrackingService? = null

    @Synchronized
    fun reset() {
        supportedTypes = emptySet()
        capabilityFailure = null
        connectFailure = null
        trackers.clear()
        latestService = null
    }

    @Synchronized
    internal fun tracker(type: HealthTrackerType): HealthTracker =
        trackers.getOrPut(type) { HealthTracker() }

    @Synchronized
    internal fun bind(service: HealthTrackingService) {
        latestService = service
    }

    @Synchronized
    fun emit(type: HealthTrackerType, points: List<DataPoint>) {
        check(latestService != null) { "TEST ONLY fake SDK has no connected HealthTrackingService" }
        tracker(type).emit(points)
    }
}

interface ConnectionListener {
    fun onConnectionSuccess()
    fun onConnectionEnded()
    fun onConnectionFailed(error: HealthTrackerException?)
}

class HealthTrackerException(message: String) : Exception(message) {
    fun getErrorCode(): Int = 0
    fun hasResolution(): Boolean = false
    fun resolve(activity: Activity) = Unit
}

class HealthTrackerCapability internal constructor() {
    val supportHealthTrackerTypes: List<HealthTrackerType>
        get() = FreshRunAndroidServiceFlowTestOnlySamsungSdk.capabilityFailure?.let { throw it }
            ?: FreshRunAndroidServiceFlowTestOnlySamsungSdk.supportedTypes.toList()
    val version: String get() = "TEST_ONLY_FAKE_SDK"
}

class HealthTrackingService(
    private val listener: ConnectionListener,
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    val trackingCapability: HealthTrackerCapability = HealthTrackerCapability()

    fun connectService() {
        FreshRunAndroidServiceFlowTestOnlySamsungSdk.bind(this)
        val failure = FreshRunAndroidServiceFlowTestOnlySamsungSdk.connectFailure
        if (failure != null) listener.onConnectionFailed(failure) else listener.onConnectionSuccess()
    }

    fun getHealthTracker(type: HealthTrackerType): HealthTracker =
        FreshRunAndroidServiceFlowTestOnlySamsungSdk.tracker(type)

    fun getHealthTracker(type: HealthTrackerType, @Suppress("UNUSED_PARAMETER") ppgTypes: Set<PpgType>): HealthTracker =
        FreshRunAndroidServiceFlowTestOnlySamsungSdk.tracker(type)

    fun disconnectService() = Unit
}

class HealthTracker {
    enum class TrackerError { TEST_ONLY_ERROR }

    interface TrackerEventListener {
        fun onDataReceived(dataPoints: MutableList<DataPoint>)
        fun onFlushCompleted()
        fun onError(error: TrackerError)
    }

    private var listener: TrackerEventListener? = null

    fun setEventListener(listener: TrackerEventListener) {
        this.listener = listener
    }

    fun unsetEventListener() {
        listener = null
    }

    fun flush(): Boolean {
        listener?.onFlushCompleted()
        return true
    }

    internal fun emit(points: List<DataPoint>) {
        requireNotNull(listener) { "TEST ONLY fake SDK listener was not registered" }
            .onDataReceived(points.toMutableList())
    }
}
