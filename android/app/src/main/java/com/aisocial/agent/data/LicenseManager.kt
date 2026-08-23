package com.aisocial.agent.data

import com.aisocial.agent.api.ApiClient
import com.aisocial.agent.api.ApiException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 卡密管理：本地缓存激活状态 + 启动时向后端刷新 + Kill Switch 标记。
 *
 * Kill Switch：服务端吊销卡密后，WebSocket 收到 kill_switch 事件即置位，
 * 此后所有自动回复立即停止，直到重新激活。
 */
object LicenseManager {

    @Volatile
    private var localValid = false

    @Volatile
    private var expiresAt: String? = null

    @Volatile
    private var features: Map<String, Any> = emptyMap()

    private val killSwitch = AtomicBoolean(false)

    /** 卡密是否本地有效（缓存） */
    fun isValidCached(): Boolean = localValid && !killSwitch.get()

    /** 是否被服务端吊销（Kill Switch） */
    fun isKillSwitch(): Boolean = killSwitch.get()

    fun onKillSwitch(reason: String) {
        killSwitch.set(true)
        localValid = false
        android.util.Log.w("AISocial", "Kill Switch 生效：$reason")
    }

    /**
     * 激活卡密：调后端 POST /license/activate。
     * 成功 → 缓存有效状态并清除 Kill Switch。
     */
    suspend fun activate(code: String, deviceFingerprint: String): Boolean {
        val resp = ApiClient.activateLicense(code, deviceFingerprint)
        if (resp.activated) {
            localValid = true
            killSwitch.set(false)
            expiresAt = resp.license?.expiresAt
            features = resp.license?.features ?: emptyMap()
            AppPrefs.licenseCode = code
            AppPrefs.deviceFingerprint = deviceFingerprint
        }
        return resp.activated
    }

    /** 启动时刷新卡密状态：GET /license/info */
    suspend fun refresh(): Boolean {
        val info = ApiClient.licenseInfo()
        localValid = info.valid
        if (info.valid) {
            killSwitch.set(false)
            expiresAt = info.license?.expiresAt
            features = info.license?.features ?: emptyMap()
        } else {
            expiresAt = null
        }
        return info.valid
    }

    /** 自动回复前检查：未配置卡密或已吊销 → 不回复 */
    fun canAutoReply(): Boolean =
        AppPrefs.isEnabled && isValidCached() && AppPrefs.jwtToken.isNotBlank()
}
