package com.aisocial.agent

import android.app.Application
import android.content.Context
import com.aisocial.agent.data.AppPrefs
import com.aisocial.agent.hook.AutoReplyEngine
import com.aisocial.agent.hook.XinyuHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed 入口：Hook 心遇（com.netease.moyi）。
 *
 * 全本地架构：消息 Hook → 手机直连 AI API 生成回复 → 发送/悬浮窗，
 * 不依赖任何中间服务器。
 *
 * 注意：心遇的 IM（网易云信 nimlib）运行在 :core 进程，
 * Hook 对所有进程安装，收发消息跨进程调用 SDK 均可。
 */
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        val process = lpparam.processName
        XposedBridge.log("[AISocial] 检测到心遇进程：$process")
        classLoaderRef = lpparam.classLoader

        // 初始化本地配置存储（系统 Context，与进程无关）
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
        AppPrefs.init(context)
        XposedBridge.log("[AISocial] 模块启用：${AppPrefs.isEnabled}（进程 $process）")

        // Application.attach 后再安装各组件（悬浮窗需要 Application Context）
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as? Application
                    if (app != null) {
                        com.aisocial.agent.hook.SuggestOverlay.install(app)
                    }
                    XinyuHook.install(lpparam.classLoader, AutoReplyEngine.instance)
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
    }
}
