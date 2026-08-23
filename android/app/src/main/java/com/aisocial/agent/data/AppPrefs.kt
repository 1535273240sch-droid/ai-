package com.aisocial.agent.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地配置存储（SharedPreferences 单例）。
 * 全部本地运行：API 地址 / Key / 模型 / 人设 / 开关，不依赖任何服务器。
 */
object AppPrefs {

    private const val PREFS_NAME = "aisocial_agent"

    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model_name"
    private const val KEY_PERSONA = "persona_prompt"
    private const val KEY_ENABLED = "module_enabled"
    private const val KEY_AUTO_MODE = "auto_mode"

    private lateinit var prefs: SharedPreferences

    /** 必须在 handleLoadPackage 或 Activity onCreate 中调用一次 */
    fun init(context: Context) {
        if (!this::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun safeGet(): SharedPreferences =
        if (this::prefs.isInitialized) prefs else throw IllegalStateException("AppPrefs 未初始化")

    // ---- API 配置（本地） ----
    var apiUrl: String
        get() = safeGet().getString(KEY_API_URL, "") ?: ""
        set(v) = safeGet().edit().putString(KEY_API_URL, v.trim()).apply()

    var apiKey: String
        get() = safeGet().getString(KEY_API_KEY, "") ?: ""
        set(v) = safeGet().edit().putString(KEY_API_KEY, v.trim()).apply()

    var modelName: String
        get() = safeGet().getString(KEY_MODEL, "") ?: ""
        set(v) = safeGet().edit().putString(KEY_MODEL, v.trim()).apply()

    var personaPrompt: String
        get() = safeGet().getString(KEY_PERSONA, "") ?: ""
        set(v) = safeGet().edit().putString(KEY_PERSONA, v).apply()

    // ---- 开关 ----
    var isEnabled: Boolean
        get() = safeGet().getBoolean(KEY_ENABLED, false)
        set(v) = safeGet().edit().putBoolean(KEY_ENABLED, v).apply()

    var isAutoMode: Boolean
        get() = safeGet().getBoolean(KEY_AUTO_MODE, false)
        set(v) = safeGet().edit().putBoolean(KEY_AUTO_MODE, v).apply()

    /** 自动回复前的本地检查：开关打开 + API 配置齐全 */
    fun readyForAutoReply(): Boolean =
        isEnabled && apiUrl.isNotBlank() && apiKey.isNotBlank()
}
