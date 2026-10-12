@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.samsung.android.service.health.tracking.data

/**
 * TEST-CLASSPATH ONLY companion to FreshRunAndroidServiceFlowTestOnlySamsungSdk.
 *
 * Minimal Samsung data API used by production HealthSensorService listener code. These classes
 * exist only in the JVM test output, never in the application APK/DEX. Values are supplied solely
 * by a named test callback; they neither emulate a Samsung device nor prove a hardware reading.
 */
enum class HealthTrackerType {
    PPG_CONTINUOUS,
    HEART_RATE_CONTINUOUS,
    ACCELEROMETER_CONTINUOUS,
    SKIN_TEMPERATURE_CONTINUOUS,
    EDA_CONTINUOUS,
}

enum class PpgType { GREEN, RED, IR }

class ValueKey<T> {
    class EdaSet {
        companion object {
            @JvmField val SKIN_CONDUCTANCE = ValueKey<Float>()
        }
    }

    class PpgSet {
        companion object {
            @JvmField val PPG_GREEN = ValueKey<Int>()
            @JvmField val PPG_IR = ValueKey<Int>()
            @JvmField val PPG_RED = ValueKey<Int>()
        }
    }

    class HeartRateSet {
        companion object {
            @JvmField val IBI_LIST = ValueKey<List<*>>()
            @JvmField val IBI_STATUS_LIST = ValueKey<List<*>>()
            @JvmField val HEART_RATE = ValueKey<Number>()
            @JvmField val HEART_RATE_STATUS = ValueKey<Number>()
        }
    }

    class SkinTemperatureSet {
        companion object {
            @JvmField val OBJECT_TEMPERATURE = ValueKey<Float>()
            @JvmField val AMBIENT_TEMPERATURE = ValueKey<Float>()
            @JvmField val STATUS = ValueKey<Int>()
        }
    }

    class AccelerometerSet {
        companion object {
            @JvmField val ACCELEROMETER_X = ValueKey<Int>()
            @JvmField val ACCELEROMETER_Y = ValueKey<Int>()
            @JvmField val ACCELEROMETER_Z = ValueKey<Int>()
        }
    }
}

class DataPoint(
    val timestamp: Long,
    private val values: Map<ValueKey<*>, Any?> = emptyMap(),
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> getValue(key: ValueKey<T>): T? = values[key] as T?
}
