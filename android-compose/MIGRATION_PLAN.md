# WebView 到 Compose 迁移规划

目标：把当前 `ai-ledger/` 里的 WebView 主界面逐步迁到 Android 原生 Compose，让 WebView 从“主渲染层”退回到“备用/网页内容展示层”。

## 为什么现在卡顿变化不明显

当前网页版本的性能瓶颈主要来自：

```text
index.html 一次性加载所有页面
大量 CSS 玻璃模糊和动画
JS 全局事件监听
DOM 字符串整块重绘
MutationObserver 反复扫描新增节点
安卓 WebView 对 backdrop-filter / blur 支持成本高
```

如果 Compose 只是外面包了一层 WebView，这些成本仍然存在。

## 阶段 1：原生主框架

状态：已开始。

对应文件：

```text
android-compose/app/src/main/java/com/yuchen/ailedger/MainActivity.kt
```

目标：

```text
原生底部导航
原生聊天页
原生输入框
原生功能页入口
原生设置分组入口
轻量玻璃风格背景
```

不要做：

```text
不要在主界面继续加载 ai-ledger/index.html
不要复刻 WebView 的重度 backdrop-filter
不要一开始就做复杂液态动画
```

## 阶段 2：聊天逻辑迁移

网页来源：

```text
ai-ledger/app-v3.js
ai-ledger/chat-actions.js
ai-ledger/ai-command-router-v2.js
ai-ledger/cloud-command-bridge.js
```

Compose 目标模块：

```text
ChatViewModel
ChatMessage
CommandRouter
AssistantRepository
```

迁移顺序：

```text
1. 消息列表状态
2. 本地意图识别
3. 记账草稿卡片
4. 云端 AI 请求
5. 手机动作卡片
```

## 阶段 3：手机能力迁移

网页来源：

```text
navigation-preferences.js
chat-actions.js
cloud-command-bridge.js
```

Compose / Android 目标：

```text
AlarmIntentHandler
NavigationIntentHandler
OpenAppIntentHandler
PhonePreferenceStore
```

优先级：

```text
1. 设置闹钟 / 提醒
2. 导航回家
3. 打开微信、支付宝等常用 App
4. 后续再做更高权限的系统控制
```

## 阶段 4：账单与数据

网页来源：

```text
localStorage: ai-ledger-records-v3
app-v3.js 里的 records
```

Compose / Android 目标：

```text
Room Database
LedgerDao
LedgerRepository
LedgerViewModel
```

迁移顺序：

```text
1. 新增账单
2. 账单列表
3. 删除账单
4. 预算
5. 导出 JSON
6. 云同步
```

## 阶段 5：设置页迁移

网页来源：

```text
settings-groups.js
settings-appearance-plus.js
settings-preferences.js
settings-performance-mode.js
background-picker.js
```

Compose 目标：

```text
SettingsScreen
AppearanceSettingsScreen
PhonePreferenceScreen
DataBudgetScreen
AccountSyncScreen
```

建议先做静态入口，再接真实数据。

## 阶段 6：旧 WebView 降级为备用入口

最终结构：

```text
Compose 原生主界面
└── 旧版网页入口：仅调试/兼容使用
```

WebView 不再承担主界面渲染。

## 建议的文件拆分

当前为了便于快速启动，代码先集中在 `MainActivity.kt`。后续稳定后建议拆成：

```text
ui/
  AiLedgerComposeApp.kt
  theme/AiLedgerTheme.kt
  components/GlassCard.kt
  components/BottomNav.kt
  chat/ChatScreen.kt
  tools/ToolsScreen.kt
  settings/SettingsScreen.kt
model/
  ChatMessage.kt
  LedgerRecord.kt
logic/
  CommandRouter.kt
  LocalAssistant.kt
android/
  AlarmIntentHandler.kt
  NavigationIntentHandler.kt
  OpenAppIntentHandler.kt
```

## 近期最优先任务

```text
1. 确认 android-compose 工程可运行
2. 拆出 ChatScreen
3. 加 ChatViewModel
4. 把本地命令识别从 JS 迁到 Kotlin
5. 加旧 WebView 备用入口
6. 接第一个真实手机动作：设置闹钟
```
