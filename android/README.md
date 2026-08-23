# AI Social Agent — Android Xposed 插件（全本地版）

Hook 心遇（`com.netease.moyi`）的 AI 自动回复助手 Xposed 模块。

**全本地架构**：手机直连 OpenAI 兼容 AI 接口生成回复，不经任何中间服务器。
API 地址 / API Key / 模型名 / 人设全部在手机配置界面填写。

## 功能

- 收到心遇新消息 → 手机直连 AI API（OpenAI 兼容 /chat/completions）生成 3 条候选回复
- **建议模式**：悬浮窗展示 3 条建议，点选后直接发送
- **自动模式**：模拟真人打字延迟（1.5~4 秒）后自动发送第 1 条
- 人设可自定义（口吻/身份/禁忌），容错解析（JSON 数组 → 按行 → 整段文本）
- 配置界面自带「测试 API 连通」按钮

## 使用

1. 手机需 root + LSPosed，安装本 APK，LSPosed 中启用模块并勾选「心遇」作用域，重启
2. 打开「AI Social Agent 配置」：填 API 地址（到 /v1）、API Key、模型名、人设
3. 点「测试 API 连通」确认可用 → 保存 → 打开「启用自动回复模块」
4. 建议模式需授予悬浮窗权限

## 工程结构

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── app/libs/stubjar-gradle/       # Xposed API 本地 stub（编译用，运行时由框架提供）
└── app/src/main/
    ├── AndroidManifest.xml        # Xposed 模块声明 + 配置界面入口 + 悬浮窗权限
    ├── assets/xposed_init         # com.aisocial.agent.MainHook
    ├── res/values/arrays.xml      # xposed_scope = com.netease.moyi
    └── java/com/aisocial/agent/
        ├── MainHook.kt            # Xposed 入口：按进程初始化
        ├── api/AIClient.kt        # 直连 AI API（OkHttp+Gson，生成3条建议+容错解析）
        ├── data/AppPrefs.kt       # 本地配置（API地址/Key/模型/人设/开关）
        ├── hook/XinyuHook.kt      # 心遇 Hook（网易云信 SDK 收/发消息）
        ├── hook/AutoReplyEngine.kt# 消息 → AI 生成 → 自动发送/悬浮窗
        ├── hook/SuggestOverlay.kt # 悬浮窗（3 条建议，点选发送）
        └── ui/ConfigActivity.kt   # 配置界面（纯动态布局）
```

## 构建

```
gradle -p android :app:assembleDebug
```

Hook 原理：网易云信 SDK 官方未混淆 API（`MsgServiceObserve.observeReceiveMessage` 收、
`MessageBuilder.createTextMessage` + `MsgService.sendMessage` 发），跨版本稳定，
IM 回调在 `:core` 进程，所有进程统一安装。
