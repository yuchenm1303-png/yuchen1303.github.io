# AI Ledger Agent 生产收尾基线

本文件记录当前 Android Compose App 与云端 Agent 后端的最终收口基线，避免后续修改重新引入重复执行、跨聊天串线、伪流式或无障碍空闲高负载。

## 当前生产基线

- Android 分支：`dev-update-1`
- 云端后端：`deepseek-gui-plus-cn-web-data-v312-production-final-stream-regression-guard`
- Android Workspace 协议：`compose-native-project-workspace-v3-thread-scoped`
- 客户端工具执行：统一使用持久化 `ClientToolExecutionLedger`
- 客户端工具回执：统一使用持久化 Outbox，并按 `conversationId` 恢复
- Workspace 检查点：仅在等待 Android 客户端工具时持久化，AES-256-GCM 加密
- 普通正文：真实 SSE 增量提交
- required-tool 与恢复轮次：保持事务提交，禁止用可见文本替代原生工具调用

## 必须保持的架构边界

1. `AssistantViewModel` 必须调用 `AiWorkerClient.streamChat`，收到 delta 后在 `MessageStatus.Sending` 状态更新同一消息。
2. 客户端工具执行完成后的续写必须继承原始 `onDelta`，不能回退到一次性 JSON。
3. 后端普通最终回答只能保留短协议观察窗，禁止恢复整轮 `roundDeltas` 缓冲。
4. 原生 `tool_calls`、DSML、XML 和文本化 `action` 必须保持零泄漏，并且不得从可见文本推断执行副作用。
5. 项目、计划、账本和确定性设备写操作必须复用同一个执行账本；查询类工具不得写入幂等记录。
6. Workspace、工具回执、GUI Plus 会话和活动项目必须绑定同一个 `conversationId`。
7. 只有 `GlassRole.Shell` 可以进入 OpenGL 大玻璃链；聊天 OpenGL Host 尺寸链和消息气泡父级绘制链不得改写。
8. 无障碍 XML 必须保持 Idle 低负载配置，Working 模式只能由任务运行时短暂开启。

## 云端部署顺序

1. 保留现有 Supabase `agent_workspace_checkpoints` 表。
2. 保持稳定的 `AGENT_WORKSPACE_CHECKPOINT_SECRET`。
3. 配置 `AGENT_WORKSPACE_CHECKPOINT_SERVICE_KEY` 或 `SUPABASE_SERVICE_ROLE_KEY`。
4. 部署 V312 单文件后端。
5. 访问 `/readyz`，确认：
   - `ok=true`
   - `status=ready`
   - `startupDiagnostics=[]`
   - `version=deepseek-gui-plus-cn-web-data-v312-production-final-stream-regression-guard`
6. 再构建并安装当前 `dev-update-1` APK。

## 发布前验收

- 普通长回复在模型仍生成时持续出现正文，不得结束后整包弹出。
- Workspace 工具任务实时展示公开进度，Android 工具完成后继续流式输出。
- 同一 `toolCallId` 重试计划、账本、项目和设备写操作时只执行一次。
- 两个聊天线程分别编辑不同项目，不得串用活动项目或检查点。
- 断网后恢复客户端工具回执，不得重复产生副作用。
- 高风险设备操作未确认时不执行；确认后可以正常重试。
- GUI Plus 视觉任务仍走独立视觉决策和 Android 机械执行链。
- `testDebugUnitTest`、Debug APK、Performance APK、轻量契约和流式契约全部通过。

## 回退原则

后端出现问题时优先回退单文件后端，不移动 Android 分支。Android 必须回退时，按项目规则将 `dev-update-1` 指向指定提交，并额外修改 `ai-ledger-android/app/.build-trigger.txt` 创建无害构建提交。禁止用根目录文本文件、Gradle patch 或 workflow 自动改写 Kotlin 源码。
