package com.nbjiragale.drinkwater.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "drink_water_prefs"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_INTERVAL_MS = "interval_ms"
        private const val KEY_NEXT_REMINDER_TIME = "next_reminder_time"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_DRINK_COUNT = "drink_count_today"
        private const val KEY_DRINK_DATE = "drink_date"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_ACTIVE_START_MIN = "active_start_minute"
        private const val KEY_ACTIVE_END_MIN = "active_end_minute"
        private const val KEY_PAUSED_UNTIL_MS = "paused_until_ms"

        val INTERVAL_30_MIN = 30 * 60 * 1000L
        val INTERVAL_1_HOUR = 60 * 60 * 1000L
        val INTERVAL_2_HOURS = 2 * 60 * 60 * 1000L
        val INTERVAL_3_HOURS = 3 * 60 * 60 * 1000L
        const val DEFAULT_DAILY_GOAL = 8
        const val DEFAULT_ACTIVE_START_MIN = 7 * 60   // 07:00
        const val DEFAULT_ACTIVE_END_MIN = 22 * 60    // 22:00

        /** Sentinel for "paused until the user manually resumes" — no scheduled wake-up time. */
        const val PAUSED_INDEFINITELY = Long.MAX_VALUE
    }

    var isReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()

    var intervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL_MS, INTERVAL_2_HOURS)
        set(value) = prefs.edit().putLong(KEY_INTERVAL_MS, value).apply()

    var nextReminderTime: Long
        get() = prefs.getLong(KEY_NEXT_REMINDER_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT_REMINDER_TIME, value).apply()

    var isOnboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var dailyGoal: Int
        get() = prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)
        set(value) = prefs.edit().putInt(KEY_DAILY_GOAL, value).apply()

    var activeStartMinute: Int
        get() = prefs.getInt(KEY_ACTIVE_START_MIN, DEFAULT_ACTIVE_START_MIN)
        set(value) = prefs.edit().putInt(KEY_ACTIVE_START_MIN, value).apply()

    var activeEndMinute: Int
        get() = prefs.getInt(KEY_ACTIVE_END_MIN, DEFAULT_ACTIVE_END_MIN)
        set(value) = prefs.edit().putInt(KEY_ACTIVE_END_MIN, value).apply()

    /**
     * Wall-clock millis until which reminder notifications are suppressed.
     *  - `0L` (default)           → not paused.
     *  - any future timestamp     → reminders are paused until this moment.
     *  - [PAUSED_INDEFINITELY]    → paused until the user manually resumes (no alarm scheduled).
     */
    var pausedUntilMs: Long
        get() = prefs.getLong(KEY_PAUSED_UNTIL_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_PAUSED_UNTIL_MS, value).apply()

    /** True when [pausedUntilMs] is still in the future (or set to [PAUSED_INDEFINITELY]). */
    fun isPausedNow(nowMs: Long = System.currentTimeMillis()): Boolean = pausedUntilMs > nowMs

    var drinkCountToday: Int
        get() {
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val savedDate = prefs.getString(KEY_DRINK_DATE, "")
            return if (savedDate == today) {
                prefs.getInt(KEY_DRINK_COUNT, 0)
            } else {
                prefs.edit()
                    .putString(KEY_DRINK_DATE, today)
                    .putInt(KEY_DRINK_COUNT, 0)
                    .apply()
                0
            }
        }
        set(value) {
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            prefs.edit()
                .putString(KEY_DRINK_DATE, today)
                .putInt(KEY_DRINK_COUNT, value)
                .apply()
        }

    fun incrementDrinkCount(): Int {
        val updated = drinkCountToday + 1
        drinkCountToday = updated
        return updated
    }
}
