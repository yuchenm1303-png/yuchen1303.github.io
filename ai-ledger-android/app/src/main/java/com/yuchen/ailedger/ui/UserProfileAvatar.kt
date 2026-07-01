package com.yuchen.ailedger.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun UserProfileAvatar(
    localAvatarPath: String?,
    avatarVersion: Long,
    fallbackText: String,
    size: Dp,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveAvatarPath = localAvatarPath.takeIf { loggedIn }
    val avatarBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = effectiveAvatarPath,
        key2 = avatarVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            effectiveAvatarPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.let { file -> BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
        }
    }

    val shape = CircleShape
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = if (loggedIn) {
                        listOf(
                            Color(0xFF86E8FF).copy(alpha = 0.62f),
                            Color(0xFF335FD7).copy(alpha = 0.74f),
                            Color(0xFF141A55).copy(alpha = 0.96f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color(0xFF263269).copy(alpha = 0.72f),
                            Color(0xFF11173F).copy(alpha = 0.96f),
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (loggedIn) 0.42f else 0.20f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val image = avatarBitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "用户头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color(0xFF07132D).copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = fallbackText.ifBlank { "AI" }.take(2),
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = (size.value * if (fallbackText.length > 1) 0.26f else 0.37f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
