package com.aisocial.agent.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 本地配置存储（SharedPreferences 单例，参考 WeChatAIAutoReply 的 AppPrefs 设计）。
 * 全部配置：服务器地址 / JWT / 卡密 / 模块开关 / 联系人画像。
 */
object AppPrefs {

    private const val PREFS_NAME = "aisocial_agent"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_JWT_TOKEN = "jwt_token"
    private const val KEY_LICENSE_CODE = "license_code"
    private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
    private const val KEY_ENABLED = "module_enabled"
    private const val KEY_AUTO_MODE = "auto_mode"
    private const val KEY_DEFAULT_PROFILE = "default_profile"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_CONTACT_ID_MAP = "contact_id_map"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    /** 必须在 handleLoadPackage 或 Application 中调用一次 */
    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // ---- 服务器 ----
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SERVER_URL, v.trim().trimEnd('/')).apply()

    val apiBase: String
        get() {
            val url = serverUrl
            return if (url.endsWith("/api/v1")) url else "$url/api/v1"
        }

    // ---- 认证 / 卡密 ----
    var jwtToken: String
        get() = prefs.getString(KEY_JWT_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_JWT_TOKEN, v.trim()).apply()

    var licenseCode: String
        get() = prefs.getString(KEY_LICENSE_CODE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LICENSE_CODE, v.trim()).apply()

    var deviceFingerprint: String
        get() = prefs.getString(KEY_DEVICE_FINGERPRINT, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DEVICE_FINGERPRINT, v.trim()).apply()

    // ---- 开关 ----
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    var isAutoMode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MODE, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_MODE, v).apply()

    // ---- 联系人画像 ----
    fun getDefaultProfile(): Profile? =
        prefs.getString(KEY_DEFAULT_PROFILE, null)?.let { runCatching { gson.fromJson(it, Profile::class.java) }.getOrNull() }

    fun setDefaultProfile(p: Profile?) {
        prefs.edit().putString(KEY_DEFAULT_PROFILE, if (p == null) "" else gson.toJson(p)).apply()
    }

    fun getProfiles(): MutableMap<String, Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return mutableMapOf()
        return runCatching {
            gson.fromJson(raw, object : TypeToken<MutableMap<String, Profile>>() {}.type)
        }.getOrNull() ?: mutableMapOf()
    }

    /** contactId → 画像 */
    fun getProfileForContact(contactId: String): Profile? = getProfiles()[contactId]

    fun saveProfileForContact(contactId: String, p: Profile) {
        val map = getProfiles()
        map[contactId] = p
        prefs.edit().putString(KEY_PROFILES, gson.toJson(map)).apply()
    }

    fun removeProfileForContact(contactId: String) {
        val map = getProfiles()
        map.remove(contactId)
        prefs.edit().putString(KEY_PROFILES, gson.toJson(map)).apply()
    }

    // ---- 心遇 talkerId → 后端联系人 id 映射 ----
    fun getContactIdFor(talkerId: String): Long? {
        val raw = prefs.getString(KEY_CONTACT_ID_MAP, null) ?: return null
        return runCatching {
            val map = gson.fromJson(raw, object : TypeToken<MutableMap<String, Long>>() {}.type)
            map[talkerId]
        }.getOrNull()
    }

    fun saveContactIdFor(talkerId: String, contactId: Long) {
        val raw = prefs.getString(KEY_CONTACT_ID_MAP, null)
        val map = runCatching {
            gson.fromJson(raw, object : TypeToken<MutableMap<String, Long>>() {}.type)
        }.getOrNull() ?: mutableMapOf()
        map[talkerId] = contactId
        prefs.edit().putString(KEY_CONTACT_ID_MAP, gson.toJson(map)).apply()
    }
}
