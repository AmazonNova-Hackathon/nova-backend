package com.mediagent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MemberAvatar(
    initials: String,
    colorHex: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val color = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        Color(0xFF7F77DD)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}
