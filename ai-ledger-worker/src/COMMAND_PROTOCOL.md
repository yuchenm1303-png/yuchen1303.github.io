# Command Protocol Worker

对应文件：

```txt
ai-ledger-worker/src/index.js
```

这是旧版但仍然重要的 command protocol Worker。当前主入口不是它，而是：

```txt
ai-ledger-worker/src/index-orchestrator-diagnostics.js
  ↓
ai-ledger-worker/src/index-orchestrator.js
  ↓
必要时调用 command protocol 能力
```

## 职责边界

`index.js` 只负责结构化本地指令协议，不应该继续承担天气、新闻、复杂联网搜索或多模型编排。

适合放在这里的能力：

```txt
navigation.start
navigation.modify
navigation.preference.set
alarm.set
app.open
ledger.create / draft
chat fallback
```

不适合继续放在这里的能力：

```txt
weather.query
news.query
web.search
attachment.analyze
model provider pool
Gemini / NVIDIA / Workers AI 多模型调度
复杂 intent 分类
```

这些应该交给 Orchestrator 或专门工具层。

## 当前协议返回格式

标准输出应该保持：

```json
{
  "reply": "给用户看的回复",
  "action": "chat | draft | mobile_command",
  "records": [],
  "mobileCommand": null,
  "source": "gemini_structured",
  "version": "ai-ledger-worker-command-protocol-v1"
}
```

本地动作必须走：

```txt
action = mobile_command
mobileCommand.intent = 允许的 intent
```

记账草稿必须走：

```txt
action = draft
records = 待确认账单数组
```

普通聊天必须走：

```txt
action = chat
records = []
mobileCommand = null
```

## 文件内部主要区域

`index.js` 目前可按下面逻辑理解：

```txt
1. fetch 入口
   - CORS
   - health
   - JSON body 解析
   - 输入归一化
   - deterministic 规则优先
   - Gemini structured fallback

2. Prompt 构造
   - buildSystemInstruction
   - buildUserContext
   - callGemini

3. 确定性规则
   - tryDeterministicReply
   - parseNavigationStartCommand
   - parseNavigationModifyCommand
   - parseNavigationPreferenceCommand
   - parseAlarmCommand
   - parseOpenAppCommand
   - parseSimpleRecords

4. 输出安全清洗
   - normalizeWorkerResponse
   - sanitizeAction
   - sanitizeReply
   - sanitizeMobileCommand
   - sanitizeNavigationUpdates
   - sanitizePreferenceUpdates

5. 输入安全清洗
   - normalizeCommandProtocol
   - normalizeNavigationContext
   - normalizeWebSearch
   - normalizeMessages
   - sanitizeRecords
   - sanitizeLedgerContext
   - sanitizeClientTools

6. 小工具函数
   - CORS / JSON
   - 日期
   - 分类
   - 金额
   - 地点别名
```

## 维护规则

后续修改 `index.js` 时，建议遵守：

```txt
1. 不直接整文件覆盖。
2. 不把 Worker main 改回这个文件。
3. 新能力优先放到 Orchestrator 或 tools/provider 模块。
4. 这里只保留 command protocol 和 deterministic local command。
5. 输出必须经过 normalizeWorkerResponse。
6. mobileCommand 必须经过 sanitizeMobileCommand。
7. 新增 intent 时必须同时更新：
   - ALLOWED_INTENTS
   - buildSystemInstruction
   - sanitizeMobileCommand
   - 前端 cloud-command-bridge.js
   - 前端执行兼容层
```

## 和前端的关系

前端主要由这几个文件消费 command protocol：

```txt
ai-ledger/cloud-command-bridge.js
ai-ledger/ai-command-router-v2.js
ai-ledger/navigation-execution-compat.js
```

因此修改协议字段时，必须同时检查：

```txt
mode / travelMode / navigationMode / transportMode
mapProvider
destination / destinationAlias
routeOptions
updates
```

尤其导航模式必须保证公交/地铁能一路传到地图 Deep Link，避免再次出现“卡片显示公交，但地图打开默认驾车”的问题。

## 当前整理策略

`index.js` 本体暂时不动，因为它虽然不是主入口，但仍可能被 Orchestrator 或测试流程依赖。后续真正拆分时，建议先抽出无副作用纯函数：

```txt
shared/response.js
shared/model-meta.js
shared/command-protocol.js
shared/sanitize.js
```

再逐步把 deterministic parser 拆到：

```txt
parsers/navigation.js
parsers/alarm.js
parsers/app-open.js
parsers/ledger.js
```

每次只迁移一个 parser，并跑 Worker 部署测试。
