"""测试夹具：独立临时数据库 + TestClient。"""
import os
import sys
import tempfile
import uuid

# 必须在导入 app.* 之前设置，避免污染开发库。
# 用唯一文件名：固定路径的库文件可能被其他运行中的进程（如 uvicorn / pytest）锁住，
# Windows 下 os.remove 会抛 WinError 32。
_TMP_DB = os.path.join(
    tempfile.gettempdir(), f"aisocial_test_lifespan_{uuid.uuid4().hex}.db"
)
os.environ["DATABASE_URL"] = f"sqlite:///{_TMP_DB}"
os.environ["OPENAI_API_KEY"] = ""

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402

from app.db import Base, get_db  # noqa: E402
from app.main import app  # noqa: E402
from app.models import User  # noqa: E402
from app.security import hash_password  # noqa: E402


@pytest.fixture()
def client_and_db(tmp_path):
    """返回 (TestClient, sessionmaker)，共享同一临时库，供需要直接操作 DB 的测试。"""
    db_file = tmp_path / "test.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    testing_session = sessionmaker(bind=engine, autoflush=False, autocommit=False)
    Base.metadata.create_all(bind=engine)

    s = testing_session()
    s.add(User(username="admin", password_hash=hash_password("admin123"), role="admin"))
    s.commit()
    s.close()

    def override_get_db():
        db = testing_session()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c, testing_session
    app.dependency_overrides.clear()


@pytest.fixture()
def client(client_and_db):
    return client_and_db[0]


@pytest.fixture()
def db_maker(client_and_db):
    return client_and_db[1]


@pytest.fixture()
def admin_headers(client):
    resp = client.post("/api/v1/auth/login", json={"username": "admin", "password": "admin123"})
    assert resp.status_code == 200, resp.text
    return {"Authorization": f"Bearer {resp.json()['token']}"}


@pytest.fixture()
def user_headers(client):
    resp = client.post("/api/v1/auth/register", json={"username": "user1", "password": "user123"})
    assert resp.status_code == 200, resp.text
    return {"Authorization": f"Bearer {resp.json()['token']}"}


@pytest.fixture()
def activated_license(client, admin_headers, user_headers):
    resp = client.post(
        "/api/v1/license/admin",
        json={"count": 1, "days": 30, "features": {"auto": True}},
        headers=admin_headers,
    )
    assert resp.status_code == 200, resp.text
    code = resp.json()["codes"][0]
    resp = client.post(
        "/api/v1/license/activate",
        json={"code": code, "device_fingerprint": "device-abc"},
        headers=user_headers,
    )
    assert resp.status_code == 200, resp.text
    return code
