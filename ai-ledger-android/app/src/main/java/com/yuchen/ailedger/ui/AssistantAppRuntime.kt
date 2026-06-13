package com.yuchen.ailedger.ui

import com.yuchen.ailedger.SystemActionRouter
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.service.InstalledAppIndex
import com.yuchen.ailedger.service.MobileCommand

internal const val VISUAL_ATTACHMENT_STATUS_PREFIX = "视觉附件 · "

internal data class PendingMobileAction(
    val originalText: String,
    val command: MobileCommand,
)

internal data class NavigationPreferenceUpdate(
    val slot: String,
    val label: String,
    val address: String,
)

internal data class MobileCommandSnapshot(
    val composerText: String,
    val isSending: Boolean,
    val navigationHomeAddress: String,
    val navigationSchoolAddress: String,
    val navigationCompanyAddress: String,
    val navigationDormAddress: String,
)

internal data class ChatMessagesSideEffectKey(
    val messageCount: Int,
    val notificationSignature: String,
    val latestAssistantSignature: String,
) {
    companion object {
        fun from(messages: List<ChatMessage>): ChatMessagesSideEffectKey {
            val visibleMessages = messages
                .asSequence()
                .filter { it.text.isNotBlank() }
                .filterNot { it.status == MessageStatus.Sending }
                .takeLastCompat(6)
            val notificationSignature = visibleMessages.joinToString("|") { message ->
                "${message.id}:${message.role.name}:${message.status.name}:${message.createdAt}:${message.text.stableShortHash()}"
            }
            val latestAssistant = messages.lastOrNull { it.role == MessageRole.Assistant }
            val latestAssistantSignature = when {
                latestAssistant == null -> "none"
                latestAssistant.status == MessageStatus.Sending -> "${latestAssistant.id}:sending"
                else -> "${latestAssistant.id}:${latestAssistant.status.name}:${latestAssistant.text.stableShortHash()}"
            }
            return ChatMessagesSideEffectKey(
                messageCount = messages.size,
                notificationSignature = notificationSignature,
                latestAssistantSignature = latestAssistantSignature,
            )
        }
    }
}

internal fun visibleComposerTextForAssistant(text: String): String {
    return if (text.trim().startsWith(VISUAL_ATTACHMENT_STATUS_PREFIX)) "" else text
}

@Suppress("UNUSED_PARAMETER")
internal fun parseInstalledAppOpenCommand(text: String, installedAppIndex: InstalledAppIndex): MobileCommand? {
    // 已停用旧的“打开/启动/开启 + 模糊应用名”发送前拦截。
    // 所有应用打开与 App 内后续页面任务都必须保留完整原始目标，交给 AgentBrain / GUI Plus 判断。
    // 这样“打开 QQ”与“打开 QQ 找到设置页”不会再被本地字符串规则截断成同一个打开应用弹窗。
    return null
}

internal fun parsePendingMobileActionFromLatestMessage(messages: List<ChatMessage>): PendingMobileAction? {
    val latest = messages.lastOrNull { it.role == MessageRole.Assistant && it.status == MessageStatus.Sent } ?: return null
    val text = latest.text
    val marker = "[mobile_command:"
    val start = text.indexOf(marker)
    if (start < 0) return null
    val end = text.indexOf("]", startIndex = start)
    if (end <= start) return null
    val body = text.substring(start + marker.length, end)
    val parts = body.split("|", limit = 3)
    return when (parts.firstOrNull()) {
        // 旧后端遗留的 open_app marker 不再转换成确认弹窗。
        // 新链路必须返回 agentAction/run_agent_task 或结构化内部工具步骤。
        "open_app" -> null
        else -> null
    }
}

internal fun parseCloudNavigationPreferenceUpdate(messages: List<ChatMessage>): NavigationPreferenceUpdate? {
    val latest = messages.lastOrNull { it.role == MessageRole.Assistant && it.status == MessageStatus.Sent } ?: return null
    val marker = "[navigation_pref:"
    val start = latest.text.indexOf(marker)
    if (start < 0) return null
    val end = latest.text.indexOf("]", startIndex = start)
    if (end <= start) return null
    val body = latest.text.substring(start + marker.length, end)
    val parts = body.split("|", limit = 3)
    val slot = parts.getOrNull(0).orEmpty()
    val label = parts.getOrNull(1).orEmpty()
    val address = parts.getOrNull(2).orEmpty()
    if (slot !in setOf("home", "school", "company") || address.isBlank()) return null
    return NavigationPreferenceUpdate(slot, label, address)
}

internal fun isNavigationPreferenceAlreadySaved(snapshot: MobileCommandSnapshot, update: NavigationPreferenceUpdate): Boolean {
    return when (update.slot) {
        "home" -> snapshot.navigationHomeAddress == update.address
        "school" -> snapshot.navigationSchoolAddress == update.address
        "company" -> snapshot.navigationCompanyAddress == update.address
        else -> false
    }
}

internal fun MobileCommand.resolveNavigationAddress(snapshot: MobileCommandSnapshot): MobileCommand {
    if (this !is MobileCommand.Navigate) return this
    val resolvedDestination = when (destination) {
        "home" -> snapshot.navigationHomeAddress
        "school" -> snapshot.navigationSchoolAddress
        "company" -> snapshot.navigationCompanyAddress
        "dorm" -> snapshot.navigationDormAddress
        else -> destination
    }
    return if (resolvedDestination.isBlank()) this else copy(destination = resolvedDestination)
}

internal fun isConfirmMobileActionText(text: String): Boolean = text.trim() in setOf("确认", "好的", "打开", "执行", "确定")
internal fun isCancelMobileActionText(text: String): Boolean = text.trim() in setOf("取消", "不用", "算了")

internal fun executeMobileCommand(router: SystemActionRouter?, command: MobileCommand): Pair<Boolean, String> {
    if (router == null) return false to "当前环境无法执行手机动作。"
    return when (command) {
        is MobileCommand.OpenApp -> {
            val opened = when {
                !command.launchUri.isNullOrBlank() -> router.openDeepLink(command.launchUri, command.packageName, command.appName)
                !command.packageName.isNullOrBlank() -> router.openApp(command.packageName, command.appName)
                else -> false
            }
            opened to if (opened) "已尝试打开 ${command.appName}。" else "没有找到 ${command.appName}，请确认是否已安装。"
        }
        is MobileCommand.Navigate -> router.startNavigation(command.destination).let { ok -> ok to if (ok) "已尝试打开导航。" else "没有可用的地图应用。" }
        is MobileCommand.SetAlarm -> router.setAlarm(command.hour, command.minute, command.label).let { ok -> ok to if (ok) "已尝试设置闹钟。" else "无法打开系统闹钟。" }
    }
}

private fun Sequence<ChatMessage>.takeLastCompat(count: Int): List<ChatMessage> {
    if (count <= 0) return emptyList()
    val buffer = ArrayDeque<ChatMessage>(count)
    forEach { message ->
        if (buffer.size == count) buffer.removeFirst()
        buffer.addLast(message)
    }
    return buffer.toList()
}

private fun String.stableShortHash(): Int {
    return replace('\n', ' ')
        .trim()
        .take(180)
        .hashCode()
}
