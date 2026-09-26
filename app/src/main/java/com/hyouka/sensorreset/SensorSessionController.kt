package com.hyouka.sensorreset

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

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
    val samplingPeriodHintUs: Int = SensorManager.SENSOR_DELAY_GAME,
    val eventCount: Long = 0,
    val actualHz: Float? = null,
    val lastEventTimestampNanos: Long? = null,
    val stalled: Boolean = false
)

class SensorSessionController(
    context: Context,
    private val sensorManager: SensorManager
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val firstEventTimestampNanos = mutableMapOf<MotionSensor, Long>()
    private val lastEventTimestampNanos = mutableMapOf<MotionSensor, Long>()
    private val calibrationAccumulators = mutableMapOf<MotionSensor, RunningVectorStats>()
    private val postResetCaptured = mutableSetOf<MotionSensor>()

    private var registeredSensors = emptyMap<MotionSensor, Sensor>()
    private var sessionStartElapsedNanos = 0L
    private var preResetValues = emptyMap<MotionSensor, FloatArray>()
    private var calibrationStartElapsedNanos = 0L

    var uiState by mutableStateOf(loadInitialState())
        private set

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val motionSensor =
                MotionSensor.entries.firstOrNull { it.sensorType == event.sensor.type } ?: return
            val previous = uiState.readings[motionSensor] ?: return
            val values = event.values.take(3)
            val firstTimestamp = firstEventTimestampNanos[motionSensor]
            val eventCount = previous.eventCount + 1

            val actualHz = if (
                firstTimestamp != null &&
                event.timestamp > firstTimestamp &&
                eventCount > 1
            ) {
                (
                    (eventCount - 1).toDouble() * 1_000_000_000.0 /
                        (event.timestamp - firstTimestamp)
                    ).toFloat()
            } else {
                null
            }

            if (firstTimestamp == null) {
                firstEventTimestampNanos[motionSensor] = event.timestamp
            }
            lastEventTimestampNanos[motionSensor] = event.timestamp

            val comparison = if (
                uiState.comparisons[motionSensor] == null &&
                preResetValues[motionSensor] != null &&
                motionSensor !in postResetCaptured
            ) {
                postResetCaptured += motionSensor
                compareVectors(preResetValues.getValue(motionSensor), event.values)
            } else {
                uiState.comparisons[motionSensor]
            }

            if (uiState.calibrationActive) {
                calibrationAccumulators
                    .getOrPut(motionSensor) { RunningVectorStats() }
                    .add(event.values)
            }

            val calibrationStats = if (uiState.calibrationActive) {
                uiState.calibrationStats + (
                    motionSensor to calibrationAccumulators.getValue(motionSensor).toCalibrationStats()
                )
            } else {
                uiState.calibrationStats
            }

            uiState = uiState.copy(
                readings = uiState.readings + (
                    motionSensor to previous.copy(
                        available = true,
                        values = values,
                        accuracy = event.accuracy,
                        eventCount = eventCount,
                        actualHz = actualHz,
                        lastEventTimestampNanos = event.timestamp,
                        stalled = false
                    )
                ),
                comparisons = uiState.comparisons + (motionSensor to comparison),
                calibrationStats = calibrationStats
            )
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            val motionSensor =
                MotionSensor.entries.firstOrNull { it.sensorType == sensor.type } ?: return
            val previous = uiState.readings[motionSensor] ?: return
            uiState = uiState.copy(
                readings = uiState.readings + (
                    motionSensor to previous.copy(accuracy = accuracy)
                )
            )
        }
    }

    fun start() {
        if (uiState.running) return

        val registered = linkedMapOf<MotionSensor, Sensor>()
        val capabilities = linkedMapOf<MotionSensor, SensorCapabilities>()
        val readings = allSupportedMotionSensors()
            .associateWith { SensorReading(available = false) }
            .toMutableMap()

        allSupportedMotionSensors().forEach { motionSensor ->
            val sensor = sensorManager.getDefaultSensor(motionSensor.sensorType)
            if (sensor == null) {
                capabilities[motionSensor] = SensorCapabilities(available = false)
                addLog("SENSOR unavailable: " + motionSensor.title)
                return@forEach
            }

            capabilities[motionSensor] = SensorCapabilities(
                available = true,
                name = sensor.name,
                vendor = sensor.vendor,
                version = sensor.version,
                powerMa = sensor.power,
                maximumRange = sensor.maximumRange,
                resolution = sensor.resolution,
                minDelayUs = sensor.minDelay,
                maxDelayUs = sensor.maxDelay,
                fifoMaxEventCount = sensor.fifoMaxEventCount,
                reportingMode = sensor.reportingMode,
                wakeUpSensor = sensor.isWakeUpSensor,
                sensorId = sensor.id.takeIf { it >= 0 }
            )

            if (sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            ) {
                registered[motionSensor] = sensor
                readings[motionSensor] = SensorReading(available = true)
            } else {
                addLog("REGISTER failed: " + motionSensor.title)
            }
        }

        registeredSensors = registered
        firstEventTimestampNanos.clear()
        lastEventTimestampNanos.clear()
        sessionStartElapsedNanos = SystemClock.elapsedRealtimeNanos()
        postResetCaptured.clear()

        uiState = uiState.copy(
            readings = readings,
            capabilities = capabilities,
            running = true,
            sessionStartedEpochMs = System.currentTimeMillis(),
            sessionDurationMs = 0L,
            comparisons = allSupportedMotionSensors().associateWith { null }
        )
        addLog(
            "START session; registered " +
                registeredSensors.size +
                "/" +
                allSupportedMotionSensors().size +
                " sensors"
        )
    }

    fun stop() {
        if (!uiState.running) return

        val duration = currentSessionDuration()
        if (uiState.calibrationActive) {
            finishCalibration()
        }
        sensorManager.unregisterListener(listener)
        registeredSensors = emptyMap()
        sessionStartElapsedNanos = 0L

        uiState = uiState.copy(
            running = false,
            sessionDurationMs = duration
        )
        addLog(
            "STOP session; duration=" +
                formatDuration(duration) +
                ", events=" +
                totalEventCount()
        )
    }

    fun restartAllSensors() {
        if (uiState.calibrationActive) {
            finishCalibration()
        }

        val snapshot = uiState.readings
            .filterValues { it.values.isNotEmpty() }
            .mapValues { (_, reading) -> reading.values.toFloatArray() }

        val previousDuration = currentSessionDuration()
        val availableSensors = uiState.capabilities
            .filterValues { it.available }
            .keys
            .toList()
        val registeredCountBeforeReset = registeredSensors.size
        val eventCount = totalEventCount()
        val nextResetCount = uiState.resetCount + 1
        val supportedSensorCount = allSupportedMotionSensors().size

        // Explicit app-level restart boundary: unregister first, clear the old session,
        // then start a fresh registration cycle.
        sensorManager.unregisterListener(listener)
        registeredSensors = emptyMap()
        firstEventTimestampNanos.clear()
        lastEventTimestampNanos.clear()
        preResetValues = snapshot
        postResetCaptured.clear()

        uiState = uiState.copy(
            readings = uiState.readings.mapValues { (_, reading) ->
                reading.copy(
                    values = emptyList(),
                    eventCount = 0L,
                    actualHz = null,
                    lastEventTimestampNanos = null,
                    stalled = false
                )
            },
            resetCount = nextResetCount,
            running = false,
            sessionDurationMs = 0L,
            comparisons = allSupportedMotionSensors().associateWith { null }
        )

        addLog(
            "RESTART begin; unregistered " +
                registeredCountBeforeReset +
                "/" +
                supportedSensorCount +
                " sensors; previous duration=" +
                formatDuration(previousDuration) +
                ", events=" +
                eventCount
        )

        start()

        val restartCompletedEpochMs = System.currentTimeMillis()
        val entry = ResetHistoryEntry(
            timestampEpochMs = restartCompletedEpochMs,
            availableSensorCount = availableSensors.size,
            registeredSensorCount = registeredCountBeforeReset,
            eventCount = eventCount,
            sessionDurationMs = previousDuration,
            sensors = availableSensors
        )
        val history = (listOf(entry) + uiState.history).take(MAX_HISTORY)

        uiState = uiState.copy(
            history = history,
            lastResetEpochMs = restartCompletedEpochMs,
            sessionDurationMs = 0L
        )
        persistHistory(history)

        addLog(
            "RESTART complete; registered " +
                registeredSensors.size +
                "/" +
                supportedSensorCount +
                " sensors; new session duration=00:00"
        )
    }

    fun startCalibration() {
        if (uiState.calibrationActive) return
        if (!uiState.running) start()

        calibrationAccumulators.clear()
        uiState = uiState.copy(
            calibrationActive = true,
            calibrationStartedEpochMs = System.currentTimeMillis(),
            calibrationStats = emptyMap()
        )
        calibrationStartElapsedNanos = SystemClock.elapsedRealtimeNanos()
        addLog("CALIBRATION session started")
    }

    fun finishCalibration() {
        if (!uiState.calibrationActive) return

        val started = uiState.calibrationStartedEpochMs ?: System.currentTimeMillis()
        val ended = System.currentTimeMillis()
        val result = CalibrationSessionResult(
            startedAtEpochMs = started,
            endedAtEpochMs = ended,
            durationMs = if (calibrationStartElapsedNanos != 0L) {
                max(
                    0L,
                    (SystemClock.elapsedRealtimeNanos() - calibrationStartElapsedNanos) /
                        1_000_000L
                )
            } else {
                max(0L, ended - started)
            },
            sensors = uiState.calibrationStats
        )

        calibrationStartElapsedNanos = 0L
        calibrationAccumulators.clear()
        uiState = uiState.copy(
            calibrationActive = false,
            calibrationStartedEpochMs = null,
            lastCalibrationResult = result
        )
        persistCalibrationResult(result)
        addLog(
            "CALIBRATION session finished; duration=" +
                formatDuration(result.durationMs) +
                ", samples=" +
                result.sensors.values.sumOf { it.count }
        )
    }

    fun updateDiagnosticsClock() {
        if (!uiState.running) return

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val readings = uiState.readings.mapValues { (sensor, reading) ->
            val lastTimestamp = lastEventTimestampNanos[sensor]
            val minDelayUs = uiState.capabilities[sensor]
                ?.minDelayUs
                ?.takeIf { it > 0 }
                ?: SensorManager.SENSOR_DELAY_GAME
            val stallThresholdNanos = max(
                1_000_000_000L,
                minDelayUs.toLong() * 3L * 1_000L
            )
            reading.copy(
                stalled = lastTimestamp != null &&
                    nowNanos - lastTimestamp > stallThresholdNanos
            )
        }

        uiState = uiState.copy(
            readings = readings,
            sessionDurationMs = currentSessionDuration()
        )
    }

    fun clearLogs() {
        uiState = uiState.copy(logs = emptyList())
        preferences.edit().remove(KEY_LOGS).apply()
    }

    fun buildExportReport(format: ExportFormat): String {
        return when (format) {
            ExportFormat.TEXT -> buildTextReport()
            ExportFormat.JSON -> buildJsonReport()
            ExportFormat.LOGS -> uiState.logs.joinToString(separator = "\n")
        }
    }

    private fun loadInitialState(): SensorUiState {
        val readings = MotionSensor.entries.associateWith {
            SensorReading(available = false)
        }

        return SensorUiState(
            readings = readings,
            capabilities = emptyMap(),
            running = false,
            resetCount = preferences.getInt(KEY_RESET_COUNT, 0),
            lastResetEpochMs = preferences.getLong(KEY_LAST_RESET, -1L)
                .takeIf { it >= 0L },
            sessionStartedEpochMs = null,
            sessionDurationMs = 0L,
            calibrationActive = false,
            calibrationStartedEpochMs = null,
            calibrationStats = emptyMap(),
            lastCalibrationResult = loadLastCalibrationResult(),
            comparisons = MotionSensor.entries.associateWith { null },
            history = loadHistory(),
            logs = loadLogs()
        )
    }

    private fun addLog(message: String) {
        val entry = formatDateTime(System.currentTimeMillis()) + "  " + message
        val logs = (uiState.logs + entry).takeLast(MAX_LOGS)

        uiState = uiState.copy(logs = logs)
        preferences.edit()
            .putString(KEY_LOGS, JSONArray(logs).toString())
            .putInt(KEY_RESET_COUNT, uiState.resetCount)
            .putLong(KEY_LAST_RESET, uiState.lastResetEpochMs ?: -1L)
            .apply()
    }

    private fun persistHistory(history: List<ResetHistoryEntry>) {
        val array = JSONArray()
        history.forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestampEpochMs", entry.timestampEpochMs)
                    .put("availableSensorCount", entry.availableSensorCount)
                    .put("registeredSensorCount", entry.registeredSensorCount)
                    .put("eventCount", entry.eventCount)
                    .put("sessionDurationMs", entry.sessionDurationMs)
                    .put("sensors", JSONArray(entry.sensors.map { it.name }))
            )
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun loadHistory(): List<ResetHistoryEntry> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val sensors = item.optJSONArray("sensors")?.let { names ->
                        buildList {
                            for (sensorIndex in 0 until names.length()) {
                                runCatching {
                                    add(MotionSensor.valueOf(names.getString(sensorIndex)))
                                }
                            }
                        }
                    }.orEmpty()

                    add(
                        ResetHistoryEntry(
                            timestampEpochMs = item.getLong("timestampEpochMs"),
                            availableSensorCount = item.getInt("availableSensorCount"),
                            registeredSensorCount = item.getInt("registeredSensorCount"),
                            eventCount = item.getLong("eventCount"),
                            sessionDurationMs = item.getLong("sessionDurationMs"),
                            sensors = sensors
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistCalibrationResult(result: CalibrationSessionResult) {
        val sensors = JSONObject()
        result.sensors.forEach { (sensor, stats) ->
            sensors.put(
                sensor.name,
                JSONObject()
                    .put("count", stats.count)
                    .put("mean", JSONArray(stats.mean))
                    .put("standardDeviation", JSONArray(stats.standardDeviation))
            )
        }

        preferences.edit().putString(
            KEY_LAST_CALIBRATION,
            JSONObject()
                .put("startedAtEpochMs", result.startedAtEpochMs)
                .put("endedAtEpochMs", result.endedAtEpochMs)
                .put("durationMs", result.durationMs)
                .put("sensors", sensors)
                .toString()
        ).apply()
    }

    private fun loadLastCalibrationResult(): CalibrationSessionResult? {
        val raw = preferences.getString(KEY_LAST_CALIBRATION, null) ?: return null

        return runCatching {
            val root = JSONObject(raw)
            val sensorObject = root.getJSONObject("sensors")
            val sensors = linkedMapOf<MotionSensor, CalibrationSensorStats>()

            sensorObject.keys().forEach { name ->
                runCatching {
                    val sensor = MotionSensor.valueOf(name)
                    val stats = sensorObject.getJSONObject(name)
                    sensors[sensor] = CalibrationSensorStats(
                        count = stats.getLong("count"),
                        mean = jsonFloatList(stats.getJSONArray("mean")),
                        standardDeviation = jsonFloatList(stats.getJSONArray("standardDeviation"))
                    )
                }
            }

            CalibrationSessionResult(
                startedAtEpochMs = root.getLong("startedAtEpochMs"),
                endedAtEpochMs = root.getLong("endedAtEpochMs"),
                durationMs = root.getLong("durationMs"),
                sensors = sensors
            )
        }.getOrNull()
    }

    private fun loadLogs(): List<String> {
        val raw = preferences.getString(KEY_LOGS, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getString(index))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildTextReport(): String {
        val builder = StringBuilder()

        builder.appendLine("Hyouka Sensor Reset Diagnostics")
        builder.appendLine("Generated: " + formatDateTime(System.currentTimeMillis()))
        builder.appendLine("Device: " + Build.MANUFACTURER + " " + Build.MODEL)
        builder.appendLine("Android API: " + Build.VERSION.SDK_INT)
        builder.appendLine("App target API: " + appContext.applicationInfo.targetSdkVersion)
        builder.appendLine()

        MotionSensor.entries.forEach { sensor ->
            val capability = uiState.capabilities[sensor]
            val reading = uiState.readings[sensor]
            val health = evaluateSensorHealth(
                reading?.available == true,
                reading?.eventCount ?: 0L,
                reading?.accuracy ?: SensorManager.SENSOR_STATUS_UNRELIABLE,
                reading?.stalled == true
            )

            builder.appendLine("[" + sensor.title + "]")
            builder.appendLine("Health: " + health.name)
            builder.appendLine("Name: " + (capability?.name ?: "Unavailable"))
            builder.appendLine("Vendor: " + (capability?.vendor ?: "Unavailable"))
            builder.appendLine("Version: " + (capability?.version ?: 0))
            builder.appendLine("Power: " + (capability?.powerMa?.let(::formatFloat) ?: "—") + " mA")
            builder.appendLine(
                "Maximum range: " +
                    (capability?.maximumRange?.let(::formatFloat) ?: "—") +
                    " " +
                    sensor.unit
            )
            builder.appendLine(
                "Resolution: " +
                    (capability?.resolution?.let(::formatFloat) ?: "—") +
                    " " +
                    sensor.unit
            )
            builder.appendLine("Min delay: " + (capability?.minDelayUs ?: "—") + " us")
            builder.appendLine("Max delay: " + (capability?.maxDelayUs ?: "—") + " us")
            builder.appendLine("FIFO max events: " + (capability?.fifoMaxEventCount ?: "—"))
            builder.appendLine("Reporting mode: " + (capability?.reportingMode ?: "—"))
            builder.appendLine("Wake-up sensor: " + (capability?.wakeUpSensor ?: "—"))
            builder.appendLine("Sensor ID: " + (capability?.sensorId ?: "—"))
            builder.appendLine("Events: " + (reading?.eventCount ?: 0L))
            builder.appendLine("Actual rate: " + (reading?.actualHz?.let(::formatFloat) ?: "—") + " Hz")
            builder.appendLine("Accuracy: " + (reading?.accuracy ?: "—"))
            builder.appendLine("Stalled: " + (reading?.stalled ?: false))
            builder.appendLine("Reading: " + (reading?.values?.joinToString(", ") ?: "—"))
            builder.appendLine()
        }

        builder.appendLine("Session")
        builder.appendLine("Running: " + uiState.running)
        builder.appendLine("Duration: " + formatDuration(uiState.sessionDurationMs))
        builder.appendLine("Reset count: " + uiState.resetCount)
        builder.appendLine(
            "Last reset: " +
                (uiState.lastResetEpochMs?.let(::formatDateTime) ?: "Never")
        )
        builder.appendLine()

        builder.appendLine("Calibration")
        uiState.lastCalibrationResult?.let { result ->
            builder.appendLine("Last session: " + formatDateTime(result.endedAtEpochMs))
            builder.appendLine("Duration: " + formatDuration(result.durationMs))
            result.sensors.forEach { (sensor, stats) ->
                builder.appendLine(
                    sensor.title +
                        ": samples=" +
                        stats.count +
                        ", mean=" +
                        stats.mean +
                        ", stddev=" +
                        stats.standardDeviation
                )
            }
        } ?: builder.appendLine("No saved calibration session.")
        builder.appendLine()

        builder.appendLine("Reset History")
        uiState.history.forEach { entry ->
            builder.appendLine(
                formatDateTime(entry.timestampEpochMs) +
                    " | sensors=" +
                    entry.availableSensorCount +
                    " | registered=" +
                    entry.registeredSensorCount +
                    " | events=" +
                    entry.eventCount +
                    " | duration=" +
                    formatDuration(entry.sessionDurationMs)
            )
        }
        if (uiState.history.isEmpty()) {
            builder.appendLine("No resets recorded.")
        }

        builder.appendLine()
        builder.appendLine("Debug Log")
        builder.appendLine(uiState.logs.joinToString(separator = "\n"))
        builder.appendLine()
        builder.appendLine(
            "Hardware note: this app reinitializes app-level sensor listeners and records diagnostics. " +
                "It does not claim to reset sensor firmware or perform hardware calibration."
        )

        return builder.toString()
    }

    private fun buildJsonReport(): String {
        val root = JSONObject()
            .put("generatedAt", formatDateTime(System.currentTimeMillis()))
            .put(
                "device",
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("androidApi", Build.VERSION.SDK_INT)
                    .put("androidRelease", Build.VERSION.RELEASE)
            )
            .put(
                "session",
                JSONObject()
                    .put("running", uiState.running)
                    .put("durationMs", uiState.sessionDurationMs)
                    .put("resetCount", uiState.resetCount)
                    .put(
                        "lastResetEpochMs",
                        uiState.lastResetEpochMs ?: JSONObject.NULL
                    )
            )

        val sensors = JSONObject()
        MotionSensor.entries.forEach { sensor ->
            val capability = uiState.capabilities[sensor]
            val reading = uiState.readings[sensor]
            val comparison = uiState.comparisons[sensor]

            sensors.put(
                sensor.name,
                JSONObject()
                    .put("available", capability?.available ?: false)
                    .put("name", capability?.name ?: JSONObject.NULL)
                    .put("vendor", capability?.vendor ?: JSONObject.NULL)
                    .put("version", capability?.version ?: JSONObject.NULL)
                    .put("powerMa", capability?.powerMa ?: JSONObject.NULL)
                    .put("maximumRange", capability?.maximumRange ?: JSONObject.NULL)
                    .put("resolution", capability?.resolution ?: JSONObject.NULL)
                    .put("minDelayUs", capability?.minDelayUs ?: JSONObject.NULL)
                    .put("maxDelayUs", capability?.maxDelayUs ?: JSONObject.NULL)
                    .put("fifoMaxEventCount", capability?.fifoMaxEventCount ?: JSONObject.NULL)
                    .put("reportingMode", capability?.reportingMode ?: JSONObject.NULL)
                    .put("wakeUpSensor", capability?.wakeUpSensor ?: JSONObject.NULL)
                    .put("sensorId", capability?.sensorId ?: JSONObject.NULL)
                    .put("eventCount", reading?.eventCount ?: 0L)
                    .put("actualHz", reading?.actualHz ?: JSONObject.NULL)
                    .put("accuracy", reading?.accuracy ?: JSONObject.NULL)
                    .put("stalled", reading?.stalled ?: false)
                    .put(
                        "health",
                        evaluateSensorHealth(
                            reading?.available == true,
                            reading?.eventCount ?: 0L,
                            reading?.accuracy ?: SensorManager.SENSOR_STATUS_UNRELIABLE,
                            reading?.stalled == true
                        ).name
                    )
                    .put(
                        "values",
                        JSONArray(reading?.values ?: emptyList<Float>())
                    )
                    .put(
                        "comparisonDelta",
                        comparison?.let { JSONArray(it.delta.toList()) } ?: JSONObject.NULL
                    )
                    .put(
                        "comparisonMaxAbsoluteDelta",
                        comparison?.maxAbsoluteDelta ?: JSONObject.NULL
                    )
            )
        }
        root.put("sensors", sensors)

        val history = JSONArray()
        uiState.history.forEach { entry ->
            history.put(
                JSONObject()
                    .put("timestampEpochMs", entry.timestampEpochMs)
                    .put("availableSensorCount", entry.availableSensorCount)
                    .put("registeredSensorCount", entry.registeredSensorCount)
                    .put("eventCount", entry.eventCount)
                    .put("sessionDurationMs", entry.sessionDurationMs)
                    .put("sensors", JSONArray(entry.sensors.map { it.name }))
            )
        }
        root.put("resetHistory", history)

        uiState.lastCalibrationResult?.let { result ->
            val calibrationSensors = JSONObject()
            result.sensors.forEach { (sensor, stats) ->
                calibrationSensors.put(
                    sensor.name,
                    JSONObject()
                        .put("count", stats.count)
                        .put("mean", JSONArray(stats.mean))
                        .put("standardDeviation", JSONArray(stats.standardDeviation))
                )
            }

            root.put(
                "lastCalibration",
                JSONObject()
                    .put("startedAtEpochMs", result.startedAtEpochMs)
                    .put("endedAtEpochMs", result.endedAtEpochMs)
                    .put("durationMs", result.durationMs)
                    .put("sensors", calibrationSensors)
            )
        }

        root.put("debugLog", JSONArray(uiState.logs))
        root.put(
            "scopeNote",
            "App-level sensor listener reinitialization and diagnostics only; no firmware reset or hardware calibration claim."
        )
        return root.toString(2)
    }

    private fun totalEventCount(): Long {
        return uiState.readings.values.sumOf { it.eventCount }
    }

    private fun currentSessionDuration(): Long {
        if (!uiState.running || sessionStartElapsedNanos == 0L) {
            return uiState.sessionDurationMs
        }
        return (
            SystemClock.elapsedRealtimeNanos() - sessionStartElapsedNanos
            ) / 1_000_000L
    }

    private fun RunningVectorStats.toCalibrationStats(): CalibrationSensorStats {
        return CalibrationSensorStats(
            count = count,
            mean = mean().toList(),
            standardDeviation = standardDeviation().toList()
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "sensor_reset_state"
        private const val KEY_HISTORY = "reset_history"
        private const val KEY_LAST_CALIBRATION = "last_calibration"
        private const val KEY_LOGS = "debug_logs"
        private const val KEY_RESET_COUNT = "reset_count"
        private const val KEY_LAST_RESET = "last_reset"
        private const val MAX_HISTORY = 20
        private const val MAX_LOGS = 200
    }
}

enum class ExportFormat {
    TEXT,
    JSON,
    LOGS
}

private fun jsonFloatList(array: JSONArray): List<Float> {
    return buildList {
        for (index in 0 until array.length()) {
            add(array.getDouble(index).toFloat())
        }
    }
}

private fun formatFloat(value: Float): String {
    return String.format(Locale.US, "%.4f", value)
}

private fun formatDateTime(epochMs: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
