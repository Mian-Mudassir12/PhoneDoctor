package com.phonedoctor

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import java.io.File

data class SecurityReport(
    val isRooted: Boolean,
    val rootMethod: String,
    val isBootloaderUnlocked: Boolean,
    val hasCustomRom: Boolean,
    val activeMdmApps: List<String>,
    val suspiciousPackages: List<String>,
    val frpBypassDetected: Boolean,
    val selinuxMode: String
)

class SecurityDiagnostic(private val context: Context) {

    private val rootPackages = listOf(
        "com.topjohnwu.magisk", "com.kingroot.kinguser",
        "com.noshufou.android.su", "com.koushikdutta.superuser",
        "eu.chainfire.supersu", "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot", "com.saurik.substrate",
        "de.robv.android.xposed.installer"
    )

    private val patchPackages = listOf(
        "com.mme.imeichange", "com.imei.changer",
        "net.hubalek.android.apps.imeichangerpro", "com.imei.patcher"
    )

    fun diagnose(): SecurityReport {
        val rootResult = checkRoot()
        return SecurityReport(
            isRooted = rootResult.first,
            rootMethod = rootResult.second,
            isBootloaderUnlocked = checkBootloaderStatus(),
            hasCustomRom = checkCustomRom(),
            activeMdmApps = getActiveMdmAdmins(),
            suspiciousPackages = findSuspiciousPackages(),
            frpBypassDetected = detectFrpBypass(),
            selinuxMode = getSELinuxMode()
        )
    }

    private fun checkRoot(): Pair<Boolean, String> {
        val rootBinaries = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/su", "/data/local/xbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su"
        )
        for (path in rootBinaries) {
            if (File(path).exists()) return Pair(true, "SU Binary: $path")
        }
        if (File("/sbin/.magisk").exists() || File("/dev/.magisk").exists()) {
            return Pair(true, "Magisk detected")
        }
        for (pkg in rootPackages) {
            if (isPackageInstalled(pkg)) return Pair(true, "Root app: $pkg")
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            reader.close()
            process.destroy()
            if (output.contains("uid=0")) Pair(true, "SU command works")
            else Pair(false, "None")
        } catch (_: Exception) { Pair(false, "None") }
    }

    private fun checkBootloaderStatus(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.boot.verifiedbootstate")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val value = reader.readLine()?.trim() ?: ""
            reader.close()
            process.destroy()
            value == "orange" || value == "yellow"
        } catch (_: Exception) { false }
    }

    private fun checkCustomRom(): Boolean {
        val buildTags = android.os.Build.TAGS ?: ""
        val buildType = android.os.Build.TYPE ?: ""
        return buildTags.contains("test-keys") || buildTags.contains("dev-keys") ||
               buildType == "userdebug" || buildType == "eng"
    }

    private fun getActiveMdmAdmins(): List<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.activeAdmins?.map { it.packageName } ?: emptyList()
    }

    private fun findSuspiciousPackages(): List<String> {
        return (patchPackages + rootPackages).filter { isPackageInstalled(it) }
    }

    private fun detectFrpBypass(): Boolean {
        val frpPackages = listOf("com.frpfile.bypass", "com.frpbypass.fastboot")
        return frpPackages.any { isPackageInstalled(it) }
    }

    private fun getSELinuxMode(): String {
        return try {
            val file = File("/sys/fs/selinux/enforce")
            when {
                !file.exists() -> "Disabled"
                file.readText().trim() == "1" -> "Enforcing ✅"
                else -> "Permissive ⚠️"
            }
        } catch (_: Exception) { "Unknown" }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
    }
}
