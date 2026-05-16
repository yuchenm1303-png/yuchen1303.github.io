# AI Ledger Worker 云端架构地图

> 目的：把云端 AI 从“多入口补丁”整理成“统一 Orchestrator + 专用工具模块 + 本地执行协议”的结构，避免天气、新闻、普通聊天、附件识别和本地动作互相抢活。

## 当前部署入口

`wrangler.toml` 当前应保持：

```toml
main = "src/index-orchestrator.js"
```

不要改回：

```txt
src/index-multimodal.js
src/index-attachments-gateway.js
```

附件网关只作为 Orchestrator 调用的能力层，不再作为最终统一入口。

## 当前核心流程

```txt
用户一句话
↓
前端本地高置信路由 ai-command-router-v2.js
↓
本地能处理：直接生成动作卡片
↓
本地不能处理：请求 Cloudflare Worker
↓
index-orchestrator.js 判断 intent
↓
天气 / 联网搜索 / 附件 / 普通聊天 / command protocol
↓
云端只返回结构化 JSON 或普通 reply
↓
本地 cloud-command-bridge.js 验证并转成动作卡片
↓
用户确认后本地执行
```

## Worker 文件职责

### `src/index-orchestrator.js`

当前统一入口。

负责：

- 判断 intent。
- 分流到天气、联网搜索、附件网关、普通聊天、command protocol。
- 管理模型选择：`auto / kimi / mistral / gemini / workers`。
- 维护“手动选择严格模式”：手动选哪个模型，就只用哪个模型；只有 `auto` 允许 fallback。

不负责：

- 直接执行本地手机动作。
- 直接控制安卓系统。
- 把复杂 UI 逻辑塞到 Worker。

### `src/index-attachments-gateway.js`

附件能力网关。

负责：

- 图片识别。
- PDF / 文本文件读取。
- 附件模型选择。
- Gemini / NVIDIA NIM / Workers AI 视觉兜底。

注意：

- 它不是主入口。
- 识图失败时应明确返回模型失败原因，不要伪装成本地听不懂。

### `src/index.js`

原 command protocol Worker。

负责：

- `navigation.start`
- `navigation.modify`
- `navigation.preference.set`
- `alarm.set`
- `app.open`
- `ledger.create`
- `chat` 兼容

当前仍保留，因为 Orchestrator 会把本地动作类请求委托给它。

## 推荐后续目录结构

现在先不移动文件，后续稳定后可逐步整理为：

```txt
src/
  index-orchestrator.js
  index-attachments-gateway.js
  index.js

  providers/
    gemini.js
    nvidia.js
    workers-ai.js
    tavily.js

  tools/
    weather.js
    web-search.js
    attachment.js

  protocol/
    command-protocol.js
    mobile-command-schema.js

  shared/
    cors.js
    json.js
    errors.js
    model-meta.js
    memory.js
```

## 模型变量规范

建议 Cloudflare Variables 明确配置：

```txt
GEMINI_MODEL = gemini-2.5-flash
AI_MODEL = @cf/meta/llama-3.1-8b-instruct

NVIDIA_BASE_URL = https://integrate.api.nvidia.com/v1
NVIDIA_KIMI_MODEL = moonshotai/kimi-k2.6
NVIDIA_MISTRAL_MODEL = mistralai/mistral-medium-3.5-128b
NVIDIA_PLANNER_MODEL = mistralai/mistral-medium-3.5-128b
PLANNER_PROVIDER = nvidia
```

Secret：

```txt
GEMINI_API_KEY
TAVILY_API_KEY
NVIDIA_API_KEY
```

注意：

- `NVIDIA_KIMI_MODEL` 和 `NVIDIA_MISTRAL_MODEL` 应拆开，不要都复用 `NVIDIA_CHAT_MODEL`。
- NVIDIA 页面能看到模型，不等于该模型一定能被当前 API Key 直接调用。
- 如果 Kimi/Mistral 返回 HTTP 错误，优先检查 View Code 里的精确模型名、endpoint 类型、API key 权限和额度。

## Intent 边界

### 天气

走 Open-Meteo 工具。

示例：

```txt
重庆今天天气如何
今天会下雨吗
```

如果没有城市，应追问城市，不能把“新闻”“生命定义”等词当城市。

### 新闻 / 联网搜索

走 Tavily 搜索，再由模型总结。

示例：

```txt
今天有什么大新闻吗
搜一下 fussing 是什么意思
```

搜索结果必须整理总结，并在末尾附参考网址。

### 普通聊天

走模型池。

示例：

```txt
人工智能的定义是什么
tackle 是什么意思
```

### 本地动作

交给 command protocol。

示例：

```txt
导航坐公交去重庆大学
把家设为重庆大学
明天早上8点叫我起床
打开微信
今天午饭28
```

## 手动模型选择规则

```txt
auto    → 允许 Kimi / Mistral / Gemini / Workers AI 自动 fallback
kimi    → 只调用 Kimi，失败就返回所选模型失败
mistral → 只调用 Mistral，失败就返回所选模型失败
gemini  → 只调用 Gemini，失败就返回所选模型失败
workers → 只调用 Workers AI，失败就返回所选模型失败
```

这样做的原因：用户手动选择模型时，标签必须和实际回答模型一致，不能出现“选择 Kimi，实际 Gemini 回答”。

## 回包结构约定

云端统一返回：

```json
{
  "reply": "给用户看的回复",
  "action": "chat | draft | mobile_command",
  "records": [],
  "mobileCommand": null,
  "source": "gemini_chat | nvidia_chat | tavily_ai_summary | weather_tool | ...",
  "provider": "Gemini | NVIDIA NIM | Cloudflare Workers AI | Tavily | Open-Meteo",
  "model": "真实模型 id",
  "modelLabel": "展示给用户看的模型名称",
  "version": "当前 worker 版本"
}
```

前端标签应优先使用 `source + modelLabel + version`，不能只根据当前按钮猜测。

## 冒烟测试

部署 Worker 后访问：

```txt
/health
```

应看到：

```txt
version: ai-ledger-orchestrator-...
providerPool
selectableModels
attachmentGateway.version
```

每次改 Worker 后测试：

- `重庆今天天气如何`
- `今天有什么大新闻吗`
- `人工智能的定义是什么`
- `tackle是什么意思`
- `导航坐公交去重庆大学`
- `把家设为重庆大学`
- `明天早上8点叫我起床`
- 上传图片并提取文字
- 模型选择：自动 / Kimi / Mistral / Gemini / Workers
