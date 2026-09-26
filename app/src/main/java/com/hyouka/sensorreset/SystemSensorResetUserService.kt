package com.hyouka.sensorreset

import android.content.Intent
import android.os.IBinder
import rikka.shizuku.Shizuku

class SystemSensorResetUserService : Shizuku.UserService() {
    private val binder = object : ISystemSensorResetService.Stub() {
        override fun cycleSensorService(packageName: String): String {
            require(PACKAGE_NAME_PATTERN.matches(packageName)) { "Invalid package name" }

            val commands = systemSensorResetCommands(packageName)
            val restrict = runCommand(commands[0])
            if (restrict.exitCode != 0) {
                return "FAIL|stage=restrict|exit=" + restrict.exitCode +
                    "|output=" + sanitize(restrict.output)
            }

            val enable = runCommand(commands[1])
            if (enable.exitCode != 0) {
                val retry = runCommand(commands[1])
                if (retry.exitCode != 0) {
                    return "FAIL|stage=enable|exit=" + enable.exitCode +
                        "|output=" + sanitize(enable.output) +
                        "|retry=" + sanitize(retry.output)
                }
                return "OK|uid=" + android.os.Process.myUid() +
                    "|restrict=" + restrict.exitCode +
                    "|enable_retry=" + retry.exitCode
            }

            return "OK|uid=" + android.os.Process.myUid() +
                "|restrict=" + restrict.exitCode +
                "|enable=" + enable.exitCode
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun runCommand(command: List<String>): CommandResult {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
                .trim()
                .take(MAX_OUTPUT)
            val exitCode = process.waitFor()
            CommandResult(exitCode, output)
        }.getOrElse { throwable ->
            CommandResult(
                -1,
                throwable.javaClass.simpleName + ":" + (throwable.message ?: "unknown")
            )
        }
    }

    private fun sanitize(output: String): String {
        return output.replace("\n", " ").replace("|", "/").take(MAX_OUTPUT)
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )

    companion object {
        private const val MAX_OUTPUT = 240
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_\\.]+")
    }
}
