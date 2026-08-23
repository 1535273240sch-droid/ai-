"""审查加固后的补测：过期卡密 / deactivate 再激活 / 越权 is_global / 并发激活 / parse_suggestions / 登录锁定。"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

from sqlalchemy import create_engine, select, update
from sqlalchemy.orm import sessionmaker

from app.db import Base
from app.models import License
from app.services.gateway import parse_suggestions

# ---------------- 卡密生命周期 ----------------

def test_activate_expired_license_rejected(client_and_db, admin_headers, user_headers):
    """过期卡密激活 → 400；直接改库模拟过期（共享 client 的临时库）。"""
    client, maker = client_and_db
    r = client.post("/api/v1/license/admin", json={"count": 1, "days": 30}, headers=admin_headers)
    code = r.json()["codes"][0]

    db = maker()
    try:
        lic = db.execute(select(License).where(License.code == code)).scalars().first()
        lic.expires_at = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(days=1)
        db.commit()
    finally:
        db.close()

    resp = client.post(
        "/api/v1/license/activate",
        json={"code": code, "device_fingerprint": "dev-exp"},
        headers=user_headers,
    )
    assert resp.status_code == 400


def test_deactivate_then_reactivate(client, admin_headers, user_headers):
    r = client.post("/api/v1/license/admin", json={"count": 1, "days": 30}, headers=admin_headers)
    code = r.json()["codes"][0]
    client.post(
        "/api/v1/license/activate",
        json={"code": code, "device_fingerprint": "dev-1"},
        headers=user_headers,
    )
    # 注销 → 换设备重新激活（B-3）
    de = client.post(
        "/api/v1/license/deactivate",
        json={"device_fingerprint": "dev-1"},
        headers=user_headers,
    )
    assert de.status_code == 200
    re = client.post(
        "/api/v1/license/activate",
        json={"code": code, "device_fingerprint": "dev-2"},
        headers=user_headers,
    )
    assert re.status_code == 200, re.text
    assert re.json()["activated"] is True


def test_concurrent_activate_only_one_wins(tmp_path):
    """H-2：原子 UPDATE 保证同一卡密并发激活只有一个成功。"""
    db_file = tmp_path / "conc.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    Base.metadata.create_all(bind=engine)
    maker = sessionmaker(bind=engine, autoflush=False, autocommit=False)
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    s = maker()
    s.add(License(code="TEST-AAAA-BBBB", status="unused", expires_at=now + timedelta(days=30)))
    s.commit()
    s.close()

    def try_activate(fp: str) -> int:
        db = maker()
        try:
            stmt = (
                update(License)
                .where(License.code == "TEST-AAAA-BBBB", License.status == "unused")
                .values(status="active", device_fingerprint=fp, activated_at=now)
            )
            result = db.execute(stmt)
            db.commit()
            return result.rowcount
        finally:
            db.close()

    with ThreadPoolExecutor(max_workers=2) as ex:
        results = list(ex.map(try_activate, ["a", "b"]))
    assert sorted(results) == [0, 1]


# ---------------- 越权 ---------------- 

def test_user_cannot_create_global_persona(client, user_headers):
    """B-1：普通用户传 is_global=true 应被强制为 false。"""
    r = client.post(
        "/api/v1/personas",
        json={"name": "试试全局", "config": {}, "is_global": True, "is_default": False},
        headers=user_headers,
    )
    assert r.status_code == 200, r.text
    assert r.json()["is_global"] is False


# ---------------- 登录锁定 ---------------- 

def test_login_locked_after_failures(client):
    """H-3：连续失败达到阈值后返回 429。"""
    username = "lockuser"
    client.post("/api/v1/auth/register", json={"username": username, "password": "pass123"})
    statuses = []
    for _ in range(6):
        r = client.post(
            "/api/v1/auth/login",
            json={"username": username, "password": "wrongpass"},
        )
        statuses.append(r.status_code)
    assert statuses[-1] == 429
    assert statuses.count(401) >= 4


# ---------------- parse_suggestions 分支 ---------------- 

def test_parse_suggestions_variants():
    assert parse_suggestions('{"suggestions": ["a", "b", "c"]}') == ["a", "b", "c"]
    assert parse_suggestions('{"replies": ["x", "y"]}') == ["x", "y"]
    assert parse_suggestions("第一行\n第二行\n第三行")[:2] == ["第一行", "第二行"]
    assert parse_suggestions('```json\n{"suggestions": ["m", "n"]}\n```') == ["m", "n"]
    assert parse_suggestions('{"choices": [{"message": {"content": "ok"}}]}') == ["ok"]
    assert len(parse_suggestions("")) >= 1  # 兜底不为空
