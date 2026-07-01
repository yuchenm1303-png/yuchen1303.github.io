package com.yuchen.ailedger.service

import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualIntelligenceDiagnosticsReportTest {
    @Test
    fun reportKeepsExecutionSignalsSuppressesNodeNoiseAndBuildsReplayHtml() {
        val session = Files.createTempDirectory("visual-diagnostics-report").toFile()
        try {
            session.resolve("summary.json").writeText(
                JSONObject().apply {
                    put("taskId", 7L)
                    put("goal", "在测试应用里完成一次操作")
                    put("status", "任务已暂停")
                }.toString()
            )
            session.resolve("trace.jsonl").writeText(
                listOf(
                    event(
                        type = "screen_observation",
                        turnId = "",
                        indexTime = 100L,
                        extra = JSONObject().apply {
                            put("structuralFingerprint", "surface-a")
                            put("visual", JSONObject().apply {
                                put("frameFile", "frame_before.jpg")
                                put("differenceHash", "0000000000000000")
                            })
                        },
                    ),
                    event(
                        type = "model_request_memory",
                        turnId = "",
                        indexTime = 110L,
                        extra = JSONObject().apply {
                            put("details", JSONObject().apply {
                                put("actualRequestActions", JSONArray())
                            })
                        },
                    ),
                    event(
                        type = "planned_action",
                        turnId = "",
                        indexTime = 120L,
                        extra = JSONObject().apply {
                            put("details", JSONObject().apply {
                                put("type", "tap_xy")
                                put("targetText", "继续")
                                put("toolArgs", JSONObject().apply {
                                    put("__androidModelX", 0.75)
                                    put("__androidModelY", 0.85)
                                })
                            })
                        },
                    ),
                    event(
                        type = "execution_result",
                        turnId = "",
                        indexTime = 130L,
                        extra = JSONObject().apply {
                            put("details", JSONObject().apply {
                                put("ok", true)
                                put("message", "点击成功，边界保护后实际落点 900,2400")
                                put("summary", "boundaryAdjusted=true")
                            })
                        },
                    ),
                    event(
                        type = "screen_observation",
                        turnId = "",
                        indexTime = 140L,
                        extra = JSONObject().apply {
                            put("structuralFingerprint", "surface-a")
                            put("visual", JSONObject().apply {
                                put("frameFile", "frame_after.jpg")
                                put("differenceHash", "ffffffffffffffff")
                            })
                        },
                    ),
                ).joinToString("\n") { it.toString() } + "\n"
            )

            VisualIntelligenceDiagnosticsReport.build(session)

            val findings = session.resolve("findings.txt").readText()
            assertTrue(findings.contains("点击坐标被边界保护调整"))
            assertFalse(findings.contains("视觉画面明显变化但结构指纹未变化"))

            val html = session.resolve("report.html").readText()
            assertTrue(html.contains("turn-001"))
            assertTrue(html.contains("frame_before.jpg"))
            assertTrue(html.contains("frame_after.jpg"))
            assertTrue(html.contains("marker"))
        } finally {
            session.deleteRecursively()
        }
    }

    @Test
    fun reportReconcilesCurrentBackendDiagnosticsWithoutMissingPayloadWarnings() {
        val session = Files.createTempDirectory("visual-diagnostics-current-backend").toFile()
        try {
            session.resolve("summary.json").writeText(
                JSONObject().apply {
                    put("taskId", 8L)
                    put("goal", "统计当前后端诊断")
                    put("status", "任务已完成")
                }.toString()
            )
            session.resolve("trace.jsonl").writeText(
                listOf(
                    event(
                        type = "model_request_memory",
                        turnId = "turn-001",
                        indexTime = 100L,
                        extra = JSONObject().apply {
                            put("details", JSONObject().apply {
                                put("actualRequestActions", JSONArray())
                            })
                        },
                    ),
                    event(
                        type = "model_response",
                        turnId = "turn-001",
                        indexTime = 200L,
                        extra = JSONObject().apply {
                            put("details", JSONObject().apply {
                                put("ok", true)
                                put("reportSummary", JSONObject().apply {
                                    put("requestBytes", 1_234L)
                                    put("modelResponseBytes", 567L)
                                    put("modelDurationMs", 890L)
                                    put("modelFailureCount", 0)
                                })
                                put("reportDiagnostics", JSONObject().apply {
                                    put("model", JSONObject().apply {
                                        put("calls", JSONArray().put(JSONObject().apply {
                                            put("requestBodyBytes", 1_234L)
                                            put("responseBodyBytes", 567L)
                                            put("sanitizedRequest", JSONObject().apply {
                                                put("model", "gui-plus")
                                            })
                                            put("rawResponse", "{\"output\":\"ok\"}")
                                        }))
                                    })
                                })
                            })
                        },
                    ),
                ).joinToString("\n") { it.toString() } + "\n"
            )

            VisualIntelligenceDiagnosticsReport.build(session)

            val summary = JSONObject(session.resolve("summary.json").readText())
            assertEquals(1, summary.getInt("modelRequestCount"))
            assertEquals(1, summary.getInt("modelResponseCount"))
            assertEquals(0, summary.getInt("modelFailureCount"))
            assertEquals(1_234L, summary.getLong("modelRequestBytes"))
            assertEquals(567L, summary.getLong("modelResponseBytes"))
            assertEquals(890L, summary.getLong("modelDurationMs"))

            val findings = session.resolve("findings.txt").readText()
            assertFalse(findings.contains("尚未采集完整 HTTP 请求体"))
            assertFalse(findings.contains("尚未采集完整 HTTP 响应体"))
        } finally {
            session.deleteRecursively()
        }
    }

    @Test
    fun frameHashDistanceIsStableAndUnsignedSafe() {
        assertEquals(0, VisualDiagnosticFrameAnalyzer.hammingDistance("ffffffffffffffff", "ffffffffffffffff"))
        assertEquals(64, VisualDiagnosticFrameAnalyzer.hammingDistance("0000000000000000", "ffffffffffffffff"))
        assertNull(VisualDiagnosticFrameAnalyzer.hammingDistance("bad", "ffffffffffffffff"))
    }

    private fun event(
        type: String,
        turnId: String,
        indexTime: Long,
        extra: JSONObject,
    ): JSONObject = JSONObject().apply {
        put("type", type)
        if (turnId.isNotBlank()) put("turnId", turnId)
        put("capturedAt", indexTime)
        val keys = extra.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, extra.opt(key))
        }
    }
}
