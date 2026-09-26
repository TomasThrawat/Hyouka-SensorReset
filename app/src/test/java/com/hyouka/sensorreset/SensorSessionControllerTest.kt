package com.hyouka.sensorreset

import org.junit.Assert.assertEquals
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
}
