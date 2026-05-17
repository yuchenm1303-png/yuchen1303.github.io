# AI 助手 Compose 原生迁移工程

这个目录是从 `ai-ledger/` WebView 页面迁移到 Android 原生 Jetpack Compose 的起点。

现有网页版本仍然保留在：

```text
ai-ledger/
```

新的原生工程放在：

```text
android-compose/
```

这样做的目的不是一次性推倒旧版，而是先把最影响体验的高频路径迁出来：聊天页、底部导航、输入框、功能入口、设置分组。

## 当前已经完成的内容

当前版本已经有一个可以继续开发的 Compose 原生骨架：

```text
android-compose/
├── settings.gradle.kts
├── build.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/yuchen/ailedger/MainActivity.kt
        └── res/
```

目前 `MainActivity.kt` 里包含：

```text
AiLedgerComposeApp      原生主框架
ChatScreen              原生 AI 聊天页
ToolsScreen             原生功能页入口
SettingsScreen          原生设置分组入口
GlassBottomBar          原生底部导航
MessageBubble           原生聊天气泡
ChatComposer            原生输入框
```

## 如何打开

用 Android Studio 打开这个目录：

```text
android-compose/
```

不要打开仓库根目录，否则 Android Studio 可能会把 GitHub Pages 网页目录也当成普通文件一起扫。

打开后等待 Gradle Sync 完成，然后运行 `app` 模块。

## 迁移原则

不要再把整个 `index.html` 包进 WebView 里当主界面。

后续应该逐步变成：

```text
Compose 原生主界面
├── 原生聊天页
├── 原生功能页
├── 原生设置页
├── 原生手机能力调用
├── 原生本地数据库
└── WebView 只作为旧版备用入口或网页阅读器
```

旧 WebView 不要马上删除。建议先保留一个“旧版网页界面”入口，等原生功能稳定后再移除。

## 下一步建议

第一步：确认这个工程能在 Android Studio 正常同步和运行。

第二步：把聊天状态从 `MainActivity.kt` 内部状态迁到 `ViewModel`。

第三步：把网页里的本地命令识别逻辑，从 JS 迁到 Kotlin，例如：

```text
ai-command-router-v2.js     -> Kotlin CommandRouter
chat-actions.js             -> Kotlin ActionHandler
navigation-preferences.js   -> Kotlin PreferenceStore
```

第四步：把账单记录迁到 Room 数据库。

第五步：把云端 AI 接口迁到 Kotlin 网络层。

第六步：把手机能力，例如闹钟、导航、打开应用，逐步接到 Android Intent 或辅助服务。

## 重要提醒

Compose 这边不要复刻 WebView 的重度 `backdrop-filter` 模糊。安卓端建议使用轻量玻璃感：半透明背景、渐变、高光、圆角、少量阴影。这样更稳，也更不容易掉帧。
