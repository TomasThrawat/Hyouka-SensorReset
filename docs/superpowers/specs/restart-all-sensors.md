# Restart All Sensors

## Goal
Provide one Material You action that reinitializes every motion sensor supported by the app.

## Behavior
- Restart is an explicit lifecycle boundary: unregister the shared listener first, clear the current session timer and readings, then register all supported sensors again.
- The new session duration starts at 00:00 after the restart completes.
- Reset History timestamps are wall-clock restart times; the previous session duration is stored separately so a timestamp cannot be mistaken for sensor-open duration.
- The action records the current session in the existing reset history.
- The action unregisters the shared sensor listener from all registered sensors.
- The action clears current app-level session readings and timing state.
- The action registers every supported MotionSensor again.
- Existing diagnostics, calibration history, comparison data, and debug logging remain functional.
- The feature does not claim to reset sensor firmware, driver state, or hardware calibration.

## UI
Use the existing pure-black Material 3/Material You screen and replace the reset wording with a single Restart all sensors action.

## Supported sensors
Gyroscope, Accelerometer, Magnetometer, and Rotation Vector.
