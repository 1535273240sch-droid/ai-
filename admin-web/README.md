# AI Social Agent — Web 管理后台

React 18 + Vite 5 + TypeScript + Ant Design 5 实现的运营管理后台。

## 功能

- **仪表盘** `/`：消息量 / AI 回复量 / 活跃联系人 / 人设数 / 卡密数统计 + 最近日志
- **联系人** `/contacts`：联系人画像 CRUD（关系、互动风格、回复频率、句式、禁忌话题）
- **人设** `/personas`：人设 CRUD（config 为 JSON），默认/全局开关
- **卡密管理** `/licenses`：批量生成（数量/天数/功能点）、列表、**吊销（Kill Switch）**
- **日志** `/logs`：审计日志分页查看，可展开完整 payload

## 运行

```bash
npm install

# 开发（需后端已启动在 8000 端口；/api 由 Vite 代理到 http://localhost:8000）
npm run dev        # http://localhost:5173

# 生产构建
npm run build      # 产物在 dist/
```

## 默认账号

启动后端时自动创建管理员：`admin / admin123456`（请尽快修改）。

## 目录

```
src/
├── main.tsx / App.tsx      # 入口 + 路由守卫
├── api/client.ts           # fetch 封装：Bearer token 注入、401 自动登出
├── api/types.ts            # 接口类型（对齐 docs/API_CONTRACT.md）
├── components/Layout.tsx   # 侧边导航 + 顶栏
└── pages/                  # Login / Dashboard / Contacts / Personas / Licenses / Logs
```
