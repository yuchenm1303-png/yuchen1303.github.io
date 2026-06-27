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
import com.yuchen.ailedger.data.AssistantMemoryItem
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryState
import com.yuchen.ailedger.model.AssistantUiState

private const val MEMORY_EDITOR_MAX_LENGTH = 500

@Composable
fun AccountMemorySettingsContent(state: AssistantUiState) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { AssistantMemoryRepository.get(context) }
    val memoryState by repository.state.collectAsState()

    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var clearConfirmation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(memoryState.accountUserId) {
        editorVisible = false
        editingId = null
        draft = ""
        pendingDeleteId = null
        clearConfirmation = false
    }

    MemoryStatusMetrics(memoryState)

    when {
        memoryState.accountUserId == null -> MemoryLockedCard()
        memoryState.loading -> MemoryLoadingCard(memoryState.accountEmail)
        !memoryState.cloudReady -> MemoryUnavailableCard(
            message = memoryState.message,
            state = state,
            refreshing = memoryState.loading,
            onRefresh = repository::refresh
        )
        else -> {
            MemoryMasterCard(
                memoryState = memoryState,
                onEnabledChange = repository::setMemoryEnabled
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                MemoryGlassAction(
                    title = "添加记忆",
                    subtitle = "手动保存长期信息",
                    state = state,
                    enabled = !memoryState.saving,
                    modifier = Modifier.weight(1f)
                ) {
                    editingId = null
                    draft = ""
                    editorVisible = true
                    pendingDeleteId = null
                    clearConfirmation = false
                }
                MemoryGlassAction(
                    title = if (memoryState.loading) "同步中" else "刷新",
                    subtitle = "重新读取云端记忆",
                    state = state,
                    enabled = !memoryState.saving && !memoryState.loading,
                    modifier = Modifier.weight(1f),
                    onClick = repository::refresh
                )
            }

            if (editorVisible) {
                MemoryEditorCard(
                    value = draft,
                    editing = editingId != null,
                    saving = memoryState.saving,
                    onValueChange = { draft = it.take(MEMORY_EDITOR_MAX_LENGTH) },
                    onCancel = {
                        editorVisible = false
                        editingId = null
                        draft = ""
                    },
                    onSave = {
                        val id = editingId
                        if (id == null) repository.addMemory(draft) else repository.updateMemory(id, draft)
                        if (draft.trim().isNotBlank()) {
                            editorVisible = false
                            editingId = null
                            draft = ""
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
                MemoryEmptyCard()
            } else {
                memoryState.memories.forEach { item ->
                    MemoryItemCard(
                        item = item,
                        saving = memoryState.saving,
                        confirmDelete = pendingDeleteId == item.id,
                        onToggle = { repository.setItemEnabled(item.id, it) },
                        onEdit = {
                            editingId = item.id
                            draft = item.content
                            editorVisible = true
                            pendingDeleteId = null
                            clearConfirmation = false
                        },
                        onDelete = {
                            if (pendingDeleteId == item.id) {
                                repository.deleteMemory(item.id)
                                pendingDeleteId = null
                            } else {
                                pendingDeleteId = item.id
                                editorVisible = false
                                clearConfirmation = false
                            }
                        },
                        onCancelDelete = { pendingDeleteId = null }
                    )
                }

                MemoryGlassAction(
                    title = if (clearConfirmation) "确认清除全部" else "清除全部记忆",
                    subtitle = if (clearConfirmation) "再次点击将永久删除" else "只清除当前登录账号",
                    state = state,
                    enabled = !memoryState.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (clearConfirmation) {
                        repository.clearAll()
                        clearConfirmation = false
                    } else {
                        clearConfirmation = true
                        pendingDeleteId = null
                        editorVisible = false
                    }
                }
            }

            MemoryMessage(memoryState)
        }
    }
}

@Composable
private fun MemoryStatusMetrics(memoryState: AssistantMemoryState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MemoryMetric(
            label = "长期记忆",
            value = when {
                memoryState.accountUserId == null -> "需要登录"
                !memoryState.cloudReady -> "未配置"
                memoryState.memoryEnabled -> "已开启"
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
private fun MemoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
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
private fun MemoryLockedCard() {
    MemoryCenteredCard(
        icon = "锁",
        title = "登录后使用长期记忆",
        description = "长期记忆会与 Supabase 账号绑定并按用户隔离。请先在“服务”页面完成登录或注册。"
    )
}

@Composable
private fun MemoryLoadingCard(email: String?) {
    MemoryCenteredCard(
        icon = "忆",
        title = "正在同步长期记忆",
        description = email?.takeIf { it.isNotBlank() } ?: "正在确认当前账号和云端数据。"
    )
}

@Composable
private fun MemoryUnavailableCard(
    message: String,
    state: AssistantUiState,
    refreshing: Boolean,
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
        Text(
            "云端记忆尚未就绪",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            message,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "普通聊天和同一会话内的上下文不会受影响。完成 Supabase 建表与 RLS 后，点击刷新即可接通。",
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )
        MemoryGlassAction(
            title = if (refreshing) "正在检查" else "重新检查",
            subtitle = "读取 assistant_memories 表",
            state = state,
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefresh
        )
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
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "长期记忆",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    memoryState.accountEmail.orEmpty(),
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
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
                "聊天请求会携带已启用的记忆，Qwen、DeepSeek 和识图模型共用同一份快照。"
            } else {
                "记忆仍保存在账号中，但关闭期间不会发送给聊天模型。"
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
    editing: Boolean,
    saving: Boolean,
    onValueChange: (String) -> Unit,
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
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
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF8DF9EA)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxSize()
            )
            if (value.isBlank()) {
                Text(
                    "例如：用户偏好简洁回答，不喜欢太多分点。",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${value.length}/$MEMORY_EDITOR_MAX_LENGTH",
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
                    if (item.category == "manual") "手动记忆" else item.category,
                    color = Color(0xFF8DF9EA).copy(alpha = if (item.enabled) 0.68f else 0.34f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    item.content,
                    color = Color.White.copy(alpha = if (item.enabled) 0.82f else 0.42f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                enabled = !saving
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (confirmDelete) {
                Text(
                    "确认删除这条记忆？",
                    color = Color(0xFFFFB4B4).copy(alpha = 0.82f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    if (item.enabled) "会加入模型记忆快照" else "已停用，不发送给模型",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
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
private fun MemoryEmptyCard() {
    MemoryCenteredCard(
        icon = "忆",
        title = "还没有长期记忆",
        description = "可以手动添加称呼、偏好、项目背景或长期习惯。保存后会同步到当前账号。"
    )
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
            Text(
                icon,
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            title,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
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
private fun MemoryMessage(memoryState: AssistantMemoryState) {
    Text(
        memoryState.message,
        color = if (memoryState.error) {
            Color(0xFFFFB4B4).copy(alpha = 0.88f)
        } else {
            Color.White.copy(alpha = 0.44f)
        },
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Bold
    )
}
