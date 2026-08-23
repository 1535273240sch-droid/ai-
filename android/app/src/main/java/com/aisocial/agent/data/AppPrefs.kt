package com.aisocial.agent.data

import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XSharedPreferences

/**
 * 配置存储（双通道，修复跨进程不可见问题）：
 * - 写入端（模块自身进程 / ConfigActivity）：普通 SharedPreferences
 * - 读取端（Hook 所在的心遇进程）：XSharedPreferences 直读模块包的 prefs 文件
 *   （普通 SharedPreferences 跨进程不同步；系统 Context 的 prefs 与模块目录
 *   更是两个文件 —— 审查确认的 P0 问题，此为修复方案）
 */
object AppPrefs {

    private const val PREFS_NAME = "aisocial_agent"
    const val MODULE_PACKAGE = "com.aisocial.agent"

    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model_name"
    private const val KEY_PERSONA = "persona_prompt"
    private const val KEY_ENABLED = "module_enabled"
    private const val KEY_AUTO_MODE = "auto_mode"

    private var writer: SharedPreferences? = null
    private var reader: XSharedPreferences? = null
    private var hookInitTried = false

    /** 写入端初始化：模块自身进程（ConfigActivity）调用 */
    fun init(context: Context) {
        if (writer == null) {
            writer = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 读取端初始化：Hook 进程（心遇）调用，走 XSharedPreferences */
    fun initForHook() {
        if (hookInitTried) return
        hookInitTried = true
        reader = runCatching {
            val xsp = XSharedPreferences(MODULE_PACKAGE, PREFS_NAME)
            if (xsp.file.canRead()) xsp else null
        }.getOrNull()
    }

    /** Hook 进程读配置前刷新（XSharedPreferences 持有文件快照） */
    fun reloadForHook() {
        runCatching { reader?.reload() }
    }

    private fun initialized(): Boolean = reader != null || writer != null

    // ---- 分支读取：Hook 进程走 XSharedPreferences，模块进程走 SharedPreferences ----
    private fun str(key: String): String =
        reader?.getString(key, "") ?: writer?.getString(key, "") ?: ""

    private fun bool(key: String): Boolean =
        reader?.getBoolean(key, false) ?: writer?.getBoolean(key, false) ?: false

    private fun requireWriter(): SharedPreferences =
        writer ?: throw IllegalStateException("AppPrefs 写端未初始化（Hook 进程只读）")

    /** 写入统一走 commit()（同步落盘，供 Hook 进程 XSharedPreferences 读取） */
    private fun putStr(key: String, v: String) {
        requireWriter().edit().putString(key, v).commit()
    }

    private fun putBool(key: String, v: Boolean) {
        requireWriter().edit().putBoolean(key, v).commit()
    }

    // ---- API 配置 ----
    var apiUrl: String
        get() = if (initialized()) str(KEY_API_URL) else ""
        set(v) = putStr(KEY_API_URL, v.trim())

    var apiKey: String
        get() = if (initialized()) str(KEY_API_KEY) else ""
        set(v) = putStr(KEY_API_KEY, v.trim())

    var modelName: String
        get() = if (initialized()) str(KEY_MODEL) else ""
        set(v) = putStr(KEY_MODEL, v.trim())

    var personaPrompt: String
        get() = if (initialized()) str(KEY_PERSONA) else ""
        set(v) = putStr(KEY_PERSONA, v)

    // ---- 开关 ----
    var isEnabled: Boolean
        get() = if (initialized()) bool(KEY_ENABLED) else false
        set(v) = putBool(KEY_ENABLED, v)

    var isAutoMode: Boolean
        get() = if (initialized()) bool(KEY_AUTO_MODE) else false
        set(v) = putBool(KEY_AUTO_MODE, v)

    /** 自动回复前的本地检查：开关打开 + API 配置齐全 */
    fun readyForAutoReply(): Boolean =
        isEnabled && apiUrl.isNotBlank() && apiKey.isNotBlank()
}
