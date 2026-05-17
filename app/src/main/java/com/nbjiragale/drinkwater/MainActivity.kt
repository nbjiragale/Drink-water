package com.nbjiragale.drinkwater

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nbjiragale.drinkwater.alarm.NotificationService
import com.nbjiragale.drinkwater.alarm.WaterReminderAlarmScheduler
import com.nbjiragale.drinkwater.databinding.ActivityMainBinding
import com.nbjiragale.drinkwater.util.BackgroundRestrictionDetector
import com.nbjiragale.drinkwater.util.PreferencesManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var detector: BackgroundRestrictionDetector
    private lateinit var scheduler: WaterReminderAlarmScheduler

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateUI() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        detector = BackgroundRestrictionDetector(this)
        scheduler = WaterReminderAlarmScheduler(this)

        NotificationService.createNotificationChannel(this)

        if (!prefs.isOnboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setupIntervalChips()
        setupReminderToggle()
        setupProgressCard()
        setupFixPermissionsButton()
        setupBatteryOptButton()
        setupFsiButton()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        startCountdown()
    }

    override fun onPause() {
        super.onPause()
        stopCountdown()
    }

    private fun setupIntervalChips() {
        val currentInterval = prefs.intervalMs
        binding.chip30min.isChecked = currentInterval == PreferencesManager.INTERVAL_30_MIN
        binding.chip1hour.isChecked = currentInterval == PreferencesManager.INTERVAL_1_HOUR
        binding.chip2hours.isChecked = currentInterval == PreferencesManager.INTERVAL_2_HOURS
        binding.chip3hours.isChecked = currentInterval == PreferencesManager.INTERVAL_3_HOURS

        binding.chipGroupInterval.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val newInterval = when (checkedIds.first()) {
                R.id.chip30min -> PreferencesManager.INTERVAL_30_MIN
                R.id.chip1hour -> PreferencesManager.INTERVAL_1_HOUR
                R.id.chip2hours -> PreferencesManager.INTERVAL_2_HOURS
                R.id.chip3hours -> PreferencesManager.INTERVAL_3_HOURS
                else -> return@setOnCheckedStateChangeListener
            }
            if (newInterval != prefs.intervalMs) {
                prefs.intervalMs = newInterval
                if (prefs.isReminderEnabled) {
                    scheduler.cancelScheduledAlarm()
                    scheduler.scheduleNextAlarm()
                }
                updateCountdownText()
            }
        }
    }

    private fun setupReminderToggle() {
        binding.switchReminder.setOnCheckedChangeListener { _, isEnabled ->
            prefs.isReminderEnabled = isEnabled
            if (isEnabled) {
                scheduler.scheduleNextAlarm()
            } else {
                scheduler.cancelScheduledAlarm()
            }
            updateUI()
        }
    }

    private fun setupFixPermissionsButton() {
        binding.btnFixPermissions.setOnClickListener {
            when {
                !detector.isNotificationPermissionGranted() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                !detector.isExactAlarmPermissionGranted() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        settingsLauncher.launch(intent)
                    }
                }
            }
        }
    }

    private fun setupBatteryOptButton() {
        binding.btnBatteryOpt.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                settingsLauncher.launch(intent)
            }
        }
    }

    private fun setupProgressCard() {
        binding.btnGoalMinus.setOnClickListener {
            val current = prefs.dailyGoal
            if (current > 1) {
                prefs.dailyGoal = current - 1
                updateProgressCard()
            }
        }
        binding.btnGoalPlus.setOnClickListener {
            val current = prefs.dailyGoal
            if (current < 16) {
                prefs.dailyGoal = current + 1
                updateProgressCard()
            }
        }
        binding.btnLogDrink.setOnClickListener {
            prefs.incrementDrinkCount()
            if (prefs.isReminderEnabled) {
                scheduler.scheduleNextAlarm()
            }
            updateProgressCard()
            updateCountdownText()
        }
    }

    private fun updateProgressCard() {
        val count = prefs.drinkCountToday
        val goal = prefs.dailyGoal
        binding.tvDrinkCount.text = count.toString()
        binding.tvDailyGoal.text = goal.toString()
        val pct = ((count.toFloat() / goal) * 100).toInt().coerceAtMost(100)
        binding.progressDrink.progress = pct
    }

    private fun setupFsiButton() {
        binding.btnFixFsi.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 34) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENTS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                settingsLauncher.launch(intent)
            }
        }
    }

    private fun updateUI() {
        val isEnabled = prefs.isReminderEnabled

        // Detach listener before setting programmatic state to avoid re-entrancy
        binding.switchReminder.setOnCheckedChangeListener(null)
        binding.switchReminder.isChecked = isEnabled
        setupReminderToggle()

        binding.tvReminderStatus.text = if (isEnabled)
            getString(R.string.reminders_on)
        else
            getString(R.string.reminders_off)

        val alarmOk = detector.isExactAlarmPermissionGranted()
        val notifOk = detector.isNotificationPermissionGranted()
        val battOk = detector.isIgnoringBatteryOptimizations()
        val fsiOk = detector.canUseFullScreenIntent()
        val isRestricted = detector.isInRestrictedBucket()

        binding.tvPermAlarm.text =
            if (alarmOk) "✓ Exact alarms: Granted" else "✗ Exact alarms: Not granted"
        binding.tvPermNotif.text =
            if (notifOk) "✓ Notifications: Granted" else "✗ Notifications: Not granted"
        binding.tvPermBattery.text =
            if (battOk) "✓ Battery optimization: Excluded" else "✗ Battery optimization: Not excluded"

        if (!fsiOk) {
            binding.tvPermFsi.visibility = View.VISIBLE
            binding.tvPermFsi.text = "✗ Full-screen alerts: Not allowed"
        } else {
            binding.tvPermFsi.visibility = View.GONE
        }

        if (isRestricted) {
            binding.tvPermRestricted.visibility = View.VISIBLE
            binding.tvPermRestricted.text =
                "⚠ App is in Restricted standby bucket — reminders may be delayed"
        } else {
            binding.tvPermRestricted.visibility = View.GONE
        }

        val needsPermFix = !alarmOk || !notifOk
        binding.btnFixPermissions.visibility = if (needsPermFix) View.VISIBLE else View.GONE
        binding.btnBatteryOpt.visibility = if (!battOk) View.VISIBLE else View.GONE
        binding.btnFixFsi.visibility = if (!fsiOk) View.VISIBLE else View.GONE
        binding.cardPermissions.visibility =
            if (needsPermFix || !battOk || !fsiOk || isRestricted) View.VISIBLE else View.GONE

        updateProgressCard()
        updateCountdownText()
    }

    private fun startCountdown() {
        stopCountdown()
        countdownRunnable = object : Runnable {
            override fun run() {
                updateCountdownText()
                handler.postDelayed(this, 1_000)
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun updateCountdownText() {
        if (!prefs.isReminderEnabled) {
            binding.tvCountdown.text = getString(R.string.reminders_disabled_short)
            binding.tvCountdownLabel.visibility = View.INVISIBLE
            return
        }
        val nextTime = prefs.nextReminderTime
        if (nextTime <= 0L) {
            binding.tvCountdown.text = getString(R.string.scheduling)
            binding.tvCountdownLabel.visibility = View.INVISIBLE
            return
        }
        val diff = nextTime - System.currentTimeMillis()
        if (diff <= 0L) {
            binding.tvCountdown.text = getString(R.string.due_now)
            binding.tvCountdownLabel.visibility = View.INVISIBLE
            return
        }
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
        binding.tvCountdown.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        binding.tvCountdownLabel.visibility = View.VISIBLE
    }
}
