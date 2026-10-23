package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.GuardianApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val app = context.applicationContext as? GuardianApplication
            val repository = app?.repository
            // If daily auto reset is enabled, check or reset blocked apps
            if (repository != null && repository.settingsFlow.value.dailyAutoResetEnabled) {
                repository.resetAllBlocked()
            }
        }
    }
}
