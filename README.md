# Hyouka-SensorReset

Native Kotlin Android utility for reinitializing the app's motion-sensor session and inspecting sensor behavior exposed through Android public sensor APIs.

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

The app performs app-level sensor listener reinitialization and diagnostics. It does **not** claim to recalibrate physical sensors or reset sensor firmware/driver state. Android public sensor APIs expose sensor metadata and event delivery, but hardware calibration/reset remains device/system controlled.

## Build

The GitHub Actions workflow uses JDK 17, Gradle 9.4.1, Android Gradle Plugin 9.2.0, and compile SDK 37.

Minimum Android API: 26  
Target Android API: 36
