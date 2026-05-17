# WebView 到 Compose 迁移规划

目标：把当前 `ai-ledger/` 里的 WebView 主界面逐步迁到 Android 原生 Compose，让 WebView 从“主渲染层”退回到“备用/网页内容展示层”。

## 当前状态

Compose 原生界面已经跑通，并开始迁入真实原生逻辑。

已经完成：

```text
android-compose/app/src/main/java/com/yuchen/ailedger/MainActivity.kt
原生主界面、聊天页、输入框、底部导航、功能页、设置页入口。

android-compose/app/src/main/java/com/yuchen/ailedger/model/AppTab.kt
底部导航模型。

android-compose/app/src/main/java/com/yuchen/ailedger/model/AssistantModels.kt
聊天消息、动作卡片、记账草稿模型。

android-compose/app/src/main/java/com/yuchen/ailedger/logic/CommandRouter.kt
从网页 JS 迁来的第一版 Kotlin 本地命令识别。

android-compose/app/src/main/java/com/yuchen/ailedger/android/AndroidActionExecutor.kt
设置闹钟、导航、打开 App 的 Android Intent 执行框架。
```

现在可以测试的原生输入：

```text
明天早上8点叫我起床
导航回家
打开微信
打开支付宝
今天午饭28
今天奶茶12
工资到账300
```

## 为什么之前卡顿变化不明显

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

状态：基本完成，后续需要拆分文件和进一步打磨 UI。

对应文件：

```text
android-compose/app/src/main/java/com/yuchen/ailedger/MainActivity.kt
```

已完成：

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

状态：第一版本地命令路由已完成。

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

当前已迁：

```text
1. 消息列表状态：已在 Compose 中实现，下一步移入 ChatViewModel
2. 本地意图识别：已迁到 CommandRouter.kt
3. 手机动作卡片：已在 Compose 聊天气泡下展示
4. 执行动作按钮：已接 AndroidActionExecutor.kt
```

下一步：

```text
1. 把消息状态从 MainActivity.kt 移到 ChatViewModel
2. 把云端 AI 请求迁到 Kotlin AssistantRepository
3. 把聊天记录持久化到 DataStore 或 Room
```

## 阶段 3：手机能力迁移

状态：第一版 Intent 框架已完成。

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

当前已迁：

```text
1. 设置闹钟 / 提醒：已接 AlarmClock.ACTION_SET_ALARM
2. 导航回家：已接 geo Intent，后续接家庭地址偏好
3. 打开微信、支付宝等常用 App：已接包名映射和启动 Intent
```

下一步：

```text
1. 设置页添加家庭地址
2. 设置页添加默认地图
3. 支持更多常用 App 包名
4. 增加失败提示和权限引导
```

## 阶段 4：账单与数据

状态：下一批重点。

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

状态：已有静态入口，未接真实数据。

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

当前为了便于快速启动，UI 还集中在 `MainActivity.kt`。后续稳定后建议拆成：

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
1. 重新跑 GitHub Actions 生成 0.2.0-native-router APK
2. 手机实测：闹钟、导航、打开微信、记账草稿
3. 加 ChatViewModel
4. 加 Room 数据库
5. 把记账草稿的“确认记账”接入本地保存
6. 接云端 AI 接口
```
