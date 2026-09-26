# Restart All Sensors

## Goal
Provide one Material You action that reinitializes every motion sensor supported by the app.

## Behavior
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
