package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppRule
import com.example.data.model.GuardianSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class GuardianRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _rulesFlow = MutableStateFlow<Map<String, AppRule>>(emptyMap())
    val rulesFlow: StateFlow<Map<String, AppRule>> = _rulesFlow.asStateFlow()

    private val _settingsFlow = MutableStateFlow(GuardianSettings())
    val settingsFlow: StateFlow<GuardianSettings> = _settingsFlow.asStateFlow()

    init {
        loadSettings()
        loadRules()
    }

    @Synchronized
    private fun loadSettings() {
        val masterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        val pin = prefs.getString(KEY_PIN, "1234") ?: "1234"
        val isPinConfigured = prefs.getBoolean(KEY_PIN_CONFIGURED, false)
        val defaultMsg = prefs.getString(
            KEY_DEFAULT_MSG,
            "Your screen time limit has been reached for this app. It's time to take a break!"
        ) ?: "Your screen time limit has been reached for this app. It's time to take a break!"
        val dailyReset = prefs.getBoolean(KEY_DAILY_RESET, true)

        _settingsFlow.value = GuardianSettings(
            masterEnabled = masterEnabled,
            pinHash = pin,
            isPinConfigured = isPinConfigured,
            defaultWarningMessage = defaultMsg,
            dailyAutoResetEnabled = dailyReset
        )
    }

    @Synchronized
    private fun loadRules() {
        val rulesJson = prefs.getString(KEY_RULES_JSON, "[]") ?: "[]"
        val map = mutableMapOf<String, AppRule>()
        try {
            val array = JSONArray(rulesJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.getString("packageName")
                val rule = AppRule(
                    packageName = pkg,
                    appName = obj.optString("appName", pkg),
                    isRestricted = obj.optBoolean("isRestricted", true),
                    timeLimitSeconds = obj.optInt("timeLimitSeconds", 60),
                    customWarningMessage = if (obj.has("customWarningMessage") && !obj.isNull("customWarningMessage")) {
                        obj.getString("customWarningMessage")
                    } else null,
                    isBlocked = obj.optBoolean("isBlocked", false),
                    blockedTimestamp = obj.optLong("blockedTimestamp", 0L),
                    lastSessionUsedSeconds = obj.optInt("lastSessionUsedSeconds", 0)
                )
                map[pkg] = rule
            }
        } catch (_: Exception) {
            // fallback
        }
        _rulesFlow.value = map
    }

    @Synchronized
    private fun saveRulesToPrefs(map: Map<String, AppRule>) {
        val array = JSONArray()
        for (rule in map.values) {
            val obj = JSONObject().apply {
                put("packageName", rule.packageName)
                put("appName", rule.appName)
                put("isRestricted", rule.isRestricted)
                put("timeLimitSeconds", rule.timeLimitSeconds)
                if (rule.customWarningMessage != null) {
                    put("customWarningMessage", rule.customWarningMessage)
                }
                put("isBlocked", rule.isBlocked)
                put("blockedTimestamp", rule.blockedTimestamp)
                put("lastSessionUsedSeconds", rule.lastSessionUsedSeconds)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES_JSON, array.toString()).apply()
        _rulesFlow.value = map
    }

    @Synchronized
    fun getRule(packageName: String): AppRule? {
        return _rulesFlow.value[packageName]
    }

    @Synchronized
    fun setAppRestricted(packageName: String, appName: String, restricted: Boolean) {
        val current = _rulesFlow.value.toMutableMap()
        if (restricted) {
            val existing = current[packageName]
            if (existing != null) {
                current[packageName] = existing.copy(isRestricted = true, appName = appName)
            } else {
                current[packageName] = AppRule(
                    packageName = packageName,
                    appName = appName,
                    isRestricted = true,
                    timeLimitSeconds = 60 // 1 minute default
                )
            }
        } else {
            val existing = current[packageName]
            if (existing != null) {
                current[packageName] = existing.copy(isRestricted = false)
            }
        }
        saveRulesToPrefs(current)
    }

    @Synchronized
    fun updateRule(rule: AppRule) {
        val current = _rulesFlow.value.toMutableMap()
        current[rule.packageName] = rule
        saveRulesToPrefs(current)
    }

    @Synchronized
    fun markAppBlocked(packageName: String) {
        val current = _rulesFlow.value.toMutableMap()
        val existing = current[packageName]
        if (existing != null) {
            current[packageName] = existing.copy(
                isBlocked = true,
                blockedTimestamp = System.currentTimeMillis()
            )
            saveRulesToPrefs(current)
        }
    }

    @Synchronized
    fun unblockApp(packageName: String) {
        val current = _rulesFlow.value.toMutableMap()
        val existing = current[packageName]
        if (existing != null) {
            current[packageName] = existing.copy(
                isBlocked = false,
                lastSessionUsedSeconds = 0
            )
            saveRulesToPrefs(current)
        }
    }

    @Synchronized
    fun resetAllBlocked() {
        val current = _rulesFlow.value.toMutableMap()
        for ((pkg, rule) in current) {
            current[pkg] = rule.copy(isBlocked = false, lastSessionUsedSeconds = 0)
        }
        saveRulesToPrefs(current)
    }

    @Synchronized
    fun removeRule(packageName: String) {
        val current = _rulesFlow.value.toMutableMap()
        current.remove(packageName)
        saveRulesToPrefs(current)
    }

    @Synchronized
    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(masterEnabled = enabled)
    }

    @Synchronized
    fun setPin(newPin: String) {
        prefs.edit()
            .putString(KEY_PIN, newPin)
            .putBoolean(KEY_PIN_CONFIGURED, true)
            .apply()
        _settingsFlow.value = _settingsFlow.value.copy(
            pinHash = newPin,
            isPinConfigured = true
        )
    }

    @Synchronized
    fun verifyPin(pin: String): Boolean {
        return _settingsFlow.value.pinHash == pin
    }

    @Synchronized
    fun updateDefaultMessage(message: String) {
        prefs.edit().putString(KEY_DEFAULT_MSG, message).apply()
        _settingsFlow.value = _settingsFlow.value.copy(defaultWarningMessage = message)
    }

    @Synchronized
    fun setDailyAutoReset(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DAILY_RESET, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(dailyAutoResetEnabled = enabled)
    }

    fun getEffectiveWarningMessage(rule: AppRule?): String {
        return rule?.customWarningMessage?.takeIf { it.isNotBlank() }
            ?: _settingsFlow.value.defaultWarningMessage
    }

    companion object {
        private const val PREFS_NAME = "screentime_guardian_prefs"
        private const val KEY_MASTER_ENABLED = "key_master_enabled"
        private const val KEY_PIN = "key_pin"
        private const val KEY_PIN_CONFIGURED = "key_pin_configured"
        private const val KEY_DEFAULT_MSG = "key_default_msg"
        private const val KEY_DAILY_RESET = "key_daily_reset"
        private const val KEY_RULES_JSON = "key_rules_json"
    }
}
