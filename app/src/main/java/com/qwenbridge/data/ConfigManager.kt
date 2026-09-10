package com.qwenbridge.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

class ConfigManager private constructor(context: Context) {

    private val prefs: SharedPreferences
    private val encryptedPrefs: SharedPreferences

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    private val logsList = CopyOnWriteArrayList<LogEntry>()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isChallengeActive = MutableStateFlow(false)
    val isChallengeActive: StateFlow<Boolean> = _isChallengeActive.asStateFlow()

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = try {
            EncryptedSharedPreferences.create(
                context,
                "qwen_encrypted_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("qwen_fallback_prefs", Context.MODE_PRIVATE)
        }

        prefs = context.getSharedPreferences("qwen_bridge_prefs", Context.MODE_PRIVATE)
        loadConfig()
    }

    private fun loadConfig() {
        val port = prefs.getInt(KEY_PORT, 8787)
        val token = encryptedPrefs.getString(KEY_TOKEN, "") ?: ""
        val endpoint = prefs.getString(KEY_ENDPOINT, "https://chat.qwen.ai") ?: "https://chat.qwen.ai"
        val model = prefs.getString(KEY_MODEL, "qwen3.8-max") ?: "qwen3.8-max"
        val thinking = prefs.getBoolean(KEY_THINKING, true)
        val autoStart = prefs.getBoolean(KEY_AUTOSTART, true)
        val mediaSession = prefs.getBoolean(KEY_MEDIA_SESSION, true)
        val wakeLock = prefs.getBoolean(KEY_WAKELOCK, true)
        val floatingOverlay = prefs.getBoolean(KEY_FLOATING_OVERLAY, true)
        val totalReqs = prefs.getLong(KEY_TOTAL_REQS, 0L)
        val lastChallenge = prefs.getLong(KEY_LAST_CHALLENGE, 0L)

        _config.value = AppConfig(
            port = port,
            token = token,
            upstreamEndpoint = endpoint,
            defaultModel = model,
            enableThinking = thinking,
            autoStartOnBoot = autoStart,
            keepAliveMediaSession = mediaSession,
            keepAliveWakeLock = wakeLock,
            floatingOverlay = floatingOverlay,
            totalRequests = totalReqs,
            lastChallengeSolvedTime = lastChallenge
        )
    }

    fun updateToken(token: String) {
        encryptedPrefs.edit().putString(KEY_TOKEN, token.trim()).apply()
        _config.value = _config.value.copy(token = token.trim())
    }

    fun updatePort(port: Int) {
        prefs.edit().putInt(KEY_PORT, port).apply()
        _config.value = _config.value.copy(port = port)
    }

    fun updateDefaultModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
        _config.value = _config.value.copy(defaultModel = model)
    }

    fun updateThinking(enable: Boolean) {
        prefs.edit().putBoolean(KEY_THINKING, enable).apply()
        _config.value = _config.value.copy(enableThinking = enable)
    }

    fun updateAutoStart(enable: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSTART, enable).apply()
        _config.value = _config.value.copy(autoStartOnBoot = enable)
    }

    fun updateKeepAlive(mediaSession: Boolean, wakeLock: Boolean) {
        prefs.edit()
            .putBoolean(KEY_MEDIA_SESSION, mediaSession)
            .putBoolean(KEY_WAKELOCK, wakeLock)
            .apply()
        _config.value = _config.value.copy(
            keepAliveMediaSession = mediaSession,
            keepAliveWakeLock = wakeLock
        )
    }

    fun updateFloatingOverlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_OVERLAY, enabled).apply()
        _config.value = _config.value.copy(floatingOverlay = enabled)
    }

    fun incrementRequestsCount() {
        val updated = _config.value.totalRequests + 1
        prefs.edit().putLong(KEY_TOTAL_REQS, updated).apply()
        _config.value = _config.value.copy(totalRequests = updated)
    }

    fun setLastChallengeSolvedTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_CHALLENGE, timestamp).apply()
        _config.value = _config.value.copy(lastChallengeSolvedTime = timestamp)
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setChallengeActive(active: Boolean) {
        _isChallengeActive.value = active
    }

    fun addLog(entry: LogEntry) {
        logsList.add(0, entry)
        if (logsList.size > 200) {
            logsList.removeAt(logsList.size - 1)
        }
        _logs.value = logsList.toList()
    }

    fun clearLogs() {
        logsList.clear()
        _logs.value = emptyList()
    }

    companion object {
        private const val KEY_PORT = "pref_port"
        private const val KEY_TOKEN = "pref_token"
        private const val KEY_ENDPOINT = "pref_endpoint"
        private const val KEY_MODEL = "pref_model"
        private const val KEY_THINKING = "pref_thinking"
        private const val KEY_AUTOSTART = "pref_autostart"
        private const val KEY_MEDIA_SESSION = "pref_media_session"
        private const val KEY_WAKELOCK = "pref_wakelock"
        private const val KEY_FLOATING_OVERLAY = "pref_floating_overlay"
        private const val KEY_TOTAL_REQS = "pref_total_reqs"
        private const val KEY_LAST_CHALLENGE = "pref_last_challenge"

        @Volatile
        private var INSTANCE: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
