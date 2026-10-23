# PulsePush — Webhook-to-Android 实时消息推送系统

外部系统（CI/CD、监控告警、自动化脚本）以 Webhook 形式 POST 到本系统，
后端校验通道 Token 后，经 Firebase Cloud Messaging (FCM) 扇出推送到账号绑定的
全部 Android 设备；App 被杀/息屏时由 GMS 代弹系统通知，点击后落本地库并打开详情。

按 PRD v2.0.0 实现，并落实评审修正：设备字段统一、level→通知渠道映射、
补历史消息接口、FCM 4KB 截断、单通道限流、密钥不入库。

## 结构

```
backend/    FastAPI 后端（鉴权 / 设备 / 通道 / Webhook 解析 / FCM 扇出）
android/    Android 客户端（Kotlin + Jetpack Compose，包名 com.shing.pulsepush）
DEPLOY.md   部署说明（后端上服务器 + App 填服务器地址）
```

## 快速开始

后端（本地，dry-run 模式不需要 Firebase 凭证）：

```bash
cd backend
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
cp .env.example .env   # 至少改 JWT_SECRET
./run.sh               # 默认 http://127.0.0.1:8000
```

发一条测试 Webhook（需先注册账号并建通道，见 backend/README.md）：

```bash
curl -X POST http://127.0.0.1:8000/api/v1/push/<channel_token> \
  -H 'Content-Type: application/json' \
  -d '{"title":"[CI 告警] 部署失败","body":"main 分支构建异常","level":"critical"}'
```

Android：用 Android Studio 打开 `android/` 直接构建，或命令行
`./gradlew assembleRelease`（release 签名需 `keystore.properties`，见 android/README.md）。
装好后在登录页填入你部署的后端地址即可使用。

## 安全须知

- Firebase Admin SDK 的 service account JSON 是**密钥**：只通过后端环境变量
  `FIREBASE_CREDENTIALS` 指向的文件路径引用，**永远不要提交进仓库**（.gitignore 已排除）。
- `google-services.json` 是客户端配置（已放在 android/app/ 下），可以入库。
- Android 签名 keystore 与密码不入库，单独保管：丢失后无法为同一 App 发布升级包。
