package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel

internal const val AI_WORKER_DEFAULT_CONNECT_TIMEOUT_MS = 15_000
internal const val AI_WORKER_DEFAULT_READ_TIMEOUT_MS = 45_000
internal const val AI_WORKER_QWEN_VISION_ROUTE_ID = "qwen_vision"
internal const val AI_WORKER_CHAT_CLIENT_NAME = "android-compose"
internal const val AI_WORKER_CHAT_PROTOCOL_VERSION = 6
internal const val AI_WORKER_AUTO_ROUTE_AUTHORITY = "android_local_v2"
internal const val AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_SCHEMA =
    "ai_ledger_normal_chat_device_tool_probe_v2"
internal const val AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_MAX_APPS = 180

internal val AI_WORKER_NORMAL_CHAT_DEVICE_TOOL_TYPES: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    DeviceControlRouter.normalChatSupportedStepTypes()
}

internal data class AiWorkerModelRoute(
    val requested: ChatModel,
    val resolved: ChatModel,
    val reason: String,
) {
    val isAuto: Boolean get() = requested == ChatModel.Auto
}
