package com.nbjiragale.drinkwater.util

import android.Manifest
import android.app.AlarmManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

class BackgroundRestrictionDetector(private val context: Context) {

    fun isExactAlarmPermissionGranted(): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getAppStandbyBucket(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usageStatsManager.appStandbyBucket
        } else {
            0
        }
    }

    fun isInRestrictedBucket(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // STANDBY_BUCKET_RESTRICTED = 45
            return getAppStandbyBucket() == UsageStatsManager.STANDBY_BUCKET_RESTRICTED
        }
        return false
    }

    fun isAppRestricted(): Boolean {
        return !isExactAlarmPermissionGranted()
            || !isIgnoringBatteryOptimizations()
            || !isNotificationPermissionGranted()
            || isInRestrictedBucket()
    }

    fun getDetectedOem(): OemType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemType.XIAOMI
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> OemType.OPPO
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> OemType.VIVO
            manufacturer.contains("samsung") -> OemType.SAMSUNG
            else -> OemType.GENERIC
        }
    }

    enum class OemType {
        XIAOMI, OPPO, VIVO, SAMSUNG, GENERIC
    }
}
