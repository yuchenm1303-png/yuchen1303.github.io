# providers/

这个目录用于后续逐步承接云端模型和外部服务的调用逻辑。

当前还没有正式抽离代码，先建立边界说明，避免继续把 provider 调用全部塞进 `index-orchestrator.js`。

## 未来文件规划

```txt
gemini.js       Google Gemini 文本/视觉调用
nvidia.js       NVIDIA NIM / Kimi / Mistral 调用
workers-ai.js   Cloudflare Workers AI 调用
tavily.js       Tavily 搜索调用
open-meteo.js   Open-Meteo 天气和地理编码调用
```

## 迁移原则

- 先抽纯函数，不改变返回结构。
- 每次只迁移一个 provider。
- 迁移后 `/health` 必须仍显示真实 providerPool。
- 手动模型选择严格模式不能变：手动选哪个模型，就只能调用哪个模型。

## Provider 返回建议

统一返回：

```js
{
  ok: true,
  text: '模型返回文本',
  provider: 'Gemini | NVIDIA NIM | Cloudflare Workers AI | Tavily | Open-Meteo',
  model: '真实模型 id',
  modelLabel: '展示名称',
  source: 'gemini_chat | nvidia_chat | tavily_ai_summary | weather_tool'
}
```

错误时返回或抛出明确错误，不要吞掉真实 HTTP 状态。Kimi/Mistral 调用失败时尤其要保留 NVIDIA 原始错误摘要。
