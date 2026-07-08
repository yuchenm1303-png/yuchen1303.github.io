package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.yuchen.ailedger.data.SupabaseAccountMessageTone
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState

private enum class AnchoredLoginMode { Login, Register }

private val LoginQuickPanelWidth = 326.dp
private val LoginQuickPanelHeight = 328.dp
private val LoginQuickPanelMinHeight = 286.dp

@Composable
internal fun AccountLoginDialogHost(
    visible: Boolean,
    anchorBounds: Rect,
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    AnchoredQuickPanel(
        visible = visible,
        anchorBounds = anchorBounds,
        desiredWidth = LoginQuickPanelWidth,
        desiredHeight = LoginQuickPanelHeight,
        minHeight = LoginQuickPanelMinHeight,
        preferredPlacement = AnchoredQuickPanelPlacement.Below,
        horizontalBias = 0.82f,
        quality = state.quality,
        glassIntensity = (state.glassIntensity * 1.04f).coerceIn(0.86f, 1.22f),
        motionIntensity = state.motionIntensity,
        onDismiss = onDismiss,
        cornerRadius = 25.dp,
        tailHeight = 12.dp,
        tailHalfWidth = 15.dp,
        surfaceColor = Color(0xFF06122E).copy(alpha = 0.98f),
    ) { layout ->
        AnchoredLoginContent(
            compact = layout.compact,
            placement = layout.placement,
            tailHeight = layout.tailHeight,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun AnchoredLoginContent(
    compact: Boolean,
    placement: AnchoredQuickPanelPlacement,
    tailHeight: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val accountState by authRepository.state.collectAsState()

    var mode by rememberSaveable { mutableStateOf(AnchoredLoginMode.Login) }
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
    val topTailInset = if (placement == AnchoredQuickPanelPlacement.Below) tailHeight else 0.dp
    val bottomTailInset = if (placement == AnchoredQuickPanelPlacement.Above) tailHeight else 0.dp
    val spacing = if (compact) 7.dp else 9.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (compact) 13.dp else 15.dp,
                top = topTailInset + if (compact) 10.dp else 12.dp,
                end = if (compact) 13.dp else 15.dp,
                bottom = bottomTailInset + if (compact) 9.dp else 11.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = if (mode == AnchoredLoginMode.Login) "登录 AI Ledger" else "创建 AI Ledger 账号",
            color = Color.White.copy(alpha = 0.97f),
            fontSize = if (compact) 16.sp else 18.sp,
            lineHeight = if (compact) 19.sp else 22.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        LoginPanelHairline()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            LoginModeButton(
                text = "登录",
                selected = mode == AnchoredLoginMode.Login,
                compact = compact,
                modifier = Modifier.weight(1f),
                onClick = {
                    mode = AnchoredLoginMode.Login
                    submitted = false
                },
            )
            LoginModeButton(
                text = "注册",
                selected = mode == AnchoredLoginMode.Register,
                compact = compact,
                modifier = Modifier.weight(1f),
                onClick = {
                    mode = AnchoredLoginMode.Register
                    submitted = false
                },
            )
        }

        LoginQuickTextField(
            value = email,
            onValueChange = { email = it.take(80) },
            placeholder = "邮箱 name@example.com",
            keyboardType = KeyboardType.Email,
            enabled = !accountState.loading,
            compact = compact,
        )

        LoginQuickTextField(
            value = password,
            onValueChange = { password = it.take(72) },
            placeholder = "密码至少 6 位",
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !accountState.loading,
            compact = compact,
        )

        LoginPrimaryAction(
            title = when {
                accountState.loading -> "处理中…"
                mode == AnchoredLoginMode.Register -> "创建账号"
                else -> "登录"
            },
            enabled = canSubmit,
            compact = compact,
            onClick = {
                submitted = true
                if (mode == AnchoredLoginMode.Register) {
                    authRepository.signUp(email, password)
                } else {
                    authRepository.signIn(email, password)
                }
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 16.dp else 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = loginMessageColor(accountState.tone),
                    fontSize = if (compact) 9.5.sp else 10.5.sp,
                    lineHeight = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoginPanelHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.11f),
                        Color(0xFF8DFFF4).copy(alpha = 0.07f),
                        Color.Transparent,
                    )
                )
            )
    )
}

@Composable
private fun LoginModeButton(
    text: String,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(0.64f, Spring.StiffnessMedium),
        label = "login-mode-scale",
    )
    val shape = RoundedCornerShape(if (compact) 13.dp else 15.dp)

    Box(
        modifier = modifier
            .height(if (compact) 34.dp else 36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f + (1f - scale) * 0.42f
            }
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (selected) {
                        listOf(
                            Color.White.copy(alpha = 0.11f),
                            Color(0xFF8DFFF4).copy(alpha = 0.055f),
                            Color(0xFF9B73FF).copy(alpha = 0.050f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.040f),
                            Color.Transparent,
                        )
                    }
                )
            )
            .border(
                0.7.dp,
                Color.White.copy(alpha = if (selected) 0.14f else 0.065f),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.54f),
            fontSize = if (compact) 11.5.sp else 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun LoginQuickTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean,
    compact: Boolean,
) {
    val shape = RoundedCornerShape(if (compact) 15.dp else 17.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 42.dp else 46.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = if (enabled) 0.072f else 0.042f),
                        Color(0xFF8DFFF4).copy(alpha = if (enabled) 0.026f else 0.012f),
                        Color(0xFF9B73FF).copy(alpha = if (enabled) 0.026f else 0.012f),
                    )
                )
            )
            .border(
                0.7.dp,
                Color.White.copy(alpha = if (enabled) 0.095f else 0.052f),
                shape,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White.copy(alpha = if (enabled) 0.94f else 0.55f),
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(Color(0xFF8DFFF4).copy(alpha = 0.90f)),
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
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoginPrimaryAction(
    title: String,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = spring(0.60f, Spring.StiffnessMedium),
        label = "login-primary-scale",
    )
    val shape = RoundedCornerShape(if (compact) 17.dp else 19.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 46.dp else 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f + (1f - scale) * 0.48f
            }
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (enabled) {
                        listOf(
                            Color.White.copy(alpha = 0.095f),
                            Color(0xFF8DFFF4).copy(alpha = 0.085f),
                            Color(0xFF9B73FF).copy(alpha = 0.065f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.045f),
                            Color.White.copy(alpha = 0.025f),
                        )
                    }
                )
            )
            .border(
                0.8.dp,
                if (enabled) Color(0xFFB9FFF6).copy(alpha = 0.17f) else Color.White.copy(alpha = 0.07f),
                shape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = if (enabled) 0.96f else 0.60f),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private fun loginMessageColor(tone: SupabaseAccountMessageTone): Color {
    return when (tone) {
        SupabaseAccountMessageTone.Success -> Color(0xFF8DF9EA).copy(alpha = 0.88f)
        SupabaseAccountMessageTone.Error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
        SupabaseAccountMessageTone.Normal -> Color.White.copy(alpha = 0.50f)
    }
}
