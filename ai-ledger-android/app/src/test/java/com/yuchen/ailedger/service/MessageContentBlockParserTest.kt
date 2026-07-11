package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ActionGroupContentBlock
import com.yuchen.ailedger.model.CalloutContentBlock
import com.yuchen.ailedger.model.ChartContentBlock
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.CodeContentBlock
import com.yuchen.ailedger.model.ImageContentBlock
import com.yuchen.ailedger.model.ImageGalleryContentBlock
import com.yuchen.ailedger.model.KeyValueContentBlock
import com.yuchen.ailedger.model.MessageActionType
import com.yuchen.ailedger.model.MessageChartType
import com.yuchen.ailedger.model.RichTextContentBlock
import com.yuchen.ailedger.model.TableContentBlock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentBlockParserTest {
    @Test
    fun parsesMixedBlocksInServerOrder() {
        val payload = JSONObject().put(
            "contentBlocks",
            JSONArray()
                .put(JSONObject().put("type", "rich_text").put("text", "结论 **成立**"))
                .put(
                    JSONObject()
                        .put("type", "code")
                        .put("language", "kotlin")
                        .put("fileName", "Demo.kt")
                        .put("code", "fun main() = println(\"ok\")"),
                )
                .put(
                    JSONObject()
                        .put("type", "table")
                        .put("columns", JSONArray(listOf("项目", "数值")))
                        .put("rows", JSONArray().put(JSONArray(listOf("电压", "220 V")))),
                )
                .put(
                    JSONObject()
                        .put("type", "line_chart")
                        .put("title", "趋势")
                        .put("series", JSONArray().put(
                            JSONObject()
                                .put("name", "电流")
                                .put("points", JSONArray(listOf(1.0, 2.5, 2.0))),
                        )),
                )
                .put(
                    JSONObject()
                        .put("type", "image")
                        .put("url", "https://example.com/preview.png")
                        .put("caption", "预览图"),
                )
                .put(
                    JSONObject()
                        .put("type", "image_gallery")
                        .put("images", JSONArray(listOf(
                            JSONObject().put("url", "https://example.com/a.png"),
                            JSONObject().put("url", "https://example.com/b.png"),
                        ))),
                )
                .put(
                    JSONObject()
                        .put("type", "key_value")
                        .put("items", JSONArray().put(
                            JSONObject().put("label", "效率").put("value", "96%"),
                        )),
                )
                .put(
                    JSONObject()
                        .put("type", "callout")
                        .put("tone", "warning")
                        .put("text", "请先断电"),
                )
                .put(
                    JSONObject()
                        .put("type", "action_group")
                        .put("actions", JSONArray()
                            .put(JSONObject().put("type", "copy_text").put("label", "复制").put("value", "abc"))
                            .put(JSONObject().put("type", "open_url").put("label", "打开").put("url", "https://example.com"))),
                ),
        )

        val blocks = MessageContentBlockParser.parse(payload)

        assertEquals(9, blocks.size)
        assertTrue(blocks[0] is RichTextContentBlock)
        assertTrue(blocks[1] is CodeContentBlock)
        assertTrue(blocks[2] is TableContentBlock)
        assertEquals(MessageChartType.Line, (blocks[3] as ChartContentBlock).type)
        assertEquals("预览图", (blocks[4] as ImageContentBlock).image.caption)
        assertEquals(2, (blocks[5] as ImageGalleryContentBlock).images.size)
        assertEquals("96%", (blocks[6] as KeyValueContentBlock).items.single().value)
        assertTrue(blocks[7] is CalloutContentBlock)
        val actions = (blocks[8] as ActionGroupContentBlock).actions
        assertEquals(listOf(MessageActionType.CopyText, MessageActionType.OpenUrl), actions.map { it.type })
    }

    @Test
    fun ignoresUnknownBlocksAndUnsafeActions() {
        val payload = JSONObject().put(
            "contentBlocks",
            JSONArray()
                .put(JSONObject().put("type", "unknown").put("value", "ignored"))
                .put(
                    JSONObject()
                        .put("type", "action_group")
                        .put("actions", JSONArray()
                            .put(JSONObject().put("type", "run_shell").put("value", "rm -rf /"))
                            .put(JSONObject().put("type", "open_url").put("url", "javascript:alert(1)"))),
                )
                .put(JSONObject().put("type", "rich_text").put("text", "保留内容")),
        )

        val blocks = MessageContentBlockParser.parse(payload)

        assertEquals(1, blocks.size)
        assertEquals("保留内容", (blocks.single() as RichTextContentBlock).text)
    }

    @Test
    fun contentOnlyResponseIsAValidChatResponse() {
        val data = JSONObject()
            .put("model", "qwen")
            .put(
                "contentBlocks",
                JSONArray().put(
                    JSONObject()
                        .put("type", "table")
                        .put("columns", JSONArray(listOf("A", "B")))
                        .put("rows", JSONArray().put(JSONArray(listOf("1", "2")))),
                ),
            )
        val response = AiWorkerResponseParser.parse(
            data = data,
            body = data.toString(),
            payload = JSONObject(),
            route = AiWorkerModelRoute(
                requested = ChatModel.Kimi,
                resolved = ChatModel.Kimi,
                reason = "test",
            ),
        )

        assertTrue(response.reply.isBlank())
        assertEquals(1, response.contentBlocks.size)
        assertFalse(response.contentBlocks.first() !is TableContentBlock)
    }

    @Test
    fun capsLargeTablesAndActionGroups() {
        val rows = JSONArray().apply {
            repeat(120) { index ->
                put(JSONArray(listOf("行 $index", index)))
            }
        }
        val actions = JSONArray().apply {
            repeat(20) { index ->
                put(
                    JSONObject()
                        .put("type", "copy_text")
                        .put("label", "复制 $index")
                        .put("value", index.toString()),
                )
            }
        }
        val payload = JSONObject().put(
            "contentBlocks",
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", "table")
                        .put("columns", JSONArray(listOf("名称", "值")))
                        .put("rows", rows),
                )
                .put(
                    JSONObject()
                        .put("type", "action_group")
                        .put("actions", actions),
                ),
        )

        val blocks = MessageContentBlockParser.parse(payload)

        assertEquals(80, (blocks[0] as TableContentBlock).rows.size)
        assertEquals(8, (blocks[1] as ActionGroupContentBlock).actions.size)
    }

    @Test
    fun upgradesLegacyMarkdownWithoutDuplicatingHeavyContent() {
        val reply = """
            下面是结果。

            ```kotlin Demo.kt
            val voltage = 220
            println(voltage)
            ```

            | 项目 | 数值 |
            | --- | ---: |
            | 电压 | 220 V |

            ![接线示意图](https://example.com/wiring.png "实验接线")

            以上内容可直接使用。
        """.trimIndent()
        val data = JSONObject().put("reply", reply).put("model", "qwen")

        val response = AiWorkerResponseParser.parse(
            data = data,
            body = data.toString(),
            payload = JSONObject(),
            route = AiWorkerModelRoute(ChatModel.Kimi, ChatModel.Kimi, "test"),
        )

        assertEquals("下面是结果。\n\n以上内容可直接使用。", response.reply)
        assertEquals(3, response.contentBlocks.size)
        assertEquals("Demo.kt", (response.contentBlocks[0] as CodeContentBlock).fileName)
        assertEquals("220 V", (response.contentBlocks[1] as TableContentBlock).rows.single()[1])
        assertEquals("实验接线", (response.contentBlocks[2] as ImageContentBlock).image.caption)
    }

    @Test
    fun upgradesChartProtocolFenceToTypedChart() {
        val reply = """
            数据趋势如下。

            ```chart
            {"type":"bar","title":"功率","labels":["A","B"],"values":[12,18]}
            ```
        """.trimIndent()
        val data = JSONObject().put("reply", reply).put("model", "qwen")

        val response = AiWorkerResponseParser.parse(
            data = data,
            body = data.toString(),
            payload = JSONObject(),
            route = AiWorkerModelRoute(ChatModel.Kimi, ChatModel.Kimi, "test"),
        )

        assertEquals("数据趋势如下。", response.reply)
        val chart = response.contentBlocks.single() as ChartContentBlock
        assertEquals(MessageChartType.Bar, chart.type)
        assertEquals(listOf(12.0, 18.0), chart.series.single().points.map { it.y })
    }

    @Test
    fun explicitServerBlocksTakePriorityOverMarkdownFallback() {
        val reply = """
            ```kotlin
            val shouldRemainText = true
            ```
        """.trimIndent()
        val data = JSONObject()
            .put("reply", reply)
            .put(
                "contentBlocks",
                JSONArray().put(
                    JSONObject()
                        .put("type", "table")
                        .put("columns", JSONArray(listOf("A", "B")))
                        .put("rows", JSONArray().put(JSONArray(listOf("1", "2")))),
                ),
            )

        val response = AiWorkerResponseParser.parse(
            data = data,
            body = data.toString(),
            payload = JSONObject(),
            route = AiWorkerModelRoute(ChatModel.Kimi, ChatModel.Kimi, "test"),
        )

        assertEquals(reply, response.reply)
        assertEquals(1, response.contentBlocks.size)
        assertTrue(response.contentBlocks.single() is TableContentBlock)
    }
}
