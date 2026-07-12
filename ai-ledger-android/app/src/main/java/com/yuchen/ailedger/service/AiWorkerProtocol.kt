package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel

internal const val AI_WORKER_DEFAULT_CONNECT_TIMEOUT_MS = 15_000
internal const val AI_WORKER_DEFAULT_READ_TIMEOUT_MS = 240_000
internal const val AI_WORKER_QWEN_VISION_ROUTE_ID = "qwen_vision"
internal const val AI_WORKER_CHAT_CLIENT_NAME = "android-compose"
internal const val AI_WORKER_CHAT_PROTOCOL_VERSION = 7
internal const val AI_WORKER_AUTO_ROUTE_AUTHORITY = "cloud_final_model_v1"
internal const val AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_SCHEMA =
    "ai_ledger_normal_chat_device_tool_probe_v2"
internal const val AI_WORKER_CLIENT_TOOL_CALL_SCHEMA = "ai_ledger_client_tool_call_v1"
internal const val AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL = "android_client_tool_result_v1"
internal const val AI_WORKER_NORMAL_CHAT_DEVICE_PROBE_MAX_APPS = 180

internal val AI_WORKER_NORMAL_CHAT_DEVICE_TOOL_TYPES: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    (DeviceControlRouter.normalChatSupportedStepTypes() + CloudAgentStep.ledgerToolTypes)
        .distinct()
        .sorted()
}

internal data class AiWorkerModelRoute(
    val requested: ChatModel,
    val resolved: ChatModel,
    val reason: String,
) {
    val isAuto: Boolean get() = requested == ChatModel.Auto
}
