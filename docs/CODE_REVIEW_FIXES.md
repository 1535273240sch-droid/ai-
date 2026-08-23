# 代码审查修复清单（CODE REVIEW FIXES）

> 来源：独立审查会话对主会话交付成果的并行审查（后端代码审查 + 前端代码审查/契约核验 + 安全审计 + pytest/构建独立复验，5 个智能体）。
> 日期：2026-08-23。本文件自包含，主会话按清单顺序修复即可，无需其他上下文。

## 0. 复验结论（先看这两条）

1. **pytest "19 个全过" 有前提**：裸跑 `python -m pytest` 会因收集 `scripts/smoke_test.py`（模块顶层发起 httpx 请求）在收集阶段报 `UnsupportedProtocol` 中断。需在 `backend/pytest.ini` 或 `pyproject.toml` 中配置 `testpaths = tests`，或运行时 `--ignore=scripts/smoke_test.py`。
2. **smoke_test 可复现性存疑**：`scripts/smoke_test.py:23` 用 `admin/admin123` 登录，但 `app/config.py:28` 默认 seed 密码是 `admin123456`。按当前代码直接跑冒烟第一步即 401。修复见第 6 节。

前端构建复验通过（tsc 零错误，JS 1103.21 kB，仅 chunk>500kB 警告），与声明一致。

---

## 1. P0 — 必须最先修（1 项）

### [P0-1] 默认密钥 + 默认管理员口令，公网暴露即被接管
- 位置：`backend/app/config.py:17`（SECRET_KEY 固定字符串）、`config.py:28`（ADMIN_PASSWORD 默认 `admin123456`）、`app/main.py:32-40`（启动自动 seed admin）
- 风险：任何人可用公开默认值伪造 `role=admin` JWT 或直接登录，接管卡密生成/吊销、看板、全量审计日志。
- 修复：
  1. 启动时（lifespan 或 pydantic validator）检测 `SECRET_KEY` / `ADMIN_PASSWORD` 仍为默认值且 `ENV=production`（或非 localhost 绑定）→ 抛异常拒绝启动；
  2. 若未显式设置 `ADMIN_PASSWORD`，首次启动用 `secrets.token_urlsafe(12)` 生成随机密码打印一次；
  3. README 更新部署说明：密钥只经环境变量注入。

## 2. 高危安全（3 项）

### [H-1] 敏感词转人工可被 mode 参数绕过
- 位置：`backend/app/services/agent.py:92-101`（仅 `mode=="half"` 检查敏感词）、`app/schemas.py:164`（mode 为客户端字段）
- 修复：敏感词命中时对 `auto`/`suggest`/`half` 一律降级为 `manual` 转人工并写审计；mode 的可信来源改为服务端（卡密 features 或服务端 rules 表——注意 `rules` 表当前写入后从未被 `process_message` 读取，需接上或删除）。

### [H-2] 卡密激活 check-then-act 竞态，可一卡多用
- 位置：`backend/app/routers/license.py:54-65`
- 修复：改为单条原子更新 `UPDATE licenses SET status='active', user_id=:u, device_fingerprint=:d, activated_at=:t WHERE code=:c AND status='unused'`（SQLAlchemy `update().where().values()`），以 `rowcount` 判断成败；同时给激活接口加失败次数限制。

### [H-3] 全站无限流
- 位置：`routers/auth.py:15,32`（登录/注册）、`routers/license.py:49`（激活）、`routers/agent.py:32`（LLM 调用）
- 修复：MVP 可用内存版限流中间件：登录失败按用户名+IP 计数锁定（如 5 次/15 分钟）；激活按 IP 限频；`/agent/suggest` 按用户配额（防 OpenAI 额度被批量消耗）；生产关闭 `/docs`。

## 3. P1 后端（6 项）

### [B-1] 普通用户可创建全局人设污染所有用户 AI 上下文
- 位置：`routers/personas.py:44`（create）、`:58`（update setattr 循环）；`services/agent.py:48-59` 优先选用全局默认人设
- 修复：`is_global` / `is_default` 字段仅 `require_admin` 可设置；非 admin 请求中强制置 False。

### [B-2] 外键无级联且未开 PRAGMA foreign_keys，删除产生孤儿数据
- 位置：`app/models.py:89-91,143-145`（MemoryEntry/Message/Rule 的外键）；`app/db.py` 未开 PRAGMA
- 修复：外键加 `ondelete="CASCADE"`（SQLite 需重建表生效，可直接删库重建——MVP 阶段）；`db.py` 在 engine connect 事件中执行 `PRAGMA foreign_keys=ON`，并顺带开启 `PRAGMA journal_mode=WAL`（缓解 SQLite 写锁）。

### [B-3] deactivate 后卡密永久无法重新激活（设备迁移即烧卡）
- 位置：`routers/license.py:84-96`（置 `inactive`）vs `:54`（activate 只接受 `unused`）
- 修复：`activate` 接受 `unused` 和 `inactive` 两种状态（原子 UPDATE 的 WHERE 条件改为 `status IN ('unused','inactive')`），或 deactivate 改回 `unused`。推荐前者并在契约中注明。

### [B-4] WS 推送与调度器是空壳
- 位置：`routers/ws.py:27-32`（`broadcast` 全库无调用方）、`services/scheduler.py:5-11`（纯延迟计算，无后台任务）
- 修复（二选一，不要保留假实现）：a) 在 `process_message` 产生建议/Kill Switch 时真正调用 `manager.broadcast`，auto 模式由服务端 asyncio 任务延时下发；b) MVP 先删除 broadcast 死代码与 WS 未用导入（`ws.py:9` 的 HTTPBearer），docstring 改为如实描述，调度留给客户端并在契约注明。

### [B-5] async 端点内同步 DB 阻塞事件循环
- 位置：`routers/agent.py:32-36`
- 修复：端点改 `def`（FastAPI 自动线程池），或 DB 操作移至 `run_in_executor`；配合 B-2 的 WAL。

### [B-6] 首个注册用户自动成为 admin（危险提权向量 + 死代码）
- 位置：`routers/auth.py:19-21`
- 修复：删除该逻辑，admin 只能由 seed 或已有 admin 创建。

## 4. P1 前端 + 契约（4 项，其中 2 项需后端协同）

### [F-1] 不填功能点生成卡密发送 `features: null` → 后端 422，功能不可用
- 位置：`admin-web/src/pages/Licenses.tsx:81-85` vs `backend/app/schemas.py:63`（`features` 非 Optional）
- 修复：前端空时省略字段 `...(features ? { features } : {})`；或后端 schema 改 `dict | None = None`。推荐前端省略字段（不动契约）。

### [F-2] 审计日志翻页失效（后端无 total）
- 位置：`admin-web/src/pages/Logs.tsx:54-64` vs `backend/app/routers/audit.py:24-25`（只返回 items）
- 修复：后端响应加 `total`（`select(func.count())`），同步更新 `docs/API_CONTRACT.md` 与 `admin-web/src/api/types.ts:92-94`。

### [F-3] 侧边栏高亮恒停在"仪表盘"
- 位置：`admin-web/src/components/Layout.tsx:31-34`（`/` 的 startsWith 匹配一切路径）
- 修复：`m.key === '/' ? pathname === '/' : pathname.startsWith(m.key)`。

### [F-4] `LicenseStatus` 缺 `unused`
- 位置：`admin-web/src/api/types.ts:18`、`Licenses.tsx:28-33`（STATUS_MAP）
- 修复：两处补 `unused`（建议蓝/灰色 Tag"未使用"）。

契约其他偏差：`GET /license/admin` 后端硬编码 `.limit(200)`（`license.py:114`）而契约无分页参数——加 `limit/offset` 参数并写进契约；`GET /auth/me` 契约有、前端未用——补登录后校验或暂维持现状。

## 5. 中低危加固（随首个迭代）

| 项 | 位置 | 修复 |
|---|---|---|
| JWT 无 jti/吊销，改密后旧 token 有效 7 天 | `config.py:19`、`security.py:95-106` | 加 jti + 服务端会话表；有效期收敛到数小时 |
| WS 把 JWT 放 URL query | `ws.py:39-46` | 改 `Sec-WebSocket-Protocol` 或首帧认证；连接时校验 license 状态 |
| CORS `*` + credentials | `main.py:50-53` | 白名单域名 |
| `device_fingerprint` 客户端任意伪造、无长度限制 | `license.py:63`、`schemas.py:34` | 加 `max_length`；服务端参与生成或校验格式 |
| 消息/审计明文存储、无保留策略 | `models.py:138-149` 等 | 脱敏 + 保留期清理任务 |
| SSRF 面：OPENAI_BASE_URL 无限制 | `config.py:31`、`gateway.py:77-87` | 限 https + 域名白名单、`allow_redirects=False` |
| TOKEN_ALGORITHM 可被配置污染 | `config.py:18` | 硬编码 HS256 白名单 |
| `deps.py` 裸 except + `int(sub)` 在 try 外会 500 | `deps.py:21-26` | 纳入 try、统一 401 |
| license 列表 N+1 查询用户名 | `license.py:117` | 一次 join |
| `memory.py` 全表载入、实现与注释不符（缺尾条） | `services/memory.py:25-37` | `func.count()` + 首尾各取 1 条 |
| 重复索引、死代码（PlatformEvent、SUGGESTION_COUNT、首注册 admin、auth/me） | `models.py`、`config.py:35` 等 | 清理 |
| 登录复用 RegisterRequest，短密码登录返回 422 而非 401 | `schemas.py:9-11`、`auth.py:33` | 单独 LoginRequest |
| services 各自 commit 破坏请求级事务 | `memory.py:11`、`audit.py:10` | commit 收敛到路由层 |
| 前端 422 错误显示 `[object Object]` | `client.ts:93-99` | 识别 detail 数组拼接 msg |
| chunk 1.1MB | `App.tsx`、`vite.config.ts` | 6 页面 `React.lazy` + `manualChunks`（react/antd/icons 三组 vendor） |
| `noUnusedLocals/noUnusedParameters` 为 false | `tsconfig.json:15-16` | 置 true（先清理 `Licenses.tsx:2-17` 未用的 Row/Col） |
| `destroyOnClose` 已弃用 | `Contacts.tsx:225` 等 3 处 | 改 `destroyOnHidden` |
| 删除操作无 loading 态可重复点击 | `Contacts.tsx:140-148` 等 | 按钮 loading |
| 无 alembic 迁移 | `main.py:28` | MVP 后引入 |
| requirements 全 `>=` 无锁文件 | `requirements.txt` | 固定版本 |
| 测试缺关键分支 | `tests/` | 补：过期卡密、deactivate 后再激活、越权 is_global、并发激活、WS、gateway.parse_suggestions |

## 6. 冒烟脚本修复

- `scripts/smoke_test.py:23`：管理员凭证改从环境变量读取（`ADMIN_PASSWORD`，缺省回退到 config 默认），与 seed 逻辑同源；
- 将 `smoke_test.py` 移出 pytest 收集（改名 `_smoke_test.py`、移入 `scripts/` 并配置 `testpaths = tests`，任选其一）；
- 清理 backend 根目录遗留的 `ai_social_agent.db`、`smoke.db`（确认无用于提交前删除）。

## 7. 建议执行顺序与验证要求

顺序：P0-1 → H-1 → F-1/F-2（前后端协同，含契约更新）→ H-2 + B-3（同一次改动）→ H-3 → B-1/B-2/B-5/B-6 → 第 5 节加固项。

每完成一批必须验证（不许"理论上能行"）：
1. `py -m pytest -v`（配置 testpaths 后应 19+ 全过，新增用例随修复补充）；
2. `cd admin-web && npm run build`（tsc 零错误）；
3. 涉及契约的改动同步更新 `docs/API_CONTRACT.md`；
4. H-1/B-3 修复后重跑修复版 smoke_test 验证端到端闭环。
