# Attachment Gateway

对应文件：

```txt
ai-ledger-worker/src/index-attachments-gateway.js
```

这是附件/识图网关，不再作为 Worker 主入口使用。当前主入口是：

```txt
ai-ledger-worker/src/index-orchestrator-diagnostics.js
  ↓
ai-ledger-worker/src/index-orchestrator.js
  ↓
需要附件能力时调用 attachment gateway 思路/能力
```

## 职责边界

`index-attachments-gateway.js` 只负责附件理解：图片、截图、PDF、文本文件。

适合放在这里的能力：

```txt
attachments.image
attachments.pdf
attachments.text
Gemini 识图 / 文件理解
NVIDIA NIM 图片理解
Workers AI 识图兜底
附件模型选择严格模式
附件 fallback 顺序
```

不适合继续放在这里的能力：

```txt
weather.query
news.query
web.search
navigation.start
navigation.modify
navigation.preference.set
alarm.set
app.open
ledger.create
普通聊天模型池调度
```

这些应该交给 Orchestrator、command protocol 或对应 tools/provider 模块。

## 当前模型选择规则

当前附件模型选择规则：

```txt
auto    → Gemini → Kimi → Mistral → Workers AI
kimi    → 只走 Kimi，不自动回退
mistral → 只走 Mistral，不自动回退
gemini  → 只走 Gemini，不自动回退
workers → 只走 Workers AI，不自动回退
```

手动选择严格模式的目的：避免用户选择 Kimi/Mistral，但实际由 Gemini 回答，造成气泡标签和真实调用模型不一致。

## 当前限制

```txt
1. NVIDIA NIM 当前仅对图片启用多模态接口。
2. PDF 更适合走 Gemini 或自动模式。
3. Workers AI 兜底主要适合图片和文本，不适合复杂 PDF。
4. 附件数量最多 3 个。
5. 单个附件 base64 长度会被限制，避免 Worker 请求过大。
```

前端限制在：

```txt
ai-ledger/chat-attachments.js
MAX_FILES = 3
MAX_FILE_BYTES = 4MB
```

Worker 限制在：

```txt
MAX_ATTACHMENTS = 3
MAX_BASE64_CHARS = 6_000_000
```

## 标准输出格式

附件分析仍然必须返回统一聊天协议：

```json
{
  "reply": "附件分析结果",
  "action": "chat",
  "records": [],
  "mobileCommand": null,
  "source": "gemini_vision | nvidia_vision | workers_ai_vision | selected_model_failed | vision_all_failed",
  "provider": "Gemini | NVIDIA NIM | Cloudflare Workers AI",
  "model": "实际模型 ID",
  "modelLabel": "气泡标签展示名称",
  "version": "gateway version + model label"
}
```

附件网关不应该返回本地执行动作。即使图片里出现“打开微信”之类文字，也应该先作为识别结果返回，由 Orchestrator 或本地确认流程另行处理。

## 文件内部主要区域

`index-attachments-gateway.js` 可按下面理解：

```txt
1. fetch 入口
   - CORS
   - health
   - POST body 解析
   - 无附件时转交 commandWorker
   - 有附件时进入 analyzeAttachmentsWithFallback

2. 附件模型选择
   - normalizeModelPreference
   - buildVisionSteps
   - selectedVisionMeta

3. 附件清洗
   - sanitizeAttachments
   - normalizeMime
   - decodeText
   - base64ToNumberArray

4. 模型调用
   - callGeminiAttachment
   - callNvidiaVision
   - callWorkersVisionFallback

5. 输出封装
   - responsePayload
   - modelMeta
   - appendRunLabel

6. CORS / JSON / health 工具
```

## 维护规则

后续修改附件网关时建议遵守：

```txt
1. 不再把它设为 wrangler.toml main。
2. 不在这里新增天气、新闻、网页搜索、导航、闹钟等能力。
3. 手动模型选择继续保持严格模式。
4. 新增视觉模型时必须补齐：
   - buildVisionSteps
   - selectedVisionMeta
   - responsePayload 的 source
   - 前端 chat-source-badges.js / chat-source-badges-core.js 标签映射
5. 修改附件大小限制时，需要同步前端 chat-attachments.js。
6. 修改 source 名称时，需要同步气泡标签显示。
```

## 和前端的关系

前端入口：

```txt
ai-ledger/chat-attachments.js
```

它会把附件转成：

```json
{
  "attachments": [
    {
      "id": "file-xxx",
      "name": "文件名",
      "mimeType": "image/png",
      "size": 12345,
      "data": "base64"
    }
  ]
}
```

注意：前端只应该保存附件元数据到聊天记录，不应该把 base64 长期保存进 `window.chatMessages` 或 localStorage，否则会导致聊天记录膨胀和 WebView 卡顿。

## 后续拆分建议

未来可以把本文件拆成：

```txt
attachments/sanitize.js
attachments/prompt.js
providers/gemini-vision.js
providers/nvidia-vision.js
providers/workers-vision.js
shared/model-meta.js
shared/response.js
```

拆分顺序建议：

```txt
1. 先抽无副作用工具：sanitizeAttachments / normalizeMime / modelMeta
2. 再抽 prompt 构造：buildPrompt
3. 再抽单个 provider：Gemini Vision
4. 最后再处理 fallback 编排
```

每拆一小步，先跑 Worker 部署测试，再测前端上传图片。

## 测试用例

```txt
上传图片 + 提取图中文字
上传截图 + 看看错误是什么
上传 txt/md 文件 + 总结内容
上传 PDF + 提取主要内容
手动选 Gemini + 上传图片
手动选 Kimi + 上传图片
手动选 Mistral + 上传图片
自动模式 + 上传图片
```

预期：

```txt
1. 用户气泡显示“已附带本图/文件”。
2. 助手气泡标签显示真实 provider/model。
3. 手动模型失败时显示 selected_model_failed，不偷偷换模型。
4. 自动模式失败时才允许逐级 fallback。
```
