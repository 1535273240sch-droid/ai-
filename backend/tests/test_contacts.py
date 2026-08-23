def test_contact_crud(client, user_headers):
    r = client.post(
        "/api/v1/contacts",
        json={
            "platform": "xinyu",
            "platform_contact_id": "uid-1001",
            "nickname": "小花",
            "profile": {"relationship": "老朋友", "interaction_style": "幽默", "taboos": ["政治"]},
        },
        headers=user_headers,
    )
    assert r.status_code == 200, r.text
    cid = r.json()["id"]
    assert r.json()["profile"]["relationship"] == "老朋友"

    lst = client.get("/api/v1/contacts", headers=user_headers)
    assert len(lst.json()["items"]) == 1

    upd = client.put(
        f"/api/v1/contacts/{cid}",
        json={"nickname": "小花2", "profile": {"sentence_style": "短句"}},
        headers=user_headers,
    )
    assert upd.json()["nickname"] == "小花2"
    assert upd.json()["profile"]["sentence_style"] == "短句"
    assert upd.json()["profile"]["relationship"] == "老朋友"  # 原有字段保留

    d = client.delete(f"/api/v1/contacts/{cid}", headers=user_headers)
    assert d.status_code == 200
    assert client.get("/api/v1/contacts", headers=user_headers).json()["items"] == []


def test_contact_duplicate_rejected(client, user_headers):
    body = {"platform": "xinyu", "platform_contact_id": "uid-1", "nickname": "A"}
    assert client.post("/api/v1/contacts", json=body, headers=user_headers).status_code == 200
    assert client.post("/api/v1/contacts", json=body, headers=user_headers).status_code == 400


def test_contact_not_found(client, user_headers):
    assert client.put("/api/v1/contacts/999", json={"nickname": "x"}, headers=user_headers).status_code == 404
