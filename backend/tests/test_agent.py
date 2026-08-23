"""Agent 测试：无卡密 → 转人工；有卡密 → 3 条建议；half 敏感词 → 人工。"""
from app.services import gateway as gateway_module


def _make_contact(client, user_headers):
    r = client.post(
        "/api/v1/contacts",
        json={"platform": "xinyu", "platform_contact_id": "uid-1", "nickname": "小花"},
        headers=user_headers,
    )
    return r.json()["id"]


async def _fake_suggestions(messages, limit=3):
    return ["回复一", "回复二", "回复三"]


def test_suggest_without_license_returns_manual(client, user_headers, monkeypatch):
    monkeypatch.setattr(gateway_module, "generate_suggestions", _fake_suggestions)
    cid = _make_contact(client, user_headers)
    r = client.post(
        "/api/v1/agent/suggest",
        json={"contact_id": cid, "message": "在吗", "mode": "suggest"},
        headers=user_headers,
    )
    assert r.status_code == 200
    body = r.json()
    assert body["decision"]["mode"] == "manual"
    assert "卡密" in body["decision"]["reason"]


def test_suggest_with_license_returns_three(client, user_headers, activated_license, monkeypatch):
    monkeypatch.setattr(gateway_module, "generate_suggestions", _fake_suggestions)
    cid = _make_contact(client, user_headers)
    r = client.post(
        "/api/v1/agent/suggest",
        json={"contact_id": cid, "message": "周末去爬山吗？", "mode": "suggest"},
        headers=user_headers,
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["decision"]["mode"] == "suggest"
    assert len(body["suggestions"]) == 3
    assert body["delay_ms"] > 0


def test_half_mode_sensitive_keyword_manual(client, user_headers, activated_license, monkeypatch):
    monkeypatch.setattr(gateway_module, "generate_suggestions", _fake_suggestions)
    cid = _make_contact(client, user_headers)
    r = client.post(
        "/api/v1/agent/suggest",
        json={"contact_id": cid, "message": "能借钱给我吗", "mode": "half"},
        headers=user_headers,
    )
    body = r.json()
    assert body["decision"]["mode"] == "manual"
    assert "敏感词" in body["decision"]["reason"]
    assert body["suggestions"] == []


def test_sensitive_hits_all_modes(client, user_headers, activated_license, monkeypatch):
    """H-1：敏感词对 suggest/auto 模式同样转人工，不可被 mode 参数绕过。"""
    monkeypatch.setattr(gateway_module, "generate_suggestions", _fake_suggestions)
    cid = _make_contact(client, user_headers)
    for mode in ("suggest", "auto"):
        r = client.post(
            "/api/v1/agent/suggest",
            json={"contact_id": cid, "message": "能借钱给我吗", "mode": mode},
            headers=user_headers,
        )
        assert r.json()["decision"]["mode"] == "manual"


def test_server_rule_overrides_mode(client, user_headers, activated_license, monkeypatch):
    """H-1：rules 表接入决策——active 规则为 manual 时请求 auto 也转人工。"""
    monkeypatch.setattr(gateway_module, "generate_suggestions", _fake_suggestions)
    cid = _make_contact(client, user_headers)
    r = client.post(
        "/api/v1/agent/rules",
        json={"contact_id": cid, "mode": "manual", "keywords": ["测试"], "active": True},
        headers=user_headers,
    )
    assert r.status_code == 200, r.text
    r = client.post(
        "/api/v1/agent/suggest",
        json={"contact_id": cid, "message": "今天天气不错", "mode": "auto"},
        headers=user_headers,
    )
    assert r.json()["decision"]["mode"] == "manual"


def test_auto_mode_decision_auto(client, user_headers, activated_license, monkeypatch):
    monkeypatch.setattr(gateway_module, "generate_suggestions", _fake_suggestions)
    cid = _make_contact(client, user_headers)
    r = client.post(
        "/api/v1/agent/suggest",
        json={"contact_id": cid, "message": "吃饭了吗", "mode": "auto"},
        headers=user_headers,
    )
    assert r.json()["decision"]["mode"] == "auto"


def test_audit_log_written(client, admin_headers, user_headers, activated_license, monkeypatch):
    monkeypatch.setattr(gateway_module, "generate_suggestions", _fake_suggestions)
    cid = _make_contact(client, user_headers)
    client.post(
        "/api/v1/agent/suggest",
        json={"contact_id": cid, "message": "你好呀", "mode": "suggest"},
        headers=user_headers,
    )
    logs = client.get("/api/v1/audit/logs", headers=admin_headers).json()["items"]
    assert any(item["event"] == "agent.suggest" for item in logs)
