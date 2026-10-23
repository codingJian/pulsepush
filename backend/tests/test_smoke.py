import os, sys, tempfile
_tmp = tempfile.mkdtemp(prefix="webhook_pytest_")
os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{_tmp}/test.db"
os.environ["JWT_SECRET"] = "pytest-secret"
os.environ.pop("FIREBASE_CREDENTIALS", None)
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient
from app.main import app


def test_full_chain_dry_run():
    with TestClient(app) as client:
        r = client.post("/api/v1/auth/register", json={"username": "tuser", "email": "t@example.com", "password": "secret123"})
        assert r.status_code == 200
        token = r.json()["data"]["access_token"]
        h = {"Authorization": f"Bearer {token}"}
        r = client.post("/api/v1/channels", json={"channel_name": "CI"}, headers=h)
        ch = r.json()["data"]["channel_token"]
        r = client.post("/api/v1/devices/bind", json={"device_id": "d1", "fcm_token": "fake1", "device_name": "Pixel"}, headers=h)
        assert r.status_code == 200
        r = client.post(f"/api/v1/push/{ch}", json={"title": "hi", "body": "there", "level": "warning"})
        assert r.status_code == 200 and r.json()["dry_run"] is True
        r = client.get("/api/v1/messages", headers=h)
        assert r.json()["data"]["total"] == 1
