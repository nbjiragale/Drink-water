package com.nbjiragale.drinkwater

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nbjiragale.drinkwater.databinding.ActivityOnboardingBinding
import com.nbjiragale.drinkwater.util.BackgroundRestrictionDetector
import com.nbjiragale.drinkwater.util.OemSettingsHelper
import com.nbjiragale.drinkwater.util.PreferencesManager

/**
 * Three-step onboarding wizard as specified in the PDF:
 *   Step 1 – POST_NOTIFICATIONS (Android 13+)
 *   Step 2 – SCHEDULE_EXACT_ALARM
 *   Step 3 – Battery optimization exclusion
 *   Step 4 – OEM-specific autostart settings (Xiaomi / Oppo / Vivo / Samsung)
 *
 * Only steps whose corresponding permission is not yet granted are shown.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var detector: BackgroundRestrictionDetector
    private lateinit var prefs: PreferencesManager

    private val steps = mutableListOf<OnboardingStep>()
    private var currentStepIndex = 0

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { moveToNextStep() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { moveToNextStep() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        detector = BackgroundRestrictionDetector(this)

        buildStepList()

        if (steps.isEmpty()) {
            finishOnboarding()
            return
        }

        showStep(0)
    }

    private fun buildStepList() {
        steps.clear()
        if (!detector.isNotificationPermissionGranted()) steps.add(OnboardingStep.NotificationPermission)
        if (!detector.isExactAlarmPermissionGranted()) steps.add(OnboardingStep.ExactAlarm)
        if (!detector.isIgnoringBatteryOptimizations()) steps.add(OnboardingStep.BatteryOptimization)
        if (!detector.canUseFullScreenIntent()) steps.add(OnboardingStep.FullScreenIntent)
        val oem = detector.getDetectedOem()
        if (oem != BackgroundRestrictionDetector.OemType.GENERIC) {
            steps.add(OnboardingStep.OemAutoStart(oem))
        }
    }

    private fun showStep(index: Int) {
        if (index >= steps.size) {
            finishOnboarding()
            return
        }
        currentStepIndex = index
        val step = steps[index]
        val totalSteps = steps.size

        binding.tvStepIndicator.text = getString(R.string.step_of, index + 1, totalSteps)

        val isLastStep = index == totalSteps - 1
        binding.btnOnboardingSkip.text =
            if (isLastStep) getString(R.string.done) else getString(R.string.skip)
        binding.btnOnboardingSkip.setOnClickListener { moveToNextStep() }

        when (step) {
            is OnboardingStep.NotificationPermission -> {
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_notifications_title)
                binding.tvOnboardingBody.text = getString(R.string.onboarding_notifications_body)
                binding.btnOnboardingAction.text = getString(R.string.grant_permission)
                binding.btnOnboardingAction.setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        moveToNextStep()
                    }
                }
            }

            is OnboardingStep.ExactAlarm -> {
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_step_1_title)
                binding.tvOnboardingBody.text = getString(R.string.onboarding_step_1_body)
                binding.btnOnboardingAction.text = getString(R.string.grant_permission)
                binding.btnOnboardingAction.setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        settingsLauncher.launch(intent)
                    } else {
                        moveToNextStep()
                    }
                }
            }

            is OnboardingStep.BatteryOptimization -> {
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_step_2_title)
                binding.tvOnboardingBody.text = getString(R.string.onboarding_step_2_body)
                binding.btnOnboardingAction.text = getString(R.string.open_settings)
                binding.btnOnboardingAction.setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        settingsLauncher.launch(intent)
                    } else {
                        moveToNextStep()
                    }
                }
            }

            is OnboardingStep.FullScreenIntent -> {
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_fsi_title)
                binding.tvOnboardingBody.text = getString(R.string.onboarding_fsi_body)
                binding.btnOnboardingAction.text = getString(R.string.open_settings)
                binding.btnOnboardingAction.setOnClickListener {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        settingsLauncher.launch(intent)
                    } else {
                        moveToNextStep()
                    }
                }
            }

            is OnboardingStep.OemAutoStart -> {
                binding.tvOnboardingTitle.text = getString(R.string.onboarding_step_3_title)
                binding.tvOnboardingBody.text = buildOemBodyText(step.oemType)
                binding.btnOnboardingAction.text = getString(R.string.open_settings)
                binding.btnOnboardingAction.setOnClickListener {
                    openOemSettings(step.oemType)
                    moveToNextStep()
                }
            }
        }
    }

    private fun buildOemBodyText(oemType: BackgroundRestrictionDetector.OemType): String {
        val oemName = when (oemType) {
            BackgroundRestrictionDetector.OemType.XIAOMI -> "Xiaomi / MIUI / HyperOS"
            BackgroundRestrictionDetector.OemType.OPPO -> "Oppo / Realme / ColorOS"
            BackgroundRestrictionDetector.OemType.VIVO -> "Vivo / Funtouch OS"
            BackgroundRestrictionDetector.OemType.SAMSUNG -> "Samsung / OneUI"
            BackgroundRestrictionDetector.OemType.GENERIC -> "your device"
        }
        return getString(R.string.onboarding_step_3_body, oemName)
    }

    private fun openOemSettings(oemType: BackgroundRestrictionDetector.OemType) {
        when (oemType) {
            BackgroundRestrictionDetector.OemType.XIAOMI -> OemSettingsHelper.openXiaomiAutoStartSettings(this)
            BackgroundRestrictionDetector.OemType.OPPO -> OemSettingsHelper.openOppoAutoLaunchSettings(this)
            BackgroundRestrictionDetector.OemType.VIVO -> OemSettingsHelper.openVivoAutoStartSettings(this)
            BackgroundRestrictionDetector.OemType.SAMSUNG -> OemSettingsHelper.openSamsungNeverSleepingApps(this)
            BackgroundRestrictionDetector.OemType.GENERIC -> OemSettingsHelper.openStandardAppSettings(this)
        }
    }

    private fun moveToNextStep() {
        val next = currentStepIndex + 1
        if (next >= steps.size) finishOnboarding() else showStep(next)
    }

    private fun finishOnboarding() {
        prefs.isOnboardingDone = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    sealed class OnboardingStep {
        object NotificationPermission : OnboardingStep()
        object ExactAlarm : OnboardingStep()
        object BatteryOptimization : OnboardingStep()
        object FullScreenIntent : OnboardingStep()
        data class OemAutoStart(val oemType: BackgroundRestrictionDetector.OemType) : OnboardingStep()
    }
}
