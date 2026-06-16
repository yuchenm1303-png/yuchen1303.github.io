package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 旧版全屏统一玻璃层的兼容入口。
 *
 * 当前普通 Compose 玻璃已经全部由 OrdinaryGlassSceneHost 单链接管，Shell 则由各自
 * OpenGL 宿主负责。继续保留旧全屏 Canvas 只会在每次背景刷新时扫描旧 registry，
 * 即使没有可绘制节点也会产生一张常驻全屏绘制节点。
 *
 * 保留函数签名用于兼容现有 App 结构，但不再创建 Canvas，也不参与任何 registry。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun UnifiedGlassBackdropLayer(modifier: Modifier = Modifier) = Unit
