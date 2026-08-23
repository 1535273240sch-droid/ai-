# AI Social Agent — 后端服务

FastAPI 实现的 AI 自动回复助手后端。按企业级架构提供：JWT 认证、卡密授权（Kill Switch）、联系人画像、人设、记忆、模型网关、AI 决策 Agent、节奏调度、WebSocket、审计日志、管理看板。

## 快速开始

```bash
# 1. 安装依赖
py -m pip install -r requirements.txt

# 2. 配置（可选，用环境变量或 .env 覆盖默认值）
#    复制 .env.example 为 .env 并按需修改（AI 网关、密钥等）

# 3. 启动
py -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

启动后自动建表，并创建默认管理员 `admin / admin123456`（请尽快修改）。

## 配置项（.env / 环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `SECRET_KEY` | 开发默认值 | JWT 签名密钥，生产必须修改 |
| `DATABASE_URL` | `sqlite:///./ai_social_agent.db` | 数据库连接串（可换 PostgreSQL） |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | admin / admin123456 | 初始管理员 |
| `OPENAI_BASE_URL` / `OPENAI_API_KEY` / `OPENAI_MODEL` | 空 | AI 网关（OpenAI 兼容）；**为空时返回 mock 建议**，便于无 key 联调 |
| `DELAY_MIN_MS` / `DELAY_MAX_MS` | 800 / 15000 | 自然延迟区间 |
| `SENSITIVE_KEYWORDS` | 转账,借钱,… | half 模式命中即转人工 |

## 测试与冒烟

```bash
# 单元/接口测试（19 个用例）
py -m pytest tests/ -v

# 端到端冒烟（需先启动服务，默认连 8000 端口）
py scripts/smoke_test.py
```

## API 一览

完整契约见 `../docs/API_CONTRACT.md`。Swagger 文档启动后访问 `http://localhost:8000/docs`。

- `POST /api/v1/auth/register|login`，`GET /auth/me`
- `POST /api/v1/license/activate|deactivate`，`GET /license/info`；admin：`POST|GET /license/admin`，`POST /license/admin/{code}/revoke`（吊销即 Kill Switch）
- `GET|POST /api/v1/contacts`，`PUT|DELETE /contacts/{id}`
- `GET|POST /api/v1/personas`，`PUT|DELETE /personas/{id}`
- `POST /api/v1/agent/suggest`（mode: suggest/auto/half），`GET|POST|PUT|DELETE /agent/rules`
- `POST /api/v1/platform/events|reply`（Android 端上报）
- `GET /api/v1/audit/logs`，`GET /api/v1/dashboard/stats`（admin）
- `WS /api/v1/ws?token=<jwt>`
