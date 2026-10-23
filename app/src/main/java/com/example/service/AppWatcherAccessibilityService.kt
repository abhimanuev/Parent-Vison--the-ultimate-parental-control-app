package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.GuardianApplication
import com.example.data.GuardianRepository
import com.example.ui.OverlayDialogManager
import com.example.ui.TimeLimitWarningActivity

class AppWatcherAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentMonitoredPackage: String? = null
    private var limitRunnable: Runnable? = null

    private val repository: GuardianRepository
        get() = (application as? GuardianApplication)?.repository
            ?: GuardianRepository(applicationContext)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val settings = repository.settingsFlow.value
        if (settings.masterEnabled) {
            GuardianNotificationManager.showActiveNotification(applicationContext)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val rawPkg = event.packageName?.toString() ?: return
        val myPkg = packageName

        // Ignore system navigation, keyboard, own app, and system UI
        if (rawPkg == myPkg ||
            rawPkg == "com.android.systemui" ||
            rawPkg == "android" ||
            rawPkg.contains("launcher") ||
            rawPkg.contains("home") ||
            rawPkg.contains("trebuchet") ||
            rawPkg.contains("nexuslauncher") ||
            rawPkg.contains("inputmethod")
        ) {
            stopCurrentSessionTimer()
            currentMonitoredPackage = null
            return
        }

        // If user is already in the currently monitored package
        if (rawPkg == currentMonitoredPackage) {
            return
        }

        // Changed foreground package
        stopCurrentSessionTimer()
        currentMonitoredPackage = rawPkg

        val settings = repository.settingsFlow.value
        if (!settings.masterEnabled) {
            return
        }

        val rule = repository.getRule(rawPkg)
        if (rule == null || !rule.isRestricted) {
            return
        }

        // App is restricted
        val message = repository.getEffectiveWarningMessage(rule)

        if (rule.isBlocked || rule.isInstantBlock) {
            triggerDualWarningAndExit(rawPkg, rule.appName, message)
            return
        }

        // Start session countdown
        val durationMs = rule.timeLimitSeconds * 1000L
        val task = Runnable {
            if (currentMonitoredPackage == rawPkg) {
                repository.markAppBlocked(rawPkg)
                triggerDualWarningAndExit(rawPkg, rule.appName, message)
            }
        }
        limitRunnable = task
        handler.postDelayed(task, durationMs)
    }

    private fun stopCurrentSessionTimer() {
        limitRunnable?.let { handler.removeCallbacks(it) }
        limitRunnable = null
    }

    private fun triggerDualWarningAndExit(pkgName: String, appName: String, warningMessage: String) {
        // Send user to Home screen immediately
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 1. Direct System WindowManager Overlay (essential for MIUI 11 background restriction bypass)
        if (Settings.canDrawOverlays(this)) {
            OverlayDialogManager.showOverlayWarning(
                context = this,
                packageName = pkgName,
                appName = appName,
                warningMessage = warningMessage,
                onDismiss = {
                    stopCurrentSessionTimer()
                }
            )
        }

        // 2. Translucent Dialog Activity (standard Android UI)
        try {
            val dialogIntent = Intent(this, TimeLimitWarningActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(TimeLimitWarningActivity.EXTRA_PACKAGE_NAME, pkgName)
                putExtra(TimeLimitWarningActivity.EXTRA_APP_NAME, appName)
                putExtra(TimeLimitWarningActivity.EXTRA_WARNING_MESSAGE, warningMessage)
            }
            startActivity(dialogIntent)
        } catch (_: Exception) {
            // Handled gracefully via WindowManager overlay above
        }

        // 3. Clear the background game process after a brief interval
        handler.postDelayed({
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(pkgName)
            } catch (_: Exception) {
            }
        }, 500)
    }

    override fun onInterrupt() {
        stopCurrentSessionTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentSessionTimer()
        OverlayDialogManager.removeCurrentOverlay(this)
    }
}
