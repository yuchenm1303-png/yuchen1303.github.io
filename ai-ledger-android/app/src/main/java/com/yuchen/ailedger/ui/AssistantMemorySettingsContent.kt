package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH
import com.yuchen.ailedger.data.ASSISTANT_MEMORY_MAX_CONTENT_LENGTH
import com.yuchen.ailedger.data.AssistantCustomInstructionsRepository
import com.yuchen.ailedger.data.AssistantCustomInstructionsState
import com.yuchen.ailedger.data.AssistantMemoryItem
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryState
import com.yuchen.ailedger.data.memoryCategoryLabel
import com.yuchen.ailedger.data.memoryPriorityLabel
import com.yuchen.ailedger.model.AssistantUiState

private const val MEMORY_PAGE_SIZE = 20
private val MEMORY_CATEGORIES = listOf(
    "profile" to "个人信息",
    "preference" to "偏好",
    "project" to "项目",
    "rule" to "长期规则",
    "other" to "其他"
)
private val MEMORY_PRIORITIES = listOf(
    0 to "低",
    1 to "普通",
    2 to "重要",
    3 to "核心"
)

@Composable
fun AccountMemorySettingsContent(state: AssistantUiState) {
    val context = LocalContext.current.applicationContext
    val memoryRepository = remember(context) { AssistantMemoryRepository.get(context) }
    val customRepository = remember(context) { AssistantCustomInstructionsRepository.get(context) }
    val memoryState by memoryRepository.state.collectAsState()
    val customState by customRepository.state.collectAsState()
    val accountUserId = memoryState.accountUserId ?: customState.accountUserId

    var customDraft by rememberSaveable(accountUserId) { mutableStateOf("") }
    var clearCustomConfirmation by rememberSaveable(accountUserId) { mutableStateOf(false) }

    var editorVisible by rememberSaveable(accountUserId) { mutableStateOf(false) }
    var editingId by rememberSaveable(accountUserId) { mutableStateOf<String?>(null) }
    var memoryDraft by rememberSaveable(accountUserId) { mutableStateOf("") }
    var categoryDraft by rememberSaveable(accountUserId) { mutableStateOf("preference") }
    var priorityDraft by rememberSaveable(accountUserId) { mutableIntStateOf(1) }
    var pinnedDraft by rememberSaveable(accountUserId) { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable(accountUserId) { mutableStateOf<String?>(null) }
    var clearAllConfirmation by rememberSaveable(accountUserId) { mutableStateOf(false) }
    var visibleMemoryCount by rememberSaveable(accountUserId) { mutableIntStateOf(MEMORY_PAGE_SIZE) }

    LaunchedEffect(customState.accountUserId, customState.content, customState.updatedAt) {
        customDraft = customState.content
        clearCustomConfirmation = false
    }

    LaunchedEffect(memoryState.accountUserId) {
        editorVisible = false
        editingId = null
        memoryDraft = ""
        categoryDraft = "preference"
        priorityDraft = 1
        pinnedDraft = false
        pendingDeleteId = null
        clearAllConfirmation = false
        visibleMemoryCount = MEMORY_PAGE_SIZE
    }

    PersonalizationStatusMetrics(customState, memoryState)

    if (accountUserId == null) {
        MemoryCenteredCard(
            icon = "锁",
            title = "登录后使用个性化与长期记忆",
            description = "自定义指令和长期记忆都会与 Supabase 账号绑定并按用户隔离。请先在“服务”页面完成登录。"
        )
        return
    }

    SectionInlineTitle("自定义指令", "适合放几千字的稳定规则，优先级高于普通记忆。")
    when {
        customState.loading -> MemoryCenteredCard(
            icon = "令",
            title = "正在同步自定义指令",
            description = customState.accountEmail.orEmpty()
        )
        !customState.cloudReady -> PersonalizationUnavailableCard(
            title = "自定义指令云端表尚未就绪",
            message = customState.message,
            state = state,
            buttonTitle = "重新检查",
            buttonSubtitle = "读取 assistant_custom_instructions 表",
            onRefresh = customRepository::refresh
        )
        else -> CustomInstructionsCard(
            state = state,
            customState = customState,
            draft = customDraft,
            clearConfirmation = clearCustomConfirmation,
            onDraftChange = {
                customDraft = it.take(ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH)
                clearCustomConfirmation = false
            },
            onEnabledChange = { enabled ->
                if (customDraft.trim().isNotBlank()) {
                    customRepository.save(customDraft, enabled)
                } else {
                    customRepository.setEnabled(enabled)
                }
            },
            onSave = {
                customRepository.save(customDraft, customState.enabled)
                clearCustomConfirmation = false
            },
            onRefresh = customRepository::refresh,
            onClear = {
                if (clearCustomConfirmation) {
                    customRepository.clear()
                    clearCustomConfirmation = false
                } else {
                    clearCustomConfirmation = true
                }
            }
        )
    }

    SectionInlineTitle("长期记忆", "适合姓名、偏好、项目背景和长期规则，可逐条控制。")
    when {
        memoryState.loading -> MemoryCenteredCard(
            icon = "忆",
            title = "正在同步长期记忆",
            description = memoryState.accountEmail.orEmpty()
        )
        !memoryState.cloudReady -> PersonalizationUnavailableCard(
            title = "长期记忆云端表尚未就绪",
            message = memoryState.message,
            state = state,
            buttonTitle = "重新检查",
            buttonSubtitle = "读取 assistant_memories 表",
            onRefresh = memoryRepository::refresh
        )
        else -> {
            MemoryMasterCard(
                memoryState = memoryState,
                onEnabledChange = memoryRepository::setMemoryEnabled
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                MemoryGlassAction(
                    title = "添加记忆",
                    subtitle = "单条最多 $ASSISTANT_MEMORY_MAX_CONTENT_LENGTH 字",
                    state = state,
                    enabled = !memoryState.saving,
                    modifier = Modifier.weight(1f)
                ) {
                    editingId = null
                    memoryDraft = ""
                    categoryDraft = "preference"
                    priorityDraft = 1
                    pinnedDraft = false
                    editorVisible = true
                    pendingDeleteId = null
                    clearAllConfirmation = false
                }
                MemoryGlassAction(
                    title = if (memoryState.loading) "同步中" else "刷新",
                    subtitle = "重新读取云端记忆",
                    state = state,
                    enabled = !memoryState.saving && !memoryState.loading,
                    modifier = Modifier.weight(1f),
                    onClick = memoryRepository::refresh
                )
            }

            if (editorVisible) {
                MemoryEditorCard(
                    value = memoryDraft,
                    category = categoryDraft,
                    priority = priorityDraft,
                    pinned = pinnedDraft,
                    editing = editingId != null,
                    saving = memoryState.saving,
                    onValueChange = { memoryDraft = it.take(ASSISTANT_MEMORY_MAX_CONTENT_LENGTH) },
                    onCategoryChange = { categoryDraft = it },
                    onPriorityChange = { priorityDraft = it },
                    onPinnedChange = { pinnedDraft = it },
                    onCancel = {
                        editorVisible = false
                        editingId = null
                        memoryDraft = ""
                    },
                    onSave = {
                        val id = editingId
                        if (id == null) {
                            memoryRepository.addMemory(
                                content = memoryDraft,
                                category = categoryDraft,
                                priority = priorityDraft,
                                pinned = pinnedDraft
                            )
                        } else {
                            memoryRepository.updateMemory(
                                id = id,
                                content = memoryDraft,
                                category = categoryDraft,
                                priority = priorityDraft,
                                pinned = pinnedDraft
                            )
                        }
                        if (memoryDraft.trim().isNotBlank()) {
                            editorVisible = false
                            editingId = null
                            memoryDraft = ""
                        }
                    }
                )
            }

            Text(
                "已保存的记忆",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )

            if (memoryState.memories.isEmpty()) {
                MemoryCenteredCard(
                    icon = "忆",
                    title = "还没有长期记忆",
                    description = "可以保存称呼、偏好、项目背景或长期规则。高优先级和置顶内容会优先进入模型快照。"
                )
            } else {
                memoryState.memories.take(visibleMemoryCount).forEach { item ->
                    MemoryItemCard(
                        item = item,
                        saving = memoryState.saving,
                        confirmDelete = pendingDeleteId == item.id,
                        onToggle = { memoryRepository.setItemEnabled(item.id, it) },
                        onEdit = {
                            editingId = item.id
                            memoryDraft = item.content
                            categoryDraft = item.category
                            priorityDraft = item.priority
                            pinnedDraft = item.pinned
                            editorVisible = true
                            pendingDeleteId = null
                            clearAllConfirmation = false
                        },
                        onDelete = {
                            if (pendingDeleteId == item.id) {
                                memoryRepository.deleteMemory(item.id)
                                pendingDeleteId = null
                            } else {
                                pendingDeleteId = item.id
                                editorVisible = false
                                clearAllConfirmation = false
                            }
                        },
                        onCancelDelete = { pendingDeleteId = null }
                    )
                }

                if (visibleMemoryCount < memoryState.memories.size) {
                    MemoryGlassAction(
                        title = "加载更多",
                        subtitle = "还剩 ${memoryState.memories.size - visibleMemoryCount} 条",
                        state = state,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        visibleMemoryCount = (visibleMemoryCount + MEMORY_PAGE_SIZE)
                            .coerceAtMost(memoryState.memories.size)
                    }
                }

                MemoryGlassAction(
                    title = if (clearAllConfirmation) "确认清除全部" else "清除全部记忆",
                    subtitle = if (clearAllConfirmation) "再次点击将永久删除" else "只清除当前登录账号",
                    state = state,
                    enabled = !memoryState.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (clearAllConfirmation) {
                        memoryRepository.clearAll()
                        clearAllConfirmation = false
                    } else {
                        clearAllConfirmation = true
                        pendingDeleteId = null
                        editorVisible = false
                    }
                }
            }

            PersonalizationMessage(memoryState.message, memoryState.error)
        }
    }
}

@Composable
private fun PersonalizationStatusMetrics(
    customState: AssistantCustomInstructionsState,
    memoryState: AssistantMemoryState
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MemoryMetric(
            label = "自定义指令",
            value = when {
                customState.accountUserId == null -> "需要登录"
                !customState.cloudReady -> "未配置"
                customState.enabled -> "已启用"
                else -> "已关闭"
            },
            modifier = Modifier.weight(1f)
        )
        MemoryMetric(
            label = "已保存",
            value = "${memoryState.memories.size} 条",
            modifier = Modifier.weight(1f)
        )
        MemoryMetric(
            label = "当前生效",
            value = "${memoryState.activeCount} 条",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CustomInstructionsCard(
    state: AssistantUiState,
    customState: AssistantCustomInstructionsState,
    draft: String,
    clearConfirmation: Boolean,
    onDraftChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val dirty = draft != customState.content
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "大段自定义指令",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    customState.accountEmail.orEmpty(),
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = customState.enabled,
                onCheckedChange = onEnabledChange,
                enabled = !customState.saving && draft.trim().isNotBlank()
            )
        }

        Text(
            "这里适合放回答风格、固定工作流程、项目保护规则和长期协作要求。它的优先级高于普通记忆，但不能覆盖系统安全与工具协议。",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.13f))
                .padding(12.dp)
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = !customState.saving,
                textStyle = TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF8DF9EA)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxSize()
            )
            if (draft.isBlank()) {
                Text(
                    "例如：\n回答使用中文，先给结论，再解释关键原因。\n处理 Android 项目时必须保护指定架构，不使用临时补丁。\n遇到不确定信息要明确说明，不要编造。",
                    color = Color.White.copy(alpha = 0.30f),
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${draft.length}/$ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH",
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    customState.saving -> "保存中"
                    dirty -> "有未保存修改"
                    customState.enabled -> "已启用"
                    else -> "已保存"
                },
                color = if (dirty) Color(0xFFFFD27A).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.38f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            MemoryGlassAction(
                title = if (customState.saving) "保存中" else "保存指令",
                subtitle = if (customState.enabled) "保存后继续启用" else "保存但保持关闭",
                state = state,
                enabled = !customState.saving && draft.trim().isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = onSave
            )
            MemoryGlassAction(
                title = "刷新",
                subtitle = "重新读取云端版本",
                state = state,
                enabled = !customState.saving,
                modifier = Modifier.weight(1f),
                onClick = onRefresh
            )
        }

        MemoryGlassAction(
            title = if (clearConfirmation) "确认清除指令" else "清除自定义指令",
            subtitle = if (clearConfirmation) "再次点击将永久删除" else "只清除当前登录账号",
            state = state,
            enabled = !customState.saving && customState.content.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = onClear
        )

        PersonalizationMessage(customState.message, customState.error)
    }
}

@Composable
private fun MemoryMasterCard(
    memoryState: AssistantMemoryState,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "长期记忆总开关",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    memoryState.accountEmail.orEmpty(),
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = memoryState.memoryEnabled,
                onCheckedChange = onEnabledChange,
                enabled = !memoryState.saving
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Text(
            if (memoryState.memoryEnabled) {
                "当前 ${memoryState.activeCount} 条记忆会按置顶、优先级和更新时间排序，裁剪后发送给 Qwen、DeepSeek 与识图模型。"
            } else {
                "记忆仍保存在账号中，但关闭期间当前生效为 0 条，不会发送给聊天模型。"
            },
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MemoryEditorCard(
    value: String,
    category: String,
    priority: Int,
    pinned: Boolean,
    editing: Boolean,
    saving: Boolean,
    onValueChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (editing) "编辑记忆" else "添加记忆",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )

        Text("分类", color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        SelectionChipRows(
            items = MEMORY_CATEGORIES,
            selected = category,
            enabled = !saving,
            label = { it.second },
            key = { it.first },
            onSelected = { onCategoryChange(it.first) }
        )

        Text("优先级", color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        SelectionChipRows(
            items = MEMORY_PRIORITIES,
            selected = priority,
            enabled = !saving,
            label = { it.second },
            key = { it.first },
            onSelected = { onPriorityChange(it.first) }
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("置顶记忆", color = Color.White.copy(alpha = 0.78f), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                Text("置顶内容会优先进入有限的记忆快照", color = Color.White.copy(alpha = 0.36f), fontSize = 9.5.sp)
            }
            Switch(checked = pinned, onCheckedChange = onPinnedChange, enabled = !saving)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black.copy(alpha = 0.12f))
                .padding(12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !saving,
                textStyle = TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF8DF9EA)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxSize()
            )
            if (value.isBlank()) {
                Text(
                    "一条记忆尽量只表达一件稳定的事情，例如：用户名字是邹羽宸。",
                    color = Color.White.copy(alpha = 0.30f),
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${value.length}/$ASSISTANT_MEMORY_MAX_CONTENT_LENGTH",
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            MemoryTextAction("取消", enabled = !saving, onClick = onCancel)
            Spacer(Modifier.size(14.dp))
            MemoryTextAction(
                text = if (saving) "保存中" else "保存",
                enabled = !saving && value.trim().isNotBlank(),
                emphasized = true,
                onClick = onSave
            )
        }
    }
}

@Composable
private fun <T, K> SelectionChipRows(
    items: List<T>,
    selected: K,
    enabled: Boolean,
    label: (T) -> String,
    key: (T) -> K,
    onSelected: (T) -> Unit
) {
    items.chunked(3).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            rowItems.forEach { item ->
                val active = key(item) == selected
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = if (active) 0.13f else 0.055f))
                        .clickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = null
                        ) { onSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label(item),
                        color = Color.White.copy(alpha = if (active) 0.94f else 0.52f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MemoryItemCard(
    item: AssistantMemoryItem,
    saving: Boolean,
    confirmDelete: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Color.White.copy(alpha = if (item.enabled) 0.060f else 0.038f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    buildString {
                        if (item.pinned) append("置顶 · ")
                        append(memoryCategoryLabel(item.category))
                        append(" · ")
                        append(memoryPriorityLabel(item.priority))
                    },
                    color = Color(0xFF8DF9EA).copy(alpha = if (item.enabled) 0.70f else 0.34f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    item.content,
                    color = Color.White.copy(alpha = if (item.enabled) 0.82f else 0.42f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                enabled = !saving
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (confirmDelete) {
                    "确认删除这条记忆？"
                } else if (item.enabled) {
                    "会按优先级加入模型记忆快照"
                } else {
                    "已停用，不发送给模型"
                },
                color = if (confirmDelete) {
                    Color(0xFFFFB4B4).copy(alpha = 0.82f)
                } else {
                    Color.White.copy(alpha = 0.34f)
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (confirmDelete) {
                MemoryTextAction("取消", enabled = !saving, onClick = onCancelDelete)
                Spacer(Modifier.size(12.dp))
                MemoryTextAction("确认删除", enabled = !saving, destructive = true, onClick = onDelete)
            } else {
                MemoryTextAction("编辑", enabled = !saving, onClick = onEdit)
                Spacer(Modifier.size(12.dp))
                MemoryTextAction("删除", enabled = !saving, destructive = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun PersonalizationUnavailableCard(
    title: String,
    message: String,
    state: AssistantUiState,
    buttonTitle: String,
    buttonSubtitle: String,
    onRefresh: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(
            message,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "普通聊天不会因此崩溃；完成 Supabase 个性化升级 SQL 后，点击重新检查即可接通。",
            color = Color.White.copy(alpha = 0.36f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )
        MemoryGlassAction(
            title = buttonTitle,
            subtitle = buttonSubtitle,
            state = state,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefresh
        )
    }
}

@Composable
private fun SectionInlineTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.84f), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MemoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.50f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            value,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoryCenteredCard(icon: String, title: String, description: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.048f))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White.copy(alpha = 0.065f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = Color.White.copy(alpha = 0.66f), fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(
            description,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MemoryGlassAction(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (enabled) 1f else 0.68f,
        motionIntensity = 0f,
        radius = 22,
        modifier = modifier.height(58.dp),
        role = GlassRole.Chip,
        onClick = { if (enabled) onClick() }
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = if (enabled) 0.92f else 0.42f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = if (enabled) 0.46f else 0.26f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MemoryTextAction(
    text: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.28f)
            destructive -> Color(0xFFFFB4B4).copy(alpha = 0.88f)
            emphasized -> Color(0xFF8DF9EA).copy(alpha = 0.92f)
            else -> Color.White.copy(alpha = 0.62f)
        },
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    )
}

@Composable
private fun PersonalizationMessage(message: String, error: Boolean) {
    Text(
        message,
        color = if (error) Color(0xFFFFB4B4).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.44f),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Bold
    )
}
