# tools/

这个目录用于后续承接 Orchestrator 中的具体工具逻辑。

当前工具逻辑仍在 `index-orchestrator.js` 和 `index-attachments-gateway.js` 中，暂时不移动，先建立目录边界。

## 未来文件规划

```txt
weather.js       天气查询、城市清洗、Open-Meteo geocode/forecast
web-search.js    Tavily 搜索、搜索结果总结、参考网址格式化
attachment.js    附件类型清洗、图片/PDF/文本处理
intent.js        意图分类 prompt、启发式分类、false-positive guard
```

## 工具边界

- `weather.js` 只负责天气，不参与新闻/普通聊天。
- `web-search.js` 只负责联网搜索和总结，不处理本地动作。
- `attachment.js` 只负责附件解析和识图，不作为 Worker 主入口。
- `intent.js` 只判断意图，不直接生成长回答。

## 迁移原则

- 先把独立函数搬出来，例如 `cleanWeatherLocation`、`weatherText`、`formatSourceLinks`。
- 每次迁移后都测：天气、新闻、普通聊天、本地动作。
- 不要让工具层直接执行手机动作。手机动作必须通过 command protocol 回到本地确认执行。
