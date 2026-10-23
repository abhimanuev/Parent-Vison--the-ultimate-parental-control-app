package com.example.data.model

data class AppRule(
    val packageName: String,
    val appName: String,
    val isRestricted: Boolean = true,
    val timeLimitSeconds: Int = 60, // default 1 min
    val customWarningMessage: String? = null,
    val isBlocked: Boolean = false,
    val blockedTimestamp: Long = 0L,
    val lastSessionUsedSeconds: Int = 0
) {
    val isInstantBlock: Boolean
        get() = timeLimitSeconds <= 0
}

data class GuardianSettings(
    val masterEnabled: Boolean = true,
    val pinHash: String = "1234", // default initial PIN
    val isPinConfigured: Boolean = false,
    val defaultWarningMessage: String = "Your screen time limit has been reached for this app. It's time to take a break!",
    val dailyAutoResetEnabled: Boolean = true,
    val dailyResetHour: Int = 0,
    val presetMessages: List<String> = listOf(
        "Your screen time limit has been reached for this app. It's time to take a break!",
        "Time's up! Let's get back to studying or family activities.",
        "Daily gaming quota exceeded. Come back tomorrow!",
        "Parental Control: Session closed by parent's rule."
    )
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val rule: AppRule? = null
)
