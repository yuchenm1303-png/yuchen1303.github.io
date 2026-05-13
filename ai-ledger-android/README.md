# AI 助手 Android 版

这是 `ai-ledger` 的 Android 封装工程，使用 Capacitor 将现有网页应用打包成可安装的 Android App。当前方向已经从单纯记账 App 升级为多功能 AI 助手。

## 当前功能

- AI 对话入口
- 一句话智能记账
- 自动分类
- 本月预算
- 收支统计
- 近 7 天趋势
- 本地数据保存
- 功能中心：账单、统计、提醒闹钟、应用控制
- Android 原生动作插件骨架：设置闹钟、打开常用 App
- GitHub Actions 自动构建 debug APK

## GitHub 在线打包 APK

仓库已经添加自动构建流程：

```text
.github/workflows/build-android-apk.yml
```

手动打包步骤：

1. 打开 GitHub 仓库。
2. 进入 `Actions`。
3. 选择 `Build Android APK`。
4. 点击 `Run workflow`。
5. 分支选择 `dev-update-1`。
6. 等待构建完成。
7. 打开最新一次成功的运行记录。
8. 在 `Artifacts` 下载 `AI-Assistant-debug-apk`。
9. 解压后得到 `AI-Assistant-debug.apk`。
10. 发送到手机并安装。

说明：

- 这个 APK 是 debug 包，适合测试和自用。
- 手机安装时可能提示未知来源，请允许本次安装。
- 只有 APK 安装版能调用 Android 原生能力；浏览器网页版本不能真正打开微信或设置系统闹钟。

## Windows 本地环境

- Node.js 18+。
- JDK 使用 Eclipse Temurin 17，并设置 `JAVA_HOME`。
- Android SDK 需要包含 `platform-tools`、`platforms;android-36` 和 `build-tools;36.0.0`。
- 如果 PowerShell 阻止 `npm.ps1`，请使用 `npm.cmd`，例如 `npm.cmd run android:init`。
- 依赖已固定在 Capacitor 6.2.1，避免 `latest` 升到需要 Java 21 的版本。

## 本地生成 Android 工程

```bash
cd ai-ledger-android
npm.cmd run android:init
```

该命令会：

1. 安装依赖；
2. 复制网页资源到 `www`；
3. 生成 Capacitor Android 工程；
4. 自动安装 `MobileAssistant` 原生插件。
5. 尽量自动修复 Windows 中文路径构建、Gradle 镜像和 Android SDK 本地路径。

## 同步网页更新到 Android 工程

```bash
npm.cmd run android:sync
```

该命令会同步网页文件，并重新安装原生插件。

## 单独安装原生插件

```bash
npm.cmd run android:install-plugin
```

插件会写入：

```text
android/app/src/main/java/com/yuchen/ailedger/MobileAssistantPlugin.java
```

它提供两个方法给网页端调用：

```js
MobileAssistant.setAlarm({ hour, minute, label, date })
MobileAssistant.openApp({ appName })
```

## 在 Android Studio 打开

```bash
npm.cmd run android:open
```

## 构建 debug APK

```bash
npm.cmd run android:build:debug
```

生成路径通常为：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 测试建议

在 App 的 AI 助手页输入：

```text
明天早上8点叫我起床
打开微信
打开支付宝
今天午饭28
```

预期效果：

- 闹钟和打开应用会生成动作卡片；
- 点击确认后，Android 版会尝试调用原生插件；
- 部分手机设置系统闹钟时会打开闹钟确认页，需要用户再点一次保存；
- 记账功能仍然走原来的账单流程。

## 后续升级方向

1. 扩展更多 Android 原生能力：通知提醒、日历、定位、打开系统设置页。
2. 接入更完整的云端 AI 工具规划，让 AI 自动判断应该调用哪个手机工具。
3. 登录功能：接入手机号验证码或邮箱登录。
4. 云同步：把账单从 localStorage 迁移到后端数据库。
5. 导出账单、月报、预算提醒等。
