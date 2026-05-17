package com.nbjiragale.drinkwater.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nbjiragale.drinkwater.util.PreferencesManager

/**
 * Restores the scheduling queue after system events that would otherwise
 * silently cancel registered AlarmManager intents:
 *   - Device reboot (BOOT_COMPLETED)
 *   - App update (MY_PACKAGE_REPLACED)
 *   - Clock / timezone change (TIME_SET, TIMEZONE_CHANGED)
 *
 * On Vivo, Xiaomi, and Oppo devices this receiver will only fire if the user
 * has granted "Autostart" privileges in the OEM battery settings.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        Log.d(TAG, "Received system broadcast: ${intent.action}")

        val prefs = PreferencesManager(context)
        if (!prefs.isReminderEnabled) {
            Log.d(TAG, "Reminders disabled; skipping reschedule.")
            return
        }

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                try {
                    val scheduler = WaterReminderAlarmScheduler(context)
                    scheduler.cancelScheduledAlarm()
                    scheduler.scheduleNextAlarm()
                    Log.i(TAG, "Self-healing: alarm queue restored after '${intent.action}'.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore scheduling queue.", e)
                }
            }
        }
    }
}
