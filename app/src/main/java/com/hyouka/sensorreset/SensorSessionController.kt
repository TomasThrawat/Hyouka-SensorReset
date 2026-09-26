package com.hyouka.sensorreset

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MotionSensor(val sensorType: Int, val title: String, val unit: String) {
    GYROSCOPE(Sensor.TYPE_GYROSCOPE, "Gyroscope", "rad/s"),
    ACCELEROMETER(Sensor.TYPE_ACCELEROMETER, "Accelerometer", "m/s²"),
    MAGNETOMETER(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", "µT"),
    ROTATION_VECTOR(Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector", "normalized")
}

data class SensorReading(
    val available: Boolean,
    val values: List<Float> = emptyList(),
    val accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val samplingPeriodHintUs: Int = SensorManager.SENSOR_DELAY_GAME
)

data class SensorUiState(
    val readings: Map<MotionSensor, SensorReading>,
    val running: Boolean,
    val resetCount: Int,
    val lastResetEpochMs: Long?
)

class SensorSessionController(
    private val sensorManager: SensorManager
) {
    private var registeredSensors = emptyMap<MotionSensor, Sensor>()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val motionSensor = MotionSensor.entries.firstOrNull { it.sensorType == event.sensor.type } ?: return
            val previous = uiState.readings.getValue(motionSensor)
            val values = event.values.take(3).map { it }

            uiState = uiState.copy(
                readings = uiState.readings + (
                    motionSensor to previous.copy(
                        available = true,
                        values = values,
                        accuracy = event.accuracy
                    )
                )
            )
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            val motionSensor = MotionSensor.entries.firstOrNull { it.sensorType == sensor.type } ?: return
            val previous = uiState.readings.getValue(motionSensor)
            uiState = uiState.copy(
                readings = uiState.readings + (
                    motionSensor to previous.copy(accuracy = accuracy)
                )
            )
        }
    }

    var uiState by mutableStateOf(initialState())
        private set

    fun start() {
        if (uiState.running) return

        val available = linkedMapOf<MotionSensor, Sensor>()
        val readings = uiState.readings.toMutableMap()

        MotionSensor.entries.forEach { motionSensor ->
            val sensor = sensorManager.getDefaultSensor(motionSensor.sensorType)
            if (sensor == null) {
                readings[motionSensor] = readings.getValue(motionSensor).copy(
                    available = false,
                    values = emptyList()
                )
                return@forEach
            }

            val registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )

            if (registered) {
                available[motionSensor] = sensor
                readings[motionSensor] = readings.getValue(motionSensor).copy(
                    available = true,
                    values = emptyList()
                )
            } else {
                readings[motionSensor] = readings.getValue(motionSensor).copy(
                    available = false,
                    values = emptyList()
                )
            }
        }

        registeredSensors = available
        uiState = uiState.copy(
            readings = readings,
            running = true
        )
    }

    fun stop() {
        if (!uiState.running) return
        sensorManager.unregisterListener(listener)
        registeredSensors = emptyMap()
        uiState = uiState.copy(running = false)
    }

    fun reset() {
        sensorManager.unregisterListener(listener)
        registeredSensors = emptyMap()

        val resetReadings = uiState.readings.mapValues { (_, reading) ->
            reading.copy(values = emptyList())
        }

        uiState = uiState.copy(
            readings = resetReadings,
            resetCount = uiState.resetCount + 1,
            lastResetEpochMs = System.currentTimeMillis(),
            running = false
        )

        start()
    }

    private fun initialState(): SensorUiState {
        val readings = MotionSensor.entries.associateWith {
            SensorReading(available = false)
        }
        return SensorUiState(
            readings = readings,
            running = false,
            resetCount = 0,
            lastResetEpochMs = null
        )
    }
}
