# AI助手

这是一个多功能 Android AI 助手项目。当前应用支持网页端使用，也可以通过 Capacitor 打包成 Android APK。

## 当前功能

- AI 聊天
- 一句话记账
- 查账单与消费汇总
- 功能中心
- 提醒闹钟动作卡片
- 应用控制动作卡片
- 打开微信 / 支付宝
- Android 原生动作插件
- GitHub Actions 自动打包 debug APK

## 项目结构

- `ai-ledger/`：AI助手网页端主应用。
- `ai-ledger-worker/`：Cloudflare Worker 后端，用于云端 AI 解析。
- `ai-ledger-android/`：Android 打包工程，保留原生动作插件。
- `.github/workflows/build-android-apk.yml`：主要 APK 自动构建流程。

## Android 打包

主要 workflow 是 `Build Android APK`，触发分支为 `dev-update-1`。

本地 Windows 常用命令：

```bash
cd ai-ledger-android
npm.cmd run android:sync
npm.cmd run android:open
npm.cmd run android:build:debug
```

说明：原项目由 AI 记账 App 升级而来，现在对外名称统一为 AI助手。
