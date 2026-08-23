package com.aisocial.agent.ui

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import com.aisocial.agent.api.AIClient
import com.aisocial.agent.data.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 配置界面（全本地）：API 地址 / API Key / 模型名 / 人设 / 开关。
 * 纯动态布局，无 XML 资源。
 */
class ConfigActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var etPersona: EditText
    private lateinit var cbEnabled: CheckBox
    private lateinit var cbAuto: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 本 Activity 运行在模块自身进程，必须自己初始化 AppPrefs
        AppPrefs.init(applicationContext)
        setContentView(buildUi())
        loadFromPrefs()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(title("AI Social Agent 配置"))

        root.addView(hint("API 地址（OpenAI 兼容，填到 /v1，如 https://api.openai.com/v1 或中转地址）"))
        etApiUrl = input("https://api.openai.com/v1")
        root.addView(etApiUrl)

        root.addView(hint("API Key"))
        etApiKey = input("sk-...").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(etApiKey)

        root.addView(hint("模型名（如 gpt-4o-mini / deepseek-chat，留空默认 gpt-4o-mini）"))
        etModel = input(AIClient.DEFAULT_MODEL)
        root.addView(etModel)

        root.addView(hint("人设（你希望 AI 用什么身份/口吻回复，留空用默认）"))
        etPersona = input("例：28岁男性，性格幽默，说话简短").apply {
            minLines = 2
            gravity = Gravity.TOP
        }
        root.addView(etPersona)

        cbEnabled = CheckBox(this).apply { text = "启用自动回复模块" }
        cbAuto = CheckBox(this).apply { text = "自动模式（直接自动回复；关闭则弹 3 条建议悬浮窗手动选）" }
        root.addView(cbEnabled)
        root.addView(cbAuto)

        root.addView(space())

        val btnTest = Button(this).apply { text = "测试 API 连通" }
        btnTest.setOnClickListener { testApi() }
        root.addView(btnTest)

        val btnSave = Button(this).apply { text = "保存设置" }
        btnSave.setOnClickListener { saveToPrefs() }
        root.addView(btnSave)

        val btnGrantOverlay = Button(this).apply { text = "授予悬浮窗权限（建议模式需要，跳到心遇的授权页）" }
        btnGrantOverlay.setOnClickListener {
            runCatching {
                // 悬浮窗挂在心遇进程，系统考核的是心遇的授权，所以跳心遇的授权页
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:com.netease.moyi"),
                    ),
                )
            }
        }
        root.addView(btnGrantOverlay)

        root.addView(space())
        root.addView(hint("1. 模块需在 LSPosed 启用并勾选「心遇」作用域后重启手机。"))
        root.addView(hint("2. 修改配置后需重启心遇（或重启手机）才对新消息生效。"))
        root.addView(hint("3. 仅自动回复单聊文本消息；群聊、图片语音不处理。"))

        return ScrollView(this).apply { addView(root) }
    }

    private fun loadFromPrefs() {
        etApiUrl.setText(AppPrefs.apiUrl)
        etApiKey.setText(AppPrefs.apiKey)
        etModel.setText(AppPrefs.modelName)
        etPersona.setText(AppPrefs.personaPrompt)
        cbEnabled.isChecked = AppPrefs.isEnabled
        cbAuto.isChecked = AppPrefs.isAutoMode
    }

    private fun saveToPrefs() {
        AppPrefs.apiUrl = etApiUrl.text.toString()
        AppPrefs.apiKey = etApiKey.text.toString()
        AppPrefs.modelName = etModel.text.toString()
        AppPrefs.personaPrompt = etPersona.text.toString()
        AppPrefs.isEnabled = cbEnabled.isChecked
        AppPrefs.isAutoMode = cbAuto.isChecked
        toast("已保存")
    }

    private fun testApi() {
        saveToPrefs()
        if (AppPrefs.apiUrl.isBlank() || AppPrefs.apiKey.isBlank()) {
            toast("请先填写 API 地址和 Key")
            return
        }
        toast("测试中…")
        scope.launch {
            // AIClient 内部已切换 IO，无需再套 withContext
            runCatching { AIClient.testApi() }
                .onSuccess { toast("API 正常，模型回复：$it") }
                .onFailure { toast("API 测试失败：${it.message?.take(120)}") }
        }
    }

    // ---------------- UI helpers ----------------
    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 22f
        setPadding(0, 0, 0, 16)
    }

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(0xFF888888.toInt())
        setPadding(0, 8, 0, 4)
    }

    private fun input(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
    }

    private fun space(): View = Space(this).apply { minimumHeight = 24 }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
