package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OrganizationFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = if (selected) OrganizationAccent.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(
            1.dp,
            if (selected) OrganizationAccent.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f),
        ),
    ) {
        Text(
            label,
            color = if (selected) OrganizationAccent else Color.White.copy(alpha = 0.64f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun OrganizationPrimaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = OrganizationAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, OrganizationAccent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = OrganizationAccent.copy(alpha = if (enabled) 0.92f else 0.34f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
internal fun OrganizationTextAction(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.045f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
internal fun OrganizationSecondaryAction(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.70f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(11.dp),
        )
    }
}

@Composable
internal fun OrganizationDangerAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.composeGlassMotionClickable(shape = shape, enabled = enabled, onClick = onClick),
        shape = shape,
        color = OrganizationCritical.copy(alpha = if (enabled) 0.12f else 0.04f),
        border = BorderStroke(1.dp, OrganizationCritical.copy(alpha = if (enabled) 0.26f else 0.07f)),
    ) {
        Text(
            text,
            color = OrganizationCritical.copy(alpha = if (enabled) 0.90f else 0.32f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(11.dp),
        )
    }
}
