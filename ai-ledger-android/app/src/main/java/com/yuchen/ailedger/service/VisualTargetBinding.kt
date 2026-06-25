package com.yuchen.ailedger.service

interface VisualTargetBinding {
    fun reset()
    fun bind(packageName: String)
}

object GlobalVisualTargetBinding : VisualTargetBinding {
    override fun reset() {
        ForegroundTargetBinding.reset()
    }

    override fun bind(packageName: String) {
        ForegroundTargetBinding.bind(packageName)
    }
}
