# 智能体统计数据架构

## 目标

第一阶段只建立稳定、可追溯的数据基础，不设计统计页面，不改变现有账单中心，也不接入任何 OpenGL 玻璃链。

统计页面后续统一通过 `AgentAnalyticsViewModel` 读取数据。ViewModel 合并 `AgentAnalyticsRepository` 的活动统计与 `AgentSkillInventoryRepository` 的 Skill 资产统计；界面禁止直接读取诊断目录、当前浮窗状态、账单数据库或操作学习数据库。

## 数据来源

### 普通聊天

`AssistantMemoryUsageBridge` 在一次逻辑聊天请求完成后旁路记录：

- 模型与供应商标识
- 成功或失败
- 请求、响应字节数
- 网络耗时
- 输入、输出、推理、缓存与总 Token
- 联网搜索
- 图片理解请求
- 结构化工具、设备控制和内部能力调用

备用端点只属于同一逻辑聊天请求的传输容错，不会虚增会话数量；最终成功或最终失败会写入一次聊天活动，模型调用统计保留该次最终传输结果。

### GUI Plus / 视觉智能体

`VisualAgentClient` 与 `AgentRuntimeController.progress` 旁路记录：

- 任务开始、结束、结果与持续时间
- 模型轮次与模型失败
- 执行动作、动作成功与失败
- 重新观察、计划拒绝和执行失败
- 风险确认、用户输入、用户接管与恢复
- 使用的动作与应用
- 每次 GUI Plus 请求的 Token、字节数与耗时

统计逻辑不参与模型路由、页面理解、点击决策、完成许可、截图或无障碍模式切换。

### 操作学习 Skill

`AgentSkillInventoryRepository` 只读聚合 `operation_learning.db`，并通过 Room InvalidationTracker 在相关表变化后刷新：

- Skill 总数与各生命周期状态
- 已批准、已验证和可用 Skill 数
- 编译步骤总数
- 覆盖应用数
- 演示次数
- Skill 运行次数与成功次数

它不读取录制轨迹、不复制 Skill 正文，也不参与 Skill 编译或执行。

## Token 口径

每条 Token 数据都带有准确性来源：

- `Provider`：云端响应返回真实 usage。
- `Estimated`：云端没有返回 usage 时，由本地根据请求与响应文本保守估算。

解析器兼容 OpenAI、DashScope、Gemini 与常见自定义 Worker 字段，并支持嵌套的缓存 Token、推理 Token 明细。估算会去除 Android 兼容字段中的重复 prompt/message/text/content，不会把 Base64 图片字符当作文本 Token；每张图只记固定的保守视觉估算量。缓存 Token 作为输入 Token 的子集单独保存，不重复计入总量。推理 Token 在多数协议中已经属于输出 Token，也不重复累计。

后续界面应明确区分真实 Token 与估算 Token，不能将估算值展示成精确计费数据。

## 本地数据库

数据库：`agent_analytics.db`

### `agent_daily_activity`

按本地自然日聚合，是 Token 活动热力图的直接数据源。包含：

- 每日 Token 总量、真实量与估算量
- 聊天、模型调用和失败
- 智能体任务结果
- 任务时长与动作数量
- 观察、重试、确认、输入与接管
- 联网与图片活动

### `agent_token_events`

保留每一次模型请求的细粒度 Token 事件，用于后续按模型、任务和来源追溯。

### `agent_task_analytics`

保留任务级完整摘要，用于任务历史、最长任务、成功率、自主性和失败分析。进程异常结束后，下次启动会把未收口任务标记为中断，避免永久显示为运行中。

### `agent_model_usage`

按模型累计调用、失败、Token、网络耗时和字节数。

### `agent_capability_usage`

按 `feature`、`tool`、`action`、`app` 分类累计能力使用次数及成功失败。

## 保留策略

- 每日热力图聚合：5 年
- Token 细粒度事件：2 年
- 任务摘要：最近 1000 条
- 页面状态默认读取最近 500 条任务计算近期指标

这些数据与视觉诊断黑匣子完全分离。清理诊断截图不会删除用户统计，关闭诊断也不会停止产品统计。

## 第二阶段可直接使用的指标

- 累计 Token、峰值日 Token
- 每日 Token 活动热力图
- Provider Token 与 Estimated Token 占比
- 当前连续活跃天数、最长连续活跃天数
- 聊天请求、模型调用与失败率
- 任务总数、完成数、成功率
- 累计执行动作、重新观察与计划拒绝
- 总任务时长、最长任务时长
- 最常用模型、能力、动作和应用
- 确认次数、输入次数、用户接管次数
- 联网搜索与图片理解活动
- 已探索、待审核、已批准、已验证 Skill 数
- Skill 覆盖应用、步骤、运行与成功情况

## 安全和性能约束

- 所有统计数据库写入均在 IO 协程中完成。
- 统计入口会吞掉自身解析或数据库异常，不能反向影响聊天或智能体执行。
- 不增加无障碍事件监听，不主动扫描节点，不额外截图。
- 不修改 OpenGL 聊天框稳定系统、聊天气泡父级绘制链或任何玻璃 registry。
