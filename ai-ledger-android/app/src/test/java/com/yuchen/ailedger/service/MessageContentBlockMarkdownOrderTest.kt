package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.CodeContentBlock
import com.yuchen.ailedger.model.ImageContentBlock
import com.yuchen.ailedger.model.TableContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentBlockMarkdownOrderTest {
    @Test
    fun preservesOriginalMixedBlockOrder() {
        val extraction = MessageContentBlockMarkdownExtractor.extract(
            """
                开始。

                ![先出现的图片](https://example.com/first.png)

                | 项目 | 数值 |
                | --- | ---: |
                | 电流 | 2 A |

                ```kotlin
                val last = true
                ```

                结束。
            """.trimIndent(),
        )

        assertEquals("开始。\n\n结束。", extraction.reply)
        assertEquals(3, extraction.blocks.size)
        assertTrue(extraction.blocks[0] is ImageContentBlock)
        assertTrue(extraction.blocks[1] is TableContentBlock)
        assertTrue(extraction.blocks[2] is CodeContentBlock)
    }
}
