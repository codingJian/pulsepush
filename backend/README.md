# Webhook Push — Backend (FastAPI)

FastAPI relay gateway per PRD v2.0.0 (with the review fixes applied): accepts
webhooks per `channel_token`, parses/normalizes payloads, stores full messages,
and fans out via FCM to all devices bound to the channel owner.

Android app package: `com.shing.pulsepush` (PulsePush) — backend base URL is
configurable in-app, so no server address is baked in here either.

## Quick start

```bash
cd backend
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # edit JWT_SECRET; leave FIREBASE_CREDENTIALS empty for dry-run
./run.sh               # http://localhost:8000  (docs at /docs, health at /health)
```

## Environment variables

| Var | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `sqlite+aiosqlite:///./webhook_push.db` | SQLite (WAL) by default; PostgreSQL: `postgresql+asyncpg://user:pass@host:5432/webhook_push` |
| `JWT_SECRET` | dev placeholder | **Change in production.** HS256, 7-day expiry |
| `JWT_EXPIRE_DAYS` | `7` | |
| `FIREBASE_CREDENTIALS` | _(empty)_ | Path to service-account JSON on the server. Empty/missing → **dry-run** (no real sends, push responses carry `dry_run: true`) |
| `BASE_URL` | `http://localhost:8000` | Used to build `webhook_url` in channel responses |
| `RATE_LIMIT_PER_MINUTE` | `60` | Per channel_token, in-memory sliding window |

Tables are created on startup via `create_all`. For production schema changes,
introduce Alembic migrations (not required for this phase).

**Never commit** `.env`, `*.db`, or any Firebase service-account JSON — see `.gitignore`.

## Design notes (fixes vs. the original PRD)

- `user_devices` unified fields incl. `app_version` + `invalid_reason`; unique
  `(user_id, device_id)`. Binding an existing `device_id` moves it to the new
  user (no double-push on account switch); an `fcm_token` held by another
  `device_id` is deactivated with `invalid_reason=TOKEN_REASSIGNED`.
- Notification channel ≠ webhook channel: Android channel is derived from
  `level` — `critical→critical_channel`, `warning/info→default_channel`,
  `silent→silent_channel`.
- FCM 4KB cap: notification title ≤100 chars, body ≤300 chars; `data` only
  carries `msg_id / level / url (truncated) / channel_name / android_channel_id`.
  Full text lives in `message_logs`; the app fetches it via `GET /messages/{msg_id}`.
- Invalid FCM tokens (`UnregisteredError` / `SenderIdMismatch`) are set
  `is_active=FALSE` with `invalid_reason` on the next send (self-healing).
- `msg_id` format: `msg_YYYYMMDD_HHMMSS_` + 6-char random suffix.
- Unified response `{code, message, data}`; push also returns top-level
  `dispatched_count / msg_id / dry_run` per the PRD example.

## API

| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/auth/register` | — |
| POST | `/api/v1/auth/login` | — |
| POST | `/api/v1/devices/bind` | Bearer |
| POST | `/api/v1/devices/unbind` | Bearer |
| GET | `/api/v1/devices` | Bearer |
| GET / POST | `/api/v1/channels` | Bearer |
| DELETE | `/api/v1/channels/{id}` | Bearer |
| POST | `/api/v1/push/{channel_token}` | channel token in URL |
| GET | `/api/v1/messages?limit=20&offset=0` | Bearer |
| GET | `/api/v1/messages/{msg_id}` | Bearer |
| POST | `/api/v1/messages/{msg_id}/read` | Bearer |

Push accepts any Content-Type: standard JSON, unstructured JSON (recursively
beautified into body), `text/plain`, and form-encoded payloads.

## curl examples

```bash
# register + login
curl -s -X POST localhost:8000/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"davis","email":"davis@example.com","password":"secret123"}'
TOKEN=$(curl -s -X POST localhost:8000/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"davis","password":"secret123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["access_token"])')

# create a channel
curl -s -X POST localhost:8000/api/v1/channels -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"channel_name":"DevOps告警"}'

# bind a device
curl -s -X POST localhost:8000/api/v1/devices/bind -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"device_id":"my-phone-1","fcm_token":"<fcm-token>","device_name":"Pixel 8","app_version":"1.0.0"}'

# fire a webhook (replace <channel_token>)
curl -s -X POST localhost:8000/api/v1/push/<channel_token> -H 'Content-Type: application/json' \
  -d '{"title":"[CI 告警] 部署失败","body":"main 构建异常","level":"critical","url":"https://example.com/run/1"}'

# history
curl -s localhost:8000/api/v1/messages -H "Authorization: Bearer $TOKEN"
```

## Tests

```bash
python smoke_test.py     # standalone dry-run full chain
pytest -q                # same chain as a test case
```
