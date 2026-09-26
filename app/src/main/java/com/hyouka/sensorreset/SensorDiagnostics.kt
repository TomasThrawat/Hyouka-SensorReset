package com.hyouka.sensorreset

import android.hardware.SensorManager
import kotlin.math.max
import kotlin.math.sqrt

fun allSupportedMotionSensors(): List<MotionSensor> = MotionSensor.entries

class RunningVectorStats {
    private var means = DoubleArray(0)
    private var m2 = DoubleArray(0)

    var count: Long = 0
        private set

    fun add(values: FloatArray) {
        if (values.isEmpty()) return
        if (means.isEmpty()) {
            val size = minOf(values.size, 3)
            means = DoubleArray(size)
            m2 = DoubleArray(size)
        }

        val size = minOf(values.size, means.size)
        count++

        for (index in 0 until size) {
            val value = values[index].toDouble()
            val delta = value - means[index]
            means[index] += delta / count
            val delta2 = value - means[index]
            m2[index] += delta * delta2
        }
    }

    fun mean(): FloatArray {
        return FloatArray(means.size) { index -> means[index].toFloat() }
    }

    fun standardDeviation(): FloatArray {
        if (count == 0L) return FloatArray(means.size)
        return FloatArray(means.size) { index ->
            sqrt(max(0.0, m2[index] / count)).toFloat()
        }
    }
}

enum class SensorHealthStatus {
    PASS,
    WARNING,
    UNAVAILABLE
}

fun evaluateSensorHealth(
    available: Boolean,
    eventCount: Long,
    accuracy: Int,
    stalled: Boolean = false
): SensorHealthStatus {
    if (!available) return SensorHealthStatus.UNAVAILABLE

    if (
        eventCount == 0L ||
        stalled ||
        accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
    ) {
        return SensorHealthStatus.WARNING
    }

    return when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorHealthStatus.PASS
        else -> SensorHealthStatus.WARNING
    }
}

data class VectorComparison(
    val delta: FloatArray,
    val maxAbsoluteDelta: Float
)

fun compareVectors(before: FloatArray, after: FloatArray): VectorComparison {
    val size = minOf(before.size, after.size, 3)
    val delta = FloatArray(size)
    var maxAbsoluteDelta = 0f

    for (index in 0 until size) {
        val difference = after[index] - before[index]
        delta[index] = difference
        maxAbsoluteDelta = max(maxAbsoluteDelta, kotlin.math.abs(difference))
    }

    return VectorComparison(delta, maxAbsoluteDelta)
}

data class SensorCapabilities(
    val available: Boolean,
    val name: String = "Unavailable",
    val vendor: String = "Unavailable",
    val version: Int = 0,
    val powerMa: Float? = null,
    val maximumRange: Float? = null,
    val resolution: Float? = null,
    val minDelayUs: Int? = null,
    val maxDelayUs: Int? = null,
    val fifoMaxEventCount: Int? = null,
    val reportingMode: Int? = null,
    val wakeUpSensor: Boolean? = null,
    val sensorId: Int? = null
)

data class CalibrationSensorStats(
    val count: Long,
    val mean: List<Float>,
    val standardDeviation: List<Float>
)

data class CalibrationSessionResult(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
    val sensors: Map<MotionSensor, CalibrationSensorStats>
)

data class ResetHistoryEntry(
    val timestampEpochMs: Long,
    val availableSensorCount: Int,
    val registeredSensorCount: Int,
    val eventCount: Long,
    val sessionDurationMs: Long,
    val sensors: List<MotionSensor>
)

data class SensorUiState(
    val readings: Map<MotionSensor, SensorReading>,
    val capabilities: Map<MotionSensor, SensorCapabilities>,
    val running: Boolean,
    val resetCount: Int,
    val lastResetEpochMs: Long?,
    val sessionStartedEpochMs: Long?,
    val sessionDurationMs: Long,
    val calibrationActive: Boolean,
    val calibrationStartedEpochMs: Long?,
    val calibrationStats: Map<MotionSensor, CalibrationSensorStats>,
    val lastCalibrationResult: CalibrationSessionResult?,
    val comparisons: Map<MotionSensor, VectorComparison?>,
    val history: List<ResetHistoryEntry>,
    val logs: List<String>,
    val restartInProgress: Boolean = false,
    val systemResetStatus: String = "Not attempted"
)
