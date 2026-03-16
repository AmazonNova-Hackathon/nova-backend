package com.mediagent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagent.app.util.SaMDStringHelper

enum class DisclaimerVariant { FULL, SHORT, INLINE }

@Composable
fun DisclaimerBar(
    variant: DisclaimerVariant = DisclaimerVariant.FULL,
    modifier: Modifier = Modifier,
) {
    val text = when (variant) {
        DisclaimerVariant.FULL -> SaMDStringHelper.disclaimerFull
        DisclaimerVariant.SHORT -> SaMDStringHelper.disclaimerShort
        DisclaimerVariant.INLINE -> SaMDStringHelper.disclaimerInline
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F8F6))
            .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(0.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color(0xFF888780),
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
