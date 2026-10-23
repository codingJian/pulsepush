# PulsePush Android 客户端

Webhook 消息推送系统的原生 Android 客户端（PRD v2.0.0 第 4、7 节）。

- 包名 / applicationId：`com.shing.pulsepush`（与 Firebase 注册一致，不可改）
- 显示名：PulsePush
- 技术栈：Kotlin 2.0 + Jetpack Compose + MVVM + Hilt + Room + DataStore + Retrofit2/OkHttp + Firebase Cloud Messaging
- AGP 8.5.2 / Gradle 8.9 / compileSdk·targetSdk 34 / minSdk 26 / JDK 17

## 功能

- 登录 / 注册（登录页可填**服务器地址**，存 DataStore，立即生效）
- 设备绑定：登录后自动获取 FCM Token 并 `POST /api/v1/devices/bind`；`onNewToken` 轮换时已登录则静默重新 bind；`device_id` 用 ANDROID_ID，兜底为持久化 UUID
- Inbox：Room Flow 本地展示 + 下拉刷新从后端同步历史；未读圆点、level 色标（info 绿 / warning 橙 / critical 红）；点进详情自动标已读（本地 + `POST /messages/{msg_id}/read`）
- 消息详情：打开 `url`、复制正文
- 通道管理：列表 / 新建 / 删除、复制 Webhook URL、复制 curl 测试示例
- 设置：修改服务器地址、退出登录（先调 `devices/unbind` 再清本地会话）
- 通知：Application 初始化三级渠道 `critical_channel`(HIGH) / `default_channel`(DEFAULT) / `silent_channel`(LOW)；前台收到推送时写 Room 并发本地通知；后台由系统按 `notification` 块代发，点击通知经 MainActivity 落 Room 并直达详情
- 本地消息以服务端 `msg_id` 唯一索引去重（推送与历史同步不会重复落库）

## 后端地址可配置（重要）

Retrofit 实例本身建在占位地址 `https://push.example.com/` 上；每个请求都经过
`DynamicBaseUrlInterceptor`，按 DataStore 中保存的服务器地址（登录页 / 设置页可改，
`ServerUrlHolder` 同步持有）重写 scheme / host / port / 路径前缀，改地址无需重启或重建。
JWT 由 `AuthInterceptor` 从 `TokenHolder` 读取并自动加 `Authorization: Bearer`（登录/注册接口除外）。

接口契约（base `/api/v1/`）：`auth/register`、`auth/login`、`devices/bind`、`devices/unbind`、
`GET/POST channels`、`DELETE channels/{id}`、`GET messages?limit&offset`、`POST messages/{msg_id}/read`，
统一响应 `{code, message, data}`；列表类响应做了容错解析（数组直出或 `items/messages/channels/list` 包装均可）。

## 构建

前置：JDK 17、Android SDK 34。仓库里 `gradle/wrapper/gradle-wrapper.properties` 已指向
gradle-8.9；`gradlew` 与 `gradle-wrapper.jar` 是占位，需在装好工具链的机器上生成一次：

```bash
gradle wrapper --gradle-version 8.9
```

然后：

```bash
# Debug
./gradlew :app:assembleDebug

# Release（需先配置 keystore.properties，见下）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

`app/google-services.json` 已就位（Firebase 项目 webhook-push-2bc7c）。
注意：Firebase **Admin SDK** 的 service account JSON 属于后端机密，绝不进本仓库（.gitignore 已排除）。

## Release 签名

`app/build.gradle.kts` 在构建时读取 `keystore.properties`（先找项目根目录，再找 `app/` 目录）。
文件不存在时 release 构建只是不签名，**不会中断 debug 构建**。生成 keystore 后新建
`android/keystore.properties`（已被 .gitignore 排除，不要提交）：

```properties
storeFile=../release-key.jks
storePassword=<store password>
keyAlias=pulsepush
keyPassword=<key password>
```

`storeFile` 相对路径以项目根目录为基准解析。

## 源码结构

```
app/src/main/java/com/shing/pulsepush/
├── PulsePushApp.kt            # @HiltAndroidApp，初始化通知渠道
├── MainActivity.kt            # 通知点击 extras 落库 + 导航、权限、冷启动重绑
├── di/AppModule.kt            # Room / DataStore / OkHttp / Retrofit
├── data/
│   ├── local/                 # Room Entity（PRD 第 7 节）/ DAO / Database
│   ├── preferences/           # DataStore + ServerUrlHolder / TokenHolder
│   ├── remote/                # ApiService / DTO / Auth & 动态 baseUrl 拦截器 / 容错解析
│   └── repository/            # Auth(+Device) / Message / Channel
├── service/MyFirebaseMessagingService.kt
├── util/                      # 通知渠道与展示、device_id、Task await
└── ui/                        # auth / inbox / detail / channels / settings / navigation / theme
```
