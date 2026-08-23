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
 * 配置读取：本进程（心遇）通过 XSharedPreferences 读模块包的配置文件，
 * ConfigActivity 在模块进程写入 —— 修复系统 Context 读写错位的 P0 问题。
 *
 * IM 回调在 :core 进程，handleLoadPackage 对每个进程执行，统一安装。
 */
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        XposedBridge.log("[AISocial] 检测到心遇进程：${lpparam.processName}")
        classLoaderRef = lpparam.classLoader

        // 读取端：XSharedPreferences 直读模块配置文件（不再用系统 Context 的 prefs）
        AppPrefs.initForHook()
        AppPrefs.reloadForHook()
        XposedBridge.log("[AISocial] 模块启用：${AppPrefs.isEnabled}（API已配置：${AppPrefs.apiUrl.isNotBlank() && AppPrefs.apiKey.isNotBlank()}）")

        // Application.attach 后安装各组件（悬浮窗需要心遇 Application Context）
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

        /** 目标进程 classLoader，供 XinyuHook.sendMessage 调用 SDK 时使用 */
        @Volatile
        var classLoaderRef: ClassLoader? = null
            private set
    }
}
