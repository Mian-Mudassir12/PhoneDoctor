package com.phonedoctor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

data class BatteryReport(
    val percentage: Int,
    val health: String,
    val cycleCount: Int,
    val chargeFull: Int,
    val chargeFullDesign: Int,
    val degradationPercent: Float,
    val isDataSpoofed: Boolean,
    val temperature: Float,
    val voltage: Int
)

class BatteryDiagnostic(private val context: Context) {

    fun diagnose(): BatteryReport {
        val apiData = getBatteryFromAPI()
        val kernelData = getBatteryFromKernel()

        val chargeFull = kernelData["charge_full"] ?: apiData.chargeFull
        val chargeFullDesign = kernelData["charge_full_design"] ?: 1

        val degradation = if (chargeFullDesign > 0) {
            ((chargeFullDesign - chargeFull).toFloat() / chargeFullDesign) * 100
        } else 0f

        val isSpoofed = apiData.health == "Good" && degradation > 40f

        return BatteryReport(
            percentage = apiData.percentage,
            health = apiData.health,
            cycleCount = kernelData["cycle_count"] ?: -1,
            chargeFull = chargeFull,
            chargeFullDesign = kernelData["charge_full_design"] ?: 0,
            degradationPercent = if (degradation < 0) 0f else degradation,
            isDataSpoofed = isSpoofed,
            temperature = apiData.temperature,
            voltage = apiData.voltage
        )
    }

    private fun getBatteryFromAPI(): BatteryApiData {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeFull = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val healthStr = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
            else -> "Unknown"
        }
        return BatteryApiData(
            percentage = if (scale > 0) (level * 100 / scale) else 0,
            health = healthStr,
            chargeFull = chargeFull / 1000,
            temperature = temp,
            voltage = voltage
        )
    }

    private fun getBatteryFromKernel(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val kernelPaths = mapOf(
            "cycle_count" to listOf(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/bms/cycle_count"
            ),
            "charge_full" to listOf(
                "/sys/class/power_supply/battery/charge_full",
                "/sys/class/power_supply/bms/charge_full"
            ),
            "charge_full_design" to listOf(
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design"
            ),
            "capacity" to listOf("/sys/class/power_supply/battery/capacity")
        )
        for ((key, paths) in kernelPaths) {
            for (path in paths) {
                try {
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        val value = file.readText().trim().toIntOrNull()
                        if (value != null) {
                            result[key] = if (key.contains("charge")) value / 1000 else value
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return result
    }

    data class BatteryApiData(
        val percentage: Int,
        val health: String,
        val chargeFull: Int,
        val temperature: Float,
        val voltage: Int
    )
}
