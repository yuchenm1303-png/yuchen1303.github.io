# AI助手 Android 版

这是 AI助手的 Android 打包工程，使用 Capacitor 将 `ai-ledger` 网页应用封装成可安装的 Android App。Android 版保留 `MobileAssistant` 原生动作插件，用于执行手机任务。

## 当前功能

- AI 聊天
- 一句话记账
- 查账单与消费汇总
- 功能中心：账单中心、数据统计、提醒闹钟、应用控制、快捷指令、任务记录
- 提醒闹钟动作卡片
- 应用控制动作卡片
- 百度地图导航动作卡片
- 打开微信 / 支付宝
- Android 原生动作插件
- GitHub Actions 自动构建 debug APK

## GitHub 在线打包 APK

主要构建流程：

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
7. 在 `Artifacts` 下载 `AI-Assistant-debug-apk`。
8. 解压后得到 `AI-Assistant-debug.apk`。

说明：

- 这个 APK 是 debug 包，适合测试和自用。
- 只有 APK 安装版能调用 Android 原生能力；浏览器网页版本只能生成动作卡片。
- workflow 使用 `npx cap sync android`，因为仓库已经保留 Android 工程目录。

## Windows 本地环境

- Node.js 18+。
- JDK 使用 Eclipse Temurin 17，并设置 `JAVA_HOME`。
- Android SDK 需要包含 `platform-tools`、`platforms;android-36` 和 `build-tools;36.0.0`。
- PowerShell 中请使用 `npm.cmd`。
- 依赖固定在 Capacitor 6.2.1，避免升级到需要 Java 21 的版本。

## 同步网页更新到 Android 工程

```bash
cd ai-ledger-android
npm.cmd run android:sync
```

该命令会复制网页文件、执行 `npx cap sync android`，并重新安装原生插件。

## 单独安装原生插件

```bash
npm.cmd run android:install-plugin
```

插件文件：

```text
android/app/src/main/java/com/yuchen/ailedger/MobileAssistantPlugin.java
```

网页端调用方法：

```js
MobileAssistant.setAlarm({ hour, minute, label, date })
MobileAssistant.openApp({ appName })
MobileAssistant.navigate({ destination, mode })
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

在 AI助手页输入：

```text
明天早上8点叫我起床
打开微信
打开支付宝
导航回家
今天午饭28
我这个月餐饮花了多少
```

预期效果：

- 闹钟、打开应用和百度地图导航会生成动作卡片。
- 点击确认后，Android 版会尝试调用原生插件。
- 部分手机设置系统闹钟时会打开闹钟确认页，需要用户再点一次保存。
- 记账和查账单功能继续走现有账单数据流程。
