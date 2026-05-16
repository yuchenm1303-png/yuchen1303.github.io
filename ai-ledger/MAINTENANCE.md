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

## 当前建议优先整理文件

```txt
ai-ledger/chat-source-badges.js
ai-ledger/chat-attachments.js
ai-ledger/cloud-command-bridge.js
ai-ledger/ai-command-router-v2.js
ai-ledger-worker/src/index-orchestrator.js
ai-ledger-worker/src/index-attachments-gateway.js
ai-ledger-worker/src/index.js
```

## 前端聊天拆分目标

`chat-source-badges.js` 后续应拆成：

```txt
chat-model-picker.js
chat-source-badges.js
chat-typing-indicator.js
chat-scroll-stability.js
chat-request-patcher.js
```

先复制/迁移一小块逻辑，验证稳定后再删除旧逻辑。

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

## 每次提交前自检

- `wrangler.toml` 是否仍然指向 `src/index-orchestrator.js`。
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
