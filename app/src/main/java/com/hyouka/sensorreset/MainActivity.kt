package com.hyouka.sensorreset

import android.content.Context
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = false

        setContent {
            SensorResetTheme {
                SensorResetScreen()
            }
        }
    }
}

@Composable
private fun SensorResetTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val baseScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }

    MaterialTheme(
        colorScheme = baseScheme.copy(
            background = Color.Black,
            surface = Color.Black
        ),
        content = content
    )
}

private enum class ScreenMode {
    NORMAL,
    ADVANCED
}

@Composable
private fun SensorResetScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        SensorSessionController(
            context = context,
            sensorManager = context.getSystemService(SensorManager::class.java)
        )
    }
    val state = controller.uiState
    var mode by remember { mutableStateOf(ScreenMode.NORMAL) }

    val textExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) saveExport(context, uri, controller.buildExportReport(ExportFormat.TEXT))
    }

    val jsonExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) saveExport(context, uri, controller.buildExportReport(ExportFormat.JSON))
    }

    val logExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) saveExport(context, uri, controller.buildExportReport(ExportFormat.LOGS))
    }

    LaunchedEffect(controller, state.running) {
        while (state.running) {
            controller.updateDiagnosticsClock()
            delay(500)
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.start()
                Lifecycle.Event.ON_PAUSE -> controller.stop()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.stop()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(running = state.running)

            ModeSelector(
                mode = mode,
                onModeChange = { mode = it }
            )

            ResetCard(
                resetCount = state.resetCount,
                lastResetEpochMs = state.lastResetEpochMs,
                sessionDurationMs = state.sessionDurationMs,
                restartInProgress = state.restartInProgress,
                systemResetStatus = state.systemResetStatus,
                onReset = controller::restartAllSensors
            )

            when (mode) {
                ScreenMode.NORMAL -> {
                    SensorStatusSummary(state)
                    state.readings.forEach { (sensor, reading) ->
                        SensorCard(
                            sensor = sensor,
                            reading = reading,
                            capability = state.capabilities[sensor],
                            comparison = null
                        )
                    }
                    HistoryCard(state.history)
                }

                ScreenMode.ADVANCED -> {
                    DiagnosticsOverview(state)

                    state.readings.forEach { (sensor, reading) ->
                        SensorCard(
                            sensor = sensor,
                            reading = reading,
                            capability = state.capabilities[sensor],
                            comparison = state.comparisons[sensor]
                        )
                    }

                    HealthCard(state)
                    CalibrationCard(state, controller)
                    ComparisonCard(state)
                    HistoryCard(state.history)
                    DebugLogCard(
                        logs = state.logs,
                        onClear = controller::clearLogs,
                        onExport = { logExporter.launch(exportFileName("sensor-reset-log", "txt")) }
                    )
                    ExportCard(
                        onExportText = {
                            textExporter.launch(
                                exportFileName("sensor-reset-diagnostics", "txt")
                            )
                        },
                        onExportJson = {
                            jsonExporter.launch(
                                exportFileName("sensor-reset-diagnostics", "json")
                            )
                        }
                    )
                    InfoCard()
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Header(running: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Sensor Reset",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (running) "Motion sensor session is active" else "Sensor session is paused",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeSelector(
    mode: ScreenMode,
    onModeChange: (ScreenMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = mode == ScreenMode.NORMAL,
            onClick = { onModeChange(ScreenMode.NORMAL) },
            label = { Text("Normal") }
        )
        FilterChip(
            selected = mode == ScreenMode.ADVANCED,
            onClick = { onModeChange(ScreenMode.ADVANCED) },
            label = { Text("Advanced") }
        )
    }
}

@Composable
private fun ResetCard(
    resetCount: Int,
    lastResetEpochMs: Long?,
    sessionDurationMs: Long,
    restartInProgress: Boolean,
    systemResetStatus: String,
    onReset: () -> Unit
) {
    SectionCard {
        Text(
            "Restart all system sensors",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "This temporarily disables every sensor currently active in Android SensorService for all apps, then restores the system sensor state. The app restores its own listeners afterward. This is not a physical sensor power-cycle or driver reset.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onReset,
            enabled = !restartInProgress,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (restartInProgress) "Restarting system sensors…" else "Restart all system sensors")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Restarts", resetCount.toString())
            Metric("Current session", formatDuration(sessionDurationMs))
        }

        Text(
            "Last restart: " + (lastResetEpochMs?.let(::formatClock) ?: "Not restarted yet"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "System reset: " + systemResetStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SensorStatusSummary(state: SensorUiState) {
    SectionCard {
        SectionTitle(Icons.Default.Assessment, "Sensor Status")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Available", state.capabilities.values.count { it.available }.toString())
            Metric("Registered", state.readings.count { it.value.available }.toString())
            Metric(
                "Events",
                state.readings.values.sumOf { it.eventCount }.toString()
            )
        }
    }
}

@Composable
private fun DiagnosticsOverview(state: SensorUiState) {
    SectionCard {
        SectionTitle(Icons.Default.Speed, "Diagnostics Overview")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Available", state.capabilities.values.count { it.available }.toString())
            Metric("Events", state.readings.values.sumOf { it.eventCount }.toString())
            Metric("Duration", formatDuration(state.sessionDurationMs))
        }
        Text(
            "Sampling profile: SENSOR_DELAY_GAME. Actual Hz is measured from sensor event timestamps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SensorCard(
    sensor: MotionSensor,
    reading: SensorReading,
    capability: SensorCapabilities?,
    comparison: VectorComparison?
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sensor.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    sensor.unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatusPill(
                label = when {
                    !reading.available -> "Unavailable"
                    reading.stalled -> "Stalled"
                    reading.eventCount == 0L -> "Waiting"
                    else -> "Live"
                },
                icon = if (!reading.available || reading.stalled) {
                    Icons.Default.Warning
                } else {
                    Icons.Default.CheckCircle
                }
            )
        }

        AxisRow("X", reading.values.getOrNull(0), sensor.unit)
        AxisRow("Y", reading.values.getOrNull(1), sensor.unit)
        AxisRow("Z", reading.values.getOrNull(2), sensor.unit)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("Events", reading.eventCount.toString())
            Metric("Avg rate", reading.actualHz?.let { formatFloat(it) + " Hz" } ?: "—")
            Metric(
                "Accuracy",
                accuracyLabel(reading.accuracy)
            )
        }

        Text(
            "Diagnostics",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        InfoRow("Vendor", capability?.vendor ?: "Unavailable")
        InfoRow("Model", capability?.name ?: "Unavailable")
        InfoRow("Version", capability?.version?.toString() ?: "—")
        InfoRow("Power", capability?.powerMa?.let { formatFloat(it) + " mA" } ?: "—")
        InfoRow(
            "Maximum range",
            capability?.maximumRange?.let { formatFloat(it) + " " + sensor.unit } ?: "—"
        )
        InfoRow(
            "Resolution",
            capability?.resolution?.let { formatFloat(it) + " " + sensor.unit } ?: "—"
        )
        InfoRow("Min delay", capability?.minDelayUs?.let { "$it µs" } ?: "—")
        InfoRow("Max delay", capability?.maxDelayUs?.let { "$it µs" } ?: "—")
        InfoRow("FIFO max events", capability?.fifoMaxEventCount?.toString() ?: "—")
        InfoRow("Reporting mode", capability?.reportingMode?.toString() ?: "—")
        InfoRow("Wake-up sensor", capability?.wakeUpSensor?.toString() ?: "—")
        InfoRow("Sensor ID", capability?.sensorId?.toString() ?: "—")
        InfoRow("Requested profile", "SENSOR_DELAY_GAME")

        comparison?.let {
            Text(
                "After reset delta: " + it.delta.joinToString(", ") { value ->
                    formatFloat(value)
                } + " " + sensor.unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HealthCard(state: SensorUiState) {
    SectionCard {
        SectionTitle(Icons.Default.CheckCircle, "Sensor Health Test")
        MotionSensor.entries.forEach { sensor ->
            val reading = state.readings[sensor]
            val capability = state.capabilities[sensor]
            val status = evaluateSensorHealth(
                available = reading?.available == true,
                eventCount = reading?.eventCount ?: 0L,
                accuracy = reading?.accuracy ?: 0,
                stalled = reading?.stalled == true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sensor.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                StatusPill(
                    label = status.name,
                    icon = when (status) {
                        SensorHealthStatus.PASS -> Icons.Default.CheckCircle
                        SensorHealthStatus.WARNING -> Icons.Default.Warning
                        SensorHealthStatus.UNAVAILABLE -> Icons.Default.Warning
                    }
                )
            }
        }
        Text(
            "PASS means the registered sensor is producing events with medium or high accuracy. WARNING is a diagnostic signal, not proof of hardware failure.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalibrationCard(
    state: SensorUiState,
    controller: SensorSessionController
) {
    SectionCard {
        SectionTitle(Icons.Default.Science, "Calibration Session")

        Text(
            if (state.calibrationActive) {
                "Collecting live samples. Mean and standard deviation are updated for each sensor."
            } else {
                "Measures a session of sensor data. This records statistics and does not change hardware calibration."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                if (state.calibrationActive) {
                    controller.finishCalibration()
                } else {
                    controller.startCalibration()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                if (state.calibrationActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.calibrationActive) "Finish calibration session" else "Start calibration session")
        }

        state.calibrationStats.forEach { (sensor, stats) ->
            Text(
                sensor.title + " • samples " + stats.count,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            InfoRow("Mean", stats.mean.joinToString(", ") { formatFloat(it) })
            InfoRow(
                "Std dev",
                stats.standardDeviation.joinToString(", ") { formatFloat(it) }
            )
        }

        state.lastCalibrationResult?.let {
            InfoRow(
                "Last session",
                formatClock(it.endedAtEpochMs) + " • " + formatDuration(it.durationMs)
            )
        }
    }
}

@Composable
private fun ComparisonCard(state: SensorUiState) {
    SectionCard {
        SectionTitle(Icons.AutoMirrored.Filled.CompareArrows, "Before / After Reset")

        val availableComparisons = state.comparisons.filterValues { it != null }
        if (availableComparisons.isEmpty()) {
            Text(
                "Reset the session, then wait for a fresh event. The first post-reset vector will be compared against the last pre-reset vector.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            availableComparisons.forEach { (sensor, comparison) ->
                comparison ?: return@forEach
                InfoRow(
                    sensor.title,
                    "Δ " +
                        comparison.delta.joinToString(", ") { formatFloat(it) } +
                        " • max |Δ| " +
                        formatFloat(comparison.maxAbsoluteDelta)
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<ResetHistoryEntry>) {
    SectionCard {
        SectionTitle(Icons.Default.History, "Reset History")

        if (history.isEmpty()) {
            Text(
                "No reset entries recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        history.take(10).forEach { entry ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "Restarted at " + formatClock(entry.timestampEpochMs),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Previous session: " +
                        formatDuration(entry.sessionDurationMs) +
                        " • Sensors " +
                        entry.availableSensorCount +
                        " • Registered " +
                        entry.registeredSensorCount +
                        " • Events " +
                        entry.eventCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DebugLogCard(
    logs: List<String>,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    SectionCard {
        SectionTitle(Icons.Default.BugReport, "Debug Log")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Text("Clear")
            }
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
        }

        if (logs.isEmpty()) {
            Text(
                "No events logged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            logs.takeLast(12).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExportCard(
    onExportText: () -> Unit,
    onExportJson: () -> Unit
) {
    SectionCard {
        SectionTitle(Icons.Default.Download, "Export Diagnostics")

        Text(
            "Save the current device, sensor, session, health, calibration, comparison, history, and debug data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onExportText,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("TXT")
            }
            Button(
                onClick = onExportJson,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("JSON")
            }
        }

        Text(
            "The Android file picker lets you choose the Download folder and filename.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard() {
    SectionCard {
        SectionTitle(Icons.Default.Info, "Scope")
        Text(
            "With Shizuku authorized, this app requests Android SensorService to enter restricted mode and then return to normal, causing SensorService to disable and re-enable sensors. This is deeper than listener re-registration, but it is still not a driver rebind, firmware reset, or physical power-cycle. Without Shizuku, only app-level listener reinitialization is available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF080808)
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun AxisRow(
    label: String,
    value: Float?,
    unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            value?.let(::formatFloat) ?: "—",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun accuracyLabel(accuracy: Int): String {
    return when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
        else -> "Unreliable"
    }
}

private fun formatFloat(value: Float): String {
    return String.format(Locale.US, "%.4f", value)
}

private fun formatClock(epochMs: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMs))
}

private fun formatDateTime(epochMs: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun exportFileName(base: String, extension: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        .format(Date(System.currentTimeMillis()))
    return base + "-" + stamp + "." + extension
}

private fun saveExport(
    context: Context,
    uri: Uri,
    content: String
) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open export destination")
    }.onSuccess {
        Toast.makeText(context, "Export saved", Toast.LENGTH_SHORT).show()
    }.onFailure { error ->
        Toast.makeText(
            context,
            "Export failed: " + (error.message ?: "unknown error"),
            Toast.LENGTH_LONG
        ).show()
    }
}
