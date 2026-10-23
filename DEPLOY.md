# 部署说明

## 后端（服务器）

要求：Python 3.11+，内存 512MB 以上即可（SQLite 模式）。公网需可达 Google
FCM 接口（fcm.googleapis.com）——服务器在境外网络无此问题；若在境内网络，
FCM 推送将无法发出，需自行解决出网通道。

```bash
cd backend
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
cp .env.example .env
```

`.env` 关键项：

| 变量 | 说明 |
| :--- | :--- |
| `JWT_SECRET` | 必改，随机长字符串 |
| `DATABASE_URL` | 默认 `sqlite+aiosqlite:///./pulsepush.db`；可换 PostgreSQL |
| `FIREBASE_CREDENTIALS` | service account JSON 的绝对路径；不配则 dry-run（只记录不真发） |
| `PUBLIC_BASE_URL` | 对外访问地址，如 `https://push.example.com`，用于拼 Webhook URL |

启动（开发）：`./run.sh`；生产建议 systemd：

```ini
[Unit]
Description=PulsePush backend
After=network.target

[Service]
WorkingDirectory=/opt/pulse-push/backend
EnvironmentFile=/opt/pulse-push/backend/.env
ExecStart=/opt/pulse-push/backend/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
```

前面用 nginx/caddy 反代到 127.0.0.1:8000 并配 HTTPS（App 默认走 HTTPS）。

## Android

1. 安装 release APK。
2. 登录页填服务器地址（如 `https://push.example.com`），注册/登录。
3. App 会自动获取 FCM Token 并绑定设备；随后在“通道”页新建通道，
   复制 Webhook URL 给外部系统使用即可。

前提：目标手机需有完整 Google Play Services，FCM 依赖 GMS 信道送达；
无 GMS 的设备（部分国行/自制 ROM）收不到推送，这是 FCM 路线的固有限制。
