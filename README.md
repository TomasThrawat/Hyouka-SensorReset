# Hyouka-SensorReset

Native Kotlin Android utility for reinitializing the app's motion-sensor session.

## What it does

- Reads Gyroscope, Accelerometer, Magnetometer, and Rotation Vector sensors.
- Shows live X/Y/Z values and sensor availability.
- "Reset sensors" unregisters the active listeners, clears cached values, and registers the supported sensors again.
- Uses a pure-black Material 3 surface with Android 12+ dynamic color accents.
- Pauses listeners when the activity is not resumed.

## Important limitation

This app does **not** claim to recalibrate the physical sensor or reset the phone's sensor driver. Android's public sensor APIs expose listener registration/unregistration and sensor event delivery; hardware calibration remains outside a normal app's control.

## Build

The GitHub Actions workflow uses JDK 17 and Gradle 8.13 with Android Gradle Plugin 8.13.2.

Minimum Android API: 26  
Target Android API: 36
