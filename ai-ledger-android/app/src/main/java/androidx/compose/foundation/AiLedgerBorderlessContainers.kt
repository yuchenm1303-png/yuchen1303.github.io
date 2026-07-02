@file:Suppress("unused")

package androidx.compose.foundation

import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp

/**
 * AI Ledger 全局无描边容器策略。
 *
 * 只移除 Kotlin / Compose 源码显式声明的 BorderStroke 与 Modifier.border，
 * 保留卡片底色、裁剪、点击区域、雾面玻璃和 OpenGL Shell 渲染。
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
