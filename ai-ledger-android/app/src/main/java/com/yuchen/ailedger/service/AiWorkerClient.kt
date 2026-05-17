package com.yuchen.ailedger.service

data class AiWorkerConfig(
    val endpoint: String = AiWorkerClient.DEFAULT_ENDPOINT
)

class AiWorkerClient(
    private val config: AiWorkerConfig = AiWorkerConfig()
) {
    val endpoint: String
        get() = config.endpoint

    companion object {
        const val DEFAULT_ENDPOINT = "https://ai-ledger-parser.552078638.workers.dev"
    }
}
