package com.yuchen.ailedger.ui

import android.net.Uri
import com.yuchen.ailedger.model.ActionGroupContentBlock
import com.yuchen.ailedger.model.KeyValueContentBlock
import com.yuchen.ailedger.model.MessageActionType
import com.yuchen.ailedger.model.MessageContentBlock

internal data class ProjectPreviewDescriptor(
    val id: String,
    val projectId: String,
    val revisionId: String?,
    val title: String,
    val description: String?,
    val frameworkLabel: String,
    val statusLabel: String,
    val fileCount: Int?,
    val previewUrl: String,
) {
    val stableKey: String get() = "$projectId:${revisionId.orEmpty()}"
}

internal sealed interface PresentedMessageContent {
    data class Standard(val block: MessageContentBlock) : PresentedMessageContent
    data class Project(val descriptor: ProjectPreviewDescriptor) : PresentedMessageContent
}

/**
 * Converts the legacy project summary + action pair into one compact project result card.
 *
 * V285 intentionally emitted only generic key/value and action blocks. Keeping this adapter on the
 * Android side means old replies become the new UI immediately while newer backends may migrate to
 * a dedicated project_preview schema later without duplicating cards.
 */
internal fun adaptAgentResultContent(blocks: List<MessageContentBlock>): List<PresentedMessageContent> {
    if (blocks.isEmpty()) return emptyList()
    val consumed = BooleanArray(blocks.size)
    val projectsBySummaryIndex = LinkedHashMap<Int, ProjectPreviewDescriptor>()

    blocks.forEachIndexed { actionIndex, block ->
        val actionGroup = block as? ActionGroupContentBlock ?: return@forEachIndexed
        val previewAction = actionGroup.actions.firstOrNull { action ->
            action.type == MessageActionType.OpenUrl && ProjectPreviewActivity.canOpen(action.value)
        } ?: return@forEachIndexed
        val uri = runCatching { Uri.parse(previewAction.value) }.getOrNull() ?: return@forEachIndexed
        val projectId = uri.getQueryParameter("projectId")?.trim().orEmpty()
        if (projectId.isBlank()) return@forEachIndexed
        val revisionId = uri.getQueryParameter("revision")?.trim()?.takeIf(String::isNotBlank)

        val summaryIndex = blocks.indices
            .asSequence()
            .filter { index -> index < actionIndex && !consumed[index] }
            .mapNotNull { index -> (blocks[index] as? KeyValueContentBlock)?.let { index to it } }
            .lastOrNull { (_, summary) ->
                summary.id.contains(projectId, ignoreCase = true) ||
                    summary.id.startsWith("project-", ignoreCase = true) &&
                    summary.items.any { item -> item.value == revisionId }
            }
            ?.first
            ?: return@forEachIndexed

        val summary = blocks[summaryIndex] as KeyValueContentBlock
        val framework = summary.items.firstOrNull { it.label.contains("项目类型") }
        val revision = summary.items.firstOrNull { it.label.contains("当前版本") }
        val status = summary.items.firstOrNull { it.label.contains("构建状态") }
        val fileCount = revision?.detail
            ?.let { Regex("(\\d+)\\s*个文件").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

        projectsBySummaryIndex[summaryIndex] = ProjectPreviewDescriptor(
            id = "project-preview-$projectId-${revisionId.orEmpty()}",
            projectId = projectId,
            revisionId = revisionId ?: revision?.value?.takeIf { it.startsWith("rev_") },
            title = summary.title?.takeIf(String::isNotBlank) ?: "网页项目",
            description = null,
            frameworkLabel = framework?.detail?.takeIf(String::isNotBlank)
                ?: framework?.value?.takeIf(String::isNotBlank)
                ?: "HTML · CSS · JavaScript",
            statusLabel = status?.value?.takeIf(String::isNotBlank) ?: "预览就绪",
            fileCount = fileCount,
            previewUrl = previewAction.value,
        )
        consumed[summaryIndex] = true
        consumed[actionIndex] = true
    }

    return buildList {
        blocks.forEachIndexed { index, block ->
            projectsBySummaryIndex[index]?.let { add(PresentedMessageContent.Project(it)) }
            if (!consumed[index]) add(PresentedMessageContent.Standard(block))
        }
    }
}
