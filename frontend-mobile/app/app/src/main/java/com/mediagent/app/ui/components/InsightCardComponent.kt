package com.mediagent.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagent.app.data.model.InsightCard
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.FhirDateFormatter
import com.mediagent.app.util.SaMDStringHelper

@Composable
fun InsightCardComponent(
    insight: InsightCard,
    onMarkRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val style = severityStyle(insight.severity)
    val unreadBg = if (!insight.read) Color(0x087F77DD) else Color.White

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = unreadBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE8E8E8)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = style.border,
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                    )
                }
                .padding(start = 4.dp)
                .padding(12.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(insight.severity)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = insight.memberName.split(" ").firstOrNull()?.ifEmpty { "?" } ?: "?",
                    fontSize = 11.sp,
                    color = TextTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(SurfaceTertiary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = FhirDateFormatter.toDisplay(insight.generatedAt),
                    fontSize = 11.sp,
                    color = TextTertiary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = insight.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = insight.summary,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (insight.citedObservations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${insight.citedObservations.size} cited observation(s)",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF0F6E56),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE1F5EE))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            if (expanded && insight.suggestedAction.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "↳ ${insight.suggestedAction}",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ⓘ ${SaMDStringHelper.disclaimerInline}",
                    fontSize = 11.sp,
                    color = TextTertiary,
                )
                Spacer(Modifier.weight(1f))
                if (!insight.read) {
                    TextButton(
                        onClick = { onMarkRead(insight.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Mark read", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark read", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
