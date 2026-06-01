package com.yuchen.ailedger.ui

import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState

@Composable
fun NavigationPreferenceSettingsPanel(
    state: AssistantUiState,
    onAddressChange: (slot: String, address: String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        quality = state.quality,
        intensity = state.glassIntensity * 0.82f,
        motionIntensity = state.motionIntensity,
        radius = 26,
        modifier = modifier.fillMaxWidth(),
        role = GlassRole.Card
    ) {
        FrostInfoGlassPanel(
            radius = 17.44f,
            backdropAlpha = 1f,
            frostAlpha = 0.082f,
            dimAlpha = 0f,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("导航偏好", color = Color.White, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text("这些地址会被云端指令和本地导航确认卡共同使用。", color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("本地保存", color = Color(0xFF8DF9EA).copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                NavigationAddressRow("家", "home", state.navigationHomeAddress, "例如：重庆大学虎溪校区", onAddressChange)
                NavigationAddressRow("学校", "school", state.navigationSchoolAddress, "例如：重庆大学A区", onAddressChange)
                NavigationAddressRow("公司", "company", state.navigationCompanyAddress, "例如：公司大楼", onAddressChange)
                NavigationAddressRow("宿舍", "dorm", state.navigationDormAddress, "例如：梅园二栋", onAddressChange)
            }
        }
    }
}

@Composable
private fun NavigationAddressRow(
    label: String,
    slot: String,
    value: String,
    placeholder: String,
    onAddressChange: (slot: String, address: String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Spacer(Modifier.weight(0.02f))
        BasicTextField(
            value = value,
            onValueChange = { onAddressChange(slot, it.take(80)) },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(placeholder, color = Color.White.copy(alpha = 0.28f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            }
        )
    }
}
