# 构建 Release APK

两种方式，任选其一。

## 方式 A：GitHub Actions 自动构建（推荐）

代码推到 GitHub 后，`.github/workflows/build-apk.yml` 会在每次 push 到
main 时自动构建，APK 在 Actions 页面的 artifact 里下载；打 `v*` 标签时
还会自动挂到 GitHub Release。

签名（可选，不配则产出未签名 APK，无法直接安装/升级）：在仓库
Settings → Secrets and variables → Actions 里加 4 个 secret：

| Secret | 值 |
| :--- | :--- |
| `KEYSTORE_BASE64` | keystore 文件的 base64：`base64 -w0 pulsepush-release.jks` |
| `KEYSTORE_PASSWORD` | keystore.properties 里的 storePassword |
| `KEY_PASSWORD` | keystore.properties 里的 keyPassword |
| `KEY_ALIAS` | `pulsepush` |

keystore 与密码由项目交付时单独提供，**不要提交进仓库**。

## 方式 B：本地 Android Studio / 命令行

要求 JDK 17 + Android SDK 34。

```bash
cd android
# 把交付的 pulsepush-release.jks 和 keystore.properties 放到本目录
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

没有 keystore.properties 时 release 构建会产出未签名 APK
（app-release-unsigned.apk），可用 apksigner 另行签名。

## 注意

- 包名固定 `com.shing.pulsepush`，与 Firebase 注册一致，不要改。
- `app/google-services.json` 必须保留，删了 FCM 初始化会失败。
- 升级版本时改 `app/build.gradle.kts` 里的 versionCode/versionName。
