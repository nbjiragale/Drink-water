package com.nbjiragale.drinkwater.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "drink_water_prefs"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_INTERVAL_MS = "interval_ms"
        private const val KEY_NEXT_REMINDER_TIME = "next_reminder_time"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"

        val INTERVAL_30_MIN = 30 * 60 * 1000L
        val INTERVAL_1_HOUR = 60 * 60 * 1000L
        val INTERVAL_2_HOURS = 2 * 60 * 60 * 1000L
        val INTERVAL_3_HOURS = 3 * 60 * 60 * 1000L
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
}
