package com.nbjiragale.drinkwater

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.nbjiragale.drinkwater.alarm.NotificationService
import com.nbjiragale.drinkwater.databinding.ActivityFullScreenReminderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen reminder that appears on the lock screen when the alarm fires.
 *
 * Behaviour:
 *  - Screen-off / locked  → this activity is launched as a full-screen intent
 *  - Screen-on            → the notification shows as a heads-up banner instead
 *  - Auto-dismisses after 30 seconds
 *  - Cancels the status-bar notification on dismiss
 */
class DrinkReminderFullScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenReminderBinding
    private val handler = Handler(Looper.getMainLooper())
    private var remainingSeconds = 30

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1_000)
        }
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            remainingSeconds--
            binding.tvAutoCount.text = getString(R.string.closing_in, remainingSeconds)
            if (remainingSeconds <= 0) dismissReminder() else handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and wake the display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        binding = ActivityFullScreenReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvAutoCount.text = getString(R.string.closing_in, remainingSeconds)
        binding.btnDismiss.setOnClickListener { dismissReminder() }

        updateClock()
        handler.postDelayed(clockRunnable, 1_000)
        handler.postDelayed(countdownRunnable, 1_000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(countdownRunnable)
    }

    @Deprecated("Handled via onBackPressedDispatcher")
    override fun onBackPressed() {
        dismissReminder()
    }

    private fun updateClock() {
        val now = Date()
        binding.tvCurrentTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        binding.tvCurrentDate.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
    }

    private fun dismissReminder() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NotificationService.NOTIFICATION_ID)
        finish()
    }
}
