package com.yuchen.ailedger.ui.gl

/**
 * OpenGL Shell 性能约束：按需渲染、单 draw call、纹理只在内容变化时上传、
 * 支持保留交换缓冲时仅清理新旧玻璃区域并集。
 */
internal object OpenGLPerformanceNotes
