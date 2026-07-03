package com.yuchen.ailedger.service

/**
 * Retained only as a source-compatibility boundary for older callers and contract checks.
 *
 * Normal chat intent and tool selection are owned exclusively by the cloud Final Chat Model.
 * Android must never inspect user wording to decide whether device, ledger, memory or visual tools
 * should be offered or executed.
 */
internal object NormalChatDeviceIntentPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldProbe(text: String): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun shouldIncludeInstalledApps(text: String): Boolean = false
}
