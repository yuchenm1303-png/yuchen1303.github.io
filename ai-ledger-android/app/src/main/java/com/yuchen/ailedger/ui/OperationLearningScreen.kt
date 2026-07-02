package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState

private val OperationLearningAccent = Color(0xFF8DF9EA)
private val OperationLearningViolet = Color(0xFFCAB8FF)

private data class LearningFlowStep(
    val index: String,
    val title: String,
    val description: String,
)

private val learningFlowSteps = listOf(
    LearningFlowStep(
        index = "01",
        title = "说明目标",
        description = "先告诉助手这次演示要完成什么，以及哪些内容每次会变化。",
    ),
    LearningFlowStep(
        index = "02",
        title = "亲自演示",
        description = "录制期间由你操作手机，助手只观察动作和页面变化，不会抢占控制。",
    ),
    LearningFlowStep(
        index = "03",
        title = "确认并保存",
        description = "检查步骤、变量和成功条件，保存后仍由视觉循环按当前界面执行。",
    ),
)

@Composable
fun OperationLearningScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onStartDemonstration: () -> Unit = {},
) {
    var showPreparation by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            OperationLearningBackButton(
                state = state,
                onBack = onBack,
            )
        }

        item {
            OperationLearningHeader()
        }

        item {
            StartDemonstrationCard(
                state = state,
                expanded = showPreparation,
                onClick = {
                    showPreparation = !showPreparation
                    onStartDemonstration()
                },
            )
        }

        item {
            AnimatedVisibility(
                visible = showPreparation,
                enter = fadeIn() + slideInVertically { -it / 8 },
                exit = fadeOut() + slideOutVertically { -it / 8 },
            ) {
                DemonstrationPreparationCard()
            }
        }

        item {
            LearningSectionTitle(
                title = "学习方式",
                trailing = "3 步完成",
            )
        }

        item {
            LearningFlowCard()
        }

        item {
            LearningSectionTitle(
                title = "我的操作",
                trailing = "0 个",
            )
        }

        item {
            LearnedOperationsEmptyCard(
                state = state,
                onStart = {
                    showPreparation = true
                    onStartDemonstration()
                },
            )
        }

        item {
            SafetyBoundaryCard()
        }
    }
}

@Composable
private fun OperationLearningBackButton(
    state: AssistantUiState,
    onBack: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier
            .fillMaxWidth(0.28f)
            .height(40.dp),
        role = GlassRole.Chip,
        onClick = onBack,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹ 功能",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun OperationLearningHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "LEARN BY DEMONSTRATION",
            color = OperationLearningAccent.copy(alpha = 0.74f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = "操作学习",
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "你演示一次，助手理解目标、步骤和成功条件，以后会根据当前界面自行完成。",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StartDemonstrationCard(
    state: AssistantUiState,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 20f,
        backdropAlpha = 1f,
        frostAlpha = 0.092f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF101743).copy(alpha = 0.24f))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "新建学习",
                        color = OperationLearningAccent.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "带助手走一遍",
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "从你开始操作到确认完成，系统会整理为可复用的操作路线，而不是机械记录点击坐标。",
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(OperationLearningViolet.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "学",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                LearningTag("显式录制", Modifier.weight(1f))
                LearningTag("理解页面", Modifier.weight(1f))
                LearningTag("动态执行", Modifier.weight(1f))
            }

            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity * 0.94f,
                motionIntensity = state.motionIntensity,
                radius = 999,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                role = GlassRole.Card,
                onClick = onClick,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "收起演示准备" else "开始演示",
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (expanded) "⌃" else "→",
                        color = OperationLearningAccent.copy(alpha = 0.82f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun LearningTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(31.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.055f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.61f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DemonstrationPreparationCard() {
    FrostInfoGlassPanel(
        radius = 17f,
        backdropAlpha = 1f,
        frostAlpha = 0.078f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141842).copy(alpha = 0.22f))
                .padding(horizontal = 17.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "演示前准备",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            PreparationRow("1", "先打开流程起始页面", "从稳定、可重复进入的位置开始演示。")
            PreparationRow("2", "避开敏感内容", "密码、验证码、支付确认和私密信息不会作为学习内容保存。")
            PreparationRow("3", "完成后明确结束", "停在能证明任务成功的页面，再结束本次录制。")
            Text(
                text = "录制器接入后，这里会进入目标说明和系统授权步骤。",
                color = OperationLearningAccent.copy(alpha = 0.68f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PreparationRow(
    index: String,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OperationLearningAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index,
                color = OperationLearningAccent.copy(alpha = 0.88f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.91f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun LearningSectionTitle(
    title: String,
    trailing: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing,
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LearningFlowCard() {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.078f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF10153A).copy(alpha = 0.22f))
                .padding(horizontal = 17.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            learningFlowSteps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = step.index,
                        color = OperationLearningViolet.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = step.title,
                            color = Color.White.copy(alpha = 0.93f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = step.description,
                            color = Color.White.copy(alpha = 0.49f),
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnedOperationsEmptyCard(
    state: AssistantUiState,
    onStart: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.074f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF12163D).copy(alpha = 0.20f))
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White.copy(alpha = 0.055f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "＋",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            Text(
                text = "还没有已学会的操作",
                color = Color.White.copy(alpha = 0.91f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "完成第一次演示后，操作卡片会显示涉及应用、可变输入、验证方式和最近运行状态。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity * 0.9f,
                motionIntensity = state.motionIntensity,
                radius = 999,
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .padding(top = 5.dp)
                    .height(42.dp),
                role = GlassRole.Card,
                onClick = onStart,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "第一次演示",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyBoundaryCard() {
    FrostInfoGlassPanel(
        radius = 17f,
        backdropAlpha = 1f,
        frostAlpha = 0.066f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101536).copy(alpha = 0.18f))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(35.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OperationLearningAccent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "盾",
                    color = OperationLearningAccent.copy(alpha = 0.76f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "每次都重新理解当前页面",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "保存的是目标、路线线索和成功证据，不是固定坐标宏。遇到敏感操作、页面异常或无法确认时会暂停并交还给你。",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
