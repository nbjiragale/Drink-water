package com.nbjiragale.drinkwater.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nbjiragale.drinkwater.util.ActiveWindow
import com.nbjiragale.drinkwater.util.DailyQuietHours
import com.nbjiragale.drinkwater.util.PreferencesManager
import com.nbjiragale.drinkwater.util.SmartIntervalCalculator

class WaterReminderAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = PreferencesManager(context)

    companion object {
        private const val TAG = "WaterAlarmScheduler"
        const val ALARM_REQUEST_CODE = 4002
    }

    fun scheduleNextAlarm() {
        val now = System.currentTimeMillis()
        val pausedUntil = prefs.pausedUntilMs

        // Indefinite pause: cancel any pending alarm and leave the queue empty until the user resumes.
        if (pausedUntil == PreferencesManager.PAUSED_INDEFINITELY) {
            cancelScheduledAlarm()
            return
        }

        val intervalMs = SmartIntervalCalculator(prefs).nextIntervalMs()
        val proposed = now + intervalMs
        // Push the trigger past an active one-off pause window so the alarm never fires inside it.
        var trigger = if (pausedUntil > now) maxOf(proposed, pausedUntil) else proposed

        // Reconcile against both the active-hours window and the daily quiet-hours window.
        // Each clamp can push the trigger into the other's exclusion zone, so iterate until
        // the time is clear of both (converges quickly; capped to stay bounded).
        val dailyPauseEnabled = prefs.isDailyPauseEnabled
        repeat(4) {
            trigger = ActiveWindow.clamp(trigger, prefs.activeStartMinute, prefs.activeEndMinute)
            if (dailyPauseEnabled) {
                trigger = DailyQuietHours.clamp(
                    trigger, prefs.dailyPauseStartMinute, prefs.dailyPauseEndMinute
                )
            }
        }
        scheduleAlarmAt(trigger)
    }

    fun scheduleAlarmAt(triggerTimeMs: Long) {
        prefs.nextReminderTime = triggerTimeMs

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_TARGET_TRIGGER_TIME", triggerTimeMs)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            try {
                // setAlarmClock forces a temporary Doze Mode exit — highest reliability
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "Exact AlarmClock scheduled for $triggerTimeMs ms.")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException scheduling exact alarm. Falling back.", e)
                scheduleInexactFallback(triggerTimeMs, pendingIntent)
            }
        } else {
            Log.w(TAG, "Exact alarm permission missing. Using inexact fallback.")
            scheduleInexactFallback(triggerTimeMs, pendingIntent)
        }
    }

    private fun scheduleInexactFallback(triggerTimeMs: Long, pendingIntent: PendingIntent) {
        // setAndAllowWhileIdle attempts delivery during Doze but is throttled to ≥15 min
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }
        Log.d(TAG, "Inexact fallback alarm registered.")
    }

    fun cancelScheduledAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Scheduled alarm cancelled.")
        }
        prefs.nextReminderTime = 0L
    }
}
