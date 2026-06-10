package com.yuchen.ailedger.service

/**
 * Separates the homepage Agent switch from internal device control.
 *
 * VisualForce is the old homepage Agent switch meaning: force GUI/computer-use style planning.
 * Internal device tools are allowed to run from normal chat when the cloud brain returns a
 * structured device_tool step, so they must not be gated by this switch.
 */
enum class AgentExecutionMode {
    NormalChatDeviceTool,
    VisualForce,
    ExplicitAgent,
}
