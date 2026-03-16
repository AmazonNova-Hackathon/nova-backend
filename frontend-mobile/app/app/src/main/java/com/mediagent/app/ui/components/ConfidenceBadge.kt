package com.mediagent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ConfidenceStyle(val bg: Color, val text: Color, val label: String)

fun confidenceStyle(confidence: Float): ConfidenceStyle = when {
    confidence >= 0.90f -> ConfidenceStyle(Color(0xFFE1F5EE), Color(0xFF0F6E56), "High confidence")
    confidence >= 0.70f -> ConfidenceStyle(Color(0xFFFAEEDA), Color(0xFF633806), "Moderate confidence")
    else -> ConfidenceStyle(Color(0xFFFCEBEB), Color(0xFFA32D2D), "Low confidence — verify with original")
}

@Composable
fun ConfidenceBadge(confidence: Float, modifier: Modifier = Modifier) {
    val style = confidenceStyle(confidence)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(style.bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Confidence",
            tint = style.text,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = " AI extraction: ${(confidence * 100).toInt()}% · ${style.label}",
            fontSize = 12.sp,
            color = style.text,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
