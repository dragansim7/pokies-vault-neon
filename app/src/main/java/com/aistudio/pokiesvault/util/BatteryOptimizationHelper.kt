package com.aistudio.pokiesvault.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Play-policy-compliant helper for inspecting Android battery optimization status
 * and opening the standard system App Battery / App Info settings screen.
 * Does NOT request the restricted REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission.
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            val pm = context.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } catch (_: Exception) {
            true
        }
    }

    fun openBatterySettings(context: Context): Boolean {
        // First try the app's own details page where Battery -> Unrestricted lives on Android 11-15 / ColorOS / OneUI
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val batteryList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(appDetails)
            true
        } catch (_: Exception) {
            try {
                context.startActivity(batteryList)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
