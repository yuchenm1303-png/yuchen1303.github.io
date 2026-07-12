package com.yuchen.ailedger.service

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientToolReceiptDeliveryTest {
    private lateinit var tempRoot: File
    private lateinit var outbox: ClientToolReceiptOutbox

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("ai-ledger-client-tool-outbox-test").toFile()
        outbox = ClientToolReceiptOutbox.createForTest(tempRoot)
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun keepsExactReceiptUntilSuccessfulReport() {
        val receipt = receipt("call-1", "created")
        assertEquals("call-1", outbox.enqueue(receipt))
        val baseTime = System.currentTimeMillis()

        val firstClaim = outbox.claim("call-1", now = baseTime)
        assertTrue(firstClaim is ClientToolReceiptClaim.Ready)
        val first = firstClaim as ClientToolReceiptClaim.Ready
        assertEquals("created", first.pending.receipt.getString("status"))
        assertEquals(1, first.pending.attemptCount)
        assertTrue(outbox.claim("call-1", now = baseTime + 1L) === ClientToolReceiptClaim.Wait)

        outbox.acknowledge("call-1", "已完成", now = baseTime + 2_000L)
        assertEquals("reported", outbox.statusForTest("call-1"))
        assertTrue(outbox.claim("call-1", now = baseTime + 3_000L) === ClientToolReceiptClaim.Done)

        outbox.enqueue(receipt)
        assertEquals("reported", outbox.statusForTest("call-1"))
    }

    @Test
    fun releasesFailedDeliveryWithoutReexecutingTool() {
        outbox.enqueue(receipt("call-2", "updated"))
        val baseTime = System.currentTimeMillis()
        val firstClaim = outbox.claim("call-2", now = baseTime)
        assertTrue(firstClaim is ClientToolReceiptClaim.Ready)
        val first = firstClaim as ClientToolReceiptClaim.Ready
        outbox.release("call-2", IOException("offline"), now = baseTime + 100L)

        assertEquals("pending", outbox.statusForTest("call-2"))
        assertTrue(outbox.claim("call-2", now = baseTime + 5_000L) === ClientToolReceiptClaim.Wait)
        val retriedClaim = outbox.claim("call-2", now = baseTime + 10_100L)
        assertTrue(retriedClaim is ClientToolReceiptClaim.Ready)
        val retried = retriedClaim as ClientToolReceiptClaim.Ready
        assertEquals(2, retried.pending.attemptCount)
        assertEquals(first.pending.receipt.toString(), retried.pending.receipt.toString())
    }

    @Test
    fun rejectsConflictingReceiptForSameToolCallId() {
        outbox.enqueue(receipt("call-3", "created"))
        assertThrows(IllegalStateException::class.java) {
            outbox.enqueue(receipt("call-3", "deleted"))
        }
    }

    @Test
    fun extractsOnlyInternalControlReportToolCallId() {
        val payload = JSONObject()
            .put("action", "internal_control_report")
            .put("internalControlReceipt", receipt("call-4", "verified"))
        assertEquals("call-4", ClientToolReceiptDeliveryRuntime.toolCallIdFromReportPayload(payload))
        assertEquals(
            null,
            ClientToolReceiptDeliveryRuntime.toolCallIdFromReportPayload(
                JSONObject(payload.toString()).put("action", "chat"),
            ),
        )
    }

    private fun receipt(toolCallId: String, status: String): JSONObject = JSONObject()
        .put("protocol", AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL)
        .put("toolCallId", toolCallId)
        .put("toolName", "project_write_files")
        .put("toolArguments", JSONObject().put("projectId", "project_12345678"))
        .put("finalModel", "deepseek_v4")
        .put("status", status)
        .put("completed", true)
        .put("handled", true)
}
