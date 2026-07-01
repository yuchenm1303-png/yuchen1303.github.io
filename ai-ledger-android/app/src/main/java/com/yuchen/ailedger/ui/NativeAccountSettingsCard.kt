package com.yuchen.ailedger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AccountModeChip(
                        "登录",
                        authMode == AccountAuthMode.Login,
                        state,
                        Modifier.weight(1f),
                    ) { authMode = AccountAuthMode.Login }
                    AccountModeChip(
                        "注册",
                        authMode == AccountAuthMode.Register,
                        state,
                        Modifier.weight(1f),
                    ) { authMode = AccountAuthMode.Register }
                }
                AccountTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it.take(80) },
                    placeholder = "邮箱 name@example.com",
                    keyboardType = KeyboardType.Email,
                    enabled = !loading,
                )
                AccountTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it.take(72) },
                    placeholder = "密码至少 6 位",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !loading,
                )
                AccountActionButton(
                    title = if (loading) {
                        "处理中…"
                    } else if (authMode == AccountAuthMode.Register) {
                        "注册"
                    } else {
                        "登录"
                    },
                    subtitle = if (authMode == AccountAuthMode.Register) {
                        "Supabase 邮箱注册"
                    } else {
                        "邮箱密码登录"
                    },
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    onClick = {
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
                color = when (accountState.tone) {
                    SupabaseAccountMessageTone.Success -> Color(0xFF8DF9EA).copy(alpha = 0.88f)
                    SupabaseAccountMessageTone.Error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
                    SupabaseAccountMessageTone.Normal -> Color.White.copy(alpha = 0.52f)
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
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
