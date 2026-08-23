# AI Social Agent

面向「心遇」聊天软件（网易，包名 `com.netease.moyi`，IM 底层网易云信 `com.netease.nimlib`）的**可扩展多平台 AI 自动回复助手**。按《企业级架构方案说明书》框架交付三个源码子项目：FastAPI 后端、Web 管理后台、Android Xposed 模块。**本工程交付源码，不打包 APK、不提供无障碍与微信伪装规避功能。**

## 架构

```
┌──────────────┐   HTTP/API + WebSocket    ┌──────────────────┐
│ Android 模块  │ ───────────────────────▶ │ FastAPI 后端      │
│ (Xposed 钩子) │ ◀─────────────────────── │ 认证/卡密/Agent/… │
└──────────────┘   suggestion / kill_switch└────────┬─────────┘
                                                    │
┌──────────────┐                                    │
│ Web 管理后台   │ ─────────────────────────────────┘
│ (React+AntD) │   运营配置：人设/画像/卡密/审计
└──────────────┘
```

- **backend/**：FastAPI + SQLAlchemy + SQLite。JWT 认证、卡密授权（吊销即 Kill Switch）、联系人画像、人设、短期记忆、OpenAI 兼容模型网关（无 key 时降级 mock）、AI 决策 Agent（敏感词 half 模式转人工、节奏调度 delay_ms）、WebSocket 推送、审计日志、管理看板。
- **admin-web/**：React 18 + Vite 5 + TS + Ant Design 5。仪表盘 / 联系人画像 / 人设 / 卡密管理（批量生成 + 吊销）/ 审计日志。
- **android/**：Kotlin + Xposed（参考 WeChatAIAutoReply 源码结构）。`api/`（后端客户端）、`data/`（配置与卡密）、`hook/`（心遇消息 Hook + 自动回复引擎 + 建议悬浮窗）、`net/`（WebSocket 收建议与 Kill Switch）、`ui/`（配置界面）。

## 快速开始

### 1. 后端（Python 3.12+）

```bash
cd backend
py -m pip install -r requirements.txt --proxy ""   # 本机需绕过本地代理时加 --proxy ""
py -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

启动自动建表并创建默认管理员 `admin / admin123456`（请尽快修改）。AI 网关未配置 `OPENAI_API_KEY` 时返回 mock 建议，便于无 key 联调；配置项见 `backend/README.md`。

### 2. 管理后台

```bash
cd admin-web
npm install
npm run dev      # http://localhost:5173，/api 代理到 http://localhost:8000
# 或 npm run build 产物在 dist/
```

### 3. Android 模块

```bash
cd android
# 需要 Android SDK；构建：
gradle :app:assembleDebug
```

安装后在 LSPosed 中启用并勾选作用域「心遇」，打开「AI Social Agent」配置：填后端地址（局域网 IP，模拟器用 `10.0.2.2`）、后台登录拿到的 JWT、卡密，激活后开启自动回复。

## 使用流程

1. 后台登录 → 卡密管理 → 批量生成卡密
2. Android 端配置 → 激活卡密
3. 后台 → 人设（默认人设）/ 联系人（画像：关系、风格、禁忌话题）
4. 收到消息：自动模式直接按 `delay_ms` 自然延迟回复；建议模式弹悬浮窗 3 条建议供点选
5. 敏感词（转账/借钱/验证码…）命中 → 转人工不自动回复
6. 吊销卡密 → 该实例所有自动化立即停止（Kill Switch，WebSocket 推送）

## 验证状态

| 子项目 | 验证 |
|---|---|
| backend | ✅ `pytest` 19 用例全过；✅ `scripts/smoke_test.py` 全链路冒烟通过（全新库） |
| admin-web | ✅ `tsc` + `vite build` 构建通过（产物 dist/） |
| android | ⚠️ 源码骨架完成，**未构建验证**（本机无 Android SDK/Gradle 环境） |

## 遗留 TODO（APK 逆向后填充）

- `android/.../hook/XinyuHook.kt`：心遇消息收发的方法签名级 Hook 点（类名/方法名以实际 APK 逆向为准；注意 IM 逻辑在 `:core` 进程）
- `android/.../hook/XinyuHook.kt#sendMessage`：实际发送消息的调用实现（当前返回 false 占位）

> 已实现：平台联系人 ID → 后端 contact_id 自动映射（先查本地缓存 → 拉 /contacts 匹配 → 自动创建），见 `AutoReplyEngine.resolveContactId`。

## 文档

- API 契约：`docs/API_CONTRACT.md`
- 参考源码（WeChatAIAutoReply 结构）：`客户专用审查/apk_fix_source_code.md`
