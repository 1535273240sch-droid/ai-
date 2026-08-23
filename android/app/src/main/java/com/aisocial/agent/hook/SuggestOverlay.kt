package com.aisocial.agent.hook

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮窗：展示 AI 生成的 3 条回复建议，点选后回调发送。
 *
 * 审查修复：
 * - addView 前检查悬浮窗权限（本窗口挂在心遇进程，考核的是心遇的授权），
 *   无权限 → 不再静默假装成功，回调 onPermissionDenied 让引擎降级提示
 * - FLAG_SECURE：悬浮窗内容不出现在截屏/录屏里
 * - 60 秒未操作自动消失（防聊天内容常驻屏幕）
 */
class SuggestOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var onPermissionDenied: (() -> Unit)? = null

    private val autoDismissRunnable = Runnable { dismiss() }

    companion object {
        private const val AUTO_DISMISS_MS = 60_000L

        /** 注入的是心遇 Application Context（Hook 侧唯一可用的宿主上下文） */
        @Volatile
        var appContext: Context? = null
            private set

        fun install(ctx: Context) {
            appContext = ctx.applicationContext
        }
    }

    fun show(message: String, suggestions: List<String>, onPick: (String) -> Unit) {
        dismiss()

        // 权限考核的是宿主（心遇）包——没授权就明确失败，不静默
        if (!Settings.canDrawOverlays(context)) {
            onPermissionDenied?.invoke()
            return
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 160
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = android.graphics.drawable.ColorDrawable(0xEEFFFFFF.toInt())
        }

        panel.addView(TextView(context).apply {
            text = "AI 建议（${message.take(20)}）"
            setTextColor(0xFF333333.toInt())
            textSize = 14f
        })

        suggestions.forEachIndexed { i, s ->
            panel.addView(Button(context).apply {
                text = "${i + 1}. ${s.take(60)}"
                setOnClickListener {
                    dismiss()
                    onPick(s)
                }
            })
        }

        val added = runCatching { windowManager.addView(panel, lp) }.isSuccess
        if (!added) {
            onPermissionDenied?.invoke()
            return
        }
        rootView = panel
        mainHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
    }

    fun setOnPermissionDenied(callback: () -> Unit) {
        onPermissionDenied = callback
    }

    fun dismiss() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
    }
}
