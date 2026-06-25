package com.yuchen.ailedger.service

interface VisualTargetBinding {
    fun reset()
    fun bind(packageName: String)
}

/**
 * Keeps one session's binding side effects idempotent. Re-verifying the same exact package must not
 * repeatedly mutate the process-wide binding, while a genuine target-package change still does.
 */
class SessionVisualTargetBinding(
    private val delegate: VisualTargetBinding,
) : VisualTargetBinding {
    private var lastBoundPackage: String = ""

    override fun reset() {
        lastBoundPackage = ""
        delegate.reset()
    }

    override fun bind(packageName: String) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank() || cleanPackage == lastBoundPackage) return
        lastBoundPackage = cleanPackage
        delegate.bind(cleanPackage)
    }
}

object GlobalVisualTargetBinding : VisualTargetBinding {
    override fun reset() {
        ForegroundTargetBinding.reset()
    }

    override fun bind(packageName: String) {
        ForegroundTargetBinding.bind(packageName)
    }
}
