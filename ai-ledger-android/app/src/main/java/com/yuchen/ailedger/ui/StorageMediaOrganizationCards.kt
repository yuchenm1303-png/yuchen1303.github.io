package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.BurstPhotoGroup
import com.yuchen.ailedger.service.SimilarPhotoGroup
import com.yuchen.ailedger.service.StorageMediaOrganizationRepository
import com.yuchen.ailedger.service.StorageOrganizationFile
import com.yuchen.ailedger.service.StorageOrganizationSnapshot
import com.yuchen.ailedger.service.loadCachedOrganizationThumbnail
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun OrganizationOverview(
    snapshot: StorageOrganizationSnapshot,
    onOpen: (StorageOrganizationTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OrganizationOverviewCard("相似照片", "${snapshot.similarGroups.size} 组 · ${snapshot.similarPhotoCount} 张", "缩略图视觉接近，不等于完全重复", OrganizationCaution) {
            onOpen(StorageOrganizationTab.Similar)
        }
        OrganizationOverviewCard("截图", "${snapshot.screenshots.size} 张", "依据截图目录和文件名识别", OrganizationAccent) {
            onOpen(StorageOrganizationTab.Screenshots)
        }
        OrganizationOverviewCard("连拍候选", "${snapshot.burstGroups.size} 组 · ${snapshot.burstPhotoCount} 张", "依据 BURST 名称或同目录 4 秒内连续照片识别", OrganizationCaution) {
            onOpen(StorageOrganizationTab.Bursts)
        }
        OrganizationOverviewCard("画质候选", "${snapshot.qualityCandidates.size} 张", "缩略图锐度或分辨率提示，需要人工预览", OrganizationWarning) {
            onOpen(StorageOrganizationTab.Quality)
        }
        OrganizationOverviewCard("授权目录分类", "${snapshot.downloadFileCount} 个文件", "安装包、压缩包、文档、媒体和其他大文件", OrganizationSuccess) {
            onOpen(StorageOrganizationTab.Downloads)
        }
    }
}

@Composable
internal fun OrganizationAnalysisPanel(
    snapshot: StorageOrganizationSnapshot?,
    analyzing: Boolean,
    includeMedia: Boolean,
    hasFolder: Boolean,
    ignoredCount: Int,
    onAnalyze: () -> Unit,
    onClearIgnoreRules: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.09f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF121743).copy(alpha = 0.30f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("当前范围", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            includeMedia && hasFolder -> "共享图片 + 授权目录"
                            includeMedia -> "共享图片"
                            hasFolder -> "授权目录"
                            else -> "尚未授权可分析范围"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (analyzing) CircularProgressIndicator(modifier = Modifier.size(23.dp), strokeWidth = 2.dp, color = OrganizationAccent)
            }
            snapshot?.let {
                OrganizationMetric("图片索引", "${it.indexedImageCount} 张")
                OrganizationMetric("感知与画质分析", "${it.perceptualHashedCount} 张")
                OrganizationMetric("画质候选", "${it.qualityCandidates.size} 张")
                OrganizationMetric("授权目录分类", "${it.indexedFolderCount} 个")
                OrganizationMetric("分析耗时", formatOrganizationElapsed(it.elapsedMs))
                if (it.limited) Text("已触发性能保护上限，结果只代表本轮已完成范围。", color = OrganizationWarning, fontSize = 10.sp)
            }
            OrganizationPrimaryAction(
                text = when {
                    analyzing -> "正在分析照片与授权目录…"
                    snapshot == null -> "开始照片与目录分析"
                    else -> "重新分析照片与目录"
                },
                enabled = !analyzing && (includeMedia || hasFolder),
                onClick = onAnalyze,
            )
            if (ignoredCount > 0) OrganizationTextAction("清空忽略规则 · $ignoredCount 条", onClearIgnoreRules)
            if (!includeMedia && !hasFolder) Text("请先在基础存储管理中授权共享图片或选择目录。", color = OrganizationWarning, fontSize = 10.5.sp)
        }
    }
}

@Composable
internal fun SimilarPhotoGroupCard(
    group: SimilarPhotoGroup,
    selectedIds: Set<String>,
    onToggle: (StorageOrganizationFile) -> Unit,
    onPreview: (StorageOrganizationFile) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = OrganizationCaution.copy(alpha = 0.065f),
        border = BorderStroke(1.dp, OrganizationCaution.copy(alpha = 0.17f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("视觉相似 · ${group.files.size} 张", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text("最大感知距离 ${group.maxHashDistance} · 批量选择只会加入待清理列表，删除前仍需预览确认。", color = Color.White.copy(alpha = 0.45f), fontSize = 9.8.sp)
            group.files.forEach { file ->
                OrganizationFileCard(file, file.stableId in selectedIds, { onToggle(file) }, { onPreview(file) })
            }
        }
    }
}

@Composable
internal fun BurstPhotoGroupCard(
    group: BurstPhotoGroup,
    selectedIds: Set<String>,
    onToggle: (StorageOrganizationFile) -> Unit,
    onPreview: (StorageOrganizationFile) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = OrganizationCaution.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, OrganizationCaution.copy(alpha = 0.15f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (group.explicitBurstName) "明确连拍文件 · ${group.files.size} 张" else "时间相邻照片 · ${group.files.size} 张",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (group.explicitBurstName) "文件名含 BURST 或连拍标记。" else "同一目录内照片时间连续且尺寸接近，不保证来自同一次拍摄。",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.8.sp,
            )
            group.files.forEach { file ->
                OrganizationFileCard(file, file.stableId in selectedIds, { onToggle(file) }, { onPreview(file) })
            }
        }
    }
}

@Composable
internal fun OrganizationFileCard(
    file: StorageOrganizationFile,
    selected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    val cardShape = RoundedCornerShape(19.dp)
    val checkShape = RoundedCornerShape(7.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().composeGlassMotionClickable(shape = cardShape, onClick = onPreview),
        shape = cardShape,
        color = riskTone(file.risk).copy(alpha = if (selected) 0.13f else 0.045f),
        border = BorderStroke(1.dp, riskTone(file.risk).copy(alpha = if (selected) 0.34f else 0.11f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(23.dp)
                    .composeGlassMotionClickable(shape = checkShape, enabled = file.canDelete, onClick = onToggle)
                    .clip(checkShape)
                    .background(if (selected) riskTone(file.risk).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (selected) "✓" else "", color = Color(0xFF101638), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            OrganizationInlineThumbnail(file)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.displayName,
                        color = Color.White.copy(alpha = 0.91f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatOrganizationBytes(file.sizeBytes), color = riskTone(file.risk), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                }
                Text("${file.kind.label} · ${file.risk.label} · ${formatOrganizationDate(file.modifiedAt)}", color = Color.White.copy(alpha = 0.44f), fontSize = 9.3.sp)
                file.reviewNote.takeIf(String::isNotBlank)?.let { note ->
                    Text(note, color = riskTone(file.risk).copy(alpha = 0.75f), fontSize = 8.9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(file.location, color = Color.White.copy(alpha = 0.29f), fontSize = 8.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!file.canDelete) Text("文档提供方未开放删除能力", color = OrganizationWarning, fontSize = 8.8.sp)
            }
        }
    }
}

@Composable
private fun OrganizationInlineThumbnail(file: StorageOrganizationFile) {
    val shape = RoundedCornerShape(12.dp)
    val isImage = file.mimeType.startsWith("image/", ignoreCase = true)
    if (!isImage) {
        Box(
            modifier = Modifier.size(62.dp).clip(shape).background(Color.White.copy(alpha = 0.055f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = organizationFileTypeLabel(file),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 11.sp,
                modifier = Modifier.padding(5.dp),
            )
        }
        return
    }

    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        StorageMediaOrganizationRepository(context.applicationContext)
    }
    val thumbnailState by produceState(
        initialValue = InlineThumbnailState(),
        key1 = file.uri,
        key2 = file.modifiedAt,
    ) {
        val bitmap = withContext(Dispatchers.IO) {
            repository.loadCachedOrganizationThumbnail(file, maxEdgePx = 160)
        }
        value = InlineThumbnailState(bitmap = bitmap, complete = true)
    }
    val bitmap = thumbnailState.bitmap

    Box(
        modifier = Modifier.size(62.dp).clip(shape).background(Color.White.copy(alpha = 0.055f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${file.displayName} 缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            !thumbnailState.complete -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
                color = OrganizationAccent.copy(alpha = 0.70f),
            )
            else -> Text(
                "无预览",
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun organizationFileTypeLabel(file: StorageOrganizationFile): String {
    val extension = file.displayName.substringAfterLast('.', missingDelimiterValue = "")
        .uppercase(Locale.ROOT)
        .take(5)
    return extension.ifBlank { file.kind.label.take(4) }
}

private data class InlineThumbnailState(
    val bitmap: Bitmap? = null,
    val complete: Boolean = false,
)
