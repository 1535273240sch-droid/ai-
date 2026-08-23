package com.aisocial.agent.hook

import com.aisocial.agent.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/** Hook 到消息后的回调 */
interface MessageListener {
    /** 对方发来消息（contactId 为平台侧会话/对方账号 ID） */
    fun onMessageReceived(contactId: String, content: String)

    /** 我方发出消息 */
    fun onMessageSent(contactId: String, content: String)
}

/**
 * 心遇（com.netease.moyi）消息 Hook。
 *
 * 逆向定稿方案（2026-08-23，APK 2.29.0，见 dump/hook_targets_sdk.txt）：
 * 全部基于**网易云信 SDK 官方未混淆 API**，不依赖心遇自身混淆业务类，跨版本稳定：
 * - 收消息：Hook `com.netease.nimlib.sdk.msg.MsgServiceObserve.observeReceiveMessage(Observer, boolean)`，
 *   对注册的 Observer 实例 hook `onEvent(Object)`，参数为 `List<IMMessage>`；
 *   IMMessage 提供 `getSessionId()` / `getFromAccount()` / `getContent()` / `getMsgType()` / `getTime()`
 * - 发消息：直接调用 SDK `MessageBuilder.createTextMessage(sessionId, SessionTypeEnum.P2P, text)`
 *   生成 IMMessage，再经 `NIMClient.getService(MsgService.class).sendMessage(IMMessage, false)` 发送
 *
 * 注意：IM 回调在 `:core` 进程（nimlib 所在进程）触发，MainHook 需对所有进程安装；
 * SDK 支持跨进程 API 调用，发送可在任一进程执行。
 */
object XinyuHook {

    private const val NIM_SDK = "com.netease.nimlib.sdk"
    private const val TAG = "AISocial"

    /** 已 hook 过的 Observer 实例，避免重复 hook（observeReceiveMessage 会被多次注册） */
    private val hookedObservers = ConcurrentHashMap.newKeySet<Any>()

    fun install(classLoader: ClassLoader, listener: MessageListener) {
        XposedBridge.log("$TAG: 安装心遇消息 Hook（SDK observeReceiveMessage 方案）")
        hookMessageReceived(classLoader, listener)
    }

    /** 收消息：hook SDK 观察者注册点，再 hook 具体 Observer 的 onEvent */
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

    /** 解析 List<IMMessage>，提取对方发来的文本消息 */
    private fun handleIncomingMessages(any: Any?, listener: MessageListener) {
        if (any !is List<*>) return
        for (item in any) {
            if (item == null) continue
            runCatching {
                val sessionId = XposedHelpers.callMethod(item, "getSessionId") as? String
                    ?: return@runCatching
                val content = XposedHelpers.callMethod(item, "getContent") as? String
                    ?: return@runCatching
                val fromAccount = XposedHelpers.callMethod(item, "getFromAccount") as? String ?: ""
                if (content.isBlank()) return@runCatching
                // 忽略自己发送/系统消息
                if (fromAccount == sessionId) return@runCatching
                listener.onMessageReceived(sessionId, content)
            }
        }
    }

    /**
     * 发送文本消息：SDK 官方 API（MessageBuilder.createTextMessage + MsgService.sendMessage）。
     * @return true=SDK 调用成功（最终发送状态由 SDK 回调决定）
     */
    fun sendMessage(contactId: String, content: String): Boolean {
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
            XposedBridge.log("$TAG: 已调用 SDK 发送 -> [$contactId] $content")
            true
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: SDK 发送调用失败: ${t.message}")
            false
        }
    }
}
