package com.yuchen.ailedger.ui

import androidx.compose.ui.Modifier

/**
 * 旧版全屏统一玻璃层的兼容入口。
 *
 * 当前普通 Compose 玻璃已经全部由 OrdinaryGlassSceneHost 单链接管，Shell 则由各自
 * OpenGL 宿主负责。旧 Canvas 和旧 registry 均不再参与生产渲染。
 *
 * 暂时保留调用签名以兼容 App 根结构；它不是 Composable，也不建立 Composition Group，
 * 内联后不会留下全屏空绘制节点或运行时函数调用。
 */
@Suppress("UNUSED_PARAMETER", "NOTHING_TO_INLINE")
inline fun UnifiedGlassBackdropLayer(modifier: Modifier = Modifier) = Unit
