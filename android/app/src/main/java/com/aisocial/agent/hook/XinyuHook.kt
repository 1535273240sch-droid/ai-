package com.aisocial.agent.hook

import com.aisocial.agent.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/** Hook 到消息后的回调 */
interface MessageListener {
    /** 对方发来的单聊文本消息（contactId 为对方账号） */
    fun onMessageReceived(contactId: String, content: String)
}

/**
 * 心遇（com.netease.moyi）消息 Hook。
 *
 * 全部基于网易云信 SDK 官方未混淆 API，跨版本稳定：
 * - 收消息：Hook MsgServiceObserve.observeReceiveMessage(Observer, boolean)，
 *   再 hook Observer.onEvent(Object)，参数为 List<IMMessage>
 * - 发消息：MessageBuilder.createTextMessage(sessionId, P2P, text)
 *   + NIMClient.getService(MsgService).sendMessage(msg, false)
 *
 * 入站过滤（审查修复）：只放行 P2P + 文本 + 方向为 In 的消息，
 * 防止：群聊误回复、图片/语音/系统消息喂给 AI、自己发的消息触发自对话循环。
 */
object XinyuHook {

    private const val NIM_SDK = "com.netease.nimlib.sdk"
    private const val TAG = "AISocial"

    /** 已 hook 过的 Observer 实例，避免重复 hook */
    private val hookedObservers = ConcurrentHashMap.newKeySet<Any>()

    fun install(classLoader: ClassLoader, listener: MessageListener) {
        XposedBridge.log("$TAG: 安装心遇消息 Hook（SDK observeReceiveMessage 方案）")
        hookMessageReceived(classLoader, listener)
    }

    private fun hookMessageReceived(classLoader: ClassLoader, listener: MessageListener) {
        try {
            val observeClass = XposedHelpers.findClass("$NIM_SDK.msg.MsgServiceObserve", classLoader)
            val observerClass = XposedHelpers.findClass("$NIM_SDK.Observer", classLoader)
            XposedHelpers.findAndHookMethod(
                observeClass,
                "observeReceiveMessage",
                observerClass,
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runCatching {
                            val observer = param.args.getOrNull(0) ?: return
                            if (!hookedObservers.add(observer)) return
                            XposedBridge.hookMethod(
                                XposedHelpers.findMethodExact(
                                    observer.javaClass, "onEvent", Any::class.java,
                                ),
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(p: MethodHookParam) {
                                        runCatching { handleIncomingMessages(p.args.getOrNull(0), listener) }
                                    }
                                },
                            )
                            XposedBridge.log("$TAG: observeReceiveMessage 观察者已 hook")
                        }
                    }
                },
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: observeReceiveMessage Hook 失败: ${t.message}")
        }
    }

    /** 解析 List<IMMessage>，只放行：单聊(P2P) + 文本(text) + 收到(In) 的消息 */
    private fun handleIncomingMessages(any: Any?, listener: MessageListener) {
        if (any !is List<*>) return
        for (item in any) {
            if (item == null) continue
            runCatching {
                // 只处理单聊（群聊 Team 消息绝不自动回复，防止在群里刷屏）
                val sessionType = XposedHelpers.callMethod(item, "getSessionType") as? Enum<*>
                    ?: return@runCatching
                if (sessionType.name != "P2P") return@runCatching

                // 只处理文本消息（图片/语音/视频/系统通知的 content 不是聊天文本）
                val msgType = XposedHelpers.callMethod(item, "getMsgType") as? Enum<*>
                    ?: return@runCatching
                if (msgType.name != "text") return@runCatching

                // 只处理对方发来的消息（Out = 自己发的，防 AI 跟自己无限对话）
                val direct = XposedHelpers.callMethod(item, "getDirect") as? Enum<*>
                    ?: return@runCatching
                if (direct.name != "In") return@runCatching

                val sessionId = XposedHelpers.callMethod(item, "getSessionId") as? String
                    ?: return@runCatching

                // 文本内容：优先 getTextContent()，回退 getContent()（不同 SDK 版本返回类型不一）
                val content = extractText(item)
                if (content.isNullOrBlank()) return@runCatching

                XposedBridge.log("$TAG: 收到文本消息 [$sessionId]（${content.length}字）")
                listener.onMessageReceived(sessionId, content)
            }
        }
    }

    /** 提取文本：getTextContent() → getContent() as String → 附件 getText 反射 */
    private fun extractText(item: Any): String? {
        runCatching {
            XposedHelpers.callMethod(item, "getTextContent")?.let { return it as? String }
        }
        runCatching {
            (XposedHelpers.callMethod(item, "getContent") as? String)?.let { return it }
        }
        runCatching {
            val attachment = XposedHelpers.getObjectField(item, "attachment")
                ?: XposedHelpers.callMethod(item, "getAttachment")
            attachment?.let {
                XposedHelpers.callMethod(it, "getText")?.let { txt -> return txt as? String }
            }
        }
        return null
    }

    /**
     * 发送文本消息（P2P）。
     * @return true=SDK 调用成功
     */
    fun sendMessage(contactId: String, content: String): Boolean {
        if (content.isBlank()) return false
        val classLoader = MainHook.classLoaderRef ?: return false
        return try {
            val builder = XposedHelpers.findClass("$NIM_SDK.msg.MessageBuilder", classLoader)
            val sessionTypeEnum = XposedHelpers.findClass("$NIM_SDK.msg.constant.SessionTypeEnum", classLoader)
            val p2p = XposedHelpers.getStaticObjectField(sessionTypeEnum, "P2P")
            val msg = XposedHelpers.callStaticMethod(builder, "createTextMessage", contactId, p2p, content)
            val nimClient = XposedHelpers.findClass("$NIM_SDK.NIMClient", classLoader)
            val msgServiceClazz = XposedHelpers.findClass("$NIM_SDK.msg.MsgService", classLoader)
            val msgService = XposedHelpers.callStaticMethod(nimClient, "getService", msgServiceClazz)
            XposedHelpers.callMethod(msgService, "sendMessage", msg, false)
            // 日志脱敏：只记录长度，不落聊天内容
            XposedBridge.log("$TAG: 已调用 SDK 发送 -> [$contactId]（${content.length}字）")
            true
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: SDK 发送调用失败: ${t.message}")
            false
        }
    }
}
