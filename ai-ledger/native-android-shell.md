# AI Ledger Android 原生化设计稿

## 总方向

当前 `ai-ledger/` 继续作为 Web 内容区保留，Android 端新增一个原生外壳。第一阶段目标不是重写所有页面，而是把最吃性能、最像系统体验的部分交给 Kotlin / Jetpack Compose：底部导航、顶部栏、原生玻璃容器、触感反馈、系统能力入口。

最终结构：

```text
Android 原生壳
├─ NativeGlassScaffold：液态玻璃背景、状态栏、底部导航、弹窗容器
├─ AiLedgerWebView：加载 ai-ledger/index.html
├─ NativeBridge：Web ↔ Android 通信
└─ SystemActionRouter：闹钟、打开应用、导航、通知入口等系统动作

Web 内容区
├─ AI 对话
├─ 功能中心
├─ 账单 / 统计
├─ 设置详情页
└─ 现有 JS 业务逻辑
```

## 第一阶段只做什么

1. 新建 Android 原生壳工程。
2. 用 WebView 加载现有 `ai-ledger/index.html`。
3. Compose 负责底部导航和主容器玻璃质感。
4. Web 侧通过 `native-bridge.js` 检测原生壳是否存在。
5. 原生壳存在时，Web 页面后续隐藏自己的 `.bottom-nav`，避免出现双底栏。

## 不在第一阶段做什么

1. 不重写 AI 聊天页面。
2. 不重写设置页全部表单。
3. 不马上做复杂 Shader 折射。
4. 不依赖华为、小米等厂商私有接口。
5. 不把所有系统控制能力一次性接完。

## 玻璃分级策略

### Basic

所有 Android 设备默认使用。用半透明背景、渐变、高光边框、轻阴影模拟玻璃，不使用重实时模糊。

### Blur

Android 12+ 开启。只在底部导航、弹窗背景、当前选中胶囊等少数区域使用原生模糊。

### Liquid

Android 13+ 且性能足够时开启。加入轻微流光、胶囊拉伸、按压弹性和边缘高光。

### Safe

低端机、省电模式、部分 WebView 或 ROM 表现不稳时开启。关闭动态模糊、降低阴影、减少弹性动画。

## Web 与原生桥接契约

Web 侧入口文件：`native-bridge.js`

原生侧建议暴露对象名：

```text
AiLedgerNative
AiLedgerAndroid
AndroidQuickAi
QuickAiBridge
```

核心消息：

```json
{
  "id": "1",
  "type": "webReady",
  "payload": {},
  "version": "2026.05.17-native-shell-phase-1",
  "at": 1779000000000
}
```

建议首批能力：

```text
webReady
haptic
setGlassMode
openView
closeQuickAi
openFullApp
```

系统动作后续再接：

```text
setAlarm
openApp
startNavigation
requestNotificationAccess
requestAccessibilityAccess
```

## Android 原生壳页面结构

```text
NativeGlassScaffold
├─ 背景渐变层
├─ 原生顶部栏
├─ WebView 内容区
├─ 原生底部导航栏
└─ 原生 Toast / Sheet / Dialog 容器
```

底部导航只保留一个可移动的液态胶囊，三个按钮只做透明度、缩放和触感反馈。不要让每个按钮都独立做复杂模糊。

## 迁移优先级

第一优先级：底部导航、顶部栏、小窗入口、触感反馈。

第二优先级：设置详情弹窗、权限中心、AI 输入栏。

第三优先级：功能中心卡片、任务记录、系统动作执行历史。

第四优先级：账单和统计页面。它们可以最后迁移，因为当前 Web 版本已经能支撑业务逻辑。

## 验收标准

1. Web 版仍可在 GitHub Pages 正常打开。
2. Android 原生壳能加载现有 Web 内容。
3. 原生底部导航切换时不卡顿、不黑块、不闪烁。
4. 华为、小米设备只做能力降级，不为每个厂商重写一套。
5. 后续每个系统动作都必须有权限提示和失败兜底。
