package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

@Composable
internal fun SettingsDetailPanel(
    panel: SettingsDetailSection,
    state: AssistantUiState,
    aiEndpoint: String,
    @Suppress("UNUSED_PARAMETER") onQualityChange: (RenderQuality) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPreviewConversationChange: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
) {
    SettingsGlassFrame(state = state, radius = 28) {
        AnimatedContent(
            targetState = panel,
            transitionSpec = {
                val direction = if (targetState.settingsOrder() >= initialState.settingsOrder()) 1 else -1
                fadeIn(
                    animationSpec = tween(
                        170,
                        delayMillis = 42,
                        easing = FastOutSlowInEasing,
                    )
                ) + slideInVertically(
                    animationSpec = tween(310, easing = FastOutSlowInEasing)
                ) { 46 * direction } + scaleIn(
                    initialScale = 0.955f,
                    animationSpec = tween(310, easing = FastOutSlowInEasing),
                ) togetherWith fadeOut(
                    animationSpec = tween(135, easing = FastOutSlowInEasing)
                ) + slideOutVertically(
                    animationSpec = tween(170, easing = FastOutSlowInEasing)
                ) { -30 * direction } + scaleOut(
                    targetScale = 0.982f,
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                )
            },
            label = "settings-detail-panel-switch",
        ) { activePanel ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailHeader(panelTitle(activePanel), panelSubtitle(activePanel))
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    when (activePanel) {
                        SettingsDetailSection.Appearance -> AppearanceContent(
                            state,
                            onBackgroundThemeChange,
                            onUploadBackgroundClick,
                            onClearCustomBackgroundClick,
                        )

                        SettingsDetailSection.Glass -> GlassContent(
                            state,
                            onGlassIntensityChange,
                            onMotionIntensityChange,
                            onRainbowPrismChange,
                            onBackdropChange,
                        )

                        SettingsDetailSection.Assistant -> VisualAgentHudSettingsContent(state)
                        SettingsDetailSection.Data -> DataContent(state)
                        SettingsDetailSection.Service -> ServiceContent(state, aiEndpoint)
                        SettingsDetailSection.Advanced -> AdvancedContent()
                        SettingsDetailSection.Chat -> ChatPageSettingsContent()
                        SettingsDetailSection.Memory -> AccountMemorySettingsContent(state)
                        SettingsDetailSection.Debug -> GlassDebugFloatingPanel(
                            state,
                            onBackdropChange,
                            onBorderChange,
                            onUploadBackgroundClick,
                            onClearCustomBackgroundClick,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGlassFrame(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    radius: Int = 28,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.fillMaxWidth().clip(shape)) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = radius,
            modifier = Modifier.matchParentSize(),
            role = GlassRole.Card,
        ) {}
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppearanceContent(
    state: AssistantUiState,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
) {
    SettingChipGrid(
        BackgroundTheme.entries,
        state.backgroundTheme,
        { themeLabel(it) },
        state,
        onBackgroundThemeChange,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingActionButton(
            "涓婁紶鑳屾櫙",
            if (state.customBackgroundPath == null) "閫夋嫨鍥剧墖" else "宸茶嚜瀹氫箟",
            state,
            Modifier.weight(1f),
            onUploadBackgroundClick,
        )
        SettingActionButton(
            "娓呴櫎鑳屾櫙",
            "鎭㈠涓婚",
            state,
            Modifier.weight(1f),
            onClearCustomBackgroundClick,
        )
    }
}

@Composable
private fun GlassContent(
    state: AssistantUiState,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
) {
    val prism = state.rainbowPrismStyle
    val backdrop = state.backdropParams

    SettingsParameterGroup(title = "鐜荤拑鍩虹", subtitle = "閫氱敤鐜荤拑鏉愯川涓庡姩鐢诲箙搴�") {
        SliderSettingRow("鐜荤拑寮哄害", "鎺у埗閫氱敤鐜荤拑鐨勫彲瑙佸害銆侀浘鎰熷拰杈圭紭鑳介噺銆�", state.glassIntensity, 0.6f..1.4f, onGlassIntensityChange)
        SliderSettingRow("鍔ㄦ€佸己搴�", "鎺у埗鍛煎惛銆佹壂鍏夊拰褰㈠彉鍔ㄧ敾骞呭害锛�0 涓洪潤鎬併€�", state.motionIntensity, 0f..1.4f, onMotionIntensityChange)
    }

    SettingsParameterGroup(title = "褰╄櫣闀€鑶�", subtitle = "鑱婂ぉ澶х幓鐠冭竟缂樹笌澶栫紭褰╄櫣鑳介噺") {
        SliderSettingRow("鏁翠綋褰╄櫣寮哄害", "缁熶竴璋冭妭鑱婂ぉ澶х幓鐠冨僵铏归晙鑶滅殑鎬昏兘閲忋€�", prism.overall, 0f..2f) { onRainbowPrismChange(prism.copy(overall = it)) }
        SliderSettingRow("妫卞僵杈圭紭楂樺厜", "澧炲己鍦嗚鍜岀幓鐠冭竟缂樺褰╄壊鍏ュ皠鍏夌殑鎹曡幏銆�", prism.edgeHighlight, 0f..2f) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
        SliderSettingRow("绮夐噾闈掕摑褰╄櫣鍏夋檿", "璋冭妭绮夈€侀噾銆侀潚銆佽摑鍦ㄧ幓鐠冨缂樺舰鎴愮殑鏌斿拰鍏夋檿銆�", prism.rainbowHalo, 0f..2f) { onRainbowPrismChange(prism.copy(rainbowHalo = it)) }
    }

    SettingsParameterGroup(title = "闅忔満娓愬彉鎵厜", subtitle = "鑱婂ぉ澶х幓鐠冮殢鏈烘壂鍏変寒搴﹀尯闂�") {
        SliderSettingRow("鎵厜寮哄害涓嬮檺", "闅忔満鎵厜姣忔鍑虹幇鏃跺厑璁哥殑鏈€浣庝寒搴︺€�", prism.sweepMin, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMin = it)) }
        SliderSettingRow("鎵厜寮哄害涓婇檺", "闅忔満鎵厜姣忔鍑虹幇鏃跺厑璁哥殑鏈€楂樹寒搴︺€�", prism.sweepMax, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMax = it)) }
    }

    SettingsParameterGroup(title = "鑳屾櫙妯＄硦閲戝瓧濉�", subtitle = "鍗曚竴鑳屾櫙婧愮殑娓呮櫚銆佷綆銆佷腑銆侀珮鍥涚骇閲囨牱") {
        SettingsParameterSlider("缂撳瓨鍒嗚鲸鐜�", "璋冭妭鑳屾櫙妯＄硦缂撳瓨鐨勬湁鏁堝垎杈ㄧ巼锛涜寖鍥翠笌杩愯鏃跺畨鍏ㄨ竟鐣屽畬鍏ㄤ竴鑷淬€�", backdrop.scale.coerceIn(0.28f, 0.72f), 0.28f..0.72f, { "${it.settingsRoundedValue()}脳" }) { onBackdropChange(backdrop.copy(scale = it)) }
        SettingsParameterSlider("妯＄硦灞傜骇", "0=娓呮櫚锛�1=浣庯紝2=涓紝4=楂橈紱涓棿鍊艰繛缁彃鍊笺€�", backdrop.radius, 0f..4f, { "${it.settingsRoundedValue()} 绾�" }) { onBackdropChange(backdrop.copy(radius = it)) }
        SettingsParameterSlider("妯＄硦杩唬", "0 璺宠繃鍏ㄩ儴妯＄硦 pass锛�1鈥�12 鎺у埗浣庛€佷腑銆侀珮缂撳瓨鐢熸垚杞暟銆�", backdrop.iterations, 0f..12f, { "${it.roundToInt()} 娆�" }) { onBackdropChange(backdrop.copy(iterations = it.roundToInt().toFloat())) }
    }

    SettingsParameterGroup(title = "鑳屾櫙鑹插僵杈撳嚭", subtitle = "妯＄硦缂撳瓨鐢熸垚鍚庣殑鏄庢殫涓庤壊褰�") {
        SettingsParameterSlider("鑳屾櫙浜害", "璋冭妭鐜荤拑閲囨牱鑳屾櫙鐨勬暣浣撴槑鏆椼€�", backdrop.brightness, 0.4f..2.2f) { onBackdropChange(backdrop.copy(brightness = it)) }
        SettingsParameterSlider("鑳屾櫙瀵规瘮搴�", "璋冭妭鐜荤拑閲囨牱鑳屾櫙鐨勬槑鏆楀弽宸€�", backdrop.contrast, 0.5f..1.8f) { onBackdropChange(backdrop.copy(contrast = it)) }
        SettingsParameterSlider("鑳屾櫙楗卞拰搴�", "璋冭妭鐜荤拑閲囨牱鑳屾櫙鐨勭患鍚堣壊褰╂祿搴︼紱鑼冨洿涓庣汗鐞嗙敓鎴愬櫒涓€鑷淬€�", backdrop.saturation.coerceIn(0.3f, 1.8f), 0.3f..1.8f) { onBackdropChange(backdrop.copy(saturation = it)) }
    }

    SettingsParameterGroup(title = "涓婁紶鍥剧墖浜害淇濇姢", subtitle = "鍙湪鍙傛暟绋冲畾鍚庨噸寤轰竴娆¤嚜瀹氫箟鑳屾櫙缂撳瓨") {
        SettingsParameterSlider("涓婁紶鍥句寒搴�", "鍙皟鑺傜敤鎴蜂笂浼犲師鍥剧殑鍩虹浜害锛涘唴缃富棰樺拰榛樿澹佺焊涓嶅彈褰卞搷銆�", backdrop.customImageBrightness, 0.50f..1.10f) { onBackdropChange(backdrop.copy(customImageBrightness = it)) }
        SettingsParameterSlider("楂樺厜鍘嬬缉璧风偣", "鍥剧墖浜害瓒呰繃璇ヤ綅缃悗寮€濮嬫煍鍜屽帇缂╋紝鏆楅儴鍜屼腑闂磋皟灏介噺淇濇寔鍘熸牱銆�", backdrop.customImageHighlightStart, 0.35f..0.85f, { "${(it * 100f).roundToInt()}%" }) {
            val start = it
            val limit = maxOf(backdrop.customImageHighlightLimit, start + 0.02f).coerceAtMost(0.92f)
            onBackdropChange(backdrop.copy(customImageHighlightStart = start, customImageHighlightLimit = limit))
        }
        SettingsParameterSlider("浜害杈撳嚭涓婇檺", "闄愬埗涓婁紶鍥剧墖鏈€浜尯鍩熺殑鏈€缁堜寒搴︼紝閬垮厤鐧借壊鑳屾櫙鍐叉贰鐜荤拑涓婄殑鏂囧瓧銆�", backdrop.customImageHighlightLimit, 0.50f..0.92f, { "${(it * 100f).roundToInt()}%" }) {
            val limit = it
            val start = minOf(backdrop.customImageHighlightStart, limit - 0.02f).coerceAtLeast(0.35f)
            onBackdropChange(backdrop.copy(customImageHighlightStart = start, customImageHighlightLimit = limit))
        }
    }

    SettingsParameterGroup(title = "鑳屾櫙浜戦浘灞�", subtitle = "鍐呯疆涓婚鐨勪簯灞傚舰鎬佷笌楂樺厜") {
        SettingsParameterSlider("浜戦浘閫忔槑搴�", "璋冭妭鍐呯疆涓婚鑳屾櫙浜戦浘灞傜殑鏁翠綋鍙搴︺€�", backdrop.cloudAlpha, 0f..2f) { onBackdropChange(backdrop.copy(cloudAlpha = it)) }
        SettingsParameterSlider("浜戦浘鏌斿寲", "璋冭妭浜戝眰杈圭紭鐨勬墿鏁ｄ笌鏌斿拰绋嬪害銆�", backdrop.cloudSoftness, 0f..3f) { onBackdropChange(backdrop.copy(cloudSoftness = it)) }
        SettingsParameterSlider("浜戝眰妯悜鎷変几", "璋冭妭浜戦浘灞傚湪姘村钩鏂瑰悜鐨勯摵灞曡寖鍥淬€�", backdrop.cloudStretchX, 0.4f..4f) { onBackdropChange(backdrop.copy(cloudStretchX = it)) }
        SettingsParameterSlider("浜戝眰绾靛悜鎷変几", "璋冭妭浜戦浘灞傚湪鍨傜洿鏂瑰悜鐨勫帤搴︺€�", backdrop.cloudStretchY, 0.2f..2f) { onBackdropChange(backdrop.copy(cloudStretchY = it)) }
        SettingsParameterSlider("浜戝眰楂樺厜", "璋冭妭浜戦浘浜儴鐨勫眬閮ㄩ珮鍏夐€忔槑搴︺€�", backdrop.cloudHighlightAlpha, 0f..1f) { onBackdropChange(backdrop.copy(cloudHighlightAlpha = it)) }
    }

    SettingsParameterGroup(title = "鑳屾櫙鏈堜寒灞�", subtitle = "鍐呯疆涓婚鐨勬湀浣撱€佸厜鏅曚笌杈圭紭") {
        SettingsParameterSlider("鏈堜寒灏哄", "璋冭妭鍐呯疆涓婚鏈堜綋鐨勬暣浣撳昂瀵搞€�", backdrop.moonScale, 0.5f..1.8f) { onBackdropChange(backdrop.copy(moonScale = it)) }
        SettingsParameterSlider("鏈堜寒鍏夋檿", "璋冭妭鏈堜綋鍛ㄥ洿鏌斿拰鍏夋檿鐨勯€忔槑搴︺€�", backdrop.moonHaloAlpha, 0f..1f) { onBackdropChange(backdrop.copy(moonHaloAlpha = it)) }
        SettingsParameterSlider("鏈堜寒杈圭紭鍏�", "璋冭妭鏈堜綋杞粨杈圭紭鐨勪寒搴︺€�", backdrop.moonRimAlpha, 0f..1.2f) { onBackdropChange(backdrop.copy(moonRimAlpha = it)) }
    }
}

@Composable
private fun SettingsParameterGroup(title: String, subtitle: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (expanded) 0.070f else 0.048f))
            .animateContentSize(animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (expanded) "鏀惰捣 锔�" else "灞曞紑 锕€", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsParameterSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String = { "${it.settingsRoundedValue()}脳" },
    onValueChange: (Float) -> Unit,
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    InsetGlassParameterSlider(title = title, description = description, value = safeValue, valueRange = valueRange, onValueChange = onValueChange, valueText = valueText(safeValue))
}

private fun Float.settingsRoundedValue(): String = ((this * 100f).roundToInt() / 100f).toString()

@Composable
private fun DataContent(state: AssistantUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MiniSettingMetric("璐﹀崟", "${state.ledgerRecords.size} 绗�", Modifier.weight(1f))
        MiniSettingMetric("棰勭畻", "楼${state.ledgerBudgetText.ifBlank { "0" }}", Modifier.weight(1f))
        MiniSettingMetric("鍚屾", "鑷姩", Modifier.weight(1f))
    }
    SettingInfoRow("鏁版嵁淇濆瓨", "LedgerStore 缁熶竴鎸佷箙鍖栵紝鎵嬪姩涓� AI 璁拌处鍏辩敤鏁版嵁婧�")
    SettingInfoRow("浜戝悓姝�", "鐧诲綍鍚庤嚜鍔ㄥ悎骞跺苟鍚屾锛涙湭鐧诲綍鏃朵繚瀛樺湪鏈満")
    SettingInfoRow("瀹�", state.navigationHomeAddress.ifBlank { "鏈缃�" })
    SettingInfoRow("瀛︽牎", state.navigationSchoolAddress.ifBlank { "鏈缃�" })
    SettingInfoRow("鍏徃", state.navigationCompanyAddress.ifBlank { "鏈缃�" })
    SettingInfoRow("瀹胯垗", state.navigationDormAddress.ifBlank { "鏈缃�" })
}

@Composable
private fun ServiceContent(state: AssistantUiState, aiEndpoint: String) {
    SettingsNestedOrdinaryGlassHost { NativeAccountSettingsCard(state) }
    SettingInfoRow("AI 鎺ュ彛", if (aiEndpoint.isBlank()) "鏈厤缃紝浣跨敤鏈湴鍗犱綅鍥炲" else aiEndpoint)
    SettingInfoRow("鎵ц妯″紡", "浜戠鐞嗚В锛屾湰鍦扮‘璁ゅ悗鎵ц")
    SettingInfoRow("浜戠鍗忚", "mobileAction / preferenceUpdate")
}

@Composable
private fun AdvancedContent() {
    SettingInfoRow("鐜荤拑娓叉煋", "浠呯湡姝ｇ殑澶у瀷 Shell 浣跨敤 OpenGL")
    SettingInfoRow("鍔熻兘椤垫爮鐩�", "鏅€氬叆鍙ｅ崱鐗囧浐瀹氫娇鐢� Compose 鐜荤拑")
    SettingInfoRow("闅旂鑼冨洿", "Card / Chip / Floating / Nav / Flex")
    SettingInfoRow("鍑犱綍鍚屾", "鏅€氭帶浠朵笉娉ㄥ唽 registry锛屼篃涓嶈姹� geometry sync")
    SettingInfoRow("璐﹀彿鎺т欢", "绾� Compose + REST API锛屼笉鎺ュ叆 OpenGL registry")
}

@Composable
private fun ChatPageSettingsContent() {
    val context = LocalContext.current
    val stickerLayout = InlineStickerDisplaySettings.layoutPreferences(context)

    SettingsParameterGroup(
        title = "鍐呰仈琛ㄦ儏鎺掔増",
        subtitle = "鐩存帴鎺у埗鑱婂ぉ姝ｆ枃閲岀殑琛ㄦ儏澶у皬銆佸亸绉汇€侀棿璺濆拰琛岄珮鍗犱綅銆�",
    ) {
        SettingsParameterSlider(
            title = "琛ㄦ儏鍖呭ぇ灏�",
            description = "鎺у埗鑱婂ぉ娑堟伅涓唴鑱旇〃鎯呯殑瀹為檯缁樺埗灏哄锛屼笉鍐嶅彧浣滀负涓婇檺銆�",
            value = stickerLayout.sizeDp,
            valueRange = InlineStickerDisplaySettings.SizeRange,
            valueText = { "${it.roundToInt()} dp" },
        ) { InlineStickerDisplaySettings.updateSizeDp(context, it) }

        SettingsParameterSlider(
            title = "涓婁笅鍋忕Щ",
            description = "鎺у埗琛ㄦ儏鐩稿鏂囧瓧鍩虹嚎鐨勪笂涓嬩綅缃紱璐熸暟涓婄Щ锛屾鏁颁笅绉汇€�",
            value = stickerLayout.verticalOffsetDp,
            valueRange = InlineStickerDisplaySettings.VerticalOffsetRange,
            valueText = {
                val rounded = it.roundToInt()
                if (rounded > 0) "+$rounded dp" else "$rounded dp"
            },
        ) { InlineStickerDisplaySettings.updateVerticalOffsetDp(context, it) }

        SettingsParameterSlider(
            title = "宸﹀彸闂磋窛",
            description = "鎺у埗琛ㄦ儏宸﹀彸涓や晶鐣欑櫧锛岄伩鍏嶈创瀛楁垨杩囧害鎸ゅ帇姝ｆ枃銆�",
            value = stickerLayout.horizontalGapDp,
            valueRange = InlineStickerDisplaySettings.HorizontalGapRange,
            valueText = { "${it.roundToInt()} dp" },
        ) { InlineStickerDisplaySettings.updateHorizontalGapDp(context, it) }

        SettingsParameterSlider(
            title = "琛岄珮浣欓噺",
            description = "鎺у埗琛ㄦ儏鍙備笌鏂囧瓧琛岄珮娴嬮噺鏃堕澶栭鐣欑殑涓婁笅绌洪棿銆�",
            value = stickerLayout.lineExtraDp,
            valueRange = InlineStickerDisplaySettings.LineExtraRange,
            valueText = { "${it.roundToInt()} dp" },
        ) { InlineStickerDisplaySettings.updateLineExtraDp(context, it) }
    }

    SettingsNestedOrdinaryGlassHost { InlineStickerExpressionSettingsControls() }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("绀轰緥娑堟伅", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold)
        OptimizedRichMessageContent(
            text = "杩欐缁堜簬璋冮『浜哰[AI_LEDGER_INLINE_STICKER:joy_burst]][[AI_LEDGER_INLINE_STICKER:sparkle_excited]]锛屽彞涓殑琛ㄦ儏浼氳窡鐫€澶у皬銆佸亸绉汇€侀棿璺濆拰琛岄珮璁剧疆瀹炴椂鍙樺寲銆�",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("鎷栧姩涓婃柟婊戝潡锛岀ず渚嬪拰鑱婂ぉ椤典腑鐨勮〃鎯呬細鍚屾鏇存柊銆�", color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsNestedOrdinaryGlassHost(content: @Composable () -> Unit) {
    OrdinaryGlassSceneHost(group = LocalGlassSceneContext.current.group, modifier = Modifier.fillMaxWidth(), renderMode = OrdinaryGlassRenderMode.ParentDraw, content = content)
}

private fun SettingsDetailSection.settingsOrder(): Int = when (this) {
    SettingsDetailSection.Appearance -> 0
    SettingsDetailSection.Glass -> 1
    SettingsDetailSection.Assistant -> 2
    SettingsDetailSection.Data -> 3
    SettingsDetailSection.Service -> 4
    SettingsDetailSection.Advanced -> 5
    SettingsDetailSection.Chat -> 6
    SettingsDetailSection.Memory -> 7
    SettingsDetailSection.Debug -> 8
}

private fun panelTitle(panel: SettingsDetailSection): String = when (panel) {
    SettingsDetailSection.Appearance -> "涓婚"
    SettingsDetailSection.Glass -> "鐜荤拑"
    SettingsDetailSection.Assistant -> "瑙嗚鏅鸿兘"
    SettingsDetailSection.Data -> "鏁版嵁鍋忓ソ"
    SettingsDetailSection.Service -> "璐﹀彿璁剧疆"
    SettingsDetailSection.Advanced -> "绯荤粺淇℃伅"
    SettingsDetailSection.Chat -> "鑱婂ぉ璁剧疆"
    SettingsDetailSection.Memory -> "璁板繂"
    SettingsDetailSection.Debug -> "鐜荤拑瀹為獙瀹�"
}

private fun panelSubtitle(panel: SettingsDetailSection): String = when (panel) {
    SettingsDetailSection.Appearance -> "鑳屾櫙銆佷富棰樺拰鑷畾涔夊浘鐗囥€�"
    SettingsDetailSection.Glass -> "鐜荤拑銆佸僵铏瑰厜鏁堛€佽儗鏅ā绯婁笌涓婁紶鍥句寒搴︿繚鎶ゃ€�"
    SettingsDetailSection.Assistant -> "杈圭紭鍏夋晥銆侀紶鏍囧厜鏍囦笌杩愯 HUD 鐨勫叏閮ㄥ弬鏁般€�"
    SettingsDetailSection.Data -> "璐﹀崟鐘舵€併€侀绠椼€佹湰鍦版暟鎹拰甯哥敤瀵艰埅鍦板潃銆�"
    SettingsDetailSection.Service -> "璐﹀彿鐧诲綍銆丄I Worker 鍜屼簯绔帴鍙ｃ€�"
    SettingsDetailSection.Advanced -> "娓叉煋杈圭晫鍜� OpenGL 闅旂鐘舵€併€�"
    SettingsDetailSection.Chat -> "鑱婂ぉ娑堟伅銆佸唴鑱旇〃鎯呮樉绀轰笌浜戠琛ㄨ揪鍋忓ソ銆�"
    SettingsDetailSection.Memory -> "鐧诲綍鍚庢煡鐪嬨€佹暣鐞嗗苟鎺у埗 AI 鐨勯暱鏈熻蹇嗐€�"
    SettingsDetailSection.Debug -> "楂樼骇鐜荤拑鍙傛暟涓庡疄楠屽叆鍙ｃ€�"
}
