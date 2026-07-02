package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.data.SupabaseAccountMessageTone
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState

private enum class CleanLoginMode { Login, Register }

private val CleanLoginPanelHeight = 336.dp
private val CleanLoginPanelShape = RoundedCornerShape(30.dp)

@Composable
internal fun AccountLoginDialogHost(
    visible: Boolean,
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    val outsideInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5000f),
    ) {
        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = outsideInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 76.dp)
                .zIndex(1f),
            enter = fadeIn(tween(145)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.90f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialOffsetY = { fullHeight -> fullHeight },
                ),
            exit = fadeOut(tween(100)) +
                slideOutVertically(
                    animationSpec = tween(145),
                    targetOffsetY = { fullHeight -> fullHeight },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CleanLoginPanelHeight)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    ),
                propagateMinConstraints = true,
            ) {
                CleanAccountLoginPanel(
                    state = state,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun CleanAccountLoginPanel(
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val accountState by authRepository.state.collectAsState()

    var mode by rememberSaveable { mutableStateOf(CleanLoginMode.Login) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(accountState.isLoggedIn) {
        if (accountState.isLoggedIn) onDismiss()
    }

    val statusMessage = when {
        accountState.loading && submitted -> accountState.message
        submitted && accountState.tone != SupabaseAccountMessageTone.Normal -> accountState.message
        submitted && accountState.requiresEmailConfirmation -> accountState.message
        else -> ""
    }
    val canSubmit = !accountState.loading && email.isNotBlank() && password.length >= 6

    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier
            .fillMaxWidth()
            .height(CleanLoginPanelHeight),
        role = GlassRole.Card,
        intensity = (state.glassIntensity * 1.08f).coerceIn(0.88f, 1.24f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(CleanLoginPanelShape)
                .background(Color(0xFF06122E).copy(alpha = 0.90f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.10f),
                    shape = CleanLoginPanelShape,
                )
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (mode == CleanLoginMode.Login) {
                    "登录 AI Ledger"
                } else {
                    "创建 AI Ledger 账号"
                },
                color = Color.White.copy(alpha = 0.97f),
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CleanModeButton(
                    text = "登录",
                    selected = mode == CleanLoginMode.Login,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        mode = CleanLoginMode.Login
                        submitted = false
                    },
                )
                CleanModeButton(
                    text = "注册",
                    selected = mode == CleanLoginMode.Register,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        mode = CleanLoginMode.Register
                        submitted = false
                    },
                )
            }

            CleanLoginTextField(
                value = email,
                onValueChange = { email = it.take(80) },
                placeholder = "邮箱 name@example.com",
                keyboardType = KeyboardType.Email,
                enabled = !accountState.loading,
            )

            CleanLoginTextField(
                value = password,
                onValueChange = { password = it.take(72) },
                placeholder = "密码至少 6 位",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !accountState.loading,
            )

            CleanPrimaryButton(
                state = state,
                title = when {
                    accountState.loading -> "处理中…"
                    mode == CleanLoginMode.Register -> "创建账号"
                    else -> "登录"
                },
                enabled = canSubmit,
                onClick = {
                    submitted = true
                    if (mode == CleanLoginMode.Register) {
                        authRepository.signUp(email, password)
                    } else {
                        authRepository.signIn(email, password)
                    }
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        color = cleanAccountMessageColor(accountState.tone),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(
                if (selected) {
                    Color.White.copy(alpha = 0.11f)
                } else {
                    Color.Transparent
                }
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (selected) 0.13f else 0.06f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.52f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun CleanLoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (enabled) 0.075f else 0.045f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.09f else 0.05f),
                shape = shape,
            )
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White.copy(alpha = if (enabled) 0.94f else 0.55f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(Color(0xFF8DF9EA).copy(alpha = 0.90f)),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isBlank()) {
            Text(
                text = placeholder,
                color = Color.White.copy(alpha = if (enabled) 0.43f else 0.28f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CleanPrimaryButton(
    state: AssistantUiState,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 19,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        role = GlassRole.Floating,
        intensity = (state.glassIntensity * if (enabled) 1.10f else 0.92f)
            .coerceIn(0.80f, 1.24f),
        onClick = { if (enabled) onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    if (enabled) {
                        Color(0xFF8DF9EA).copy(alpha = 0.10f)
                    } else {
                        Color.White.copy(alpha = 0.055f)
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) {
                        Color(0xFFB9FFF6).copy(alpha = 0.17f)
                    } else {
                        Color.White.copy(alpha = 0.075f)
                    },
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = if (enabled) 0.97f else 0.62f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

private fun cleanAccountMessageColor(tone: SupabaseAccountMessageTone): Color {
    return when (tone) {
        SupabaseAccountMessageTone.Success -> Color(0xFF8DF9EA).copy(alpha = 0.88f)
        SupabaseAccountMessageTone.Error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
        SupabaseAccountMessageTone.Normal -> Color.White.copy(alpha = 0.50f)
    }
}
