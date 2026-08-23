package com.aisocial.agent.hook

import com.aisocial.agent.api.AIClient
import com.aisocial.agent.data.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * 自动回复引擎（全本地）：
 * 1. 开关 + API 配置检查（不满足 → 静默）
 * 2. 手机直连 AI API 生成 3 条候选回复
 * 3. 自动模式 → 模拟人工延迟后发送第 1 条
 * 4. 建议模式 → 悬浮窗展示 3 条，点选发送
 */
object AutoReplyEngine : MessageListener {

    private const val TAG = "AISocial"
    private const val AI_TIMEOUT_MS = 90_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: SuggestOverlay? = null

    val instance: AutoReplyEngine get() = this

    // ---------------- MessageListener ----------------

    override fun onMessageReceived(contactId: String, content: String) {
        if (!AppPrefs.readyForAutoReply()) {
            android.util.Log.d(TAG, "未启用或 API 未配置，跳过消息")
            return
        }
        if (content.isBlank()) return
        scope.launch {
            val suggestions = withTimeoutOrNull(AI_TIMEOUT_MS) {
                runCatching { AIClient.suggestReplies(content) }.onFailure {
                    android.util.Log.w(TAG, "AI 生成失败：${it.message}")
                }.getOrNull()
            }
            if (suggestions.isNullOrEmpty()) return@launch

            if (AppPrefs.isAutoMode) {
                // 模拟真人打字延迟 1.5~4 秒
                delay(1_500L + Random.nextLong(2_500L))
                if (XinyuHook.sendMessage(contactId, suggestions.first())) {
                    android.util.Log.i(TAG, "已自动回复 [$contactId]: ${suggestions.first()}")
                } else {
                    android.util.Log.w(TAG, "自动回复发送失败 [$contactId]")
                }
            } else {
                showOverlay(contactId, content, suggestions)
            }
        }
    }

    override fun onMessageSent(contactId: String, content: String) {
        // 全本地模式：无需上报
    }

    // ---------------- 悬浮窗 ----------------

    private fun showOverlay(contactId: String, message: String, suggestions: List<String>) {
        if (suggestions.isEmpty()) return
        val ctx = SuggestOverlay.appContext ?: return
        overlay = SuggestOverlay(ctx).apply {
            show(message, suggestions) { selected ->
                scope.launch {
                    if (XinyuHook.sendMessage(contactId, selected)) {
                        android.util.Log.i(TAG, "已手动选用回复 [$contactId]: $selected")
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
