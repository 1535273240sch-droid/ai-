def test_persona_crud(client, user_headers):
    r = client.post(
        "/api/v1/personas",
        json={"name": "默认人设", "config": {"style": "正经"}, "is_default": True},
        headers=user_headers,
    )
    assert r.status_code == 200, r.text
    pid = r.json()["id"]
    assert r.json()["config"]["style"] == "正经"

    assert len(client.get("/api/v1/personas", headers=user_headers).json()["items"]) == 1

    upd = client.put(f"/api/v1/personas/{pid}", json={"config": {"style": "幽默"}}, headers=user_headers)
    assert upd.json()["config"]["style"] == "幽默"

    assert client.delete(f"/api/v1/personas/{pid}", headers=user_headers).status_code == 200
    assert client.get("/api/v1/personas", headers=user_headers).json()["items"] == []


def test_persona_isolated_between_users(client, user_headers):
    client.post("/api/v1/personas", json={"name": "A"}, headers=user_headers)
    r2 = client.post("/api/v1/auth/register", json={"username": "user2", "password": "user222"})
    h2 = {"Authorization": f"Bearer {r2.json()['token']}"}
    assert client.get("/api/v1/personas", headers=h2).json()["items"] == []
