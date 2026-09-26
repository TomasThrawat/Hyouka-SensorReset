
package com.hyouka.sensorreset

class SystemSensorResetUserService : ISystemSensorResetService.Stub() {
    override fun cycleSensorService(packageName: String): String {
        require(PACKAGE_NAME_PATTERN.matches(packageName)) { "Invalid package name" }

        val restrict = runCommand(listOf("dumpsys", "sensorservice", "restrict", packageName))
        if (restrict.exitCode != 0) {
            return "FAIL|stage=restrict|exit=" + restrict.exitCode + "|output=" + sanitize(restrict.output)
        }

        val before = readSensorInitServices()

        val interfaceResults = SENSOR_HAL_INTERFACES.map { iface ->
            iface to runCommand(listOf("setprop", "ctl.interface_restart", iface))
        }

        val serviceResults = before.values
            .filter { it.state == "running" || it.state == "restarting" }
            .filter { it.name != "sensorservice" }
            .map { it.name }
            .distinct()
            .map { name -> name to runCommand(listOf("setprop", "ctl.restart", name)) }

        val changed = waitForSensorServiceChanges(before)
        val acceptedInterfaces = interfaceResults.count { it.second.exitCode == 0 }
        val acceptedServices = serviceResults.count { it.second.exitCode == 0 }

        val enable = runCommand(listOf("dumpsys", "sensorservice", "enable"))
        if (enable.exitCode != 0) {
            return "FAIL|stage=enable|verified=" + changed.joinToString(",") +
                "|interfaceRequests=" + acceptedInterfaces +
                "|serviceRequests=" + acceptedServices +
                "|output=" + sanitize(enable.output)
        }

        if (changed.isNotEmpty()) {
            return "OK|halRestartVerified=" + changed.joinToString(",") +
                "|interfaceRequests=" + acceptedInterfaces +
                "|serviceRequests=" + acceptedServices +
                "|sensorservice=enabled"
        }

        return "FAIL|stage=hal_restart_unverified|interfaceRequests=" +
            acceptedInterfaces + "|serviceRequests=" + acceptedServices +
            "|sensorservice=enabled"
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun readSensorInitServices(): Map<String, InitServiceState> {
        val result = runCommand(listOf("getprop"))
        if (result.exitCode != 0) return emptyMap()

        val props = result.output.lineSequence()
            .mapNotNull { line ->
                INIT_PROPERTY_PATTERN.find(line)?.let { it.groupValues[1] to it.groupValues[2] }
            }
            .toMap()

        return props
            .filterKeys { it.startsWith("init.svc.") }
            .mapNotNull { (property, state) ->
                val name = property.removePrefix("init.svc.")
                if (!name.contains("sensor", ignoreCase = true) || name == "sensorservice") {
                    null
                } else {
                    name to InitServiceState(
                        name,
                        state,
                        props["init.svc_debug_pid.$name"]?.toIntOrNull()
                    )
                }
            }
            .toMap()
    }

    private fun waitForSensorServiceChanges(before: Map<String, InitServiceState>): List<String> {
        val deadline = System.currentTimeMillis() + RESTART_VERIFY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val after = readSensorInitServices()
            val changed = after.values.filter { current ->
                val previous = before[current.name]
                when {
                    previous == null -> current.state == "running" && current.pid != null
                    previous.pid != null && current.pid != null ->
                        current.state == "running" && current.pid != previous.pid
                    else -> previous.state != current.state && current.state == "running"
                }
            }.map { it.name }.sorted()

            if (changed.isNotEmpty()) return changed
            Thread.sleep(RESTART_VERIFY_POLL_MS)
        }
        return emptyList()
    }

    private fun runCommand(command: List<String>): CommandResult {
        return runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim().take(MAX_OUTPUT)
            val exitCode = process.waitFor()
            CommandResult(exitCode, output)
        }.getOrElse { throwable ->
            CommandResult(-1, throwable.javaClass.simpleName + ":" + (throwable.message ?: "unknown"))
        }
    }

    private fun sanitize(output: String): String {
        return output.replace("
", " ").replace("|", "/").take(MAX_OUTPUT)
    }

    private data class CommandResult(val exitCode: Int, val output: String)
    private data class InitServiceState(val name: String, val state: String, val pid: Int?)

    companion object {
        private const val MAX_OUTPUT = 240
        private const val RESTART_VERIFY_TIMEOUT_MS = 2_500L
        private const val RESTART_VERIFY_POLL_MS = 100L
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_\.]+")
        private val INIT_PROPERTY_PATTERN = Regex("\[([^]]+)\]\s*:\s*\[([^]]*)\]")
        private val SENSOR_HAL_INTERFACES = listOf(
            "android.hardware.sensors@2.1::ISensors/default",
            "android.hardware.sensors@2.0::ISensors/default",
            "android.hardware.sensors@1.0::ISensors/default",
            "android.hardware.sensors.ISensors/default"
        )
    }
}
