# AI Social Agent — Android Xposed 插件（源码工程）

Hook 心遇（`com.netease.moyi`）的 AI 自动回复助手 Xposed 模块。**仅交付源码，不打包 APK。**

## 功能

- 收到心遇新消息 → 校验开关/卡密（Kill Switch）→ 上报后端 → 后端 AI 生成 3 条回复建议
- **建议模式**：悬浮窗展示 3 条建议，点选后写入输入框发送
- **自动模式**：按后端节奏调度（delay_ms）直接自动发送第 1 条建议
- 卡密激活（绑定设备指纹）、WebSocket 实时接收 `kill_switch`（吊销即停）
- 联系人在后端自动创建/匹配（按平台联系人 ID）

## 工程结构

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
└── app/src/main/
    ├── AndroidManifest.xml          # Xposed 模块声明 + 配置界面入口 + 悬浮窗权限
    ├── assets/xposed_init           # com.aisocial.agent.MainHook
    ├── res/values/arrays.xml        # xposed_scope = com.netease.moyi
    └── java/com/aisocial/agent/
        ├── MainHook.kt              # Xposed 入口：按进程初始化
        ├── api/ApiClient.kt         # 后端 API（登录/卡密/建议/联系人/平台上报）
        ├── data/AppPrefs.kt         # 配置存储（服务器/JWT/卡密/开关/画像/talker映射）
        ├── data/Profile.kt          # 联系人画像（关系/风格/频率/句式/禁忌）
        ├── data/LicenseManager.kt   # 卡密状态 + Kill Switch 置位
        ├── hook/XinyuHook.kt        # 心遇 Hook 点（收/发消息，MessageListener）
        ├── hook/AutoReplyEngine.kt  # 消息 → 决策 → 建议/自动发送
        ├── hook/SuggestOverlay.kt   # 悬浮窗（3 条建议，点选发送）
        ├── net/WsClient.kt          # WebSocket：kill_switch / suggestion 推送
        └── ui/ConfigActivity.kt     # 配置界面（服务器/JWT/卡密/开关/悬浮窗授权）
```

## 与 WeChatAIAutoReply 参考源码的对应

| 参考源码 | 本项目 | 差异 |
|---|---|---|
| MainHook（Hook com.tencent.mm） | MainHook.kt（Hook com.netease.moyi） | 目标换心遇；按进程初始化 |
| AIClient（直接调 OpenAI） | ApiClient（优先后端 /agent/suggest） | 决策移到后端 Agent |
| AppPrefs | AppPrefs.kt | 增加服务器/JWT/卡密/talker 映射 |
| Profile | Profile.kt | 字段一致 |
| SuggestionOverlay（悬浮窗） | SuggestOverlay.kt | 同思路，Kotlin 实现 |
| ConfigActivity | ConfigActivity.kt | 增加卡密激活/测试连接 |

## ✅ Hook 方案已定稿（2026-08-23，APK 2.29.0 逆向）

全部基于**网易云信 SDK 官方未混淆 API**（`dump/hook_targets_sdk.txt` 验证），不依赖心遇自身混淆业务类，跨版本稳定：

| 方向 | 方案 | 验证到的签名 |
|---|---|---|
| 收消息 | Hook `MsgServiceObserve.observeReceiveMessage(Observer, boolean)`，再 hook Observer 实例 `onEvent(List<IMMessage>)`，提取 `getSessionId/getFromAccount/getContent` | `observeReceiveMessage(Lcom/netease/nimlib/sdk/Observer;, Z) -> V` ✅ |
| 发消息 | 直接调 SDK `MessageBuilder.createTextMessage(sessionId, SessionTypeEnum.P2P, text)` + `NIMClient.getService(MsgService).sendMessage(IMMessage, false)` | `createTextMessage(Ljava/lang/String;, SessionTypeEnum;, Ljava/lang/String;) -> IMMessage` ✅ |

已实现于 `hook/XinyuHook.kt`（install 收消息 + sendMessage 发送）。IM 回调在 `:core` 进程触发，MainHook 对全部进程安装 Hook。

## 遗留（可选优化，不阻塞运行）

- `AutoReplyEngine.insertToInput`：点选建议后写输入框的 `setText` 调用——当前建议模式点选已直接走 SDK 发送，此方法可留作"手动确认模式"扩展

逆向材料可参考：`C:\Users\Administrator\Desktop\apk_work\dump\classes.txt`、`manifest.xml`。

## 构建说明

用 Android Studio 打开本目录即可构建（需要 Android SDK 34+、JDK 17）。依赖：Xposed API 82（compileOnly）、OkHttp 4.12、Gson 2.11、Kotlin 协程 1.8。

```bash
# 命令行构建（需 Android SDK 环境）
./gradlew :app:assembleDebug
```
