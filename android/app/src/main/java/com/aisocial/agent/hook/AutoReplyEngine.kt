package com.aisocial.agent.hook

import android.widget.Toast
import com.aisocial.agent.api.AIClient
import com.aisocial.agent.data.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 自动回复引擎（全本地）：
 * 1. 开关 + API 配置检查 → 2. 手机直连 AI 生成 3 条候选
 * 3. 安全闸门：内容不过关 → 降级人工（悬浮窗），绝不自动发
 * 4. 自动模式 → 模拟打字延迟后发送第 1 条；建议模式 → 悬浮窗点选
 * 附：同会话节流，防消息风暴时连环自动回复。
 */
object AutoReplyEngine : MessageListener {

    private const val TAG = "AISocial"
    private const val AI_TIMEOUT_MS = 90_000L

    /** 同一会话两次自动回复的最小间隔（毫秒） */
    private const val PER_CONTACT_THROTTLE_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: SuggestOverlay? = null
    private val lastReplyAt = ConcurrentHashMap<String, Long>()

    val instance: AutoReplyEngine get() = this

    // ---------------- MessageListener ----------------

    override fun onMessageReceived(contactId: String, content: String) {
        // Hook 进程读的是 XSharedPreferences 快照，每条消息前刷新一次
        AppPrefs.reloadForHook()
        if (!AppPrefs.readyForAutoReply()) return
        if (content.isBlank()) return

        // 会话节流：5 秒内同一人连续发多条，只处理第一条（后续消息自然合并进上下文语义）
        val now = System.currentTimeMillis()
        val last = lastReplyAt[contactId] ?: 0L
        if (now - last < PER_CONTACT_THROTTLE_MS) {
            android.util.Log.d(TAG, "会话节流中 [$contactId]，跳过本条")
            return
        }
        lastReplyAt[contactId] = now

        scope.launch {
            val suggestions = withTimeoutOrNull(AI_TIMEOUT_MS) {
                runCatching { AIClient.suggestReplies(content) }.onFailure {
                    android.util.Log.w(TAG, "AI 生成失败：${it.message?.take(120)}")
                }.getOrNull()
            }
            if (suggestions.isNullOrEmpty()) return@launch

            val autoCandidate = suggestions.first()
            val wantAuto = AppPrefs.isAutoMode

            if (wantAuto && SafetyFilter.isSafeToSend(autoCandidate)) {
                // 模拟真人打字延迟 1.5~4 秒
                delay(1_500L + Random.nextLong(2_500L))
                if (XinyuHook.sendMessage(contactId, autoCandidate)) {
                    android.util.Log.i(TAG, "已自动回复 [$contactId]（${autoCandidate.length}字）")
                } else {
                    android.util.Log.w(TAG, "自动回复发送失败 [$contactId]")
                }
            } else {
                if (wantAuto) {
                    android.util.Log.w(TAG, "安全闸门拦截自动发送 [$contactId]，降级为建议模式")
                }
                showOverlay(contactId, content, suggestions)
            }
        }
    }

    // ---------------- 悬浮窗 ----------------

    private fun showOverlay(contactId: String, message: String, suggestions: List<String>) {
        if (suggestions.isEmpty()) return
        val ctx = SuggestOverlay.appContext ?: return
        overlay = SuggestOverlay(ctx).apply {
            show(message, suggestions) { selected ->
                // 人工点选 = 用户明确确认，只拦空白，不做内容过滤
                if (selected.isNotBlank()) {
                    scope.launch {
                        XinyuHook.sendMessage(contactId, selected)
                    }
                }
            }
            setOnPermissionDenied {
                // 悬浮窗无权限（心遇未被授予）：Toast 兜底提示，建议内容仍进日志
                runCatching {
                    Toast.makeText(ctx, "悬浮窗无权限，建议已写入 LSPosed 日志", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
