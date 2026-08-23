package com.aisocial.agent.hook

import android.content.Context
import com.aisocial.agent.api.ApiClient
import com.aisocial.agent.data.AppPrefs
import com.aisocial.agent.data.LicenseManager
import com.aisocial.agent.data.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 自动回复引擎：消息 → 决策 → 建议/发送。
 *
 * 流程（对齐后端 Agent 决策）：
 * 1. 开关/卡密/Kill Switch 检查（不满足 → 静默）
 * 2. 调 POST /agent/suggest（带联系人画像 + 模式）
 * 3. decision.mode == manual → 忽略（敏感词/卡密问题转人工）
 * 4. mode == auto → 按 delay_ms 延迟后自动发送第 1 条建议
 * 5. mode == suggest → 悬浮窗展示 3 条建议，用户点选后发送
 * 6. 发送成功 → POST /platform/reply 回执
 */
object AutoReplyEngine : MessageListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: SuggestOverlay? = null

    val instance: AutoReplyEngine get() = this

    private fun appContext(): Context? = SuggestOverlay.appContext

    // ---------------- MessageListener ----------------
    override fun onMessageReceived(contactId: String, content: String) {
        if (!LicenseManager.canAutoReply()) return
        val mode = if (AppPrefs.isAutoMode) "auto" else "suggest"
        scope.launch {
            runCatching {
                ApiClient.reportEvent("xinyu", contactId, "message_received", content)
            }
            process(contactId, content, mode)
        }
    }

    override fun onMessageSent(contactId: String, content: String) {
        scope.launch {
            runCatching { ApiClient.reportReply("xinyu", contactId, content) }
        }
    }

    /** 服务端 WebSocket 推来的建议（由 WsClient 调用） */
    fun showRemoteSuggestion(contactId: Long, message: String, suggestions: List<String>) {
        showOverlay(contactId, message, suggestions)
    }

    // ---------------- 核心处理 ----------------
    private suspend fun process(contactId: String, message: String, mode: String) {
        val contactIdBackend = resolveContactId(contactId)
        if (contactIdBackend == null) {
            android.util.Log.w("AISocial", "未匹配到后端联系人（talkerId=$contactId），请在后台添加或检查映射")
            return
        }

        val resp = runCatching { ApiClient.suggest(contactIdBackend, message, mode) }.getOrNull()
        if (resp == null) return

        when (resp.decision.mode) {
            "manual" -> Unit // 转人工：不自动处理
            "auto" -> {
                val reply = resp.suggestions.firstOrNull() ?: return
                delay(resp.delayMs)
                if (XinyuHook.sendMessage(contactId, reply)) {
                    runCatching { ApiClient.reportReply("xinyu", contactId, reply) }
                }
            }
            else -> showOverlay(contactIdBackend, message, resp.suggestions)
        }
    }

    /** talkerId → 后端联系人 id：先查本地映射，再拉列表匹配，最后自动创建（带本地画像） */
    private suspend fun resolveContactId(talkerId: String): Long? {
        AppPrefs.getContactIdFor(talkerId)?.let { return it }
        val contacts = runCatching { ApiClient.listContacts().items }.getOrNull() ?: return null
        val hit = contacts.firstOrNull { it.platformContactId == talkerId }
        if (hit != null) {
            AppPrefs.saveContactIdFor(talkerId, hit.id)
            return hit.id
        }
        val profile = AppPrefs.getProfileForContact(talkerId) ?: AppPrefs.getDefaultProfile()
        val created = runCatching {
            ApiClient.createContact(
                platform = "xinyu",
                platformContactId = talkerId,
                nickname = talkerId,
                profile = profile?.let {
                    ApiClient.ContactProfile(
                        relationship = it.relationship,
                        interactionStyle = it.interactionStyle,
                        replyFrequency = it.replyFrequency,
                        sentenceStyle = it.sentenceStyle,
                        taboos = it.taboos,
                    )
                },
            )
        }.getOrNull()
        if (created != null) {
            AppPrefs.saveContactIdFor(talkerId, created.id)
            return created.id
        }
        return null
    }

    // ---------------- 悬浮窗 ----------------
    private fun showOverlay(contactId: Long, message: String, suggestions: List<String>) {
        if (suggestions.isEmpty()) return
        val ctx = appContext() ?: return
        overlay = SuggestOverlay(ctx).apply {
            show(message, suggestions) { selected ->
                // 用户点选一条建议 → 发送
                val cid = contactId.toString()
                scope.launch {
                    if (XinyuHook.sendMessage(cid, selected)) {
                        runCatching { ApiClient.reportReply("xinyu", cid, selected) }
                    }
                }
            }
        }
    }

    fun hideOverlay() {
        overlay?.dismiss()
        overlay = null
    }
}
