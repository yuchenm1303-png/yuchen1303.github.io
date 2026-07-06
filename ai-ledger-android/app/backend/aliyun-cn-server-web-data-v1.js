/*
 * AI Ledger 云端后端（单文件部署包 · V261 自然真人感人格增强 · 基于 V260 随机轮换稳定版）
 *
 * 合并原则：
 * - 保留最新版的云端记忆、记忆检索与使用追踪、自定义指令、聊天、联网、表情、认证和服务基础设施。
 * - V211 将表情频率改为滚动节奏配额，默认设置也具备明确约束；高强度采用温和上限，避免连续刷屏。
 * - V212 删除聊天前置记忆关键词门控、独立记忆规划器和二次语义解析器；最终聊天模型直接通过原生
 *   memory_upsert / memory_delete 工具决定是否写入或删除，后端只做权限、参数、事务与真实回执。
 * - V213 将读取状态与写入事务状态彻底分离：写入成功时顶层状态为 mutation_applied，不再显示
 *   ready_empty；同时复用同轮已验证账号设置，并记录 Supabase 请求头、响应体、解析、服务端执行与
 *   实例冷热信息，用事实区分 SQL、跨区网络、连接建立、网关排队和响应体传输耗时。
 * - V214 将普通聊天、记忆、记账、联网、内部设备控制、导航提醒和视觉智能统一接入最终聊天模型的
 *   原生 Tool Calling 循环。普通聊天不再运行独立设备 Probe、联网意图 Router、DeepSeek 内部工具
 *   Planner 或 Android 本地 Auto 模型裁决；后端只做能力声明、Schema 校验、权限/风险控制、事务执行、
 *   客户端工具转交与真实回执。记忆和记账使用完全独立的工具名、参数契约和执行边界，禁止互相降级。
 * - V215 为所有需 Android 执行的工具增加统一 clientToolCall 协议与稳定 callId；兼容保留旧
 *   agentAction / mobileAction / preferenceUpdate 外壳，但它们不再是唯一交接依据。Android 必须按
 *   结构化工具名和参数机械执行，并将真实回执交回同一 Final Chat Model，未收到 verified 回执前禁止宣称成功。
 * - V216 修复输入法/临时系统覆盖层抢占前台包造成的工作表面误失效：后端只在同一会话已有严格验证目标、
 *   新观察绑定完整、当前前台包不属于可启动 App、且输入动作或显式覆盖层证据成立时延续底层工作表面。
 *   GUI Plus 仍是唯一视觉决策者；ExecutionPermit 绑定实际报告前台包，同时单独携带有效工作表面包，避免
 *   因 IME 弹出错误重开目标 App，也不会把真正切换到其他可启动 App 误判为覆盖层。
 * - V217 将视觉任务改为严格的“当前轮调用域”：computer_run_task 的执行目标强绑定本轮最新用户原话，
 *   模型工具参数只能作为诊断参考，不能覆盖当前请求；同一 agentSessionId 一旦目标或任务调用标识变化，
 *   立即销毁旧 GUI 历史、路由、完成候选和工作表面连续性后创建全新会话。新增幂等视觉会话遗忘协议，
 *   用户取消、客户端停止、视觉完成或失败收口后均可立即删除服务端任务状态，旧任务不得进入下一次请求。
 * - V218 将 Agent 开关升级为真正的协议级强制视觉入口：客户端显式 agent_start 请求在进入最终聊天模型前
 *   直接返回 run_agent_task，不再让普通聊天模型决定是否启动视觉。普通模式下进一步拆分 Android 系统应用管理页
 *   与 App 内设置页：open_app_settings 不再暴露在通用 device_control 枚举中，而由独立、无歧义的系统管理工具承接。
 *   任一客户端执行工具被 Schema 或策略拒绝后，同一最终模型必须继续修正工具调用，禁止退回手动教程或假装完成。
 * - V235 修复 Agent 开关 visual-only 普通聊天被误判为 visual_agent_step 的后端路由问题；
 *   forceVisualAgent 只表示最终聊天模型工具箱强制收窄为 computer_run_task / computer_observe_screen，
 *   不再代表 Android 已进入视觉循环。客户端执行工具下发阶段不生成本地可见完成话术。
 * - V236 修复“确认/开始/继续”类承接消息被当作视觉任务 goal 的问题：Agent 开关和 agent_start
 *   只收窄最终聊天模型工具域，不再绕过最终模型直接下发视觉任务；computer_run_task 的 goal 以
 *   最终模型结合完整上下文写入的 tool 参数为准，当前用户原话只作为缺失参数时的兜底。
 * - V237 只修内联表情协议传输层：彻底清除旧 Unicode Tag 残留，避免菱形问号/字体乱码；
 *   表情 marker 后遇到 Markdown 标题、列表、引用、表格、代码块或公式块时强制断行；
 *   流式与最终回复以同一套协议清洗和排版保护为准，不改动本地表达决策器。
 * - V247 将普通聊天表情从“正文里靠模型自觉插 marker”改为“纯正文 + 结构化候选侧栏”。
 *   模型输出多个语义锚点候选，后端只按频率密度和表达强度在候选中调度；
 *   若模型候选不足，才使用完整段落/列表项末尾安全锚点兜底，避免 Markdown 排版被字符串硬插破坏。
 * - V256 只优化表情表达强度/丰富度：保留 V254 稳定启动链与 V247 候选侧栏主结构；
 *   将 asset_key 从严格语义标签降级为弱视觉风格建议，高强度下由后端按资源目录轮换机械替换，
 *   避免长期只使用 soft_smile、thinking_soft、got_it_point、idea_drawing 等少数常用表情。
 * - V259 继续收缩表情强度逻辑：取消低/中强度粉毛核心池、近期冷却排序提示；普通聊天中模型只负责候选位置，
 *   assetKey 可视为占位风格，默认强度起即由后端按全目录顺序做无语义机械轮换，减少 key 文案带来的常用表情偏置。
 * - V219 对齐 Android 新视觉调用域：保留 reportedForegroundPackage 与 effectiveWorkSurfacePackage 双证据，
 *   覆盖层不再伪装成目标 App；clientToolCall 结果续写增加同客户端调用关联、同 callId 幂等缓存与冲突拒绝，
 *   服务重启后仍兼容未知旧调用。视觉会话遗忘同步移除未完成调用关联，但不影响已完成结果的幂等重放。
 * - 视觉子系统精确恢复到用户提供的 v177 / Android v15 unified execution permit 稳定基线。
 * - Android 只负责观察、设备现场校验和机械执行；视觉路由、GUI Plus 单帧单动作、任务契约与
 *   ExecutionPermit 由稳定后端主链统一管理，不保留后加的 v16 文本 bootstrap、WorkSurface 重放
 *   或 GUI Plus 动作猜测修复层。
 *
 * 维护边界：
 * 00 配置与运行时状态 -> 05 双身份认证 -> 10 HTTP/Provider -> 20 命令协议
 * -> 30 视觉契约 -> 40 智能体编排 -> 50 GUI Plus -> 60 聊天/数据工具
 * -> 65 云端记忆服务 -> 70 HTTP 服务入口。
 */

// ===== AI Ledger source module: 00-config-runtime.js =====
const http = require("http");
const crypto = require("crypto");
const { AsyncLocalStorage } = require("async_hooks");

function resolveServerPort() {
  const rawPort = String(
    process.env.AI_LEDGER_PORT ||
    process.env.FC_SERVER_PORT ||
    process.env.CA_PORT ||
    process.env.CAPORT ||
    process.env.PORT ||
    "9000"
  ).trim();
  const port = Number(rawPort);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`invalid_server_port:${rawPort || "empty"}`);
  }
  return port;
}

const PORT = resolveServerPort();
const LISTEN_HOST = String(process.env.AI_LEDGER_HOST || "0.0.0.0").trim() || "0.0.0.0";
const REQUEST_TIMEOUT_MS = Number(process.env.PROVIDER_TIMEOUT_MS || 30000);
const TOOL_ROUTER_TIMEOUT_MS = Number(process.env.TOOL_ROUTER_TIMEOUT_MS || 18000);
const STRUCTURED_ROUTER_TIMEOUT_MS = Number(process.env.STRUCTURED_ROUTER_TIMEOUT_MS || 2800);
const SEARCH_TIMEOUT_MS = Number(process.env.SEARCH_TIMEOUT_MS || 6000);
const DEVICE_ROUTER_TIMEOUT_MS = Number(process.env.DEVICE_ROUTER_TIMEOUT_MS || 2800);
const ENABLE_DEVICE_MODEL_ROUTER = String(process.env.ENABLE_DEVICE_MODEL_ROUTER || "false").toLowerCase() === "true";
// 内联表情由云端模型选择类型与位置；后端负责协议校验、设置约束、精确重复和最终硬上限，不根据用户文字选择表情。
const ENABLE_CHAT_STICKERS = String(process.env.ENABLE_CHAT_STICKERS || "true").toLowerCase() === "true";
const INLINE_STICKER_DIAGNOSTICS_ENABLED = String(process.env.INLINE_STICKER_DIAGNOSTICS || "true").toLowerCase() !== "false";
const CHAT_STICKER_REPAIR_ENABLED = false; // V240: 正文重写/修稿器永久关闭，表情只走密度提示 + 协议安检。
// truncated