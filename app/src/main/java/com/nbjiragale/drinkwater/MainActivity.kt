package com.nbjiragale.drinkwater

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nbjiragale.drinkwater.alarm.NotificationService
import com.nbjiragale.drinkwater.alarm.WaterReminderAlarmScheduler
import com.nbjiragale.drinkwater.databinding.ActivityMainBinding
import com.nbjiragale.drinkwater.util.BackgroundRestrictionDetector
import com.nbjiragale.drinkwater.util.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult -> onRingtonePicked(result) }

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
        setupActiveHours()
        setupReminderSoundCard()
        setupProgressCard()
        setupFixPermissionsButton()
        setupBatteryOptButton()
        setupFsiButton()
        installTrialTestNotificationTrigger(this, binding) // TRIAL: remove this line + TrialTestNotification.kt to disable
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

    private fun setupActiveHours() {
        refreshActiveHoursLabels()
        binding.btnActiveStart.setOnClickListener {
            showTimePicker(prefs.activeStartMinute) { picked ->
                if (picked >= prefs.activeEndMinute) {
                    Toast.makeText(this, R.string.active_hours_invalid, Toast.LENGTH_SHORT).show()
                    return@showTimePicker
                }
                prefs.activeStartMinute = picked
                onActiveHoursChanged()
            }
        }
        binding.btnActiveEnd.setOnClickListener {
            showTimePicker(prefs.activeEndMinute) { picked ->
                if (picked <= prefs.activeStartMinute) {
                    Toast.makeText(this, R.string.active_hours_invalid, Toast.LENGTH_SHORT).show()
                    return@showTimePicker
                }
                prefs.activeEndMinute = picked
                onActiveHoursChanged()
            }
        }
    }

    private fun onActiveHoursChanged() {
        refreshActiveHoursLabels()
        if (prefs.isReminderEnabled) {
            scheduler.cancelScheduledAlarm()
            scheduler.scheduleNextAlarm()
        }
        updateCountdownText()
    }

    private fun refreshActiveHoursLabels() {
        binding.tvActiveStart.text = formatMinuteOfDay(prefs.activeStartMinute)
        binding.tvActiveEnd.text = formatMinuteOfDay(prefs.activeEndMinute)
    }

    private fun formatMinuteOfDay(minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(cal.timeInMillis))
    }

    private fun showTimePicker(initialMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinute / 60,
            initialMinute % 60,
            false
        ).show()
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

    private fun setupReminderSoundCard() {
        refreshReminderSoundLabel()
        binding.btnPickSound.setOnClickListener { launchRingtonePicker() }
    }

    private fun launchRingtonePicker() {
        val currentUri: Uri? = when (val stored = prefs.reminderSoundUri) {
            null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            PreferencesManager.SOUND_URI_SILENT -> null
            else -> Uri.parse(stored)
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                getString(R.string.reminder_sound_picker_title)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun onRingtonePicked(result: ActivityResult) {
        if (result.resultCode != RESULT_OK) return
        val picked: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        val storedValue: String? = when {
            picked == null -> PreferencesManager.SOUND_URI_SILENT
            picked == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) -> null
            else -> picked.toString()
        }
        NotificationService.applyReminderSoundChange(this, storedValue)
        refreshReminderSoundLabel()
    }

    private fun refreshReminderSoundLabel() {
        binding.tvReminderSound.text = describeReminderSound(prefs.reminderSoundUri)
    }

    private fun describeReminderSound(stored: String?): String = when (stored) {
        null -> getString(R.string.reminder_sound_default)
        PreferencesManager.SOUND_URI_SILENT -> getString(R.string.reminder_sound_silent)
        else -> {
            val title = RingtoneManager.getRingtone(this, Uri.parse(stored))?.getTitle(this)
            title?.takeIf { it.isNotBlank() } ?: getString(R.string.reminder_sound_default)
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
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
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
