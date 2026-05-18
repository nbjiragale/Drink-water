package com.nbjiragale.drinkwater.util

import java.util.Calendar

object ActiveWindow {

    /**
     * Pushes a proposed alarm time into the daily [startMinute, endMinute) window.
     * - Inside the window → returned unchanged.
     * - Before the window opens today → today's opening time.
     * - At/after the window closes today → tomorrow's opening time.
     * Handles only same-day windows (endMinute > startMinute).
     */
    fun clamp(triggerTimeMs: Long, startMinute: Int, endMinute: Int): Long {
        if (endMinute <= startMinute) return triggerTimeMs // invalid config — no-op

        val cal = Calendar.getInstance().apply { timeInMillis = triggerTimeMs }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        if (minuteOfDay in startMinute until endMinute) return triggerTimeMs

        val open = Calendar.getInstance().apply {
            timeInMillis = triggerTimeMs
            set(Calendar.HOUR_OF_DAY, startMinute / 60)
            set(Calendar.MINUTE, startMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (minuteOfDay >= endMinute) open.add(Calendar.DAY_OF_YEAR, 1)
        return open.timeInMillis
    }
}
