package com.mediagent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.SaMDStringHelper

data class SeverityStyle(val bg: Color, val text: Color, val border: Color)

fun severityStyle(severity: String): SeverityStyle = when (severity) {
    "urgent" -> SeverityStyle(UrgentBg, UrgentText, UrgentBorder)
    "attention" -> SeverityStyle(AttentionBg, AttentionText, AttentionBorder)
    "informational" -> SeverityStyle(InfoBg, InfoText, InfoBorder)
    else -> SeverityStyle(InfoBg, InfoText, InfoBorder)
}

@Composable
fun SeverityBadge(severity: String, modifier: Modifier = Modifier) {
    val style = severityStyle(severity)
    Text(
        text = SaMDStringHelper.severityLabel(severity),
        fontSize = 11.sp,
        color = style.text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(style.bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
