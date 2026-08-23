package com.aisocial.agent.ui

import android.app.Activity
import android.os.Bundle
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
import com.aisocial.agent.data.AppPrefs
import com.aisocial.agent.data.LicenseManager
import com.aisocial.agent.hook.SuggestOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 配置界面：服务器地址 / JWT / 卡密激活 / 模块开关 / 自动模式。
 * 纯动态布局，无 XML 资源，便于独立编译。
 */
class ConfigActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var etServer: EditText
    private lateinit var etJwt: EditText
    private lateinit var etLicense: EditText
    private lateinit var cbEnabled: CheckBox
    private lateinit var cbAuto: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadFromPrefs()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(title("AI Social Agent 配置"))
        root.addView(hint("后端地址（如 http://192.168.1.100:8000，模拟器用 http://10.0.2.2:8000）"))
        etServer = input("服务器地址")
        root.addView(etServer)

        root.addView(hint("JWT Token（后台登录 /auth/login 获取）"))
        etJwt = input("JWT Token")
        root.addView(etJwt)

        root.addView(hint("卡密（XXXX-XXXX-XXXX，激活后自动回复才会工作）"))
        etLicense = input("卡密")
        root.addView(etLicense)

        cbEnabled = CheckBox(this).apply { text = "启用自动回复模块" }
        cbAuto = CheckBox(this).apply { text = "自动模式（收到消息直接自动回复，关闭则弹建议悬浮窗）" }
        root.addView(cbEnabled)
        root.addView(cbAuto)

        root.addView(space())

        val btnActivate = Button(this).apply { text = "激活卡密" }
        btnActivate.setOnClickListener { activate() }
        root.addView(btnActivate)

        val btnTest = Button(this).apply { text = "测试连接（GET /health）" }
        btnTest.setOnClickListener { testConnection() }
        root.addView(btnTest)

        val btnSave = Button(this).apply { text = "保存设置" }
        btnSave.setOnClickListener { saveToPrefs() }
        root.addView(btnSave)

        val btnGrantOverlay = Button(this).apply { text = "授予悬浮窗权限" }
        btnGrantOverlay.setOnClickListener {
            runCatching {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName"),
                    ),
                )
            }
        }
        root.addView(btnGrantOverlay)

        return root
    }

    private fun loadFromPrefs() {
        etServer.setText(AppPrefs.serverUrl)
        etJwt.setText(AppPrefs.jwtToken)
        etLicense.setText(AppPrefs.licenseCode)
        cbEnabled.isChecked = AppPrefs.isEnabled
        cbAuto.isChecked = AppPrefs.isAutoMode
        SuggestOverlay.install(applicationContext)
    }

    private fun saveToPrefs() {
        AppPrefs.serverUrl = etServer.text.toString()
        AppPrefs.jwtToken = etJwt.text.toString()
        AppPrefs.licenseCode = etLicense.text.toString()
        AppPrefs.isEnabled = cbEnabled.isChecked
        AppPrefs.isAutoMode = cbAuto.isChecked
        toast("已保存")
    }

    private fun activate() {
        saveToPrefs()
        if (etLicense.text.isNullOrBlank()) {
            toast("请先填写卡密")
            return
        }
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    LicenseManager.activate(etLicense.text.toString(), deviceFingerprint())
                }
            }.getOrElse {
                toast("激活失败：${it.message}")
                return@launch
            }
            if (ok) toast("卡密激活成功") else toast("卡密激活失败（无效/已被绑定/过期）")
        }
    }

    private fun testConnection() {
        saveToPrefs()
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    // 有 JWT 则刷新卡密状态；无则仅探测服务器可达性
                    if (AppPrefs.jwtToken.isNotBlank()) {
                        LicenseManager.refresh()
                    } else {
                        com.aisocial.agent.api.ApiClient.health()
                    }
                }
            }.getOrElse {
                toast("连接失败：${it.message}")
                return@launch
            }
            toast(if (ok == true) "连接正常，卡密有效" else "连接正常")
        }
    }

    private fun deviceFingerprint(): String {
        val existing = AppPrefs.deviceFingerprint
        if (existing.isNotBlank()) return existing
        val fp = "android-" + android.os.Build.MODEL.replace(" ", "-") + "-" +
            android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        AppPrefs.deviceFingerprint = fp
        return fp
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
