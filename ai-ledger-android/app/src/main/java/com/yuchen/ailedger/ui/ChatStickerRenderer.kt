package com.yuchen.ailedger.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.StructuredDataCard
import org.json.JSONObject

private const val CHAT_STICKER_STRUCTURED_TYPE = "chat_sticker_v1"
private const val CHAT_STICKER_PAYLOAD_SCHEMA = "chat_sticker_payload_v1"
private const val CHAT_STICKER_MAX_BYTES = 128 * 1024
private const val CHAT_STICKER_MAX_DIMENSION = 512

internal fun StructuredDataCard.isChatStickerData(): Boolean {
    return type.equals(CHAT_STICKER_STRUCTURED_TYPE, ignoreCase = true)
}

@Composable
internal fun MessageStickerV1(data: StructuredDataCard) {
    val encodedPayload = data.rawText.orEmpty()
    val sticker = remember(encodedPayload) { decodeChatStickerPayload(encodedPayload) } ?: return

    Image(
        bitmap = sticker.image,
        contentDescription = data.subtitle?.takeIf { it.isNotBlank() } ?: sticker.id,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .sizeIn(maxWidth = 136.dp, maxHeight = 136.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
    )
}

private data class DecodedChatSticker(
    val id: String,
    val image: ImageBitmap,
)

private fun decodeChatStickerPayload(rawText: String): DecodedChatSticker? {
    return runCatching {
        val payload = JSONObject(rawText)
        if (payload.optString("schema") != CHAT_STICKER_PAYLOAD_SCHEMA) return null
        val id = payload.optString("id").trim().takeIf { it.isNotBlank() } ?: return null
        val mimeType = payload.optString("mimeType").trim().lowercase()
        if (mimeType != "image/webp") return null
        val base64Data = payload.optString("base64Data").trim().takeIf { it.isNotBlank() } ?: return null
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        if (bytes.isEmpty() || bytes.size > CHAT_STICKER_MAX_BYTES) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (bitmap.width !in 1..CHAT_STICKER_MAX_DIMENSION || bitmap.height !in 1..CHAT_STICKER_MAX_DIMENSION) {
            bitmap.recycle()
            return null
        }
        DecodedChatSticker(id = id, image = bitmap.asImageBitmap())
    }.getOrNull()
}
