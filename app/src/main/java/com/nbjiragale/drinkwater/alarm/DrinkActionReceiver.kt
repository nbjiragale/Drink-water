package com.nbjiragale.drinkwater.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.nbjiragale.drinkwater.util.PreferencesManager

/**
 * Handles taps on the "I drank" and "Snooze 10 min" action buttons attached to the reminder
 * notification, so the user does not have to open the app to log a drink or postpone the alert.
 */
class DrinkActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DrinkActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationService.ACTION_DRINK_LOGGED -> handleDrinkLogged(context)
            NotificationService.ACTION_SNOOZE -> handleSnooze(context)
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                return
            }
        }
        // Dismiss the reminder notification regardless of which action fired.
        NotificationManagerCompat.from(context).cancel(NotificationService.NOTIFICATION_ID)
    }

    private fun handleDrinkLogged(context: Context) {
        val prefs = PreferencesManager(context)
        val updated = prefs.incrementDrinkCount()
        Log.d(TAG, "Drink logged from notification. New count: $updated")
        if (prefs.isReminderEnabled) {
            // Recompute the next interval (SmartIntervalCalculator) now that progress changed.
            WaterReminderAlarmScheduler(context).scheduleNextAlarm()
        }
    }

    private fun handleSnooze(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.isReminderEnabled) {
            Log.d(TAG, "Snooze tapped but reminders are disabled; ignoring.")
            return
        }
        val scheduler = WaterReminderAlarmScheduler(context)
        scheduler.cancelScheduledAlarm()
        scheduler.scheduleAlarmAt(System.currentTimeMillis() + NotificationService.SNOOZE_DURATION_MS)
        Log.d(TAG, "Reminder snoozed by ${NotificationService.SNOOZE_DURATION_MS / 60_000} min.")
    }
}
