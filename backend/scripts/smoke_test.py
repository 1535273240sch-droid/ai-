"""端到端冒烟脚本：注册→登录→生成卡密→激活→建联系人→AI 建议→看板→日志。

用法: py scripts/smoke_test.py [base_url]   (默认 http://127.0.0.1:8000)
管理员凭证：ADMIN_PASSWORD 环境变量优先，缺省回退到 app.config 的 ADMIN_PASSWORD；
两者都为空时（首次启动随机密码）请先设置 ADMIN_PASSWORD。
"""
import os
import sys

import httpx

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import settings  # noqa: E402

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8000"
c = httpx.Client(base_url=BASE, timeout=30)

ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD") or settings.ADMIN_PASSWORD
if not ADMIN_PASSWORD:
    print("✗ 未配置 ADMIN_PASSWORD（首次启动随机密码见后端启动日志，或设置环境变量后重试）")
    raise SystemExit(1)


def step(name, resp, expect=200):
    ok = resp.status_code == expect
    print(f"{'✓' if ok else '✗'} {name}: {resp.status_code}")
    if not ok:
        print("   ", resp.text[:300])
        raise SystemExit(1)
    return resp.json()


# 1. 管理员登录（seed 账号）
admin = step(
    "admin 登录",
    c.post(
        "/api/v1/auth/login",
        json={"username": settings.ADMIN_USERNAME, "password": ADMIN_PASSWORD},
    ),
)
ah = {"Authorization": f"Bearer {admin['token']}"}

# 2. 生成卡密
lic = step("生成卡密", c.post("/api/v1/license/admin", json={"count": 1, "days": 30}, headers=ah))
code = lic["codes"][0]
print(f"   卡密: {code}")

# 3. 注册普通用户并登录
user = step("注册用户", c.post("/api/v1/auth/register", json={"username": "client1", "password": "client123"}))
uh = {"Authorization": f"Bearer {user['token']}"}

# 4. 激活卡密
step("激活卡密", c.post("/api/v1/license/activate", json={"code": code, "device_fingerprint": "dev-x"}, headers=uh))

# 5. 创建人设 + 联系人
step("创建人设", c.post("/api/v1/personas", json={"name": "默认人设", "config": {"风格": "温柔体贴"}, "is_default": True}, headers=uh))
contact = step("创建联系人", c.post("/api/v1/contacts", json={
    "platform": "xinyu", "platform_contact_id": "uid-888", "nickname": "小雨",
    "profile": {"relationship": "熟人", "interaction_style": "活泼", "sentence_style": "短句", "taboos": ["政治"]},
}, headers=uh))
cid = contact["id"]

# 6. AI 建议（无 key → mock）
sug = step("AI 建议", c.post("/api/v1/agent/suggest", json={"contact_id": cid, "message": "周末去爬山吗？", "mode": "suggest"}, headers=uh))
print(f"   建议: {sug['suggestions']}")
print(f"   决策: {sug['decision']}  delay: {sug['delay_ms']}ms")

# 7. half 敏感词 → 人工
half = step("half 敏感词转人工", c.post("/api/v1/agent/suggest", json={"contact_id": cid, "message": "要借钱吗", "mode": "half"}, headers=uh), expect=200)
print(f"   决策: {half['decision']}")

# 8. 平台上报 + 回执
step("平台上报消息", c.post("/api/v1/platform/events", json={"platform": "xinyu", "platform_contact_id": "uid-888", "type": "message_received", "content": "在吗"}, headers=uh))
step("平台回执发送", c.post("/api/v1/platform/reply", json={"platform": "xinyu", "platform_contact_id": "uid-888", "content": "在的呀"}, headers=uh))

# 9. 看板（admin）
stats = step("看板统计", c.get("/api/v1/dashboard/stats", headers=ah))
print(f"   消息数={stats['message_count']} 回复数={stats['reply_count']} 联系人={stats['active_contacts']} 卡密={stats['license_count']}")

# 10. 审计日志
logs = step("审计日志", c.get("/api/v1/audit/logs", headers=ah))
print(f"   日志条数: {len(logs['items'])}  事件: {sorted(set(i['event'] for i in logs['items']))}")

print("\n✅ 冒烟全部通过")
