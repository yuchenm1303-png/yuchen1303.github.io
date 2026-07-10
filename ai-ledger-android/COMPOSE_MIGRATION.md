# AI Ledger 原生 Compose 架构说明

本文档用于区分当前正式运行链路、历史兼容代码和受保护架构。整理代码时必须以本文件和实际入口源码为准，不能仅凭文件名判断是否可删除。

## 当前正式运行链路

当前 Android App 使用顶层 `app` 模块构建原生 Kotlin / Jetpack Compose APK。

正式入口链路：

```text
app/src/main/AndroidManifest.xml
    → com.yuchen.ailedger.MainActivity
    → com.yuchen.ailedger.ui.AiAssistantNativeApp
    → CachedAppTabHost
    → Assistant / Tools / Settings
```

核心目录：

- App 入口：`app/src/main/java/com/yuchen/ailedger/MainActivity.kt`
- 根 Compose 路由：`app/src/main/java/com/yuchen/ailedger/ui/App.kt`
- 首页与聊天：`app/src/main/java/com/yuchen/ailedger/ui/AssistantHomePolished.kt`
- 主状态：`app/src/main/java/com/yuchen/ailedger/AssistantViewModel.kt`
- 数据模型：`app/src/main/java/com/yuchen/ailedger/model/`
- 数据层：`app/src/main/java/com/yuchen/ailedger/data/`
- 服务层：`app/src/main/java/com/yuchen/ailedger/service/`
- OpenGL 与 Compose UI：`app/src/main/java/com/yuchen/ailedger/ui/`

顶层 Gradle 工程只包含 `:app` 模块。正式 Compose 构建不依赖旧 `android/` 子工程。

## 正式构建

正式 APK workflow：

```text
.github/workflows/build-compose-android-apk.yml
```

workflow 名称：

```text
Build Compose Android APK
```

主要构建任务：

```text
:app:testDebugUnitTest
:app:assembleDebug
:app:assemblePerformance
```

统一使用以下文件进行必要的无害构建触发：

```text
app/.build-trigger.txt
```

不得重新增加 Gradle 文本替换补丁、临时源码 patch 或 workflow 自动修改 Kotlin 源码的链路。

## 历史兼容区

以下目录和文件来自早期 Capacitor / WebView 混合架构，目前不属于正式 Compose App 入口：

- `android/`
- 与 `AiLedgerWebViewFactory`、`AiLedgerNativeBridge`、`AiLedgerNativeShell` 相关的旧混合壳代码
- 旧 Capacitor 构建脚本和网页同步脚本

这些内容暂时作为历史兼容区保留。删除前必须同时满足：

1. 当前入口、Manifest、Compose 路由均无引用。
2. 全仓引用检查无调用。
3. Debug 与 Performance APK 均成功构建。
4. 轻量契约检查通过。
5. 真机核心功能回归通过。

不能因为文件看起来旧，就直接批量删除整个服务或数据目录。Manifest 注册组件、反射入口、Room/KSP 模型和 JavaScript bridge 必须单独核查。

## 受保护架构

### OpenGL 聊天框稳定链

禁止破坏或移除：

- `FixedHeightOverflowSlot`
- `modelPanelVisualHeight`
- `modelExpandDelta`
- `LocalOpenGLGlassSurfaceAnchor`
- `ChatPanelV2(viewportTopInset = modelExpandDelta)`
- `GlassPanel(... viewportTopInset = viewportTopInset)`

模型栏展开高度不得重新参与聊天大玻璃 Host 的真实布局压缩。

### 聊天气泡完整功能链

整理或优化 `AssistantHomePolished.kt` 时必须保留：

- `RichMessageContent`
- `MessageDataCards`
- `AnimatedMessageBubbleV2`
- `revealedMessageIds`
- `rememberRevealTextStateV2`
- `GeneratingMessageContentV2`
- `StreamingAssistantContentV2`
- `SweepingProgressTextV2`
- `TypewriterTrailV2`
- `LongReplyToggleV2`
- `ThinkingDotsV2`
- `thinkingPearlSurface`
- `MessageActionsV2`
- `MessageAttachmentListV2`
- `MessageBadgeV2`

性能优化只能缩小状态读取范围、稳定参数或减少无效工作，不能删减视觉和交互能力。

### OpenGL 角色边界

只允许真正的大玻璃容器使用单卡 OpenGL：

```text
GlassRole.Shell
```

`Card`、`Chip`、`Floating`、`Nav`、`Flex`、雾面玻璃、凹槽玻璃及普通小组件必须与 OpenGL registry 和 geometry sync 隔离。

### 无障碍低负载基准

`app/src/main/res/xml/ai_agent_accessibility_service.xml` 必须保持 Idle 低负载配置。窗口事件监听只能由 Kotlin 在任务执行期间临时切换，任务结束后必须恢复 Idle。

## 清理原则

代码整理按以下顺序进行：

1. 删除历史触发文件、测试残留和未接入构建的 patch 文件。
2. 清理不会参与运行的生成中间物和重复文档。
3. 使用引用检查和构建结果确认旧 Kotlin 文件是否可删除。
4. 最后再处理包结构和职责拆分。

每一阶段都必须保持 Kotlin / Compose 源码为 APK 的唯一真实实现，不允许通过 Gradle、脚本或 workflow 在构建期间改写核心源码。
