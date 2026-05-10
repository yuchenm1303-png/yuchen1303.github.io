# AI 记账 Android 版

这是 `ai-ledger` 的 Android 封装工程，使用 Capacitor 将现有网页应用打包成可安装的 Android App。

## 当前功能

- 一句话智能记账
- 自动分类
- 本月预算
- 收支统计
- 近 7 天趋势
- 本地数据保存

## 本地生成 Android 工程

```bash
cd ai-ledger-android
npm run android:init
```

## 同步网页更新到 Android 工程

```bash
npm run android:sync
```

## 在 Android Studio 打开

```bash
npm run android:open
```

## 构建 debug APK

```bash
npm run android:build:debug
```

生成路径通常为：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 后续升级方向

1. 接入云端 AI：将当前本地规则识别替换为云端模型 API。
2. 登录功能：接入手机号验证码或邮箱登录。
3. 云同步：把账单从 localStorage 迁移到后端数据库。
4. 导出账单、月报、预算提醒等。
