package com.phonedoctor

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.BufferedReader
import java.io.FileReader

data class HardwareReport(
    val declaredRamGB: Float,
    val actualRamGB: Float,
    val isRamSpoofed: Boolean,
    val internalStorageGB: Float,
    val cpuInfo: String,
    val cpuCores: Int,
    val processorFromBuild: String,
    val androidVersion: String,
    val manufacturer: String,
    val model: String,
    val isModelSpoofed: Boolean
)

class HardwareDiagnostic(private val context: Context) {

    fun diagnose(): HardwareReport {
        val declaredRam = getDeclaredRamFromBuildProp()
        val actualRam = getActualRam()
        val cpuData = readCpuInfo()
        val ramDiff = Math.abs(declaredRam - actualRam)
        val isRamSpoofed = declaredRam > 0 && ramDiff > 1.5f

        return HardwareReport(
            declaredRamGB = declaredRam,
            actualRamGB = actualRam,
            isRamSpoofed = isRamSpoofed,
            internalStorageGB = getInternalStorage(),
            cpuInfo = cpuData["model_name"] ?: "Unknown",
            cpuCores = cpuData["cores"]?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors(),
            processorFromBuild = Build.HARDWARE,
            androidVersion = Build.VERSION.RELEASE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            isModelSpoofed = false
        )
    }

    private fun getActualRam(): Float {
        return try {
            val reader = BufferedReader(FileReader("/proc/meminfo"))
            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("MemTotal:")) {
                    val kb = line.trim().split("\\s+".toRegex())[1].toLong()
                    reader.close()
                    return kb / (1024f * 1024f)
                }
                line = reader.readLine()
            }
            reader.close()
            getFallbackRam()
        } catch (_: Exception) {
            getFallbackRam()
        }
    }

    private fun getFallbackRam(): Float {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024f * 1024f * 1024f)
    }

    private fun getDeclaredRamFromBuildProp(): Float {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.product.ram_size")
            val reader = BufferedReader(java.io.InputStreamReader(process.inputStream))
            val value = reader.readLine()?.trim()?.toLongOrNull() ?: 0L
            reader.close()
            if (value > 0) value / 1024f else -1f
        } catch (_: Exception) { -1f }
    }

    private fun readCpuInfo(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        return try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var coreCount = 0
            var line = reader.readLine()
            while (line != null) {
                when {
                    line.startsWith("model name") || line.startsWith("Hardware") -> {
                        result["model_name"] = line.split(":").getOrElse(1) { "" }.trim()
                    }
                    line.startsWith("processor") -> coreCount++
                }
                line = reader.readLine()
            }
            reader.close()
            result["cores"] = coreCount.toString()
            result
        } catch (_: Exception) {
            result["model_name"] = Build.HARDWARE
            result["cores"] = Runtime.getRuntime().availableProcessors().toString()
            result
        }
    }

    private fun getInternalStorage(): Float {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        return total / (1024f * 1024f * 1024f)
    }
}
