# System SensorService Restart Implementation Plan

**Goal:** Make Restart all sensors attempt a system SensorService disable and enable cycle through Shizuku before re-registering this app listeners.

**Architecture:** Keep lifecycle ownership in SensorSessionController. Use a small AIDL contract for a Shizuku UserService so the privileged operation is isolated from the normal app process. Preserve the existing Material 3 UI and diagnostics.

**Tech Stack:** Kotlin, Android SensorManager, Shizuku 13.1.5, AIDL, Jetpack Compose Material 3, JUnit, Gradle/GitHub Actions.

## Constraints

- Minimum Android API: 26.
- Target Android API: 36.
- No unsupported vendor driver or firmware command.
- The system path uses AOSP SensorService restrict and enable.
- Without Shizuku, fallback is explicit app-level listener reinitialization.
- The system cycle may briefly affect other sensor clients.

## Tasks

- [x] Add Shizuku API/provider dependencies and provider manifest entry.
- [x] Add AIDL UserService and privileged SensorService cycle.
- [x] Make restartAllSensors asynchronous and report system success or fallback.
- [x] Keep reset history, comparison, diagnostics, and timing intact.
- [x] Add unit test for the SensorService command contract.
- [ ] Run full unit suite and debug APK build.
- [ ] Inspect complete GitHub Actions job log and verify newest artifact.

## HAL restart implementation

Use Android init control messages through the Shizuku UserService after SensorService restriction. Request standard Sensors HAL interfaces, restart exposed sensor-named init services except sensorservice, verify service state or PID changes, then return SensorService to NORMAL mode. Treat permission denials and unverified restarts as failures.
