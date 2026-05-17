package com.nbjiragale.drinkwater.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

object OemSettingsHelper {

    private const val TAG = "OemSettingsHelper"

    fun openXiaomiAutoStartSettings(context: Context) {
        tryOpenIntent(context, Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        })
    }

    fun openXiaomiAppPermissions(context: Context) {
        tryOpenIntent(context, Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", context.packageName)
        })
    }

    fun openOppoAutoLaunchSettings(context: Context) {
        tryOpenIntent(context, Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        })
    }

    fun openVivoAutoStartSettings(context: Context) {
        tryOpenIntent(context, Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        })
    }

    fun openVivoHighBackgroundBatterySettings(context: Context) {
        tryOpenIntent(context, Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
        })
    }

    fun openSamsungNeverSleepingApps(context: Context) {
        tryOpenIntent(context, Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY").apply {
            setPackage("com.samsung.android.lool")
            putExtra("activity_type", 2)
        })
    }

    private fun tryOpenIntent(context: Context, intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open OEM settings screen: ${e.message}. Falling back to app settings.")
            openStandardAppSettings(context)
        }
    }

    fun openStandardAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }
}
