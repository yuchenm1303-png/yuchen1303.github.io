package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Brush
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
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryItem
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.memoryCategoryLabel
import com.yuchen.ailedger.data.memoryPriorityLabel
import com.yuchen.ailedger.data.memoryScopeLabel
import com.yuchen.ailedger.model.AssistantUiState

private const val MEMORY_PAGE_SIZE = 20

private val MEMORY_CATEGORIES = listOf(
    "profile" to "个人信息",
    "preference" to "偏好",
    "project" to "项目",
    "rule" to "长期规则",
    "skill" to "场景技能",
    "episode" to "经历",
    "reflection" to "归纳",
    "other" to "其他",
)

private val MEMORY_SCOPES = listOf(
    "auto" to "自动识别",
    "global" to "全局",
    "english" to "英语",
    "android" to "Android",
    "coding" to "编程",
    "math" to "数学",
    "writing" to "写作",
    "finance" to "金融",
    "travel" to "旅行",
)

private val MEMORY_PRIORITIES = listOf(
    0 to "低",
    1 to "普通",
    2 to "重要",
    3 to "核心",
)

@Composable
fun AccountMemorySettingsContent(state: AssistantUiState) {
    val context = LocalContext.current.applicationContext
    val memoryRepository = remember(context) { AssistantMemoryRepository.get(context) }
    val customRepository = remember(context) { AssistantCustomInstructionsRepository.get(context) }
    val memoryState by memoryRepository.state.collectAsState()
    val customState by customRepository.state.collectAsState()
    val diagnosticsState by AssistantMemoryDiagnostics.state.collectAsState()
    val accountUserId = memoryState.accountUserId ?: customState.accountUserId

    var customDraft by rememberSaveable(accountUserId) { mutableStateOf("") }
    var clearCustomConfirmation by rememberSaveable(accountUserId) { mutableStateOf(false) }
    var editorVisible by rememberSaveable(accountUserId) { mutableStateOf(false) }
    var editingId by rememberSaveable(accountUserId) { mutableStateOf<String?>(null) }
    var memoryDraft by rememberSaveable(accountUserId) { mutableStateOf("") }
    var categoryDraft by rememberSaveable(accountUserId) { mutableStateOf("preference") }
    var scopeDraft by rememberSaveable(accountUserId) { mutableStateOf("auto") }
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
        scopeDraft = "auto"
        priorityDraft = 1
        pinnedDraft = false
        pendingDeleteId = null
        clearAllConfirmation = false
        visibleMemoryCount = MEMORY_PAGE_SIZE
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MemoryMetric(
            label = "明确指令",
            value = when {
                customState.accountUserId == null -> "需要登录"
                !customState.cloudReady -> "未配置"
                customState.enabled -> "已启用"
                else -> "已关闭"
            },
            modifier = Modifier.weight(1f),
        )
        MemoryMetric(
            label = "可检索",
            value = "${memoryState.activeCount} 条",
            modifier = Modifier.weight(1f),
        )
        MemoryMetric(
            label = "上一轮注入",
            value = diagnosticsState.latest?.itemCount?.let { "$it 条" } ?: "暂无",
            modifier = Modifier.weight(1f),
        )
    }

    SectionInlineTitle(
        title = "逐轮记忆诊断",
        subtitle = "每一句普通聊天都会记录请求、召回、过滤、重排和最终注入结果，可一键复制完整报告。",
    )
    AssistantMemoryDiagnosticsPanel(
        state = state,
        diagnosticsState = diagnosticsState,
    )

    if (accountUserId == null) {
        MemoryCenteredCard(
            icon = "锁",
            title = "登录后使用个性化与长期记忆",
            description = "诊断仍会记录匿名或请求失败状态；登录后才会读取与管理账号记忆。",
        )
        return
    }

    SectionInlineTitle(
        title = "自定义指令",
        subtitle = "账号级明确要求。系统会按当前问题筛选相关段落，并以高优先级加入模型提示词。",
    )
    when {
        customState.loading -> MemoryCenteredCard(
            icon = "令",
            title = "正在同步自定义指令",
            description = customState.accountEmail.orEmpty(),
        )
        !customState.cloudReady -> MemoryUnavailableCard(
            title = "自定义指令云端表尚未就绪",
            message = customState.message,
            buttonSubtitle = "读取 assistant_custom_instructions 表",
            state = state,
            onRefresh = customRepository::refresh,
        )
        else -> MemorySurface {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "账号级自定义指令",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        customState.accountEmail.orEmpty(),
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = customState.enabled,
                    onCheckedChange = { enabled ->
                        if (customDraft.trim().isNotBlank()) customRepository.save(customDraft, enabled)
                        else customRepository.setEnabled(enabled)
                    },
                    enabled = !customState.saving && customDraft.trim().isNotBlank(),
                )
            }
            MemoryTextEditor(
                value = customDraft,
                onValueChange = {
                    customDraft = it.take(ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH)
                    clearCustomConfirmation = false
                },
                enabled = !customState.saving,
                height = 210,
                placeholder = "例如：数学题讲解要精简通俗；处理项目代码时不得使用临时补丁。",
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${customDraft.length}/$ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                MemoryTextAction(
                    text = if (customState.saving) "保存中" else "保存",
                    enabled = !customState.saving && customDraft.trim().isNotBlank(),
                    emphasized = true,
                ) { customRepository.save(customDraft, customState.enabled) }
                Spacer(Modifier.size(14.dp))
                MemoryTextAction("刷新", enabled = !customState.saving) { customRepository.refresh() }
            }
            MemoryGlassAction(
                title = if (clearCustomConfirmation) "确认清除指令" else "清除自定义指令",
                subtitle = if (clearCustomConfirmation) "再次点击将永久删除" else "只清除当前账号",
                state = state,
                enabled = !customState.saving && customState.content.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (clearCustomConfirmation) {
                    customRepository.clear()
                    clearCustomConfirmation = false
                } else {
                    clearCustomConfirmation = true
                }
            }
            PersonalizationMessage(customState.message, customState.error)
        }
    }

    SectionInlineTitle(
        title = "长期记忆",
        subtitle = "事实、偏好、项目、经历和技能分层保存；每轮只由后端选择真正相关的少量内容。",
    )
    when {
        memoryState.loading -> MemoryCenteredCard(
            icon = "忆",
            title = "正在同步长期记忆",
            description = memoryState.accountEmail.orEmpty(),
        )
        !memoryState.cloudReady -> MemoryUnavailableCard(
            title = "长期记忆云端表尚未就绪",
            message = memoryState.message,
            buttonSubtitle = "读取 assistant_memory_items_v4 表",
            state = state,
            onRefresh = memoryRepository::refresh,
        )
        else -> {
            MemorySurface {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "长期记忆总开关",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            memoryState.accountEmail.orEmpty(),
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = memoryState.memoryEnabled,
                        onCheckedChange = memoryRepository::setMemoryEnabled,
                        enabled = !memoryState.saving,
                    )
                }
                Text(
                    if (memoryState.memoryEnabled) {
                        "当前有 ${memoryState.activeCount} 条可检索记忆。诊断面板会显示其中哪些进入候选池和最终提示词。"
                    } else {
                        "记忆仍保存在账号中，但关闭期间不会参与聊天请求。"
                    },
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                MemoryGlassAction(
                    title = "添加记忆",
                    subtitle = "类型、作用域和优先级",
                    state = state,
                    enabled = !memoryState.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    editingId = null
                    memoryDraft = ""
                    categoryDraft = "preference"
                    scopeDraft = "auto"
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
                    onClick = memoryRepository::refresh,
                )
            }

            if (editorVisible) {
                MemorySurface {
                    Text(
                        if (editingId == null) "添加记忆" else "编辑记忆",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                    EditorLabel("类型")
                    SelectionChipRows(
                        items = MEMORY_CATEGORIES,
                        selected = categoryDraft,
                        enabled = !memoryState.saving,
                        label = { it.second },
                        key = { it.first },
                    ) { categoryDraft = it.first }
                    EditorLabel("作用域")
                    SelectionChipRows(
                        items = MEMORY_SCOPES,
                        selected = scopeDraft,
                        enabled = !memoryState.saving,
                        label = { it.second },
                        key = { it.first },
                    ) { scopeDraft = it.first }
                    EditorLabel("优先级")
                    SelectionChipRows(
                        items = MEMORY_PRIORITIES,
                        selected = priorityDraft,
                        enabled = !memoryState.saving,
                        label = { it.second },
                        key = { it.first },
                    ) { priorityDraft = it.first }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "置顶记忆",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "提高候选优先级，但仍受权限、冲突和相关性规则约束",
                                color = Color.White.copy(alpha = 0.36f),
                                fontSize = 9.5.sp,
                            )
                        }
                        Switch(
                            checked = pinnedDraft,
                            onCheckedChange = { pinnedDraft = it },
                            enabled = !memoryState.saving,
                        )
                    }
                    MemoryTextEditor(
                        value = memoryDraft,
                        onValueChange = { memoryDraft = it.take(ASSISTANT_MEMORY_MAX_CONTENT_LENGTH) },
                        enabled = !memoryState.saving,
                        height = 150,
                        placeholder = "一条记忆尽量只表达一件稳定的事情。",
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${memoryDraft.length}/$ASSISTANT_MEMORY_MAX_CONTENT_LENGTH",
                            color = Color.White.copy(alpha = 0.34f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        MemoryTextAction("取消", enabled = !memoryState.saving) {
                            editorVisible = false
                            editingId = null
                            memoryDraft = ""
                        }
                        Spacer(Modifier.size(14.dp))
                        MemoryTextAction(
                            text = if (memoryState.saving) "保存中" else "保存",
                            enabled = !memoryState.saving && memoryDraft.trim().isNotBlank(),
                            emphasized = true,
                        ) {
                            val id = editingId
                            if (id == null) {
                                memoryRepository.addMemory(
                                    content = memoryDraft,
                                    category = categoryDraft,
                                    scope = scopeDraft,
                                    priority = priorityDraft,
                                    pinned = pinnedDraft,
                                )
                            } else {
                                memoryRepository.updateMemory(
                                    id = id,
                                    content = memoryDraft,
                                    category = categoryDraft,
                                    scope = scopeDraft,
                                    priority = priorityDraft,
                                    pinned = pinnedDraft,
                                )
                            }
                            editorVisible = false
                            editingId = null
                            memoryDraft = ""
                        }
                    }
                }
            }

            Text(
                "已保存的记忆",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            if (memoryState.memories.isEmpty()) {
                MemoryCenteredCard(
                    icon = "忆",
                    title = "还没有长期记忆",
                    description = "事实放进个人信息，回答方式放进偏好，必须执行的流程放进长期规则。",
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
                            scopeDraft = item.scope
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
                        onCancelDelete = { pendingDeleteId = null },
                    )
                }
                if (visibleMemoryCount < memoryState.memories.size) {
                    MemoryGlassAction(
                        title = "加载更多",
                        subtitle = "还剩 ${memoryState.memories.size - visibleMemoryCount} 条",
                        state = state,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
private fun MemoryItemCard(
    item: AssistantMemoryItem,
    saving: Boolean,
    confirmDelete: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Color.White.copy(alpha = if (item.enabled) 0.060f else 0.038f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    buildString {
                        if (item.pinned) append("置顶 · ")
                        append(memoryCategoryLabel(item.category))
                        append(" · ")
                        append(memoryScopeLabel(item.scope))
                        append(" · ")
                        append(memoryPriorityLabel(item.priority))
                    },
                    color = categoryAccent(item.category).copy(alpha = if (item.enabled) 0.76f else 0.34f),
                    fontSize = 10.2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.content,
                    color = Color.White.copy(alpha = if (item.enabled) 0.82f else 0.42f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                enabled = !saving,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    confirmDelete -> "确认删除这条记忆？"
                    !item.enabled -> "已停用，不参与检索"
                    item.useCount > 0L -> "已命中 ${item.useCount} 次"
                    else -> "会根据当前问题动态计算相关度"
                },
                color = if (confirmDelete) Color(0xFFFFB4B4).copy(alpha = 0.82f)
                else Color.White.copy(alpha = 0.34f),
                fontSize = 10.2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
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
private fun MemoryUnavailableCard(
    title: String,
    message: String,
    buttonSubtitle: String,
    state: AssistantUiState,
    onRefresh: () -> Unit,
) {
    MemorySurface {
        Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(
            message,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "连接失败不会清空已有记忆；诊断会记录聊天请求是否仍由后端访问云端记忆。",
            color = Color.White.copy(alpha = 0.36f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        MemoryGlassAction(
            title = "重新检查",
            subtitle = buttonSubtitle,
            state = state,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefresh,
        )
    }
}

@Composable
private fun <T, K> SelectionChipRows(
    items: List<T>,
    selected: K,
    enabled: Boolean,
    label: (T) -> String,
    key: (T) -> K,
    onSelected: (T) -> Unit,
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
                            indication = null,
                        ) { onSelected(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(item),
                        color = Color.White.copy(alpha = if (active) 0.94f else 0.52f),
                        fontSize = 10.2.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MemoryTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    height: Int,
    placeholder: String,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.13f))
            .padding(12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = TextStyle(
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(Color(0xFF8DF9EA)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            modifier = Modifier.fillMaxSize(),
        )
        if (value.isBlank()) {
            Text(
                placeholder,
                color = Color.White.copy(alpha = 0.30f),
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MemorySurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.070f),
                        Color.White.copy(alpha = 0.045f),
                    )
                )
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
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
            fontWeight = FontWeight.Bold,
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
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            value,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White.copy(alpha = 0.065f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, color = Color.White.copy(alpha = 0.66f), fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Text(
            title,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            description,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (enabled) 1f else 0.68f,
        motionIntensity = 0f,
        radius = 22,
        modifier = modifier.height(58.dp),
        role = GlassRole.Chip,
        onClick = { if (enabled) onClick() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = if (enabled) 0.92f else 0.42f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = if (enabled) 0.46f else 0.26f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    onClick: () -> Unit,
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
            onClick = onClick,
        ),
    )
}

@Composable
private fun EditorLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.48f),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun PersonalizationMessage(message: String, error: Boolean) {
    Text(
        message,
        color = if (error) Color(0xFFFFB4B4).copy(alpha = 0.88f)
        else Color.White.copy(alpha = 0.44f),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Bold,
    )
}

private fun categoryAccent(category: String): Color = when (category) {
    "skill", "rule" -> Color(0xFFC09BFF)
    "profile" -> Color(0xFFFFD07A)
    "preference" -> Color(0xFF83F2E5)
    "project" -> Color(0xFF83C8FF)
    "episode" -> Color(0xFFFFA9CA)
    "reflection" -> Color(0xFFA7B4FF)
    else -> Color(0xFFB8C6E8)
}
