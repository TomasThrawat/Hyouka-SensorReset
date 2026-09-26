# Hyouka-SensorReset

Native Kotlin Android utility for reinitializing the Android sensor stack through SensorService and, when authorized, the underlying Sensors HAL service via Android init, with strict result reporting and detailed diagnostics.

## Included

### Normal mode
- Reset Sensors: unregisters active listeners, clears app session readings, and registers supported sensors again.
- Sensor Status: availability, registration count, live event count, and session duration.
- Live X/Y/Z values for Gyroscope, Accelerometer, Magnetometer, and Rotation Vector.
- Reset History: reset time, sensors present, registered sensors, event count, and session duration.

### Advanced mode
- Sensor Diagnostics: vendor, model/name, version, power, maximum range, resolution, min/max delay, FIFO capacity.
- Live Sensor Monitor: event count, measured rate in Hz, accuracy, and stall detection.
- Sensor Health Test: PASS, WARNING, or UNAVAILABLE for each supported motion sensor.
- Calibration Session: collects mean and standard deviation statistics and persists the last result.
- Before/After Reset Comparison: compares the first post-reset vector with the last pre-reset vector.
- Debug Log: start/stop/reset/registration/availability events, clear, and export.
- Diagnostics Export: TXT and JSON reports, plus a separate debug-log export.

## Scope

The Restart action performs a **system-wide active-sensor cycle** through Android SensorService when Shizuku is authorized. It temporarily disables every sensor that is currently active through SensorService, not just this app's four displayed motion sensors, then restores the system sensor state. The app only re-registers its own listeners afterward.

A successful HAL restart is a real Android init service restart when the target Sensors HAL process/service reports a verified restart. This is deeper than SensorService activation cycling, but it still does **not** claim to power-cycle the physical sensor IC, reset vendor firmware, or directly rebind a kernel driver. Those operations are device/vendor controlled and are not exposed by the standard Android sensor API.

## Build

The GitHub Actions workflow uses JDK 17, Gradle 9.4.1, Android Gradle Plugin 9.2.0, and compile SDK 37.

Minimum Android API: 26  
Target Android API: 36


## System-wide sensor restart

The Restart action attempts a **system-wide active-sensor cycle** through a Shizuku UserService:

1. dumpsys sensorservice restrict <this package>
2. dumpsys sensorservice enable
3. Re-register this app's sensor listeners after the system cycle.

AOSP documents restrict as temporarily disabling active sensors and enable as restoring normal operation and re-enabling them. The package argument is only the temporary SensorService allowlist used during the restricted window; it does not limit the disable/enable operation to this app.

The action has **no successful app-only reset fallback**. If the system-wide SensorService or HAL path fails or cannot be verified, the UI reports failure; the app only restores its own listeners so it remains usable. The log distinguishes requested HAL restarts from verified HAL restarts.

Shizuku authorization is required for the system-wide path.

## Sensors HAL restart path

After the SensorService restrict step, the privileged UserService requests Android init control messages for standard Sensors HAL HIDL/AIDL interfaces and for currently running init services whose names contain sensor, except sensorservice. It waits for an observable service state or PID change before reporting a verified HAL restart. The exact vendor service is device-specific, so rejected or unobservable requests are reported as failure.
