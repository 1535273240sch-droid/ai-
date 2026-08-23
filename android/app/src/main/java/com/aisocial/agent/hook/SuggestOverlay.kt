package com.aisocial.agent.hook

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicReference

/**
 * 悬浮窗：展示 AI 生成的 3 条回复建议，点选后回调发送。
 * 使用 TYPE_APPLICATION_OVERLAY，需要 SYSTEM_ALERT_WINDOW 权限。
 */
class SuggestOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var visible = false

    /** 由 AutoReplyEngine 在初始化时注入（hook 场景无法直接拿 Application） */
    companion object {
        @Volatile
        var appContext: Context? = null

        private val holder = AtomicReference<SuggestOverlay?>(null)

        fun install(ctx: Context) {
            appContext = ctx.applicationContext
        }
    }

    fun show(message: String, suggestions: List<String>, onPick: (String) -> Unit) {
        dismiss()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 160
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = android.graphics.drawable.ColorDrawable(0xEEFFFFFF)
        }

        panel.addView(TextView(context).apply {
            text = "AI 建议（$message）"
            setTextColor(0xFF333333.toInt())
            textSize = 14f
        })

        suggestions.forEachIndexed { i, s ->
            panel.addView(Button(context).apply {
                text = "${i + 1}. $s"
                setOnClickListener {
                    dismiss()
                    onPick(s)
                }
            })
        }

        runCatching { windowManager.addView(panel, lp) }
        rootView = panel
        visible = true
    }

    fun dismiss() {
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
        visible = false
    }

    fun isVisible(): Boolean = visible
}
