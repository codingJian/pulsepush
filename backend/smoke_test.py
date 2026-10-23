"""Standalone smoke test: dry-run full chain without a running server.
Run:  python smoke_test.py   (uses a temp SQLite DB, no Firebase credentials)
Or:   pytest tests/test_smoke.py
"""
import os, sys, tempfile

# Force an isolated DB + dry-run BEFORE importing the app
_tmp = tempfile.mkdtemp(prefix="webhook_smoke_")
os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{_tmp}/smoke.db"
os.environ["JWT_SECRET"] = "smoke-test-secret"
os.environ.pop("FIREBASE_CREDENTIALS", None)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) if "__file__" in dir() else ".")

from fastapi.testclient import TestClient  # noqa: E402
from app.main import app  # noqa: E402


def run_chain(client: TestClient):
    r = client.post("/api/v1/auth/register", json={"username": "smokeuser", "email": "smoke@example.com", "password": "secret123"})
    assert r.status_code == 200, r.text
    token = r.json()["data"]["access_token"]
    h = {"Authorization": f"Bearer {token}"}

    r = client.post("/api/v1/auth/login", json={"username": "smokeuser", "password": "secret123"})
    assert r.status_code == 200, r.text

    r = client.post("/api/v1/channels", json={"channel_name": "DevOps告警"}, headers=h)
    assert r.status_code == 200, r.text
    ch_token = r.json()["data"]["channel_token"]
    assert ch_token.startswith("sec_chk_")

    r = client.post("/api/v1/devices/bind", json={
        "device_id": "smoke-device-1", "fcm_token": "fake-fcm-token-1",
        "device_name": "Pixel 8", "app_version": "1.0.0"}, headers=h)
    assert r.status_code == 200, r.text

    # 1) standard JSON
    r = client.post(f"/api/v1/push/{ch_token}", json={
        "title": "[CI 告警] 部署失败", "body": "分支 main 构建异常", "level": "critical",
        "url": "https://example.com/run/1"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["dry_run"] is True and body["dispatched_count"] == 1, body

    # 2) unstructured JSON
    r = client.post(f"/api/v1/push/{ch_token}", json={"alertname": "HighCPU", "value": 98})
    assert r.status_code == 200, r.text

    # 3) plain text
    r = client.post(f"/api/v1/push/{ch_token}", content="服务器磁盘告警\n/dev/sda1 使用率 91%",
                    headers={"Content-Type": "text/plain"})
    assert r.status_code == 200, r.text

    # 4) form
    r = client.post(f"/api/v1/push/{ch_token}", data={"title": "表单提醒", "body": "来自 form 的消息"})
    assert r.status_code == 200, r.text

    r = client.get("/api/v1/messages", headers=h)
    assert r.status_code == 200, r.text
    items = r.json()["data"]["items"]
    assert len(items) == 4, items
    msg_id = items[0]["msg_id"]

    r = client.get(f"/api/v1/messages/{msg_id}", headers=h)
    assert r.status_code == 200, r.text
    r = client.post(f"/api/v1/messages/{msg_id}/read", headers=h)
    assert r.status_code == 200 and r.json()["data"]["is_read"] is True, r.text

    r = client.get("/api/v1/devices", headers=h)
    assert r.status_code == 200 and len(r.json()["data"]) == 1, r.text
    print(f"SMOKE OK: 4 messages stored, latest msg_id={msg_id}")
    return True


if __name__ == "__main__":
    with TestClient(app) as c:
        run_chain(c)
