package com.yuchen.ailedger.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.yuchen.ailedger.data.SupabaseSessionStore
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AccountAuthMode { Login, Register }
private enum class AccountMessageTone { Normal, Success, Error }
private const val AccountFormPressMotion = 0f

@Composable
fun NativeAccountSettingsCard(state: AssistantUiState) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val sessionStore = remember(context) { SupabaseSessionStore(context) }
    val authClient = remember { SupabaseAuthClient() }

    var session by remember { mutableStateOf<SupabaseUserSession?>(null) }
    var authMode by rememberSaveable { mutableStateOf(AccountAuthMode.Login) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("未登录时仍可继续本地使用；登录后会保留 Supabase 会话。") }
    var tone by rememberSaveable { mutableStateOf(AccountMessageTone.Normal) }

    LaunchedEffect(Unit) {
        val stored = withContext(Dispatchers.IO) { sessionStore.load() }
        session = stored
        if (stored != null) {
            emailInput = stored.email
            message = "账号已接通。会话已保存在本机。"
            tone = AccountMessageTone.Success
        }
    }

    fun submitAuth() {
        val email = emailInput.trim()
        val password = passwordInput
        if (loading) return
        if (email.isBlank() || password.isBlank()) {
            message = "邮箱和密码都要填写。"
            tone = AccountMessageTone.Error
            return
        }
        loading = true
        message = if (authMode == AccountAuthMode.Register) "正在注册…" else "正在登录…"
        tone = AccountMessageTone.Normal
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (authMode == AccountAuthMode.Register) authClient.signUp(email, password)
                    else authClient.signInWithPassword(email, password)
                }
                result.session?.let { nextSession ->
                    withContext(Dispatchers.IO) { sessionStore.save(nextSession) }
                    session = nextSession
                    emailInput = nextSession.email
                    passwordInput = ""
                }
                message = result.message
                tone = if (result.requiresEmailConfirmation) AccountMessageTone.Normal else AccountMessageTone.Success
            } catch (error: Throwable) {
                message = error.friendlyAccountMessage()
                tone = AccountMessageTone.Error
            } finally {
                loading = false
            }
        }
    }

    fun refreshLogin() {
        val current = session
        if (current?.refreshToken.isNullOrBlank() || loading) {
            message = "当前会话缺少刷新令牌，请重新登录。"
            tone = AccountMessageTone.Error
            return
        }
        loading = true
        message = "正在刷新登录状态…"
        tone = AccountMessageTone.Normal
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { authClient.refreshSession(current!!.refreshToken) }
                val nextSession = result.session ?: current
                withContext(Dispatchers.IO) { sessionStore.save(nextSession) }
                session = nextSession
                emailInput = nextSession.email
                message = result.message
                tone = AccountMessageTone.Success
            } catch (error: Throwable) {
                message = error.friendlyAccountMessage()
                tone = AccountMessageTone.Error
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        val current = session
        if (loading) return
        loading = true
        message = "正在退出…"
        tone = AccountMessageTone.Normal
        scope.launch {
            withContext(Dispatchers.IO) {
                current?.accessToken?.takeIf { it.isNotBlank() }?.let { token -> runCatching { authClient.signOut(token) } }
                sessionStore.clear()
            }
            session = null
            passwordInput = ""
            message = "已退出登录，当前仅保存在本机。"
            tone = AccountMessageTone.Normal
            loading = false
        }
    }

    FrostInfoGlassPanel(
        radius = 17.44f,
        backdropAlpha = 1f,
        frostAlpha = 0.092f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("账号与同步", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(if (session?.isUsable == true) session!!.email else "未登录 · 本地模式", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AccountStatusPill(session?.isUsable == true)
            }

            AccountInfoRow("当前账号", session?.email ?: "未登录")
            AccountInfoRow("同步状态", if (session?.isUsable == true) "登录体系已接通" else "登录后开启云端能力")

            if (session?.isUsable != true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AccountModeChip("登录", authMode == AccountAuthMode.Login, state, Modifier.weight(1f)) { authMode = AccountAuthMode.Login }
                    AccountModeChip("注册", authMode == AccountAuthMode.Register, state, Modifier.weight(1f)) { authMode = AccountAuthMode.Register }
                }
                AccountTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it.take(80) },
                    placeholder = "邮箱 name@example.com",
                    keyboardType = KeyboardType.Email,
                    enabled = !loading
                )
                AccountTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it.take(72) },
                    placeholder = "密码至少 6 位",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !loading
                )
                AccountActionButton(
                    title = if (loading) "处理中…" else if (authMode == AccountAuthMode.Register) "注册" else "登录",
                    subtitle = if (authMode == AccountAuthMode.Register) "Supabase 邮箱注册" else "邮箱密码登录",
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { submitAuth() }
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    AccountActionButton("刷新会话", if (loading) "处理中" else "更新 token", state, Modifier.weight(1f)) { refreshLogin() }
                    AccountActionButton("退出登录", "回到本地模式", state, Modifier.weight(1f)) { logout() }
                }
            }

            Text(
                text = message,
                color = when (tone) {
                    AccountMessageTone.Success -> Color(0xFF8DF9EA).copy(alpha = 0.88f)
                    AccountMessageTone.Error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
                    AccountMessageTone.Normal -> Color.White.copy(alpha = 0.52f)
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AccountStatusPill(loggedIn: Boolean) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (loggedIn) Color(0xFF8DF9EA).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (loggedIn) "已登录" else "本地模式", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun AccountModeChip(text: String, selected: Boolean, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, AccountFormPressMotion, 999, modifier.height(40.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = if (selected) 0.96f else 0.62f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AccountActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, AccountFormPressMotion, 23, modifier.height(58.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

@Composable
private fun AccountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Color(0xFF8DF9EA).copy(alpha = 0.92f)),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isBlank()) {
            Text(placeholder, color = Color.White.copy(alpha = 0.38f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Throwable.friendlyAccountMessage(): String = message?.takeIf { it.isNotBlank() } ?: "账号操作失败，请稍后再试。"
