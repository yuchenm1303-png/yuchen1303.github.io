@file:Suppress("unused")

package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/**
 * AI Ledger 全局容器视觉策略。
 *
 * 1. 移除 Kotlin / Compose 源码显式声明的 BorderStroke 与 Modifier.border。
 * 2. 移除旧版雾面信息卡内部重复铺设的全尺寸半透明深蓝遮罩。
 *
 * 深蓝遮罩只按已确认的精确 ARGB 值处理；普通卡片底色、内部小组件、
 * 点击区域、雾面背景采样和 OpenGL Shell 渲染保持不变。
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun BorderStroke(width: Dp, color: Color): BorderStroke =
    BorderStroke(width, SolidColor(Color.Transparent))

@Suppress("UNUSED_PARAMETER")
fun Modifier.border(
    border: BorderStroke,
    shape: Shape = RectangleShape,
): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun Modifier.border(
    width: Dp,
    color: Color,
    shape: Shape = RectangleShape,
): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun Modifier.border(
    width: Dp,
    brush: Brush,
    shape: Shape,
): Modifier = this

/**
 * 精确拦截旧版 FrostInfoGlassPanel 内容根节点上的重复深蓝染色层。
 *
 * 使用单参数重载，使其他颜色仍回落到 Compose 原生 background(color, shape)，
 * 不改变普通背景、图标底色、标签底色或带显式 shape 的背景。
 */
fun Modifier.background(color: Color): Modifier =
    if (color.isAiLedgerRedundantFrostTint()) {
        this
    } else {
        background(color = color, shape = RectangleShape)
    }

private fun Color.isAiLedgerRedundantFrostTint(): Boolean = when (toArgb()) {
    0x3D101743, // 操作学习：新建学习
    0x38141842, // 操作学习：演示前准备
    0x3810153A, // 操作学习：学习方式
    0x3312163D, // 操作学习：空状态
    0x2E101536, // 操作学习：安全边界
    0x4F121743, // 存储 / 应用详情主卡 31%
    0x4D121743, // 智能分析 / 精细整理主卡 30%
    0x45121743, // 存储 / 应用详情分区 27%
    0x47111742, // 应用控制统计卡 28%
    0x40111742, // 应用搜索框 25%
    0x47151A4F, // 未完成工具占位卡 28%
    -> true
    else -> false
}
