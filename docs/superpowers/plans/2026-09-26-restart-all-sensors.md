# Restart All Sensors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Replace the ambiguous reset wording with one explicit action that restarts every supported motion sensor while preserving the app's existing diagnostics/history behavior.

**Architecture:** Keep sensor lifecycle ownership in SensorSessionController. Centralize the supported restart set so both the controller and tests share the same source of truth. Keep the existing Material 3 UI structure in MainActivity.kt; only change the reset action's naming and wiring.

**Tech Stack:** Kotlin, Android SensorManager, Jetpack Compose Material 3, JUnit, Gradle/GitHub Actions.

**Spec:** docs/superpowers/specs/restart-all-sensors.md

## Global Constraints

- Minimum Android API: 26.
- Target Android API: 36.
- The feature is app-level sensor listener reinitialization only.
- Keep the existing pure-black Material 3 UI.
- Restart only the four supported MotionSensor entries.

## Review Focus

- All supported sensor types are included in the restart set: covered by allSupportedSensorsAreIncludedInTheRestartSet.
- Restart uses the existing shared listener and unregister/register lifecycle: covered by controller implementation review and CI build/tests.
- The single UI action is clearly labeled and wired to the controller: covered by MainActivity inspection.
- Existing reset history/comparison behavior remains intact: covered by preserving the current restartAllSensors body and final verification.
- Missing physical sensors must remain unavailable rather than causing the whole restart to fail: covered by existing start() behavior and final build/test verification.

---

### Task 1: Define the restart set in a failing test

**Files:**
- Modify: app/src/test/java/com/hyouka/sensorreset/SensorSessionControllerTest.kt

**Interfaces:**
- Produces: allSupportedMotionSensors(): List<MotionSensor>

- [ ] Step 1: Write the failing test
  Add allSupportedSensorsAreIncludedInTheRestartSet and assert set equality plus exact size against MotionSensor.entries.

- [ ] Step 2: Run the test and verify it fails
  Run: gradle :app:testDebugUnitTest --tests com.hyouka.sensorreset.SensorSessionControllerTest.allSupportedSensorsAreIncludedInTheRestartSet --stacktrace
  Expected: FAIL because allSupportedMotionSensors is not defined yet.

### Task 2: Implement restart-all sensor wiring

**Files:**
- Modify: app/src/main/java/com/hyouka/sensorreset/SensorDiagnostics.kt
- Modify: app/src/main/java/com/hyouka/sensorreset/SensorSessionController.kt
- Modify: app/src/main/java/com/hyouka/sensorreset/MainActivity.kt

**Interfaces:**
- Consumes: allSupportedMotionSensors(): List<MotionSensor>
- Produces: SensorSessionController.restartAllSensors()

- [ ] Step 1: Implement allSupportedMotionSensors(): List<MotionSensor>
  Return exactly MotionSensor.entries so the supported restart set has one source of truth.

- [ ] Step 2: Update controller registration loops
  Use allSupportedMotionSensors() anywhere the supported restart/register set is enumerated, without changing per-sensor availability handling.

- [ ] Step 3: Rename the user action
  Rename the existing public reset operation to restartAllSensors() and preserve its existing history, snapshot/comparison, listener unregister, state clear, and re-registration behavior.

- [ ] Step 4: Wire the one Material You button
  Change the existing reset card copy to Restart all sensors and call controller.restartAllSensors(). Do not add a second reset control.

### Task 3: Verify and review

**Files:**
- No new files.

- [ ] Step 1: Run the focused unit test
  Run: gradle :app:testDebugUnitTest --tests com.hyouka.sensorreset.SensorSessionControllerTest.allSupportedSensorsAreIncludedInTheRestartSet --stacktrace
  Expected: PASS.

- [ ] Step 2: Run the full test suite
  Run: gradle :app:testDebugUnitTest --stacktrace
  Expected: PASS with no test failures.

- [ ] Step 3: Build the debug APK
  Run: gradle :app:assembleDebug --stacktrace
  Expected: PASS and produce app/build/outputs/apk/debug/app-debug.apk.

- [ ] Step 4: Verify GitHub Actions
  Confirm the Android Build workflow for the final commit completes successfully and inspect the complete job result before reporting success.
