package com.yuchen.ailedger.service

/**
 * Visual execution entry modes.
 *
 * VisualForce is the homepage Agent switch meaning: force GUI/computer-use style planning.
 * ExplicitAgent is the cloud Final Chat Model selected computer_run_task path.
 *
 * NormalChatDeviceTool is kept only as a source-compatibility tombstone for old call sites.
 * New normal-chat tool decisions must come from the cloud native tool loop and must not start a
 * local semantic probe on Android.
 */
enum class AgentExecutionMode {
    NormalChatDeviceTool,
    VisualForce,
    ExplicitAgent,
}
