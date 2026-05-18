package com.nbjiragale.drinkwater.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.nbjiragale.drinkwater.util.PreferencesManager

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKELOCK_TAG = "DrinkWater:AlarmProcessorWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 15_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received. Acquiring WakeLock.")

        val prefs = PreferencesManager(context)
        if (!prefs.isReminderEnabled) {
            Log.d(TAG, "Reminders are disabled; skipping notification.")
            return
        }

        // Safety net: an alarm scheduled before the user paused (or while a pause was about to
        // start) may still fire mid-pause. Drop the notification but reschedule past the pause.
        if (prefs.isPausedNow()) {
            Log.d(TAG, "Reminders are paused; skipping notification and rescheduling.")
            WaterReminderAlarmScheduler(context).scheduleNextAlarm()
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        // Bounded acquire prevents a leaked lock if the finally block somehow fails
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS)

        try {
            NotificationService.showReminderNotification(context)

            // Self-healing loop: each delivery immediately schedules the next alarm
            WaterReminderAlarmScheduler(context).scheduleNextAlarm()
            Log.d(TAG, "Notification shown; next alarm scheduled.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during alarm processing.", e)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock released.")
            }
        }
    }
}
