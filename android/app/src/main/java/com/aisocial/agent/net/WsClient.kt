package com.aisocial.agent.net

import com.aisocial.agent.data.AppPrefs
import com.aisocial.agent.data.LicenseManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket 客户端：连接 WS /api/v1/ws?token=<JWT>，
 * 接收服务端推送事件：
 * - type=suggestion：AI 回复建议（交 AutoReplyEngine 展示）
 * - type=kill_switch：卡密被吊销 → 立即停止自动化
 */
object WsClient {

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var connectJob: Job? = null
    private var manualClosed = false

    fun startIfConfigured(classLoader: ClassLoader?) {
        if (!AppPrefs.isEnabled || AppPrefs.jwtToken.isBlank()) return
        if (connectJob?.isActive == true) return
        manualClosed = false
        connectJob = scope.launch {
            while (!manualClosed) {
                runCatching { connect() }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    fun stop() {
        manualClosed = true
        connectJob?.cancel()
        connectJob = null
        runCatching { ws?.close(1000, "module off") }
        ws = null
    }

    private fun connect() {
        val url = AppPrefs.serverUrl
            .replace("http://", "ws://").replace("https://", "wss://")
            .trimEnd('/') + "/api/v1/ws?token=" + AppPrefs.jwtToken

        val req = Request.Builder().url(url).build()
        ws = OkHttpClient().newWebSocket(req, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("AISocial", "WS 连接失败：${t.message}")
        }
    }

    private fun handleMessage(text: String) {
        val obj = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return
        val type = obj.get("type")?.asString ?: return
        when (type) {
            "kill_switch" -> LicenseManager.onKillSwitch(obj.get("reason")?.asString ?: "卡密被吊销")
            "suggestion" -> {
                // 悬浮窗建议事件：由 AutoReplyEngine 统一展示
                val contactId = obj.get("contact_id")?.asLong ?: -1L
                val message = obj.get("message")?.asString ?: ""
                val suggestions = obj.getAsJsonArray("suggestions")?.mapNotNull { it.asString } ?: emptyList()
                com.aisocial.agent.hook.AutoReplyEngine.instance.showRemoteSuggestion(contactId, message, suggestions)
            }
            else -> Unit
        }
    }

    private const val RECONNECT_DELAY_MS = 10_000L
}
