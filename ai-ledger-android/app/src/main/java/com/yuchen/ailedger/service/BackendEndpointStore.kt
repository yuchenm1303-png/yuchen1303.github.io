package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.AiLedgerApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val AI_WORKER_TENCENT_SERVER_ENDPOINT = "http://122.51.175.208:9000"

/**
 * App 端后端环境选择。这里只决定请求哪个后端，不参与主模型选择。
 * Qwen / DeepSeek 仍由对应 server.js 后端文件自己决定。
 */
enum class BackendEndpointMode(
    val id: String,
    val label: String,
    val shortLabel: String,
    val endpoint: String,
    val description: String,
) {
    TencentServer(
        id = "server",
        label = "腾讯云服务器",
        shortLabel = "服务器",
        endpoint = AI_WORKER_TENCENT_SERVER_ENDPOINT,
        description = "备用 · 腾讯云轻量服务器",
    ),
    AliyunFunction(
        id = "aliyun",
        label = "阿里云函数计算",
        shortLabel = "阿里云",
        endpoint = AI_WORKER_ALIYUN_CN_ENDPOINT,
        description = "默认 · 阿里云函数计算",
    );

    companion object {
        fun fromId(id: String?): BackendEndpointMode = entries.firstOrNull { it.id == id } ?: AliyunFunction
    }
}

data class BackendEndpointState(
    val mode: BackendEndpointMode = BackendEndpointMode.AliyunFunction,
) {
    val endpoint: String = mode.endpoint
    val label: String = mode.label
}

class BackendEndpointStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())

    val state: StateFlow<BackendEndpointState> = _state.asStateFlow()

    fun setMode(mode: BackendEndpointMode) {
        prefs.edit().putString(KEY_MODE, mode.id).apply()
        _state.value = readState()
    }

    fun currentState(): BackendEndpointState {
        val latest = readState()
        if (_state.value != latest) _state.value = latest
        return latest
    }

    private fun readState(): BackendEndpointState {
        return BackendEndpointState(mode = BackendEndpointMode.fromId(prefs.getString(KEY_MODE, null)))
    }

    companion object {
        private const val PREFS_NAME = "ai_worker_backend_endpoint"
        private const val KEY_MODE = "backend_mode"

        @Volatile
        private var instance: BackendEndpointStore? = null

        fun get(context: Context): BackendEndpointStore {
            return instance ?: synchronized(this) {
                instance ?: BackendEndpointStore(context.applicationContext).also { instance = it }
            }
        }

        fun currentEndpointOrDefault(defaultEndpoint: String = AI_WORKER_ALIYUN_CN_ENDPOINT): String {
            val fallback = defaultEndpoint.ifBlank { AI_WORKER_ALIYUN_CN_ENDPOINT }
            val context = AiLedgerApplication.contextOrNull()?.applicationContext ?: return fallback
            return get(context).currentState().endpoint.ifBlank { fallback }
        }
    }
}
