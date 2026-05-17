package com.nbjiragale.drinkwater.util

import java.util.Calendar

class SmartIntervalCalculator(private val prefs: PreferencesManager) {

    fun nextIntervalMs(): Long {
        val base = prefs.intervalMs
        val count = prefs.drinkCountToday
        val goal = prefs.dailyGoal

        if (count >= goal) {
            return (base * 2L).coerceAtMost(PreferencesManager.INTERVAL_3_HOURS)
        }

        val now = Calendar.getInstance()
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val activeStart = 7 * 60
        val activeEnd = 23 * 60
        val totalMinutes = (activeEnd - activeStart).toFloat()
        val elapsed = (minuteOfDay - activeStart).coerceIn(0, activeEnd - activeStart)
        val expected = ((elapsed / totalMinutes) * goal).toInt()

        val multiplier = when {
            count >= expected + 2 -> 1.5   // well ahead — ease off
            count >= expected     -> 1.0   // on track
            count >= expected - 1 -> 0.75  // slightly behind — nudge sooner
            else                  -> 0.5   // well behind — remind more urgently
        }

        return (base * multiplier).toLong()
            .coerceAtLeast(15 * 60 * 1000L)
    }
}
