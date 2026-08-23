package com.aisocial.agent

import android.app.Application
import android.content.Context
import com.aisocial.agent.data.AppPrefs
import com.aisocial.agent.hook.AutoReplyEngine
import com.aisocial.agent.hook.XinyuHook
import com.aisocial.agent.net.WsClient
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed 入口：Hook 心遇（com.netease.moyi）。
 *
 * 注意：心遇的 IM 逻辑（网易云信 nimlib）运行在独立进程 `:core`，
 * 消息收发的 Hook 类所在 classLoader 需按实际 APK 逆向结果确认。
 */
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        val process = lpparam.processName
        XposedBridge.log("[AISocial] 检测到心遇进程：$process")
        classLoaderRef = lpparam.classLoader

        // 初始化配置存储（用系统 Context，与具体进程无关）
        val context = runCatching {
            val activityThread = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                "currentActivityThread",
            )
            XposedHelpers.callMethod(activityThread, "getSystemContext") as Context
        }.getOrNull()
        if (context == null) {
            XposedBridge.log("[AISocial] 获取系统 Context 失败，中止 Hook")
            return
        }
        AppPrefs.init(context.applicationContext)

        // 心遇目标进程分布（需按 APK 逆向最终确认）：
        // - 主进程：UI、发送消息入口
        // - :core 进程：IM 消息收发（nimlib 回调）
        // 骨架先对所有进程安装统一入口，方法签名级 Hook 点见 XinyuHook.TODO。
        XposedBridge.log("[AISocial] 模块启用：${AppPrefs.isEnabled()}")

        // 监听 Application.attach，确保主进程 Application 完整后再初始化
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as? Application
                    if (app != null) {
                        // 注入悬浮窗 / 引擎所需的 Application Context
                        com.aisocial.agent.hook.SuggestOverlay.install(app)
                    }
                    XinyuHook.install(lpparam.classLoader, AutoReplyEngine.instance)
                    WsClient.startIfConfigured(lpparam.classLoader)
                }
            },
        )
    }

    companion object {
        /** 心遇包名（网易，IM 底层网易云信 nimlib） */
        const val TARGET_PACKAGE = "com.netease.moyi"
        /** IM 逻辑所在独立进程 */
        const val CORE_PROCESS = ":core"

        /** 目标进程 classLoader，供 XinyuHook.sendMessage 调用 SDK 时使用 */
        @Volatile
        var classLoaderRef: ClassLoader? = null
            private set

        /** 记录目标进程 classLoader（供其他 Hook 工具使用） */
        fun rememberClassLoader(cl: ClassLoader) {
            classLoaderRef = cl
        }
    }
}
