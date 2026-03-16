package com.mediagent.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mediagent.app.ui.theme.BrandPurple

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .graphicsLayer { this.alpha = alpha }
                    .background(BrandPurple),
            )
        }
    }
}
