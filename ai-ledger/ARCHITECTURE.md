# AI Ledger 前端代码地图

> 目的：把当前补丁式增长的前端脚本先整理成清晰边界，后续修改先看本文件，避免误伤液态玻璃 UI、导航动画和安卓 WebView 稳定性。

## 当前原则

1. **不要先大重构。** 现阶段以“文档归类 + 小步抽离 + 冒烟测试”为主。
2. **液态玻璃 UI 稳定文件优先冻结。** 这些文件负责视觉和 WebView 稳定性，除非明确修 UI 问题，否则不要改。
3. **AI 能力文件和 UI 动画文件分开维护。** AI 路由、模型选择、联网搜索、附件识别不要继续塞进动画稳定文件里。
4. **所有脚本加载顺序都要谨慎。** `index.html` 里的脚本顺序会影响模型选择、附件上传、云端指令桥、动作卡片和本地路由。

## 前端主入口

- `index.html`
  - 页面结构和脚本加载顺序。
  - 不建议频繁整文件替换。
  - 新功能优先新增独立 JS 文件，再在这里追加加载。

- `app-v3.js`
  - 主应用运行时。
  - 负责聊天、账单、状态刷新、基础交互等核心流程。
  - 后续尽量不要继续把新 AI 特性塞进这里，应通过独立模块挂载。

## AI 聊天相关

- `ai-command-router-v2.js`
  - 本地高置信动作路由。
  - 只保留明确、低风险、本地可确定的动作。
  - 例如：导航偏好、导航开始/修改、提醒、记账等。
  - 低置信、开放式语义不要继续在这里堆关键词，交给云端 Orchestrator。

- `cloud-command-bridge.js`
  - 云端 command/mobileCommand 到本地动作卡片的桥。
  - 只负责验证、补全、转换、注入联网参数。
  - 不应该和天气、新闻、普通聊天抢活。

- `chat-attachments.js`
  - 图片、文件、PDF 上传前端逻辑。
  - 负责把附件带到 Worker。
  - 注意聊天记录保存时必须先 snapshot，避免清空同一个数组引用。

- `chat-source-badges.js`
  - 当前包含：气泡底部模型标签、模型选择器、请求注入、生成动画、滚动兜底。
  - 这个文件责任已经偏重，后续建议拆成：
    - `chat-model-picker.js`
    - `chat-source-badges.js`
    - `chat-typing-indicator.js`
    - `chat-scroll-stability.js`
    - `chat-request-patcher.js`

- `assistant-profile.js`
  - 助手身份、默认提示、欢迎语等。
  - 不负责模型调度和云端调用。

## 工具中心和动作执行

- `chat-actions.js`
  - 聊天动作卡片基础逻辑。

- `tools-center.js`
  - 功能页/工具中心。

- `navigation-preferences.js`
  - 导航偏好：家、学校、公司、默认地图、默认出行方式等。

- `navigation-execution-compat.js`
  - 导航执行兼容层。
  - 负责百度/高德 Deep Link、公交/地铁/驾车参数兼容。
  - 不负责识别用户语义。

## 设置与视觉稳定文件：默认冻结

这些文件主要服务液态玻璃 UI、设置页和安卓/iOS WebView 稳定性。除非明确做视觉修复，否则不要改：

- `ios-glass-motion.js`
- `glass-stability.js`
- `settings-detail-polish.js`
- `ui-density-polish.js`
- `layout-stability-polish.js`
- `navigation-polish.js`
- `settings-groups.js`
- `settings-appearance-plus.js`

## 推荐脚本加载顺序

当前建议保持大致顺序：

1. `config.js`
2. `chat-actions.js`
3. `assistant-profile.js`
4. `navigation-preferences.js`
5. `chat-attachments.js`
6. `chat-source-badges.js`
7. `tools-center.js`
8. `ai-command-router-v2.js`
9. `cloud-command-bridge.js`
10. `app-v3.js`
11. UI/设置/玻璃稳定脚本
12. `navigation-execution-compat.js`
13. `auth.js` / `sync.js`

如果新增聊天补丁脚本，优先放在 `app-v3.js` 后面；如果它要拦截请求，必须在发起请求前加载。

## 冒烟测试清单

每次改 AI 或聊天相关文件后，至少测试：

- `tackle是什么意思`
- `重庆今天天气如何`
- `今天有什么大新闻吗`
- `导航坐公交去重庆大学`
- `把家设为重庆大学`
- `明天早上8点叫我起床`
- 上传图片并说：`提取图中文字`
- 分别选择 `自动 / Kimi / Mistral / Gemini / Workers` 后提问

## 后续整理路线

第一阶段：只写文档和边界说明，不动功能。

第二阶段：从 `chat-source-badges.js` 里拆出模型选择器、生成动画、滚动稳定和请求注入。

第三阶段：把本地动作路由和云端动作桥的 schema 固化，减少重复字段补丁。

第四阶段：删除或移动旧 Worker 入口到 `legacy/`，但必须确保 `wrangler.toml` 仍指向 Orchestrator。
