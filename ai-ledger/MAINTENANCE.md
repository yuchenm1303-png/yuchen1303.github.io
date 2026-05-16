# AI Ledger 维护规则

> 本文件用于约束后续修改方式：先保稳定，再做整理。

## 修改优先级

1. **先修功能正确性**：模型是否真的匹配、天气/新闻是否走正确工具、导航是否执行正确模式。
2. **再修体验问题**：标签显示、滚动位置、生成动画。
3. **最后做结构重构**：拆文件、移动目录、删除旧入口。

## 禁止事项

- 不要一次性整文件覆盖 `index.html`。
- 不要把 Worker main 改回旧入口。
- 不要在液态玻璃稳定文件里加入 AI 业务逻辑。
- 不要让云端直接执行本地动作。
- 不要在本地关键词路由里继续堆低置信开放式问答。

## 可接受的小步修改

- 新增独立 JS 文件并在 `index.html` 加载。
- 给旧文件加 Deprecated 注释。
- 从大文件中抽出纯函数模块。
- 修复一个问题只动一个小范围。
- 每次改完跑固定冒烟测试。

## 旧代码处理流程

旧代码不要马上删除，按三步走：

```txt
标记 Deprecated
↓
确认没有主入口依赖
↓
移动到 legacy/ 或删除
```

## 当前建议冻结文件

这些文件关系到当前液态玻璃外观和 WebView 稳定性：

```txt
ai-ledger/ios-glass-motion.js
ai-ledger/glass-stability.js
ai-ledger/settings-detail-polish.js
ai-ledger/ui-density-polish.js
ai-ledger/layout-stability-polish.js
ai-ledger/navigation-polish.js
```

修改这些文件前必须明确目标是 UI/动画/稳定性，不要顺手塞 AI 逻辑。

## 当前稳定入口

```txt
ai-ledger/index.html
ai-ledger/app-v3.js
ai-ledger/chat-source-badges.js
ai-ledger-worker/src/index-orchestrator-diagnostics.js
```

其中 `index-orchestrator-diagnostics.js` 是诊断外壳，内部仍调用 `index-orchestrator.js`。

## 前端聊天模块现状

聊天相关补丁已经拆成模块，由 `chat-source-badges.js` 统一加载：

```txt
chat-request-patcher.js          注入 modelPreference，并拉长模型请求超时
chat-scroll-stability.js         聊天区滚动稳定
chat-typing-indicator.js         正在生成动画
chat-model-picker.js             模型选择器
chat-source-badges-core.js       气泡来源标签
chat-message-actions-polish.js   重试、复制、用户气泡复制
```

后续新增聊天 UI 功能，优先新建独立模块，再由 `chat-source-badges.js` 加载；不要再把大量逻辑塞回 `app-v3.js`。

## 当前建议优先整理文件

```txt
ai-ledger/chat-attachments.js
ai-ledger/cloud-command-bridge.js
ai-ledger/ai-command-router-v2.js
ai-ledger-worker/src/index-orchestrator.js
ai-ledger-worker/src/index-attachments-gateway.js
ai-ledger-worker/src/index.js
```

## Worker 拆分目标

`index-orchestrator.js` 后续应逐步抽出：

```txt
providers/gemini.js
providers/nvidia.js
providers/workers-ai.js
providers/tavily.js
tools/weather.js
tools/web-search.js
shared/json.js
shared/cors.js
shared/model-meta.js
```

当前已经开始建立 shared：

```txt
ai-ledger-worker/src/shared/response.js
ai-ledger-worker/src/shared/model-meta.js
```

但暂时不要强行整文件改 `index-orchestrator.js`，后续从附件网关或较短文件开始接入 shared。

## 当前模型状态

```txt
Gemini 2.5 Flash：可用，但免费层容易 429 限流
Workers AI：轻量稳定，适合作为默认快速兜底
Mistral via NVIDIA NIM：可用性取决于 NVIDIA 免费端点
Kimi via NVIDIA NIM：经常慢、排队或超时，不建议作为默认优先模型
```

自动模式建议：优先 Workers / Gemini，Kimi 仅作为手动体验或低优先级备用。

## 每次提交前自检

- `wrangler.toml` 是否仍然指向 `src/index-orchestrator-diagnostics.js`。
- `index.html` 是否仍能按顺序加载核心脚本。
- 是否误动了冻结 UI 文件。
- 是否新增了未加载的 JS 文件。
- 是否修改了 Cloudflare Secret 名称但没有同步文档。
- 是否改变了手动模型选择严格模式。

## 发布后测试

Worker 改动：

```txt
GitHub Actions → Deploy AI Worker → dev-update-1
```

前端脚本改动：

```txt
GitHub Actions → Build Android APK → dev-update-1
```

测试语句：

```txt
tackle是什么意思
重庆今天天气如何
今天有什么大新闻吗
导航坐公交去重庆大学
把家设为重庆大学
明天早上8点叫我起床
上传图片并提取文字
```

## 近期事故记录

- `index.html` 曾因整文件更新被截断，导致脚本链路缺失、页面变形。后续严禁在未完整校验时覆盖该文件。
- `app-startup-stability.js` 是一次启动遮罩实验，已确认不适合当前页面加载链路并删除。
