package com.hyouka.sensorreset

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

data class SystemSensorResetResult(
    val success: Boolean,
    val detail: String
)

class SystemSensorResetter(
    private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            context.packageName,
            SystemSensorResetUserService::class.java.name
        )
    )
        .tag("sensor-reset-system-service")
        .version(SERVICE_VERSION)
        .daemon(false)
        .processNameSuffix("sensor-reset")

    private var permissionRequestPending = false
    private var callback: ((SystemSensorResetResult) -> Unit)? = null
    private lateinit var serviceConnection: ServiceConnection

    init {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val pendingCallback = callback ?: return
                callback = null

                Thread {
                    val result = runCatching {
                        ISystemSensorResetService.Stub.asInterface(service)
                            .cycleSensorService(context.packageName)
                    }.fold(
                        onSuccess = { raw ->
                            SystemSensorResetResult(raw.startsWith("OK|"), raw)
                        },
                        onFailure = { throwable ->
                            SystemSensorResetResult(
                                false,
                                "FAIL|stage=binder|error=" +
                                    throwable.javaClass.simpleName + ":" +
                                    (throwable.message ?: "unknown")
                            )
                        }
                    )

                    mainHandler.post {
                        runCatching {
                            Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
                        }
                        pendingCallback(result)
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (callback != null) {
                    finish(SystemSensorResetResult(false, "FAIL|stage=service_disconnect"))
                }
            }
        }
    }

    fun reset(onComplete: (SystemSensorResetResult) -> Unit) {
        if (callback != null) {
            onComplete(SystemSensorResetResult(false, "FAIL|stage=busy"))
            return
        }

        callback = onComplete

        runCatching {
            if (!Shizuku.pingBinder()) {
                finish(SystemSensorResetResult(false, "FAIL|stage=shizuku_unavailable"))
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                runBindAndReset()
                return
            }

            if (permissionRequestPending) return

            permissionRequestPending = true
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure { throwable ->
            permissionRequestPending = false
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
            finish(
                SystemSensorResetResult(
                    false,
                    "FAIL|stage=permission|error=" +
                        throwable.javaClass.simpleName + ":" +
                        (throwable.message ?: "unknown")
                )
            )
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            if (requestCode != REQUEST_CODE || !permissionRequestPending) {
                return@OnRequestPermissionResultListener
            }
            permissionRequestPending = false
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                runBindAndReset()
            } else {
                finish(SystemSensorResetResult(false, "FAIL|stage=permission_denied"))
            }
        }

    private fun runBindAndReset() {
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        }.onFailure { throwable ->
            finish(
                SystemSensorResetResult(
                    false,
                    "FAIL|stage=bind|error=" +
                        throwable.javaClass.simpleName + ":" +
                        (throwable.message ?: "unknown")
                )
            )
        }
    }

    private fun finish(result: SystemSensorResetResult) {
        mainHandler.post {
            val pending = callback ?: return@post
            callback = null
            pending(result)
        }
    }

    companion object {
        private const val REQUEST_CODE = 4107
        private const val SERVICE_VERSION = 3
    }
}

fun systemSensorResetCommands(packageName: String): List<List<String>> {
    return listOf(
        listOf("dumpsys", "sensorservice", "restrict", packageName),
        listOf("dumpsys", "sensorservice", "enable")
    )
}

fun systemSensorHalRestartCommands(): List<List<String>> {
    return listOf(
        listOf("setprop", "ctl.interface_restart", "android.hardware.sensors@2.1::ISensors/default"),
        listOf("setprop", "ctl.interface_restart", "android.hardware.sensors@2.0::ISensors/default"),
        listOf("setprop", "ctl.interface_restart", "android.hardware.sensors@1.0::ISensors/default"),
        listOf("setprop", "ctl.interface_restart", "android.hardware.sensors.ISensors/default"),
        listOf("setprop", "ctl.restart", "sensors.qti")
    )
}

