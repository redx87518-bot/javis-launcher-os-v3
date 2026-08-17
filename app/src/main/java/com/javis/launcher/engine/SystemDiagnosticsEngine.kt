package com.javis.launcher.engine

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.javis.launcher.JavisApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object SystemDiagnosticsEngine {

    data class SystemStatus(
        val batteryLevel: Int = 0,
        val isCharging: Boolean = false,
        val cpuUsage: Float = 0f,
        val memoryUsed: Long = 0,
        val memoryTotal: Long = 0,
        val storageUsed: Long = 0,
        val storageTotal: Long = 0,
        val networkType: String = "Unknown",
        val uptime: Long = 0,
        val temperatureCelsius: Float? = null
    )

    suspend fun getSystemStatus(): SystemStatus = withContext(Dispatchers.IO) {
        val context = JavisApplication.instance.applicationContext
        val status = SystemStatus()

        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            status.batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            status.isCharging = bm.isCharging
        } catch (e: Exception) {
            Log.w("SysDiagnostics", "Battery info unavailable", e)
        }

        try {
            val act = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            act.getMemoryInfo(memInfo)
            status.memoryTotal = memInfo.totalMem
            status.memoryUsed = memInfo.totalMem - memInfo.availMem
        } catch (e: Exception) {
            Log.w("SysDiagnostics", "Memory info unavailable", e)
        }

        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            status.storageTotal = totalBlocks * blockSize
            status.storageUsed = (totalBlocks - availableBlocks) * blockSize
        } catch (e: Exception) {
            Log.w("SysDiagnostics", "Storage info unavailable", e)
        }

        try {
            status.uptime = android.os.SystemClock.uptimeMillis()
        } catch (e: Exception) {
            Log.w("SysDiagnostics", "Uptime unavailable", e)
        }

        try {
            val temp = readCpuTemperature()
            status.temperatureCelsius = temp
        } catch (e: Exception) {
            Log.w("SysDiagnostics", "Temperature unavailable", e)
        }

        try {
            status.cpuUsage = readCpuUsage()
        } catch (e: Exception) {
            Log.w("SysDiagnostics", "CPU usage unavailable", e)
        }

        status
    }

    fun generateStatusReport(status: SystemStatus): String {
        val sb = StringBuilder()
        sb.append("System diagnostics, Sir. ")

        sb.append("Battery at ${status.batteryLevel} percent")
        if (status.isCharging) sb.append(" and charging")
        sb.append(". ")

        val memUsedMB = status.memoryUsed / (1024 * 1024)
        val memTotalMB = status.memoryTotal / (1024 * 1024)
        sb.append("Memory usage: ${memUsedMB} of ${memTotalMB} megabytes. ")

        val storageUsedGB = status.storageUsed / (1024 * 1024 * 1024)
        val storageTotalGB = status.storageTotal / (1024 * 1024 * 1024)
        sb.append("Storage: ${storageUsedGB} of ${storageTotalGB} gigabytes used. ")

        status.temperatureCelsius?.let {
            sb.append("CPU temperature: ${"%.1f".format(it)} degrees Celsius. ")
        }

        val uptimeHours = status.uptime / (1000 * 60 * 60)
        val uptimeMins = (status.uptime % (1000 * 60 * 60)) / (1000 * 60)
        sb.append("Uptime: ${uptimeHours} hours and ${uptimeMins} minutes. ")

        if (status.batteryLevel < 15) {
            sb.append("Warning: battery critically low. I recommend charging immediately. ")
        }

        if (status.cpuUsage > 80f) {
            sb.append("CPU load is elevated. Consider closing unused applications. ")
        }

        return sb.toString()
    }

    private fun readCpuTemperature(): Float? {
        return try {
            val tempFile = File("/sys/class/thermal/thermal_zone0/temp")
            if (tempFile.exists()) {
                tempFile.readText().trim().toFloatOrNull()?.div(1000)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readCpuUsage(): Float {
        return try {
            val reader = BufferedReader(InputStreamReader(Runtime.getRuntime().exec("top -n 1").inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("CPU")) {
                    val parts = line!!.split(" ")
                    for (part in parts) {
                        if (part.contains("%")) {
                            val usage = part.replace("%", "").toFloatOrNull()
                            if (usage != null && usage <= 100f) return usage
                        }
                    }
                }
            }
            0f
        } catch (e: Exception) {
            0f
        }
    }
}
