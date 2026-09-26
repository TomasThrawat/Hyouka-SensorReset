package com.hyouka.sensorreset

import android.hardware.SensorManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSessionControllerTest {
    @Test
    fun sensorUnitsAreStable() {
        assertEquals("rad/s", MotionSensor.GYROSCOPE.unit)
        assertEquals("m/s²", MotionSensor.ACCELEROMETER.unit)
        assertEquals("µT", MotionSensor.MAGNETOMETER.unit)
    }

    @Test
    fun sensorTypesAreDistinct() {
        val types = MotionSensor.entries.map { it.sensorType }
        assertEquals(types.size, types.toSet().size)
    }

    @Test
    fun allSupportedSensorsAreIncludedInTheRestartSet() {
        val restartSet = allSupportedMotionSensors()

        assertEquals(MotionSensor.entries.toSet(), restartSet.toSet())
        assertEquals(MotionSensor.entries.size, restartSet.size)
    }

    @Test
    fun runningVectorStatsComputeMeanAndStandardDeviation() {
        val stats = RunningVectorStats()
        stats.add(floatArrayOf(1f, 2f, 3f))
        stats.add(floatArrayOf(3f, 4f, 5f))

        assertEquals(2L, stats.count)
        assertArrayEquals(floatArrayOf(2f, 3f, 4f), stats.mean(), 0.0001f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), stats.standardDeviation(), 0.0001f)
    }

    @Test
    fun healthEvaluatorDistinguishesUnavailableStalledAndHealthySensors() {
        assertEquals(
            SensorHealthStatus.UNAVAILABLE,
            evaluateSensorHealth(
                available = false,
                eventCount = 0,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
            )
        )
        assertEquals(
            SensorHealthStatus.WARNING,
            evaluateSensorHealth(
                available = true,
                eventCount = 0,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            )
        )
        assertEquals(
            SensorHealthStatus.WARNING,
            evaluateSensorHealth(
                available = true,
                eventCount = 3,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
            )
        )
        assertEquals(
            SensorHealthStatus.WARNING,
            evaluateSensorHealth(
                available = true,
                eventCount = 3,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW
            )
        )
        assertEquals(
            SensorHealthStatus.PASS,
            evaluateSensorHealth(
                available = true,
                eventCount = 3,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            )
        )
        assertEquals(
            SensorHealthStatus.PASS,
            evaluateSensorHealth(
                available = true,
                eventCount = 3,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            )
        )
    }

    @Test
    fun systemWideResetCommandContractUsesSensorServiceRestrictAndEnable() {
        val commands = systemSensorResetCommands("com.hyouka.sensorreset")

        assertEquals(
            listOf("dumpsys", "sensorservice", "restrict", "com.hyouka.sensorreset"),
            commands[0]
        )
        assertEquals(
            listOf("dumpsys", "sensorservice", "enable"),
            commands[1]
        )
    }

    @Test
    fun vectorComparisonComputesAxisDelta() {
        val comparison = compareVectors(
            before = floatArrayOf(1f, 2f, 3f),
            after = floatArrayOf(2f, 1f, 4f)
        )

        assertArrayEquals(floatArrayOf(1f, -1f, 1f), comparison.delta, 0.0001f)
        assertEquals(1.0f, comparison.maxAbsoluteDelta, 0.0001f)
    }
    @Test
    fun sensorHalRestartCommandContractUsesAndroidInitControlMessages() {
        val commands = systemSensorHalRestartCommands()
        assertTrue(commands.any {
            it == listOf(
                "setprop",
                "ctl.interface_restart",
                "android.hardware.sensors@2.1::ISensors/default"
            )
        })
        assertTrue(commands.any {
            it == listOf("setprop", "ctl.restart", "sensors.qti")
        })
    }
}
