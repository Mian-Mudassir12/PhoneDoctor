package com.phonedoctor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        findViewById<Button>(R.id.btnDiagnose).setOnClickListener {
            runDiagnostics()
        }

        findViewById<Button>(R.id.btnScreenTest).setOnClickListener {
            startActivity(Intent(this, ScreenDiagnosticActivity::class.java))
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), PERMISSION_CODE)
        }
    }

    private fun runDiagnostics() {
        val resultView = findViewById<TextView>(R.id.tvResults)
        resultView.text = "⏳ Scanning... Please wait"

        CoroutineScope(Dispatchers.IO).launch {
            val battery = BatteryDiagnostic(this@MainActivity).diagnose()
            val hardware = HardwareDiagnostic(this@MainActivity).diagnose()
            val security = SecurityDiagnostic(this@MainActivity).diagnose()

            val result = buildString {
                appendLine("════════════════════════")
                appendLine("🔋  BATTERY REPORT")
                appendLine("════════════════════════")
                appendLine("Health     : ${battery.health}")
                appendLine("Charge     : ${battery.percentage}%")
                appendLine("Cycle Count: ${if (battery.cycleCount == -1) "N/A" else battery.cycleCount.toString()}")
                appendLine("Capacity   : ${battery.chargeFull}mAh / ${battery.chargeFullDesign}mAh")
                appendLine("Degradation: ${"%.1f".format(battery.degradationPercent)}%")
                appendLine("Temp       : ${battery.temperature}°C")
                appendLine("Spoofed?   : ${if (battery.isDataSpoofed) "⚠️ YES - FRAUD!" else "✅ No"}")
                appendLine()
                appendLine("════════════════════════")
                appendLine("📱  HARDWARE REPORT")
                appendLine("════════════════════════")
                appendLine("RAM Actual : ${"%.1f".format(hardware.actualRamGB)} GB")
                appendLine("RAM Declare: ${"%.1f".format(hardware.declaredRamGB)} GB")
                appendLine("RAM Spoofed: ${if (hardware.isRamSpoofed) "⚠️ YES - FRAUD!" else "✅ No"}")
                appendLine("Storage    : ${"%.0f".format(hardware.internalStorageGB)} GB")
                appendLine("CPU        : ${hardware.cpuInfo}")
                appendLine("CPU Cores  : ${hardware.cpuCores}")
                appendLine("Android    : ${hardware.androidVersion}")
                appendLine("Model      : ${hardware.manufacturer} ${hardware.model}")
                appendLine()
                appendLine("════════════════════════")
                appendLine("🔒  SECURITY REPORT")
                appendLine("════════════════════════")
                appendLine("Rooted     : ${if (security.isRooted) "⚠️ YES" else "✅ No"}")
                if (security.isRooted) appendLine("Root Method: ${security.rootMethod}")
                appendLine("Bootloader : ${if (security.isBootloaderUnlocked) "⚠️ UNLOCKED" else "✅ Locked"}")
                appendLine("Custom ROM : ${if (security.hasCustomRom) "⚠️ YES" else "✅ No"}")
                appendLine("SELinux    : ${security.selinuxMode}")
                appendLine("FRP Bypass : ${if (security.frpBypassDetected) "⚠️ DETECTED!" else "✅ Clean"}")
                if (security.activeMdmApps.isNotEmpty()) {
                    appendLine("⚠️ MDM Apps: ${security.activeMdmApps.joinToString()}")
                } else {
                    appendLine("MDM Apps   : ✅ None")
                }
                if (security.suspiciousPackages.isNotEmpty()) {
                    appendLine("⚠️ Suspicious: ${security.suspiciousPackages.joinToString()}")
                }
            }

            withContext(Dispatchers.Main) {
                resultView.text = result
            }
        }
    }
}
