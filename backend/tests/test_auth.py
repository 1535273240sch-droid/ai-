def test_register_returns_user_role(client):
    r = client.post("/api/v1/auth/register", json={"username": "boss", "password": "pass123"})
    assert r.status_code == 200
    assert r.json()["user"]["role"] == "user"
    assert r.json()["token"]


def test_register_duplicate(client):
    client.post("/api/v1/auth/register", json={"username": "boss", "password": "pass123"})
    r = client.post("/api/v1/auth/register", json={"username": "boss", "password": "pass123"})
    assert r.status_code == 400


def test_login_ok_and_wrong(client):
    client.post("/api/v1/auth/register", json={"username": "boss", "password": "pass123"})
    ok = client.post("/api/v1/auth/login", json={"username": "boss", "password": "pass123"})
    assert ok.status_code == 200 and ok.json()["token"]
    bad = client.post("/api/v1/auth/login", json={"username": "boss", "password": "wrongpass"})
    assert bad.status_code == 401


def test_me_requires_token(client):
    assert client.get("/api/v1/auth/me").status_code == 401
    r = client.post("/api/v1/auth/register", json={"username": "boss", "password": "pass123"})
    token = r.json()["token"]
    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200 and me.json()["user"]["username"] == "boss"
