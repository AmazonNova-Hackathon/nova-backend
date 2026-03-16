package com.mediagent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagent.app.data.model.Observation
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.SaMDStringHelper

@Composable
fun ObservationRow(obs: Observation, modifier: Modifier = Modifier) {
    val interpColors = interpretationColors(obs.interpretation)
    val borderColor = interpColors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(interpColors.bg.copy(alpha = 0.35f))
            .drawBehind {
                drawRect(
                    color = borderColor,
                    topLeft = Offset.Zero,
                    size = Size(4.dp.toPx(), size.height),
                )
            }
            .padding(start = 4.dp) // offset for the left border
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = obs.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Text(
                text = obs.loincCode,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextTertiary,
            )
            Spacer(Modifier.height(2.dp))
            val refText = if (obs.normalLow == 0.0 && obs.normalHigh == 0.0) {
                "Ref: N/A"
            } else {
                "Ref: ${obs.normalLow} – ${obs.normalHigh} ${obs.unit}"
            }
            Text(
                text = refText,
                fontSize = 11.sp,
                color = TextTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${obs.value} ${obs.unit}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = interpColors.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = SaMDStringHelper.interpretationLabel(obs.interpretation),
                fontSize = 11.sp,
                color = interpColors.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(interpColors.bg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

data class InterpColors(val bg: Color, val text: Color)

fun interpretationColors(code: String): InterpColors = when (code) {
    "N" -> InterpColors(NormalBg, NormalText)
    "H" -> InterpColors(HighBg, HighText)
    "L" -> InterpColors(LowBg, LowText)
    "HH" -> InterpColors(CritHighBg, CritHighText)
    "LL" -> InterpColors(CritLowBg, CritLowText)
    else -> InterpColors(NormalBg, NormalText)
}
