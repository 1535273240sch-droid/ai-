# AI Social Agent — API 契约（v1）

统一前缀：`/api/v1`。认证：`Authorization: Bearer <JWT>`（admin 角色可访问管理端点）。
所有请求/响应均为 JSON。错误格式：`{"detail": "..."}`（422 校验失败时 detail 为数组，前端会拼接 msg）。

> 安全说明：登录连续失败 5 次/15 分钟锁定（429）；激活按 IP 限频（20 次/15 分钟，429）；`/agent/suggest` 每用户每日配额 2000（429）。`ENV=production` 时强制要求自定义 SECRET_KEY/ADMIN_PASSWORD/CORS 白名单，否则拒绝启动。

## 认证 auth
- `POST /auth/register`  body `{username, password}` → `{token, user:{id, username, role}}`（注册恒为普通用户，admin 由 seed 创建）
- `POST /auth/login`     body `{username, password}` → `{token, user:{id, username, role}}`
- `GET  /auth/me`        → `{user}`

## 卡密 license
- `POST /license/activate`   body `{code, device_fingerprint}` → `{activated: true, license:{code, expires_at, features, status}}`
  - 原子激活，**unused / inactive 均可激活**（注销后可在新设备重新激活）；已吊销/过期/他人占用 → 4xx
- `GET  /license/info`       → `{valid: true, license:{code, expires_at, features, status}}`
- `POST /license/deactivate` body `{device_fingerprint}` → `{ok: true}`（卡密回 inactive，可再激活）
- `POST /license/admin`           body `{count, days, features?}` → `{codes: ["XXXX-XXXX-XXXX", ...]}` (admin)（features 可省略）
- `GET  /license/admin?limit=50&offset=0` → `{items: [{code, status, activated_by, device_fingerprint, expires_at, features, created_at}], total}` (admin)
- `POST /license/admin/{code}/revoke` → `{ok: true}` (admin) — 吊销即 Kill Switch：该卡密对应实例所有自动化立即停止

## 联系人 contacts
- `GET    /contacts` → `{items:[{id, platform, platform_contact_id, nickname, profile, created_at}]}`
- `POST   /contacts` body `{platform, platform_contact_id, nickname, profile:{relationship, interaction_style, reply_frequency, sentence_style, taboos:[...]}}` → `{contact}`
- `PUT    /contacts/{id}` body 同上（可部分字段）→ `{contact}`
- `DELETE /contacts/{id}` → `{ok: true}`

## 人设 personas
- `GET    /personas` → `{items:[{id, name, config, is_default, is_global}]}`  (config 为任意 JSON)
- `POST   /personas` body `{name, config, is_default?, is_global?}` → `{persona}`（**is_global 仅 admin 生效，普通用户强制 false**）
- `PUT    /personas/{id}` → `{persona}`（普通用户不可修改 is_global）
- `DELETE /personas/{id}` → `{ok: true}`

## Agent（AI 回复）
- `POST /agent/suggest` body `{contact_id, message, mode:"suggest"|"auto"|"half"}` → `{suggestions:["...","...","..."], decision:{mode, reason}, delay_ms}`
  - 服务端组装：默认人设 + 联系人画像 + 记忆 + 最近对话 → LLM 生成 3 条建议
  - **敏感词（内置 + 用户 rules.keywords）对 suggest/auto/half 一律命中即转人工**（`decision.mode="manual"`），不可被 mode 参数绕过
  - 生效 mode 以服务端为准：匹配的 active 规则（按联系人或全局）优先于请求参数
  - `delay_ms` 由节奏调度器按消息长度/复杂度计算自然延迟
  - 生成建议后经 WebSocket 推送 `{type:"suggestion",...}` 事件
- `GET  /agent/rules` → `{items:[{id, contact_id?, mode, keywords:[], active, created_at}]}`
- `POST /agent/rules` body `{contact_id?, mode, keywords:[], active}` → `{rule}`
- `PUT  /agent/rules/{id}` / `DELETE /agent/rules/{id}`

## 审计 audit
- `GET /audit/logs?limit=50&offset=0` → `{items:[{id, event, payload, created_at}], total}`

## 看板 dashboard
- `GET /dashboard/stats` → `{message_count, reply_count, active_contacts, persona_count, license_count, active_license_count, recent_logs:[...]}`

## WebSocket
- `WS /ws?token=<JWT>` 推送事件：`{type:"suggestion", contact_id, message, suggestions:[...], delay_ms}`、`{type:"kill_switch", reason}`
  - 连接时校验 JWT 与卡密有效性，无效返回 4401/4403

## 平台 platform（Android 端上报入口）
- `POST /platform/events` body `{platform, platform_contact_id, type:"message_received"|"message_sent"|"online"|"offline", content?, timestamp}` → `{ok:true}`
- `POST /platform/reply` body `{platform, platform_contact_id, content}` → `{ok:true}`（Android 端发送成功后回执）
