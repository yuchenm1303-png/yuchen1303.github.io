package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientToolMutationPolicyTest {
    @Test
    fun durableProjectPlanAndLedgerWritesRequireAtMostOnceExecution() {
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(call("project_create")))
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(call("project_apply_edits")))
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(call("plan_create_task")))
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(call("plan_toggle_task")))
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(call("ledger_add_record")))
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(call("ledger_set_budget")))
    }

    @Test
    fun readOnlyToolsDoNotCreatePersistentExecutionRecords() {
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(call("project_read_file")))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(call("project_validate")))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(call("plan_list_tasks")))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(call("plan_get_task")))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(call("ledger_query_summary")))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(call("ledger_list_records")))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(call("unknown_tool")))
    }

    @Test
    fun genericDeviceControlUsesProjectedCanonicalStepForClassification() {
        val generic = call("device_control")

        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(generic, "set_brightness"))
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(generic, "open_app"))
        assertTrue(ClientToolMutationPolicy.requiresAtMostOnce(generic, "clear_app_data"))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(generic, "device_status"))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(generic, "shizuku_status"))
        assertFalse(ClientToolMutationPolicy.requiresAtMostOnce(generic, "unknown_step"))
    }

    private fun call(name: String): CloudClientToolCall = CloudClientToolCall(
        schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
        id = "call-$name",
        name = name,
        arguments = JSONObject(),
    )
}
