package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.data.SupabaseAccountMessageTone
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.data.UserProfileRepository
import com.yuchen.ailedger.data.defaultDisplayName
import com.yuchen.ailedger.model.AssistantUiState

private enum class AccountAuthMode { Login, Register }

@Composable
fun NativeAccountSettingsCard(state: AssistantUiState) {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val profileRepository = remember(context) { UserProfileRepository.get(context) }
    val accountState by authRepository.state.collectAsState()
    val profileState by profileRepository.state.collectAsState()

    var authMode by rememberSaveable { mutableStateOf(AccountAuthMode.Login) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var nicknameInput by rememberSaveable { mutableStateOf("") }

    val session = accountState.session
    val loggedIn = accountState.isLoggedIn
    val loading = accountState.loading
    val profileBusy = profileState.isBusy
    val profile = profileState.profile
    val displayName = profile?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: session?.email?.let(::defaultDisplayName).orEmpty()

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(profileRepository::updateAvatar)
    }

    LaunchedEffect(session?.userId, session?.email) {
        if (session != null) {
            emailInput = session.email
            passwordInput = ""
        } else {
            nicknameInput = ""
        }
    }

    LaunchedEffect(session?.userId, profile?.displayName) {
        if (session != null) {
            nicknameInput = displayName
        }
    }

    FrostInfoGlassPanel(
        radius = 17.44f,
        backdropAlpha = 1f,
        frostAlpha = 0.092f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "账号与同步",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (loggedIn) session?.email.orEmpty() else "未登录 · 本地模式",
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AccountStatusPill(loggedIn = loggedIn, loading = loading)
            }

            if (loggedIn && session != null) {
                AccountProfileEditor(
                    state = state,
                    displayName = displayName,
                    email = session.email,
                    nicknameInput = nicknameInput,
                    onNicknameChange = { nicknameInput = it.take(24) },
                    localAvatarPath = profileState.localAvatarPath,
                    avatarVersion = profile?.avatarVersion ?: 0L,
                    hasCustomAvatar = profileState.hasCustomAvatar,
                    busy = profileBusy,
                    onSaveNickname = { profileRepository.updateDisplayName(nicknameInput) },
                    onChooseAvatar = { avatarPicker.launch("image/*") },
                    onRemoveAvatar = profileRepository::removeAvatar,
                )
                Text(
                    text = profileState.message,
                    color = when {
                        profileState.error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
                        profileState.cloudReady -> Color(0xFF8DF9EA).copy(alpha = 0.82f)
                        else -> Color.White.copy(alpha = 0.48f)
                    },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            AccountInfoRow("当前账号", session?.email ?: "未登录")
            AccountInfoRow(
                "同步状态",
                when {
                    loading -> "正在同步登录状态"
                    loggedIn && profileState.cloudReady -> "会话与个人资料已同步"
                    loggedIn -> "会话有效 · 个人资料待同步"
                    else -> "登录后开启长期记忆"
                },
            )

            if (!loggedIn) {
                AccountAuthForm(
                    state = state,
                    authMode = authMode,
                    onAuthModeChange = { authMode = it },
                    emailInput = emailInput,
                    onEmailChange = { emailInput = it.take(80) },
                    passwordInput = passwordInput,
                    onPasswordChange = { passwordInput = it.take(72) },
                    loading = loading,
                    onSubmit = {
                        if (authMode == AccountAuthMode.Register) {
                            authRepository.signUp(emailInput, passwordInput)
                        } else {
                            authRepository.signIn(emailInput, passwordInput)
                        }
                    },
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AccountActionButton(
                        title = "刷新资料",
                        subtitle = if (loading || profileBusy) "处理中" else "会话与个人资料",
                        state = state,
                        modifier = Modifier.weight(1f),
                        enabled = !loading && !profileBusy,
                        onClick = {
                            authRepository.refreshSession()
                            profileRepository.refresh()
                        },
                    )
                    AccountActionButton(
                        title = "退出登录",
                        subtitle = "锁定长期记忆",
                        state = state,
                        modifier = Modifier.weight(1f),
                        enabled = !loading && !profileBusy,
                        onClick = authRepository::signOut,
                    )
                }
            }

            Text(
                text = accountState.message,
                color = accountMessageColor(accountState.tone),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun AccountLoginModalHost(
    visible: Boolean,
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val backdropInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(5000f)
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f),
            enter = fadeIn(tween(170)),
            exit = fadeOut(tween(135)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF07132D).copy(alpha = 0.10f),
                                Color(0xFF07132D).copy(alpha = 0.20f),
                                Color(0xFF03091F).copy(alpha = 0.46f),
                            )
                        )
                    )
                    .clickable(
                        interactionSource = backdropInteraction,
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .zIndex(1f),
            enter = fadeIn(tween(165)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                ) { fullHeight -> fullHeight },
            exit = fadeOut(tween(115)) +
                slideOutVertically(tween(165)) { fullHeight -> fullHeight },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = {},
                    )
            ) {
                AccountLoginBottomCard(
                    state = state,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
internal fun AccountLoginBottomCard(
    state: AssistantUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val accountState by authRepository.state.collectAsState()
    var authMode by rememberSaveable { mutableStateOf(AccountAuthMode.Login) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
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

    FrostInfoGlassPanel(
        radius = 30f,
        backdropAlpha = 1f,
        frostAlpha = 0.22f,
        dimAlpha = 0.32f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = if (authMode == AccountAuthMode.Login) {
                            "登录 AI Ledger"
                        } else {
                            "创建 AI Ledger 账号"
                        },
                        color = Color.White.copy(alpha = 0.96f),
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "同步昵称、头像、记忆与自选数据",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                PressableGlass(
                    quality = state.quality,
                    glassIntensity = state.glassIntensity,
                    motionIntensity = state.motionIntensity,
                    radius = 999,
                    modifier = Modifier
                        .width(34.dp)
                        .height(32.dp),
                    role = GlassRole.Chip,
                    onClick = onDismiss,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "×",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 17.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            AccountLoginModeSwitch(
                state = state,
                selected = authMode,
                onSelected = {
                    authMode = it
                    submitted = false
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AccountTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it.take(80) },
                    placeholder = "邮箱 name@example.com",
                    keyboardType = KeyboardType.Email,
                    enabled = !accountState.loading,
                )
                AccountTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it.take(72) },
                    placeholder = "密码至少 6 位",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !accountState.loading,
                )
            }

            AccountLoginPrimaryButton(
                state = state,
                title = when {
                    accountState.loading -> "处理中…"
                    authMode == AccountAuthMode.Register -> "创建账号"
                    else -> "登录"
                },
                enabled = !accountState.loading &&
                    emailInput.isNotBlank() &&
                    passwordInput.length >= 6,
                onClick = {
                    submitted = true
                    if (authMode == AccountAuthMode.Register) {
                        authRepository.signUp(emailInput, passwordInput)
                    } else {
                        authRepository.signIn(emailInput, passwordInput)
                    }
                },
            )

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = accountMessageColor(accountState.tone),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AccountLoginModeSwitch(
    state: AssistantUiState,
    selected: AccountAuthMode,
    onSelected: (AccountAuthMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF08132E).copy(alpha = 0.28f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AccountLoginModeButton(
            text = "登录",
            selected = selected == AccountAuthMode.Login,
            state = state,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(AccountAuthMode.Login) },
        )
        AccountLoginModeButton(
            text = "注册",
            selected = selected == AccountAuthMode.Register,
            state = state,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(AccountAuthMode.Register) },
        )
    }
}

@Composable
private fun AccountLoginModeButton(
    text: String,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (selected) 1f else 0.82f,
        motionIntensity = state.motionIntensity,
        radius = 15,
        modifier = modifier.height(36.dp),
        role = if (selected) GlassRole.Floating else GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White.copy(alpha = if (selected) 0.94f else 0.52f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun AccountLoginPrimaryButton(
    state: AssistantUiState,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (enabled) 1.04f else 0.68f,
        motionIntensity = state.motionIntensity,
        radius = 20,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        role = GlassRole.Floating,
        onClick = { if (enabled) onClick() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = Color.White.copy(alpha = if (enabled) 0.96f else 0.40f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun AccountAuthForm(
    state: AssistantUiState,
    authMode: AccountAuthMode,
    onAuthModeChange: (AccountAuthMode) -> Unit,
    emailInput: String,
    onEmailChange: (String) -> Unit,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    loading: Boolean,
    onSubmit: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AccountModeChip(
            "登录",
            authMode == AccountAuthMode.Login,
            state,
            Modifier.weight(1f),
        ) { onAuthModeChange(AccountAuthMode.Login) }
        AccountModeChip(
            "注册",
            authMode == AccountAuthMode.Register,
            state,
            Modifier.weight(1f),
        ) { onAuthModeChange(AccountAuthMode.Register) }
    }
    AccountTextField(
        value = emailInput,
        onValueChange = onEmailChange,
        placeholder = "邮箱 name@example.com",
        keyboardType = KeyboardType.Email,
        enabled = !loading,
    )
    AccountTextField(
        value = passwordInput,
        onValueChange = onPasswordChange,
        placeholder = "密码至少 6 位",
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
        enabled = !loading,
    )
    AccountActionButton(
        title = if (loading) {
            "处理中…"
        } else if (authMode == AccountAuthMode.Register) {
            "创建账号"
        } else {
            "登录"
        },
        subtitle = if (authMode == AccountAuthMode.Register) {
            "使用邮箱注册 Supabase 账号"
        } else {
            "使用邮箱和密码继续"
        },
        state = state,
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading && emailInput.isNotBlank() && passwordInput.length >= 6,
        onClick = onSubmit,
    )
}

@Composable
private fun AccountProfileEditor(
    state: AssistantUiState,
    displayName: String,
    email: String,
    nicknameInput: String,
    onNicknameChange: (String) -> Unit,
    localAvatarPath: String?,
    avatarVersion: Long,
    hasCustomAvatar: Boolean,
    busy: Boolean,
    onSaveNickname: () -> Unit,
    onChooseAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.050f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserProfileAvatar(
                localAvatarPath = localAvatarPath,
                avatarVersion = avatarVersion,
                fallbackText = displayName.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "AI" },
                size = 66.dp,
                loggedIn = true,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayName.ifBlank { "AI Ledger 用户" },
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = email,
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "头像会自动居中裁切并同步到账号",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AccountTextField(
            value = nicknameInput,
            onValueChange = onNicknameChange,
            placeholder = "输入昵称，最多 24 个字符",
            keyboardType = KeyboardType.Text,
            enabled = !busy,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            AccountActionButton(
                title = if (busy) "处理中…" else "保存昵称",
                subtitle = "同步到当前账号",
                state = state,
                modifier = Modifier.weight(1f),
                enabled = !busy && nicknameInput.isNotBlank(),
                onClick = onSaveNickname,
            )
            AccountActionButton(
                title = if (busy) "处理中…" else if (hasCustomAvatar) "更换头像" else "设置头像",
                subtitle = "从系统相册选择",
                state = state,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = onChooseAvatar,
            )
        }

        if (hasCustomAvatar) {
            AccountActionButton(
                title = "恢复默认头像",
                subtitle = "删除账号中的自定义头像",
                state = state,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = onRemoveAvatar,
            )
        }
    }
}

@Composable
private fun AccountStatusPill(loggedIn: Boolean, loading: Boolean) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    loading -> Color.White.copy(alpha = 0.08f)
                    loggedIn -> Color(0xFF8DF9EA).copy(alpha = 0.18f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                loading -> "同步中"
                loggedIn -> "已登录"
                else -> "本地模式"
            },
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun AccountModeChip(
    text: String,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        state.quality,
        state.glassIntensity,
        state.motionIntensity,
        999,
        modifier.height(40.dp),
        if (selected) GlassRole.Floating else GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = Color.White.copy(alpha = if (selected) 0.96f else 0.62f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun AccountActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    PressableGlass(
        state.quality,
        state.glassIntensity * if (enabled) 1f else 0.72f,
        state.motionIntensity,
        23,
        modifier.height(58.dp),
        GlassRole.Chip,
        onClick = { if (enabled) onClick() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.48f),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = if (enabled) 0.52f else 0.30f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccountInfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AccountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.070f else 0.040f))
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White.copy(alpha = if (enabled) 0.92f else 0.46f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(Color(0xFF8DF9EA).copy(alpha = 0.92f)),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isBlank()) {
            Text(
                placeholder,
                color = Color.White.copy(alpha = if (enabled) 0.38f else 0.24f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun accountMessageColor(tone: SupabaseAccountMessageTone): Color {
    return when (tone) {
        SupabaseAccountMessageTone.Success -> Color(0xFF8DF9EA).copy(alpha = 0.88f)
        SupabaseAccountMessageTone.Error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
        SupabaseAccountMessageTone.Normal -> Color.White.copy(alpha = 0.52f)
    }
}
