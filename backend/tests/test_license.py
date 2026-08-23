def test_admin_create_and_user_activate(client, admin_headers, user_headers):
    r = client.post(
        "/api/v1/license/admin", json={"count": 2, "days": 30, "features": {"auto": True}},
        headers=admin_headers,
    )
    assert r.status_code == 200
    codes = r.json()["codes"]
    assert len(codes) == 2 and len(codes[0].split("-")) == 3

    info = client.get("/api/v1/license/info", headers=user_headers)
    assert info.json()["valid"] is False

    act = client.post(
        "/api/v1/license/activate",
        json={"code": codes[0], "device_fingerprint": "dev-1"},
        headers=user_headers,
    )
    assert act.status_code == 200 and act.json()["activated"] is True

    info = client.get("/api/v1/license/info", headers=user_headers)
    assert info.json()["valid"] is True

    # 二次激活同一卡密失败
    again = client.post(
        "/api/v1/license/activate",
        json={"code": codes[0], "device_fingerprint": "dev-2"},
        headers=user_headers,
    )
    assert again.status_code == 400


def test_admin_revoke_is_kill_switch(client, admin_headers, user_headers):
    r = client.post(
        "/api/v1/license/admin", json={"count": 1, "days": 30},
        headers=admin_headers,
    )
    code = r.json()["codes"][0]
    client.post(
        "/api/v1/license/activate",
        json={"code": code, "device_fingerprint": "dev-1"},
        headers=user_headers,
    )
    rev = client.post(f"/api/v1/license/admin/{code}/revoke", headers=admin_headers)
    assert rev.status_code == 200
    info = client.get("/api/v1/license/info", headers=user_headers)
    assert info.json()["valid"] is False


def test_activate_nonexistent(client, user_headers):
    r = client.post(
        "/api/v1/license/activate",
        json={"code": "AAAA-BBBB-CCCC", "device_fingerprint": "dev-1"},
        headers=user_headers,
    )
    assert r.status_code == 404


def test_admin_list(client, admin_headers, user_headers, activated_license):
    r = client.get("/api/v1/license/admin", headers=admin_headers)
    assert r.status_code == 200
    items = r.json()["items"]
    assert len(items) == 1 and items[0]["activated_by"] == "user1"


def test_non_admin_cannot_access_admin(client, user_headers):
    r = client.get("/api/v1/license/admin", headers=user_headers)
    assert r.status_code == 403
