package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.yuchen.ailedger.model.AppTab

/**
 * 普通 Compose 玻璃后续父级绘制时使用的场景边界。
 *
 * 当前阶段只负责标记归属，不注册玻璃、不改变绘制顺序，也不触发任何 OpenGL 同步。
 * 一个场景代表一套相对独立的坐标、裁剪、生命周期和前后景层级。
 */
enum class GlassSceneGroup(
    val owner: GlassSceneOwner,
    val ordinaryComposeGlassAllowed: Boolean = true
) {
    Unassigned(GlassSceneOwner.Fallback),

    AssistantPage(GlassSceneOwner.Page),
    AssistantChatBubbles(GlassSceneOwner.Specialized),

    ToolsPage(GlassSceneOwner.Page),
    ToolsHomePage(GlassSceneOwner.Page),
    LedgerCenterPage(GlassSceneOwner.Page),
    StockMarketPage(GlassSceneOwner.Page),

    SettingsPage(GlassSceneOwner.Page),
    SettingsDebugInnerScroll(GlassSceneOwner.ScrollSubScene),

    GlobalBottomBar(GlassSceneOwner.Persistent),
    AttachmentDialog(GlassSceneOwner.Overlay),
    WebBrowserOverlay(GlassSceneOwner.Overlay),

    OpenGLShell(
        owner = GlassSceneOwner.OpenGL,
        ordinaryComposeGlassAllowed = false
    )
}

enum class GlassSceneOwner {
    Page,
    ScrollSubScene,
    Persistent,
    Overlay,
    Specialized,
    OpenGL,
    Fallback
}

@Immutable
data class GlassSceneContext(
    val group: GlassSceneGroup,
    val parentGroup: GlassSceneGroup?
)

val LocalGlassSceneContext = staticCompositionLocalOf {
    GlassSceneContext(
        group = GlassSceneGroup.Unassigned,
        parentGroup = null
    )
}

val LocalGlassSceneGroup: GlassSceneGroup
    @Composable get() = LocalGlassSceneContext.current.group

@Composable
fun GlassSceneScope(
    group: GlassSceneGroup,
    content: @Composable () -> Unit
) {
    val parent = LocalGlassSceneContext.current
    CompositionLocalProvider(
        LocalGlassSceneContext provides GlassSceneContext(
            group = group,
            parentGroup = parent.group.takeUnless { it == GlassSceneGroup.Unassigned }
        )
    ) {
        content()
    }
}

internal fun AppTab.defaultGlassSceneGroup(): GlassSceneGroup = when (this) {
    AppTab.Assistant -> GlassSceneGroup.AssistantPage
    AppTab.Tools -> GlassSceneGroup.ToolsPage
    AppTab.Settings -> GlassSceneGroup.SettingsPage
}
