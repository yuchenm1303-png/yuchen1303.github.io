package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.model.AppTab

/**
 * 普通 Compose 玻璃父级绘制使用的场景边界。
 *
 * 只覆盖普通 GlassPanel / PressableGlass，不触发 OpenGL、聊天气泡、
 * FrostInfoGlassPanel、InsetGlassSlot 或其他自定义玻璃绘制链。
 */
enum class GlassSceneGroup(
    val owner: GlassSceneOwner
) {
    Unassigned(GlassSceneOwner.Fallback),

    AssistantPage(GlassSceneOwner.Page),

    ToolsPage(GlassSceneOwner.Page),
    ToolsHomePage(GlassSceneOwner.Page),
    LedgerCenterPage(GlassSceneOwner.Page),
    StockMarketPage(GlassSceneOwner.Page),

    SettingsPage(GlassSceneOwner.Page),
    SettingsDebugInnerScroll(GlassSceneOwner.ScrollSubScene),

    GlobalBottomBar(GlassSceneOwner.Persistent)
}

enum class GlassSceneOwner {
    Page,
    ScrollSubScene,
    Persistent,
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (group.owner != GlassSceneOwner.Fallback) {
        OrdinaryGlassSceneHost(
            group = group,
            modifier = modifier,
            renderMode = OrdinaryGlassParentDrawController.renderModeFor(group),
            content = content
        )
    } else {
        val parent = LocalGlassSceneContext.current
        CompositionLocalProvider(
            LocalGlassSceneContext provides GlassSceneContext(
                group = group,
                parentGroup = parent.group.takeUnless {
                    it == GlassSceneGroup.Unassigned
                }
            ),
            // Fallback 必须同时退出外层父级材质接管，否则子级会关闭自身绘制，
            // 但又因 Unassigned 不注册父级节点，最终形成透明空壳。
            LocalOrdinaryGlassSceneState provides null,
            LocalOrdinaryGlassRenderMode provides OrdinaryGlassRenderMode.Shadow
        ) {
            content()
        }
    }
}

internal fun AppTab.defaultGlassSceneGroup(): GlassSceneGroup = when (this) {
    AppTab.Assistant -> GlassSceneGroup.AssistantPage
    // 工具首页与账单页会在各自入口覆盖；未覆盖的当前实际页面就是股票行情页。
    AppTab.Tools -> GlassSceneGroup.StockMarketPage
    AppTab.Settings -> GlassSceneGroup.SettingsPage
}
