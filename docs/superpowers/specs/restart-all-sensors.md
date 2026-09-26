# Restart All Sensors

## Goal

Provide one Material You action that first attempts the deepest sensor reinitialization exposed through the device Android sensor service, then restores this app listeners and diagnostics.

## Behavior

- The Restart action captures the current session for history and comparison.
- It unregisters this app listeners before the system cycle.
- With Shizuku authorization, a privileged UserService requests the SensorService restrict then enable shell sequence.
- restrict temporarily disables active sensors and enable restores normal SensorService operation.
- After the system cycle, the app registers all four supported motion sensors again.
- If Shizuku is unavailable or denied, the app falls back to app-level listener reinitialization and records that fallback.
- The previous session duration remains separate from the wall-clock restart timestamp.
- This does not claim a vendor driver rebind, sensor firmware reboot, or physical sensor power-cycle.
- The system cycle can temporarily affect other apps using sensors.

## Supported sensors

Gyroscope, Accelerometer, Magnetometer, and Rotation Vector.

## Privileged path

The privileged operation is isolated in a Shizuku UserService. The normal app process never assumes shell or root identity.

## UI

Keep the existing pure-black Material 3 screen. The single action remains Restart all sensors, with live status showing whether the system cycle succeeded or fallback was used.
