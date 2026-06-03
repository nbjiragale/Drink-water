package com.nbjiragale.drinkwater.util

import java.util.Calendar

/**
 * Customizable daily "quiet hours" window during which reminders are muted every day.
 * Unlike a one-off pause, this repeats on a daily schedule. The window may wrap past
 * midnight (e.g. 22:00 → 07:00) when [startMinute] > [endMinute].
 */
object DailyQuietHours {

    /** True when the wall-clock minute-of-day of [timeMs] falls inside the daily window. */
    fun contains(timeMs: Long, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false // zero-length window — never muted
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            // Wraps past midnight: [start, 24:00) ∪ [00:00, end)
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    /**
     * If [triggerTimeMs] falls inside the daily quiet window, returns the moment the window
     * next ends; otherwise returns [triggerTimeMs] unchanged. Disabled when start == end.
     */
    fun clamp(triggerTimeMs: Long, startMinute: Int, endMinute: Int): Long {
        if (startMinute == endMinute) return triggerTimeMs
        if (!contains(triggerTimeMs, startMinute, endMinute)) return triggerTimeMs

        val end = Calendar.getInstance().apply {
            timeInMillis = triggerTimeMs
            set(Calendar.HOUR_OF_DAY, endMinute / 60)
            set(Calendar.MINUTE, endMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // The window's end time may resolve to earlier in the same calendar day
        // (e.g. an overnight window) — in that case it closes tomorrow.
        if (end.timeInMillis <= triggerTimeMs) end.add(Calendar.DAY_OF_YEAR, 1)
        return end.timeInMillis
    }
}
