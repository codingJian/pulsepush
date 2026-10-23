# PulsePush — Webhook 转 Android 实时推送系统

> 把任意系统的事件，以一条 HTTP 请求变成手机上的一条通知。

CI/CD 流水线、服务器监控、自动化脚本、IoT 设备……任何能发 HTTP 请求的系统，
向 PulsePush 的通道地址 POST 一条 Webhook，后端即通过 Firebase Cloud Messaging (FCM)
把消息扇出到你账号绑定的全部 Android 设备。App 不在前台、甚至被划掉时，
通知由 Google Play 服务代为投递；点开通知直达消息详情，全部历史在 App 内可查、可搜、可标记已读。

**线上实例**：后端已部署于 `https://pulsepush.lxj.link`（Docker + nginx + Let's Encrypt，
经 Cloudflare 代理）；Android 客户端通过 GitHub Releases 分发签名 APK（当前版本 v1.0.1）。

---

## 功能特性

- **多通道**：一个账号可建多个通道（如「CI 告警」「服务器监控」），每个通道有独立的 `channel_token` 与 Webhook 地址，互不干扰
- **多设备扇出**：账号绑定的每台设备都会收到；设备在 App 登录时自动绑定，FCM Token 轮换时自动更新
- **四种 Payload 模式**：标准 JSON、表单（form）、纯文本、查询参数，老系统零改造也能接入
- **分级通知**：`critical / warning / info / silent` 四个级别映射到不同的 Android 通知渠道与 FCM 优先级（critical 走高优先级 + 独立渠道，可单独设置铃声与横幅）
- **可靠送达兜底**：推送之外，后端保存全部消息历史；App 每次启动自动同步，漏掉的通知打开 App 即可补看，不丢消息
- **坏 Token 自愈**：FCM 返回 Unregistered / SenderIdMismatch / 格式无效的设备会被自动停用，不再浪费投递
- **限流保护**：单通道 60 次/分钟滑动窗口限流，防脚本失控刷爆手机
- **FCM 4KB 上限处理**：标题/正文自动截断（100/300 字符），`data` 只携带小字段，消息正文以服务端历史为准
- **本地优先的客户端**：消息落 Room 本地库，离线可读；服务器地址在 App 内可配置，一套 APK 连任意部署

## 系统架构

```
 ┌──────────────┐   HTTPS POST    ┌─────────────────────────────────────────┐
 │ 发送方        │  /api/v1/push/  │           PulsePush 后端                 │
 │ CI / 监控 /  │ ──────────────▶ │  FastAPI (Uvicorn)                       │
 │ 脚本 / IoT   │  <channel_token>│   ├─ Payload 解析（4 种模式）             │
 └──────────────┘                 │   ├─ 鉴权（通道 Token / JWT）            │
                                  │   ├─ 限流（60 次/分/通道）               │
                                  │   ├─ 消息落库（SQLite WAL）              │
                                  │   └─ FCM 扇出（firebase-admin, HTTP v1） │
                                  └──────┬───────────────────▲───────────────┘
                                         │ FCM              │ HTTPS（历史同步/
                                         ▼                    │ 设备绑定/通道管理）
                                  ┌──────────────┐   ┌────────┴───────────────┐
                                  │ Google FCM / │   │ Android App             │
                                  │ GMS 长连接    │──▶│ Kotlin + Compose        │
                                  └──────────────┘   │ Room 本地库 + DataStore │
                                                     │ 3 个分级通知渠道          │
                                                     └─────────────────────────┘
```

**一次推送的旅程**：发送方 POST → 后端按 `channel_token` 定位通道与账号 →
解析并规整 Payload（级别、标题、正文、链接）→ 落库生成 `msg_id` →
组装 FCM 消息（notification + 精简 data）→ 扇出到该账号全部活跃设备 →
逐台回收结果，坏 Token 即时停用 → App 收到后落本地库并弹通知；
若当时没送达（关机、被系统冻结），下次打开 App 时经历史接口自动补齐。

## 技术栈

### 后端（`backend/`）

| 层 | 选型 |
|---|---|
| 语言 / 运行时 | Python 3.12 |
| Web 框架 | FastAPI + Uvicorn |
| 数据访问 | SQLAlchemy 2.0（asyncio）+ aiosqlite；默认 SQLite（WAL 模式），设 `DATABASE_URL` 可切 PostgreSQL（asyncpg） |
| 鉴权 | PyJWT（HS256，7 天）+ bcrypt 口令哈希；Webhook 侧用通道 Token |
| 推送 | firebase-admin（FCM HTTP v1），懒加载；未配置凭证时自动进入 dry-run 模式 |
| 部署 | Docker / Docker Compose（数据卷持久化，Firebase 凭证只读挂载）；nginx 反代 + Let's Encrypt；Cloudflare 前置代理 |
| 测试 | pytest（端到端冒烟覆盖 注册→通道→绑定→推送→历史） |

### Android 客户端（`android/`）

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose（Material 3），单 Activity + Navigation Compose |
| 架构 | MVVM + Repository；Hilt 依赖注入 |
| 本地存储 | Room（消息库，`msg_id` 唯一索引去重）+ DataStore（会话、服务器地址、设备 ID） |
| 网络 | Retrofit + OkHttp（动态 BaseUrl 拦截器切换服务器、Auth 拦截器附 JWT） |
| 推送 | Firebase Cloud Messaging；三渠道分级通知（critical / default / silent） |
| 版本 | minSdk 26 / targetSdk 34 / compileSdk 34；包名 `com.shing.pulsepush` |

### 构建与发布

- GitHub Actions：push 到 `main` 自动构建；打 `v*` 标签时构建**签名** Release APK 并自动发布到 GitHub Releases
- 签名 keystore 不入库，通过仓库 Secrets（`KEYSTORE_BASE64` 等 4 项）在 CI 中恢复

## 快速开始

### 1. 发一条推送（已部署实例）

在 App 中注册账号并创建一个通道，复制其 Webhook 地址，然后：

```bash
curl -X POST https://pulsepush.lxj.link/api/v1/push/<channel_token> \
  -H 'Content-Type: application/json' \
  -d '{"title":"[CI 告警] 部署失败","body":"main 分支构建异常，请及时处理","level":"critical","url":"https://ci.example.com/build/123"}'
```

成功返回 `{"code":200,"dispatched_count":1,"msg_id":"msg_...", "dry_run":false}`，
`dispatched_count` 为实际投递成功的设备数。

### 2. 本地运行后端（dry-run，无需 Firebase 凭证）

```bash
cd backend
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
cp .env.example .env   # 至少修改 JWT_SECRET
./run.sh               # http://127.0.0.1:8000 ，响应带 dry_run:true
```

### 3. Docker 部署（生产方式）

```bash
cd backend
mkdir -p secret && cp <你的 service-account.json> secret/firebase-sa.json && chmod 600 secret/firebase-sa.json
printf 'JWT_SECRET=%s\nBASE_URL=https://你的域名\n' "$(openssl rand -hex 32)" > .env
docker compose up -d --build   # 宿主机 8000 端口；前面请自行挂 nginx/HTTPS
```

### 4. Android 客户端

从本仓库 GitHub Releases 下载签名 APK 安装；或用 Android Studio 打开 `android/` 自行构建
（本地 release 签名需自备 `keystore.properties`，见 `BUILDING.md`）。
首次打开在登录页确认服务器地址（默认已填线上实例），注册/登录后自动绑定本机。

## API 概览（前缀 `/api/v1`）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/auth/register` | 注册 | — |
| POST | `/auth/login` | 登录，返回 JWT | — |
| GET / POST | `/channels` | 通道列表 / 新建通道（含 Webhook 地址） | JWT |
| DELETE | `/channels/{id}` | 删除通道 | JWT |
| POST | `/devices/bind` | 绑定设备（上报 FCM Token） | JWT |
| POST | `/devices/unbind` | 解绑当前设备 | JWT |
| GET | `/devices` | 我的设备列表（含失效原因） | JWT |
| POST | `/push/{channel_token}` | **接收入站 Webhook 并扇出推送** | 通道 Token |
| GET | `/messages` | 历史消息（分页） | JWT |
| GET | `/messages/{msg_id}` | 消息详情 | JWT |
| POST | `/messages/{msg_id}/read` | 标记已读 | JWT |
| GET | `/health` | 健康检查（含 FCM 是否为真发模式） | — |

Payload 字段：`title`、`body`、`level`（critical/warning/info/silent）、`url`（点击跳转，可选）。

## 配置（后端环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `DATABASE_URL` | `sqlite+aiosqlite:///./webhook_push.db` | 数据库连接串，可切 PostgreSQL |
| `JWT_SECRET` | —（必须改） | JWT 签名密钥 |
| `JWT_EXPIRE_DAYS` | `7` | 登录有效期 |
| `FIREBASE_CREDENTIALS` | 空 | service account JSON 路径；为空则 dry-run |
| `BASE_URL` | `http://localhost:8000` | 对外地址，用于生成 Webhook URL |
| `RATE_LIMIT_PER_MINUTE` | `60` | 单通道限流 |

## 已知限制

- **依赖 Google Play 服务**：FCM 投递走 GMS 长连接；无 GMS、GMS 被冻结的设备无法实时收到推送（历史同步仍可补看消息）。
- **国产 ROM 后台策略**：一加/ColorOS、小米/MIUI 等系统默认会冻结不常用应用，导致划掉后台后收不到通知。需在系统设置中为本 App 打开「自启动 / 关联启动 / 耗电不受限」，并同样豁免 Google Play 服务（一次性设置，升级不重置）。这是 FCM 在国产 ROM 上的通用约束；厂商推送通道（OPPO Heytap、小米 MiPush 等）需要开发者注册与上架审核，个人部署无法申请，故未集成。
- **JWT 无刷新/吊销机制**：登录态 7 天到期后需重新登录；改密不会使旧 Token 失效（自用场景可接受，企业化部署前需补 refresh token 与黑名单）。
- **单机部署**：限流计数在进程内存中，多实例部署需换 Redis 等共享存储。

## 安全须知

- Firebase service account JSON 是**密钥**：只通过 `FIREBASE_CREDENTIALS` 指向文件引用，**永远不要提交进仓库**（`.gitignore` 已排除，后端 Docker 部署时以只读挂载注入）。
- `google-services.json` 是客户端配置（在 `android/app/` 下），可以入库。
- Android 签名 keystore 与密码不入库、单独保管：**丢失后无法为同一 App 发布升级包**。
- 通道 Token 相当于该通道的发信凭证：不要公开分享；泄露后删除通道重建即可。

## 目录结构

```
backend/     FastAPI 后端（Dockerfile / docker-compose.yml / pytest 冒烟测试）
android/     Android 客户端（Kotlin + Compose）
DEPLOY.md    部署手册（服务器部署 + App 配置）
BUILDING.md  构建手册（CI 签名出包 + 本地构建）
```
