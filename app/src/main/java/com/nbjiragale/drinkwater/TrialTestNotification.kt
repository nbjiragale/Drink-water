package com.nbjiragale.drinkwater

// TRIAL FEATURE — delete this file and the single call site in MainActivity.onCreate to remove.
// Adds a floating "TEST" button at the bottom-right that fires an immediate notification.

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import com.nbjiragale.drinkwater.alarm.NotificationService
import com.nbjiragale.drinkwater.databinding.ActivityMainBinding

fun installTrialTestNotificationTrigger(
    activity: MainActivity,
    @Suppress("UNUSED_PARAMETER") binding: ActivityMainBinding
) {
    val density = activity.resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()

    val button = Button(activity).apply {
        text = "TEST"
        textSize = 12f
        setTextColor(Color.WHITE)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(Color.parseColor("#CC2196F3"))
        }
        stateListAnimator = null
        elevation = dp(6).toFloat()
        setOnClickListener {
            NotificationService.showReminderNotification(activity)
            Toast.makeText(activity, "Test notification fired", Toast.LENGTH_SHORT).show()
        }
    }

    val overlay = FrameLayout(activity)
    val buttonParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        setMargins(0, 0, dp(20), dp(28))
    }
    overlay.addView(button, buttonParams)

    val overlayParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    activity.addContentView(overlay, overlayParams)
}
