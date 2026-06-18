package com.yuchen.ailedger.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.StructuredDataCard
import com.yuchen.ailedger.model.WebSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationChatWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val userMessageId = inputData.getString(KEY_USER_MESSAGE_ID).orEmpty()
        val pendingMessageId = inputData.getString(KEY_PENDING_MESSAGE_ID).orEmpty()
        val prompt = inputData.getString(KEY_PROMPT).orEmpty()
        if (userMessageId.isBlank() || pendingMessageId.isBlank() || prompt.isBlank()) {
            return Result.failure()
        }

        val requestMessages = NotificationChatStore.requestMessages(applicationContext, userMessageId)
        if (requestMessages.isEmpty()) return Result.success()

        return try {
            val response = withContext(Dispatchers.IO) {
                AiWorkerClient().sendChat(
                    messages = requestMessages,
                    modelPreference = ChatModel.Auto,
                    onlineEnabled = shouldAutoEnableOnline(prompt)
                )
            }
            NotificationChatStore.complete(
                context = applicationContext,
                pendingMessageId = pendingMessageId,
                reply = response.toNotificationReply(),
                source = response.source,
                model = response.model,
                modelLabel = response.modelLabel
            )
            ChatNotificationManager.showPersistentChatEntry(applicationContext)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val friendly = error.message
                ?.takeIf { it.isNotBlank() }
                ?: "AI 请求失败，请检查网络后重试。"
            NotificationChatStore.fail(
                context = applicationContext,
                pendingMessageId = pendingMessageId,
                prompt = prompt,
                errorMessage = friendly
            )
            ChatNotificationManager.showPersistentChatEntry(applicationContext)
            // The failure is represented inside the notification. Returning success keeps later
            // queued notification prompts from inheriting a failed prerequisite.
            Result.success()
        }
    }

    private fun shouldAutoEnableOnline(text: String): Boolean {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return false
        val noOnlinePattern = Regex(
            pattern = "(不用联网|不要联网|别联网|不需要联网|无需联网|不要搜索|别搜索|不用搜索|不要查网页|不用查网页)",
            option = RegexOption.IGNORE_CASE
        )
        if (noOnlinePattern.containsMatchIn(cleanText)) return false
        val realtimePattern = Regex(
            pattern = "(今天|明天|现在|当前|实时|最新|新闻|热点|天气|气温|温度|下雨|降雨|降水|带伞|汇率|兑换|美元|人民币|日元|欧元|英镑|港币|股价|股票|行情|美股|港股|A股|a股|纳斯达克|道琼斯|标普|查一下|查查|搜索|联网|网上|官网|价格|多少钱|比赛|赛程|排名|榜单)",
            option = RegexOption.IGNORE_CASE
        )
        return realtimePattern.containsMatchIn(cleanText)
    }

    private fun AiChatResponse.toNotificationReply(): String {
        val sections = mutableListOf(reply.trim())
        structuredData?.toNotificationBlock()?.takeIf { it.isNotBlank() }?.let(sections::add)
        webSources.toNotificationSources().takeIf { it.isNotBlank() }?.let(sections::add)
        return sections.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun StructuredDataCard.toNotificationBlock(): String {
        val header = listOfNotNull(title, subtitle, timestamp)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val values = metrics.take(4).joinToString("\n") { metric ->
            val unit = metric.unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            "${metric.label}：${metric.value}$unit"
        }
        return listOf(header, values, rawText.orEmpty().take(240))
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun List<WebSource>.toNotificationSources(): String {
        if (isEmpty()) return ""
        val lines = take(3).mapIndexed { index, source ->
            val name = source.title.ifBlank { source.domain.ifBlank { "来源 ${index + 1}" } }
            "${index + 1}. $name"
        }
        return "参考来源：\n${lines.joinToString("\n")}"
    }

    companion object {
        const val KEY_USER_MESSAGE_ID = "user_message_id"
        const val KEY_PENDING_MESSAGE_ID = "pending_message_id"
        const val KEY_PROMPT = "prompt"
    }
}
