package com.yuchen.ailedger.service

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualIntelligenceDiagnosticsReportTest {
    @Test
    fun reportFindsHighValueVisualAgentFailuresAndBuildsReplayHtml() {
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
                                put("actualRequestActions", org.json.JSONArray())
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
                    event(
                        type = "open_app_verification",
                        turnId = "",
                        indexTime = 150L,
                        extra = JSONObject().apply {
                            put("details", JSONObject().apply {
                                put("verified", false)
                                put("reason", "stable_samples_incomplete")
                                put("expectedPackage", "com.example.target")
                                put("actualPackage", "com.example.target")
                                put("stableSamples", 1)
                                put("requiredStableSamples", 2)
                            })
                        },
                    ),
                    event(
                        type = "runtime_progress",
                        turnId = "",
                        indexTime = 160L,
                        extra = JSONObject().apply { put("status", "已输入") },
                    ),
                    event(
                        type = "runtime_progress",
                        turnId = "",
                        indexTime = 170L,
                        extra = JSONObject().apply { put("status", "等待输入") },
                    ),
                ).joinToString("\n") { it.toString() } + "\n"
            )

            VisualIntelligenceDiagnosticsReport.build(session)

            val findings = session.resolve("findings.txt").readText()
            assertTrue(findings.contains("点击坐标被边界保护调整"))
            assertTrue(findings.contains("目标应用已在前台却验证失败"))
            assertTrue(findings.contains("用户已经回复但很快再次被询问"))
            assertTrue(findings.contains("视觉画面明显变化但结构指纹未变化"))

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
