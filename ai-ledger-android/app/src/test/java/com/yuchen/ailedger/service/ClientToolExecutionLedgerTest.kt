package com.yuchen.ailedger.service

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientToolExecutionLedgerTest {
    private lateinit var tempRoot: File
    private lateinit var ledger: ClientToolExecutionLedger

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("ai-ledger-client-tool-ledger-test").toFile()
        ledger = ClientToolExecutionLedger.createForTest(tempRoot)
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun replaysCompletedReceiptWithoutRepeatingExecution() {
        val call = projectCall(
            id = "call-create-1",
            name = "project_create",
            arguments = JSONObject().put("name", "官网").put("files", JSONArray()),
        )
        assertTrue(ledger.begin(call) === ClientToolExecutionDecision.Execute)

        val receipt = JSONObject()
            .put("ok", true)
            .put("status", "created")
            .put("projectId", "project_12345678")
        ledger.complete(call, receipt)

        val replay = ledger.begin(call) as ClientToolExecutionDecision.Replay
        assertEquals("created", replay.receipt.getString("status"))
        assertTrue(replay.receipt.getBoolean("idempotentReplay"))
    }

    @Test
    fun rejectsSameIdWithDifferentArgumentsAndStopsInflightRetry() {
        val call = projectCall(
            id = "call-write-1",
            name = "project_write_files",
            arguments = JSONObject().put("projectId", "project_12345678").put("baseRevisionId", "rev_000001"),
        )
        assertTrue(ledger.begin(call) === ClientToolExecutionDecision.Execute)

        val inflight = ledger.begin(call) as ClientToolExecutionDecision.Reject
        assertEquals("tool_execution_state_unknown", inflight.code)

        val conflicting = call.copy(arguments = JSONObject(call.arguments.toString()).put("baseRevisionId", "rev_000002"))
        val conflict = ledger.begin(conflicting) as ClientToolExecutionDecision.Reject
        assertEquals("tool_call_id_conflict", conflict.code)
    }

    private fun projectCall(id: String, name: String, arguments: JSONObject) = CloudClientToolCall(
        schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
        id = id,
        name = name,
        arguments = arguments,
    )
}
